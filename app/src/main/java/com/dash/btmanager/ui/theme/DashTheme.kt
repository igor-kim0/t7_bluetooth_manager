package com.dash.btmanager.ui.theme

import android.content.Context
import android.graphics.Typeface

/**
 * Тема оформления по мотивам настроек ГУ (см. dash/logs/pics/themes):
 *  - «Классика» — тёмно-синий фон, матовые серо-голубые панели с тонкой
 *    светлой рамкой, белые переключатели, обычный текст;
 *  - «Комфорт» — яркий синий фон, панели с неоновой голубой рамкой и
 *    свечением, жирный текст с ореолом, голубые бегунки переключателей.
 *
 * Все цвета — ARGB. Свечение (`*Glow`) = 0 — без свечения.
 */
class DashTheme(
    val id: String,
    val title: String,

    // Фон экрана: вертикальный градиент + светлое пятно справа.
    val bgTop: Int,
    val bgBottom: Int,
    val bgSpot: Int,

    // Текст
    val text: Int,
    val textDim: Int,
    val accent: Int,
    val warn: Int,
    val bold: Boolean,
    val textGlow: Int,
    val textGlowRadius: Float,

    // Панели («карточки» со строками)
    val panelTop: Int,
    val panelBottom: Int,
    val panelStroke: Int,
    val panelStrokeDp: Float,
    val panelGlow: Int,
    val panelRadiusDp: Float,
    val divider: Int,
    val pressed: Int,

    // Вкладки
    val tabText: Int,
    val tabLine: Int,

    // Переключатель
    val trackOn: Int,
    val trackOff: Int,
    val trackStroke: Int,
    val thumbOn: Int,
    val thumbOff: Int,
    val thumbGlow: Int,

    // Иконки (шеврон, обновление) и большая иконка состояния справа
    val icon: Int,
    val heroTop: Int,
    val heroBottom: Int,
    val heroGlow: Int,
) {
    val typeface: Typeface =
        if (bold) Typeface.create("sans-serif-condensed", Typeface.BOLD)
        else Typeface.create("sans-serif", Typeface.NORMAL)

    val titleTypeface: Typeface =
        if (bold) Typeface.create("sans-serif-condensed", Typeface.BOLD)
        else Typeface.create("sans-serif-medium", Typeface.NORMAL)

    companion object {
        val CLASSIC = DashTheme(
            id = "classic",
            title = "Классика",
            bgTop = 0xFF070B14.toInt(),
            bgBottom = 0xFF16233A.toInt(),
            bgSpot = 0x3326466E,
            text = 0xFFF2F5F8.toInt(),
            textDim = 0xFFA9B4C2.toInt(),
            accent = 0xFF3FD3F5.toInt(),
            warn = 0xFFFF6B6B.toInt(),
            bold = false,
            textGlow = 0x66000000,
            textGlowRadius = 2f,
            panelTop = 0x9E5A6B82.toInt(),
            panelBottom = 0x8C3A4A60.toInt(),
            panelStroke = 0x8CC9D6E6.toInt(),
            panelStrokeDp = 1f,
            panelGlow = 0,
            panelRadiusDp = 6f,
            divider = 0x40E6EEF7,
            pressed = 0x33FFFFFF,
            tabText = 0xFFE9EEF3.toInt(),
            tabLine = 0x40FFFFFF,
            trackOn = 0xFFC9D2DC.toInt(),
            trackOff = 0xFF5F6B7A.toInt(),
            trackStroke = 0,
            thumbOn = 0xFFFFFFFF.toInt(),
            thumbOff = 0xFFE3E8EE.toInt(),
            thumbGlow = 0x55000000,
            icon = 0xFFE9EEF3.toInt(),
            heroTop = 0xFFE6FBFF.toInt(),
            heroBottom = 0xFF34C8F0.toInt(),
            heroGlow = 0xFF1FB4E8.toInt(),
        )

        val COMFORT = DashTheme(
            id = "comfort",
            title = "Комфорт",
            bgTop = 0xFF0A1F4D.toInt(),
            bgBottom = 0xFF1C4C9C.toInt(),
            bgSpot = 0x5532A6FF,
            text = 0xFFFFFFFF.toInt(),
            textDim = 0xFFC8DBF5.toInt(),
            accent = 0xFF52E5FF.toInt(),
            warn = 0xFFFF7A7A.toInt(),
            bold = true,
            textGlow = 0xCC43C9FF.toInt(),
            textGlowRadius = 8f,
            panelTop = 0x66255BB0,
            panelBottom = 0x5A143C80,
            panelStroke = 0xFF8BEBFF.toInt(),
            panelStrokeDp = 1.5f,
            panelGlow = 0xFF3FD0FF.toInt(),
            panelRadiusDp = 8f,
            divider = 0x66A9E6FF,
            pressed = 0x3352E5FF,
            tabText = 0xFFFFFFFF.toInt(),
            tabLine = 0x66BDEBFF,
            trackOn = 0xFF0D2A5C.toInt(),
            trackOff = 0xFF0D2A5C.toInt(),
            trackStroke = 0xFFE8F8FF.toInt(),
            thumbOn = 0xFF35D4FF.toInt(),
            thumbOff = 0xFFD8F4FF.toInt(),
            thumbGlow = 0xFF3FD0FF.toInt(),
            icon = 0xFFFFFFFF.toInt(),
            heroTop = 0xFFF0FDFF.toInt(),
            heroBottom = 0xFF2AC2F2.toInt(),
            heroGlow = 0xFF22C3FF.toInt(),
        )

        val ALL = listOf(CLASSIC, COMFORT)

        private const val PREFS = "ui"
        private const val KEY = "theme"

        /** Общий масштаб интерфейса: шрифты (sp) и все размеры (dp) умножаются на него. */
        const val SCALE = 1.15f

        fun load(context: Context): DashTheme {
            val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return ALL.firstOrNull { it.id == id } ?: CLASSIC
        }

        fun save(context: Context, theme: DashTheme) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, theme.id).apply()
        }
    }
}
