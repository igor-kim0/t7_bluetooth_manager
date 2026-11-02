package com.dash.btmanager.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dash.btmanager.BuildConfig
import com.dash.btmanager.bt.BtManager
import com.dash.btmanager.obd.ElmHub
import com.dash.btmanager.obd.ObdProfile
import com.dash.btmanager.obd.ProfileStore
import com.dash.btmanager.ui.theme.BackgroundDrawable
import com.dash.btmanager.ui.theme.DashTheme
import com.dash.btmanager.ui.theme.HeroView
import com.dash.btmanager.ui.theme.IconView
import com.dash.btmanager.ui.theme.PanelDrawable
import com.dash.btmanager.ui.theme.TabDrawable
import com.dash.btmanager.ui.theme.ThemedSwitch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Единственный экран под горизонтальный экран ГУ (1920×720), оформлен
 * как настройки самого ГУ (темы «Классика» и «Комфорт», см. [DashTheme]).
 * Вкладки:
 *  - Bluetooth ([BtManager]): питание адаптера, скан, спаренные
 *    и найденные устройства, спаривание с автоответом PIN, журнал;
 *  - OBD ([ElmHub]): выбранный адаптер ELM327, статус связи, все
 *    показатели и последние обмены «команда → ответ»;
 *  - Данные: JSON-профили автомобиля ([ProfileStore]) — загрузка, выбор
 *    и все величины выбранного профиля по группам (какие показывать —
 *    диалог «Какие данные показывать»);
 *  - Экран: выбор темы оформления.
 *
 * Lite-вариант ([BuildConfig.LITE] = true) содержит только вкладку Bluetooth:
 * поиск и спаривание устройств + журнал; OBD, профили и темы там нет.
 *
 * Тап по неспаренному устройству — спарить; по спаренному — меню: сделать
 * адаптером OBD, проверить SPP-подключение, забыть пару.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false
    private val ticker = object : Runnable {
        override fun run() {
            render()
            if (ticking) handler.postDelayed(this, REFRESH_MS)
        }
    }

    private lateinit var theme: DashTheme

    // Вкладки
    private val tabs = ArrayList<Pair<TextView, View>>()
    private var currentTab = 0

    // Bluetooth
    private lateinit var btStatus: TextView
    private lateinit var btInfo: TextView
    private lateinit var btHint: TextView
    private lateinit var powerSwitch: ThemedSwitch
    private lateinit var autoSwitch: ThemedSwitch
    private lateinit var pinValue: TextView
    private lateinit var scanValue: TextView
    private lateinit var scanIcon: FrameLayout
    private lateinit var deviceList: LinearLayout
    private lateinit var btLog: TextView
    private lateinit var btLogScroll: ScrollView
    private var deviceSignature = ""

    // OBD
    private lateinit var obdAdapter: TextView
    private lateinit var obdLink: TextView
    private lateinit var obdStatus: TextView
    private lateinit var obdTrace: TextView
    private lateinit var obdProfile: TextView
    private lateinit var obdBuiltin: View
    private lateinit var obdProfileNote: View
    private val cells = HashMap<String, TextView>()

    // Данные профиля
    private lateinit var dataBox: LinearLayout
    private lateinit var profileList: LinearLayout
    private var dataInfo: TextView? = null
    private val profileCells = ArrayList<Pair<TextView, ObdProfile.Param>>()
    private var dataSignature: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme = DashTheme.load(this)
        applyTheme()
        BtManager.start(this)
        if (!BuildConfig.LITE) ElmHub.start(this)
        requestLocation()
    }

    override fun onResume() {
        super.onResume()
        ticking = true
        handler.post(ticker)
    }

    override fun onPause() {
        ticking = false
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    /** Android 9 отдаёт результаты скана только с разрешением на геопозицию. */
    private fun requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOCATION
            )
        }
    }

    /** Пересобирает экран под [theme]; открытая вкладка сохраняется. */
    private fun applyTheme() {
        window.setBackgroundDrawable(BackgroundDrawable(theme))
        window.statusBarColor = theme.bgTop
        tabs.clear()
        cells.clear()
        deviceSignature = ""
        dataSignature = null
        setContentView(buildUi())
        render()
    }

    // ---------- разметка ----------

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(6), dp(24), dp(8))
        }

        val tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pages = FrameLayout(this)
        val panes = ArrayList<Pair<String, View>>().apply {
            add("Bluetooth" to buildBtPane())
            if (!BuildConfig.LITE) {
                add("OBD · ELM327" to buildObdPane())
                add("Данные" to buildDataPane())
                add("Экран" to buildScreenPane())
            }
        }
        panes.forEachIndexed { i, (title, pane) ->
            val tab = text(24f, theme.tabText, title = true).apply {
                text = title
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(10), dp(22), dp(12))
                setOnClickListener { selectTab(i) }
            }
            tabBar.addView(tab, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(18) })
            pages.addView(pane, FrameLayout.LayoutParams(MATCH, MATCH))
            tabs += tab to pane
        }
        root.addView(tabBar)
        root.addView(View(this).apply { setBackgroundColor(theme.tabLine) }, LinearLayout.LayoutParams(MATCH, dp(1)))
        root.addView(pages, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(10) })
        selectTab(currentTab)
        return root
    }

    private fun selectTab(index: Int) {
        currentTab = index
        tabs.forEachIndexed { i, (tab, pane) ->
            val active = i == index
            pane.visibility = if (active) View.VISIBLE else View.GONE
            tab.setTextColor(if (active) theme.accent else theme.tabText)
            tab.background = TabDrawable(this, theme, active)
        }
    }

    private fun buildBtPane(): View {
        powerSwitch = ThemedSwitch(this, theme)
        autoSwitch = ThemedSwitch(this, theme)
        pinValue = value()
        scanValue = value()
        scanIcon = FrameLayout(this)

        val settings = panel()
        addRows(settings,
            row("Bluetooth", powerSwitch) { togglePower() },
            row("Автоответ на запрос спаривания", autoSwitch) {
                BtManager.setAutoAnswerPairing(!BtManager.autoAnswerPairing)
                render()
            },
            row("PIN для спаривания", pinValue, chevron()) { showPin() },
            row("Поиск доступных устройств поблизости", scanValue, scanIcon) {
                if (BtManager.scanning) BtManager.stopScan() else BtManager.scan()
                render()
            },
        )
        btHint = text(20f, theme.warn).apply { setPadding(dp(8), dp(6), dp(8), 0) }
        deviceList = panel()

        val left = column(
            settings,
            btHint,
            caption("Устройства — тап: спарить / действия"),
            deviceList,
        )

        btStatus = text(24f, theme.text, title = true).apply { setPadding(dp(16), dp(8), dp(16), 0) }
        btInfo = text(18f, theme.textDim).apply { setPadding(dp(16), dp(4), dp(16), 0) }
        btLog = mono()
        btLogScroll = ScrollView(this).apply { addView(btLog) }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption("Журнал"))
            addView(panel().apply {
                addView(btStatus)
                addView(btInfo)
                addView(divider())
                addView(btLogScroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
            }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        return split(left, right)
    }

    private fun buildObdPane(): View {
        obdAdapter = value()
        obdLink = value()
        obdProfile = value()
        val settings = panel()
        addRows(settings,
            row("Адаптер OBD", obdAdapter, chevron()) { pickAdapter() },
            row("Профиль автомобиля", obdProfile, chevron()) { pickProfile() },
            row("Состояние связи", obdLink),
            row("Тест подключения", IconView(this, theme, IconView.Kind.REFRESH)) {
                ElmHub.address()?.let { BtManager.testConnect(it) } ?: pickAdapter()
            },
            row("Сбросить адаптер", chevron()) {
                ElmHub.clearDevice(this)
                render()
            },
        )

        val grid = panel()
        METRICS.chunked(GRID_COLUMNS).forEachIndexed { n, line ->
            if (n > 0) grid.addView(divider())
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            line.forEach { m ->
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, dp(6))
                }
                val v = text(40f, theme.text, title = true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                }
                box.addView(v)
                box.addView(text(17f, theme.textDim).apply {
                    text = m.label
                    gravity = Gravity.CENTER
                    maxLines = 1
                })
                cells[m.key] = v
                r.addView(box, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            grid.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        obdBuiltin = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption("Показатели (стандартный OBD-II)"))
            addView(grid, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        obdProfileNote = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption("Показатели"))
            addView(panel().apply {
                addView(row("Данные профиля — на вкладке «Данные»", chevron()) { selectTab(TAB_DATA) })
            })
        }
        val left = column(settings, obdBuiltin, obdProfileNote)

        obdStatus = text(18f, theme.textDim).apply {
            maxLines = 2
            setPadding(dp(16), dp(10), dp(16), 0)
        }
        obdTrace = mono()
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption("Обмен с адаптером"))
            addView(panel().apply {
                addView(obdStatus)
                addView(divider())
                addView(ScrollView(context).apply { addView(obdTrace) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
            }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        return split(left, right)
    }

    /**
     * Вкладка «Данные»: слева величины выбранного профиля по группам
     * (пересобираются при смене профиля в [renderData]), справа список
     * профилей и загрузка JSON.
     */
    private fun buildDataPane(): View {
        val controls = panel()
        addRows(controls, row("Какие данные показывать", chevron()) { pickVisibleParams() })
        dataBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        profileList = panel()
        val load = panel()
        addRows(load,
            row("Открыть JSON-файл…", chevron()) { openJson() },
            row("Найти JSON на флешке и в памяти", IconView(this, theme, IconView.Kind.REFRESH)) { scanJson() },
        )
        val right = column(
            caption("Профиль автомобиля"),
            profileList,
            caption("Загрузка профиля"),
            load,
            text(16f, theme.textDim).apply {
                text = "Поиск смотрит корень флешки и памяти, папки Download, Documents, obd. " +
                    "Загруженный профиль копируется в приложение."
                setPadding(dp(10), dp(8), dp(10), 0)
            },
        )
        return split(column(controls, dataBox), right, leftWeight = 2f)
    }

    private fun buildScreenPane(): View {
        val settings = panel()
        addRows(settings,
            row("Настройки темы", value().apply { text = theme.title }, chevron()) { pickTheme() },
        )
        val themes = panel()
        addRows(themes, *DashTheme.ALL.map { t ->
            val mark = check(t === theme)
            row(t.title, mark) { setTheme(t) }
        }.toTypedArray())

        val left = column(settings, caption("Тема оформления"), themes)
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(HeroView(context, theme, HeroView.Kind.HEX), LinearLayout.LayoutParams(MATCH, WRAP))
            addView(text(24f, theme.text, title = true).apply {
                text = "Тема: ${theme.title}"
                gravity = Gravity.CENTER
            })
            addView(text(18f, theme.textDim).apply {
                text = "Версия: ${versionName()}"
                gravity = Gravity.CENTER
            })
        }
        return split(left, right)
    }

    // ---------- обновление ----------

    private fun render() {
        renderBt()
        if (!BuildConfig.LITE) {
            renderObd()
            renderData()
        }
    }

    @SuppressLint("MissingPermission")
    private fun renderBt() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val on = adapter?.isEnabled == true
        btStatus.text = "Статус Bluetooth: " + when {
            adapter == null -> "нет адаптера"
            !on -> "Выключен"
            !BuildConfig.LITE && ElmHub.connected -> "Подключено к OBD"
            else -> "Включён"
        }
        btInfo.text = BtManager.adapterInfo()
        btHint.text = listOfNotNull(
            "Bluetooth выключен".takeIf { adapter != null && !on },
            "нет разрешения на геопозицию — скан не найдёт устройства".takeIf {
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            },
            "геопозиция выключена — скан может не найти устройства".takeIf { !locationEnabled() },
        ).joinToString(" · ")
        btHint.visibility = if (btHint.text.isEmpty()) View.GONE else View.VISIBLE
        powerSwitch.sync(on)
        autoSwitch.sync(BtManager.autoAnswerPairing)
        pinValue.text = BtManager.pin
        scanValue.text = if (BtManager.scanning) "идёт поиск…" else ""
        val scanKind = if (BtManager.scanning) IconView.Kind.STOP else IconView.Kind.REFRESH
        if (scanIcon.tag != scanKind) {
            scanIcon.tag = scanKind
            scanIcon.removeAllViews()
            scanIcon.addView(IconView(this, theme, scanKind))
        }

        val devices = BtManager.devices()
        // В lite колонки-бейджа «OBD» нет — адаптер выбирается только в полной версии.
        val selected = if (BuildConfig.LITE) null else ElmHub.address()
        val signature = devices.joinToString("|") { "${it.address}/${it.name}/${it.bondState}/${it.rssi}" } + "#$selected"
        if (signature != deviceSignature) {
            deviceSignature = signature
            deviceList.removeAllViews()
            if (devices.isEmpty()) {
                deviceList.addView(text(20f, theme.textDim).apply {
                    text = "нет устройств — запустите поиск"
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                })
            }
            addRows(deviceList, *devices.map { deviceRow(it, it.address == selected) }.toTypedArray())
        }

        val log = BtManager.log().joinToString("\n") { "${hms(it.at)} ${it.text}" }
        if (log != btLog.text.toString()) {
            btLog.text = log
            btLogScroll.post { btLogScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun renderObd() {
        val addr = ElmHub.address()
        obdAdapter.text = if (addr == null) "не выбран" else (ElmHub.name() ?: addr) +
            (ElmHub.method()?.let { " · $it" } ?: "")
        val readings = ElmHub.readings()
        val link = when {
            addr == null -> "выберите адаптер"
            // Напряжение (ATRV) меряет сам адаптер — связь с ЭБУ это не доказывает.
            ElmHub.connected && readings.keys.any { it != "volt" } -> "связь есть"
            ElmHub.connected && readings.isNotEmpty() -> "адаптер есть, ЭБУ молчит"
            ElmHub.connected -> "подключено"
            else -> "нет связи"
        }
        obdLink.text = link
        obdStatus.text = ElmHub.lastStatus
        METRICS.forEach { m ->
            val cell = cells[m.key] ?: return@forEach
            val v = readings[m.key]
            cell.text = v?.let(m.format) ?: "—"
            cell.setTextColor(v?.let { m.color?.invoke(it) } ?: theme.text)
        }
        obdTrace.text = ElmHub.trace().joinToString("\n").ifEmpty { "(пока пусто)" }
        val prof = ElmHub.profile()
        obdProfile.text = prof?.name ?: "стандартный OBD-II"
        obdBuiltin.visibility = if (prof == null) View.VISIBLE else View.GONE
        obdProfileNote.visibility = if (prof == null) View.GONE else View.VISIBLE
    }

    private fun renderData() {
        val prof = ElmHub.profile()
        val signature = listOf(ElmHub.profileId(), System.identityHashCode(prof))
        if (signature != dataSignature) {
            dataSignature = signature
            rebuildData(prof)
            rebuildProfiles()
        }
        dataInfo?.let { info ->
            val vi = ElmHub.vehicleInfo()
            info.text = vi.entries.joinToString("   ·   ") { "${it.key}: ${it.value}" }
            info.visibility = if (vi.isEmpty()) View.GONE else View.VISIBLE
        }
        if (profileCells.isEmpty()) return
        val readings = ElmHub.readings()
        val silent = ElmHub.silent
        for ((cell, p) in profileCells) {
            val key = ElmHub.profileKey(p)
            val v = readings[key]
            cell.text = when {
                v != null -> p.format(v)
                key in silent -> "н/д"
                else -> "—"
            }
            cell.setTextColor(when {
                v == null -> theme.textDim
                p.outOfRange(v) -> theme.warn
                else -> theme.text
            })
        }
    }

    /** Левая часть «Данных»: шапка профиля и сетки величин по группам. */
    private fun rebuildData(prof: ObdProfile?) {
        dataBox.removeAllViews()
        profileCells.clear()
        dataInfo = null
        if (prof == null) {
            dataBox.addView(panel().apply {
                addView(text(22f, theme.text, title = true).apply {
                    text = "Профиль не выбран"
                    setPadding(dp(16), dp(14), dp(16), dp(4))
                })
                addView(text(18f, theme.textDim).apply {
                    text = "Опрашивается стандартный набор OBD-II (вкладка «OBD · ELM327»). " +
                        "Выберите профиль автомобиля справа или загрузите свой JSON."
                    setPadding(dp(16), 0, dp(16), dp(14))
                })
            })
            return
        }
        val hidden = hiddenKeys()
        val hiddenCount = prof.params.count { it.key in hidden }
        dataBox.addView(panel().apply {
            setPadding(paddingLeft + dp(14), paddingTop + dp(8), paddingRight + dp(14), paddingBottom + dp(8))
            addView(text(24f, theme.accent, title = true).apply { text = prof.name })
            prof.description?.let { d -> addView(text(17f, theme.textDim).apply { text = d }) }
            addView(text(17f, theme.textDim).apply {
                text = listOfNotNull(
                    prof.protocol?.let { "протокол $it" + (prof.elmProtocol?.let { n -> " (ATSP$n)" } ?: "") },
                    prof.defaultHeader?.let { "блок $it" },
                    if (hiddenCount == 0) "параметров ${prof.params.size}"
                    else "показано ${prof.params.size - hiddenCount} из ${prof.params.size}",
                    prof.version?.let { "v$it" },
                    prof.author,
                ).joinToString(" · ")
            })
            dataInfo = text(18f, theme.text, title = true).apply {
                setPadding(0, dp(6), 0, 0)
                visibility = View.GONE
            }
            addView(dataInfo)
            if (prof.warnings.isNotEmpty()) {
                addView(text(16f, theme.warn).apply {
                    text = "Пропущено: " + prof.warnings.joinToString("; ")
                    setPadding(0, dp(6), 0, 0)
                })
            }
        })
        if (hiddenCount == prof.params.size) {
            dataBox.addView(panel().apply {
                addView(text(20f, theme.textDim).apply {
                    text = "Все величины скрыты — откройте «Какие данные показывать» и отметьте нужные."
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                })
            })
            return
        }
        for (g in prof.groups) {
            val shown = g.params.filter { it.key !in hidden }
            if (shown.isEmpty()) continue
            dataBox.addView(caption("${g.title} · ${shown.size}"))
            val grid = panel()
            shown.chunked(DATA_COLUMNS).forEachIndexed { n, line ->
                if (n > 0) grid.addView(divider())
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                line.forEach { p -> r.addView(paramCell(p), LinearLayout.LayoutParams(0, WRAP, 1f)) }
                repeat(DATA_COLUMNS - line.size) { r.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f)) }
                grid.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            dataBox.addView(grid)
        }
    }

    private fun paramCell(p: ObdProfile.Param) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(8), dp(6), dp(8))
        val v = text(30f, theme.textDim, title = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            text = "—"
        }
        addView(v)
        addView(text(17f, theme.text, title = true).apply {
            text = p.shortName
            gravity = Gravity.CENTER
            maxLines = 1
        })
        addView(text(14f, theme.textDim).apply {
            text = p.name
            gravity = Gravity.CENTER
            maxLines = 2
        })
        // Тап — подробности: команда, блок, формула, пояснение.
        background = pressable()
        setOnClickListener {
            message(listOfNotNull(
                p.name,
                "команда ${p.command}" + (p.header?.let { " → блок $it" } ?: ""),
                "формула ${p.formula.source}" + (p.unit.takeIf { it.isNotEmpty() }?.let { ", $it" } ?: ""),
                if (p.min != null || p.max != null) "диапазон ${p.min ?: "…"} … ${p.max ?: "…"}" else null,
                p.note,
            ).joinToString("\n"))
        }
        profileCells += v to p
    }

    /** Правая часть «Данных»: список профилей с отметкой выбранного. */
    private fun rebuildProfiles() {
        profileList.removeAllViews()
        val selected = ElmHub.profileId()
        val rows = ArrayList<View>()
        rows += row("Стандартный OBD-II", check(selected == null)) { chooseProfile(null) }
        for (e in ProfileStore.list(this)) {
            val trailing = ArrayList<View>()
            if (e.error != null) trailing += text(18f, theme.warn).apply { text = "ошибка" }
            else if (e.builtIn) trailing += text(16f, theme.textDim).apply { text = "встроенный" }
            trailing += check(e.id == selected)
            if (!e.builtIn) {
                trailing += IconView(this, theme, IconView.Kind.TRASH).apply {
                    setOnClickListener { confirmDelete(e) }
                }
            }
            rows += row(e.name, *trailing.toTypedArray()) {
                if (e.error != null) message("${e.name}: ${e.error}") else chooseProfile(e.id)
            }
        }
        addRows(profileList, *rows.toTypedArray())
    }

    // ---------- действия ----------

    private fun pickProfile() {
        val entries = ProfileStore.list(this).filter { it.error == null }
        val selected = ElmHub.profileId()
        val items = listOf("Стандартный OBD-II") + entries.map { it.name } + "Загрузить JSON…"
        val checked = if (selected == null) 0 else entries.indexOfFirst { it.id == selected }.let { if (it < 0) -1 else it + 1 }
        listDialog("Профиль автомобиля", items, checked = checked) { i ->
            when (i) {
                0 -> chooseProfile(null)
                items.size - 1 -> selectTab(TAB_DATA)
                else -> chooseProfile(entries[i - 1].id)
            }
        }
    }

    private fun chooseProfile(id: String?) {
        try {
            ElmHub.setProfile(this, id)
        } catch (e: Exception) {
            message("Профиль не загружается: ${e.message}")
        }
        dataSignature = null
        render()
    }

    /** Скрытые величины текущего профиля (prefs «data_view» → hidden:<id профиля>). */
    private fun hiddenKeys(): Set<String> =
        ElmHub.profileId()?.let { id ->
            getSharedPreferences(PREFS_DATA, Context.MODE_PRIVATE).getStringSet("hidden:$id", null)
        } ?: emptySet()

    /**
     * Диалог мультивыбора величин вкладки «Данные»: чек-список по группам,
     * тап — показать/скрыть, кнопки «Снять всё» / «Показать всё» / «Готово».
     * Выбор сохраняется отдельно для каждого профиля и применяется сразу.
     */
    private fun pickVisibleParams() {
        val prof = ElmHub.profile() ?: run {
            message("Сначала выберите профиль автомобиля (справа) — выбор величин делается по профилю.")
            return
        }
        val id = ElmHub.profileId() ?: return
        val prefs = getSharedPreferences(PREFS_DATA, Context.MODE_PRIVATE)
        val hidden = prefs.getStringSet("hidden:$id", null)?.let { HashSet(it) } ?: HashSet<String>()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val marks = ArrayList<Pair<IconView, ObdProfile.Param>>()
        fun sync() = marks.forEach { (m, p) -> m.visibility = if (p.key in hidden) View.INVISIBLE else View.VISIBLE }
        fun apply() {
            prefs.edit().putStringSet("hidden:$id", hidden).apply()
            dataSignature = null // сила пересборки сетки на следующем render()
            render()
        }
        val box = panel(opaque = true)
        box.addView(text(22f, theme.accent, title = true).apply {
            text = "Какие данные показывать"
            setPadding(dp(16), dp(14), dp(16), dp(2))
        })
        box.addView(text(17f, theme.textDim).apply {
            text = "${prof.name} · тап по величине — показать или скрыть"
            setPadding(dp(16), 0, dp(16), dp(8))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (g in prof.groups) {
            list.addView(caption(g.title))
            g.params.forEachIndexed { i, p ->
                if (i > 0) list.addView(divider())
                val mark = check(p.key !in hidden)
                marks += mark to p
                list.addView(row(p.name, mark) {
                    if (p.key in hidden) hidden.remove(p.key) else hidden += p.key
                    mark.visibility = if (p.key in hidden) View.INVISIBLE else View.VISIBLE
                    apply()
                }, LinearLayout.LayoutParams(MATCH, WRAP))
            }
        }
        box.addView(divider())
        box.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(MATCH, dp(430)))
        box.addView(divider())
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            fun action(label: String, block: () -> Unit) = text(20f, theme.accent, title = true).apply {
                text = label
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(14), dp(4), dp(14))
                background = pressable()
                setOnClickListener { block() }
            }
            addView(action("Снять всё") { hidden += prof.params.map { it.key }; sync(); apply() },
                LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(action("Показать всё") { hidden.clear(); sync(); apply() },
                LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(action("Готово") { dialog.dismiss() },
                LinearLayout.LayoutParams(0, WRAP, 1f))
        }, LinearLayout.LayoutParams(MATCH, WRAP))
        dialog.setContentView(box)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(dp(660), ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.55f)
        }
        dialog.show()
    }

    private fun confirmDelete(e: ProfileStore.Entry) {
        listDialog("Удалить профиль «${e.name}»?", listOf("Удалить")) {
            val wasSelected = ElmHub.profileId() == e.id
            ProfileStore.delete(this, e.id)
            if (wasSelected) chooseProfile(null)
            dataSignature = null
            render()
        }
    }

    /** Системный выбор файла (если на ГУ есть DocumentsUI). */
    private fun openJson() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        if (intent.resolveActivity(packageManager) == null) {
            message("На этом устройстве нет системного выбора файлов. Положите JSON на флешку " +
                "(в корень, Download или папку obd) и нажмите «Найти JSON на флешке и в памяти».")
            return
        }
        startActivityForResult(intent, REQ_OPEN_JSON)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Константа: в lite ветка вырезается при компиляции, obd-код не остаётся.
        if (BuildConfig.LITE) return
        if (requestCode != REQ_OPEN_JSON || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: uri.lastPathSegment ?: "profile.json"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("файл не открылся")
            if (bytes.size > MAX_JSON_BYTES) throw IllegalArgumentException("файл больше 2 МБ")
            chooseProfile(ProfileStore.import(this, name, bytes.toString(Charsets.UTF_8)))
        } catch (e: Exception) {
            message("Не удалось загрузить профиль: ${e.message}")
        }
    }

    /** Поиск *.json на флешках и во внутренней памяти; нужен доступ к памяти. */
    private fun scanJson() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_STORAGE)
            return
        }
        val files = ProfileStore.scanStorage()
        if (files.isEmpty()) {
            message("JSON-файлов не найдено. Положите профиль в корень флешки, в Download, Documents или папку obd.")
            return
        }
        listDialog("Найденные JSON — тап: загрузить", files.map { "${it.name}  ·  ${it.parent}" }) { i ->
            try {
                chooseProfile(ProfileStore.importFile(this, files[i]))
            } catch (e: Exception) {
                message("${files[i].name}: ${e.message}")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Константа: в lite поиск профилей на флешке отсутствует.
        if (BuildConfig.LITE) return
        if (requestCode != REQ_STORAGE) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) scanJson()
        else message("Без доступа к памяти найти JSON нельзя — используйте «Открыть JSON-файл…».")
    }

    private fun onDevice(d: BtManager.Device) {
        if (!d.bonded) {
            BtManager.pair(d.address)
            render()
            return
        }
        // В lite только спаривание и журнал — пункта «адаптер OBD» там нет.
        // Ветки разнесены по if (BuildConfig.LITE): ненужная компилируется
        // в мёртвый код и вырезается R8. Внутри одного when по строкам
        // Kotlin такую ветку вырезать не умеет.
        if (BuildConfig.LITE) {
            listDialog(
                "${d.name ?: "без имени"} — ${d.address}",
                listOf("Тест SPP-подключения", "Забыть пару"),
            ) { which ->
                when (which) {
                    0 -> BtManager.testConnect(d.address)
                    1 -> BtManager.unpair(d.address)
                }
                render()
            }
        } else {
            listDialog(
                "${d.name ?: "без имени"} — ${d.address}",
                listOf("Сделать адаптером OBD", "Тест SPP-подключения", "Забыть пару"),
            ) { which ->
                when (which) {
                    0 -> {
                        ElmHub.setDevice(this, d.address, d.name)
                        ElmHub.start(this)
                    }
                    1 -> BtManager.testConnect(d.address)
                    2 -> BtManager.unpair(d.address)
                }
                render()
            }
        }
    }

    /** Выбор адаптера OBD среди спаренных устройств. */
    private fun pickAdapter() {
        val bonded = BtManager.devices().filter { it.bonded }
        if (bonded.isEmpty()) {
            listDialog("Спаренных устройств нет: найдите адаптер поиском и спарьте его.", emptyList(), cancel = "OK") {}
            return
        }
        listDialog(
            "Адаптер OBD",
            bonded.map { "${it.name ?: "без имени"} — ${it.address}" },
            checked = bonded.indexOfFirst { it.address == ElmHub.address() },
        ) { i ->
            ElmHub.setDevice(this, bonded[i].address, bonded[i].name)
            ElmHub.start(this)
            render()
        }
    }

    /** PIN, которым менеджер сам ответит на запрос спаривания. */
    private fun showPin() {
        val pins = listOf("1234", "0000", "1111", "9999", "123456", "000000")
        listDialog("PIN для спаривания", pins, checked = pins.indexOf(BtManager.pin)) { i ->
            BtManager.setPin(pins[i])
            render()
        }
    }

    private fun pickTheme() {
        listDialog("Настройки темы", DashTheme.ALL.map { it.title }, checked = DashTheme.ALL.indexOf(theme)) { i ->
            setTheme(DashTheme.ALL[i])
        }
    }

    private fun setTheme(t: DashTheme) {
        if (t === theme) return
        theme = t
        DashTheme.save(this, t)
        applyTheme()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun togglePower() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (adapter.isEnabled) adapter.disable() else adapter.enable()
        render()
    }

    private fun locationEnabled(): Boolean =
        (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.isLocationEnabled ?: true

    @Suppress("DEPRECATION")
    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    // ---------- диалоги ----------

    /**
     * Диалог-список в стиле текущей темы (системный AlertDialog выглядел
     * бы чужеродно). [checked] — отмеченный пункт, -1 — нет.
     */
    private fun listDialog(
        title: String,
        items: List<String>,
        checked: Int = -1,
        cancel: String = "Отмена",
        onPick: (Int) -> Unit,
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = panel(opaque = true)
        box.addView(text(22f, theme.accent, title = true).apply {
            text = title
            setPadding(dp(16), dp(14), dp(16), dp(10))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addRows(list, *items.mapIndexed { i, s ->
            val mark = check(i == checked)
            row(s, mark) {
                dialog.dismiss()
                onPick(i)
            }
        }.toTypedArray())
        if (items.isNotEmpty()) {
            box.addView(divider())
            box.addView(ScrollView(this).apply { addView(list) },
                LinearLayout.LayoutParams(MATCH, WRAP))
        }
        box.addView(divider())
        box.addView(text(22f, theme.accent, title = true).apply {
            text = cancel
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(14))
            background = pressable()
            setOnClickListener { dialog.dismiss() }
        })
        dialog.setContentView(box)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.55f)
        }
        dialog.show()
    }

    // ---------- элементы ----------

    /** Две колонки: настройки слева, статус и «Журнал» / «Обмен с адаптером» справа. */
    private fun split(left: View, right: View, leftWeight: Float = 1.15f) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, MATCH, leftWeight))
        addView(right, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginStart = dp(28) })
    }

    private fun column(vararg views: View) = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            views.forEach { addView(it, LinearLayout.LayoutParams(MATCH, WRAP)) }
        })
    }

    private fun panel(opaque: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val bg = PanelDrawable(context, theme, opaque)
        background = bg
        setPadding(bg.pad + dp(2), bg.pad + dp(2), bg.pad + dp(2), bg.pad + dp(2))
    }

    /** Строка настройки: заголовок слева, справа значение / переключатель / иконка. */
    private fun row(title: String, vararg trailing: View, onClick: (() -> Unit)? = null) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60)
            setPadding(dp(16), dp(6), dp(10), dp(6))
            addView(text(22f, theme.text, title = true).apply { text = title }, LinearLayout.LayoutParams(0, WRAP, 1f))
            trailing.forEach { addView(it, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) }) }
            if (onClick != null) {
                background = pressable()
                setOnClickListener { onClick() }
            }
        }

    private fun deviceRow(d: BtManager.Device, obd: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64)
        setPadding(dp(12), dp(6), dp(10), dp(6))
        background = pressable()
        setOnClickListener { onDevice(d) }
        addView(IconView(this@MainActivity, theme, IconView.Kind.BLUETOOTH).apply {
            if (!d.bonded) color = theme.textDim
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(22f, if (d.bonded) theme.accent else theme.text, title = true).apply {
                text = d.name ?: "без имени"
                maxLines = 1
            })
            addView(text(17f, theme.textDim).apply {
                text = "${d.address} · ${BtManager.bondName(d)}" + (d.rssi?.let { " · $it dBm" } ?: "")
                maxLines = 1
            })
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(12) })
        // В lite бейджа «OBD» нет — адаптер выбирается только в полной версии.
        if (!BuildConfig.LITE && obd) addView(value().apply { text = "OBD" })
        addView(chevron(), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
    }

    /** Строки панели с тонкими разделителями между ними. */
    private fun addRows(panel: LinearLayout, vararg rows: View) {
        rows.forEachIndexed { i, r ->
            if (i > 0) panel.addView(divider())
            panel.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(theme.divider)
        layoutParams = LinearLayout.LayoutParams(MATCH, maxOf(1, dp(1))).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
        }
    }

    private fun pressable() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
            setColor(theme.pressed)
            cornerRadius = dp(theme.panelRadiusDp.toInt()).toFloat()
        })
    }

    private fun chevron() = IconView(this, theme, IconView.Kind.CHEVRON)

    /** Галочка выбранного пункта; у невыбранных — пустое место того же размера. */
    private fun check(on: Boolean) = IconView(this, theme, IconView.Kind.CHECK).apply {
        visibility = if (on) View.VISIBLE else View.INVISIBLE
    }

    private fun value() = text(20f, theme.text).apply { maxLines = 1 }

    private fun caption(s: String) = text(18f, theme.textDim).apply {
        text = s
        setPadding(dp(10), dp(12), dp(10), dp(6))
    }

    private fun mono() = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * DashTheme.SCALE)
        setTextColor(theme.textDim)
        typeface = Typeface.MONOSPACE
        setPadding(dp(10), dp(6), dp(10), dp(6))
    }

    /** Текст в стиле темы: шрифт, а у «Комфорта» ещё и голубой ореол. */
    private fun text(sp: Float, color: Int, title: Boolean = false) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * DashTheme.SCALE)
        setTextColor(color)
        typeface = if (title) theme.titleTypeface else theme.typeface
        if (theme.textGlow != 0) setShadowLayer(theme.textGlowRadius, 0f, 0f, theme.textGlow)
    }

    /** До первой раскладки — без анимации, иначе бегунок плавно едет. */
    private fun message(s: String) = listDialog(s, emptyList(), cancel = "OK") {}

    private fun ThemedSwitch.sync(v: Boolean) = if (isLaidOut) checked = v else setNow(v)

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v * DashTheme.SCALE, resources.displayMetrics).toInt()

    private fun hms(wallMs: Long): String = HMS.format(Date(wallMs))

    private class Metric(
        val key: String,
        val label: String,
        val format: (Double) -> String,
        val color: ((Double) -> Int)? = null,
    )

    private companion object {
        const val REQ_LOCATION = 1
        const val REQ_STORAGE = 2
        const val REQ_OPEN_JSON = 3
        const val TAB_DATA = 2
        const val PREFS_DATA = "data_view"
        const val DATA_COLUMNS = 4
        const val MAX_JSON_BYTES = 2 * 1024 * 1024
        const val REFRESH_MS = 1_000L
        const val GRID_COLUMNS = 5
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

        val GREEN = 0xFF66E28A.toInt()
        val RED = 0xFFFF5A5A.toInt()
        val YELLOW = 0xFFFDD835.toInt()
        val BLUE = 0xFF5AB8FF.toInt()

        val HMS = SimpleDateFormat("HH:mm:ss", Locale.US)

        /** Бортсеть 11,2…14,6 В — зелёный, вне диапазона — красный. */
        fun voltageColor(v: Double) = if (v < 11.2f || v > 14.6f) RED else GREEN

        /** Двигатель: <35 синий, <80 жёлтый, <110 зелёный, иначе красный. */
        fun engineTempColor(t: Double) = when {
            t < 35f -> BLUE
            t < 80f -> YELLOW
            t < 110f -> GREEN
            else -> RED
        }

        /** Все показатели [ElmHub] — в порядке опроса. */
        val METRICS = listOf(
            Metric("coolant", "ОЖ", { "%.0f°".format(it) }, ::engineTempColor),
            Metric("volt", "АКБ", { "%.1f В".format(it) }, ::voltageColor),
            Metric("rpm", "обороты", { "%.0f".format(it) }),
            Metric("load", "нагрузка", { "%.0f %%".format(it) }),
            Metric("iat", "впуск. воздух", { "%.0f°".format(it) }),
            Metric("throttle", "дроссель", { "%.0f %%".format(it) }),
            Metric("trim_short", "поправка (кратк.)", { "%+.0f %%".format(it) }),
            Metric("trim_long", "поправка (долг.)", { "%+.0f %%".format(it) }),
            Metric("map", "коллектор", { "%.0f кПа".format(it) }),
            Metric("fuel_level", "топливо бака", { "%.0f %%".format(it) }),
        )
    }
}
