package com.dash.btmanager.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/*
 * Элементы оформления, нарисованные кодом (без ресурсов), чтобы тема
 * меняла не только цвета, но и вид панелей, переключателей и иконок.
 * Свечение — Paint.setShadowLayer: на API 28+ работает и с аппаратным
 * ускорением, поэтому слои софтверными не делаем.
 */

private fun Context.px(dp: Float) = dp * resources.displayMetrics.density * DashTheme.SCALE

/** Фон экрана: вертикальный градиент и мягкое светлое пятно под иконкой справа. */
class BackgroundDrawable(private val t: DashTheme) : Drawable() {
    private val base = Paint()
    private val spot = Paint()

    override fun onBoundsChange(b: Rect) {
        base.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), t.bgTop, t.bgBottom, Shader.TileMode.CLAMP)
        val r = b.height() * 0.9f
        spot.shader = RadialGradient(b.width() * 0.78f, b.height() * 0.55f, r, t.bgSpot, 0, Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        c.drawRect(bounds, base)
        c.drawRect(bounds, spot)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

/**
 * Панель со строками настроек: полупрозрачная заливка с градиентом, рамка
 * и (у «Комфорта») неоновое свечение рамки. Рисуется с отступом [pad],
 * чтобы свечению было куда выходить, — его надо учесть в padding вида.
 * [opaque] — подложить фон экрана (для диалогов поверх содержимого).
 */
class PanelDrawable(context: Context, private val t: DashTheme, private val opaque: Boolean = false) : Drawable() {
    val pad = if (t.panelGlow != 0) context.px(6f).toInt() else context.px(1f).toInt()
    private val radius = context.px(t.panelRadiusDp)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.px(t.panelStrokeDp)
        color = t.panelStroke
        if (t.panelGlow != 0) setShadowLayer(context.px(5f), 0f, 0f, t.panelGlow)
    }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val under = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onBoundsChange(b: Rect) {
        rect.set(b.left + pad.toFloat(), b.top + pad.toFloat(), b.right - pad.toFloat(), b.bottom - pad.toFloat())
        under.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, t.bgTop, t.bgBottom, Shader.TileMode.CLAMP)
        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, t.panelTop, t.panelBottom, Shader.TileMode.CLAMP)
        // Блик сверху — «стекло» как на панелях ГУ.
        sheen.shader = LinearGradient(0f, rect.top, 0f, rect.top + min(rect.height(), 80f),
            0x22FFFFFF, 0, Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        if (opaque) c.drawRoundRect(rect, radius, radius, under)
        c.drawRoundRect(rect, radius, radius, fill)
        c.drawRoundRect(rect, radius, radius, sheen)
        c.drawRoundRect(rect, radius, radius, stroke)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Подчёркивание активной вкладки: короткая полоса акцентного цвета со свечением. */
class TabDrawable(context: Context, private val t: DashTheme, private val active: Boolean) : Drawable() {
    private val h = context.px(3f)
    private val side = context.px(14f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = t.accent
        setShadowLayer(context.px(4f), 0f, 0f, t.accent)
    }

    override fun draw(c: Canvas) {
        if (!active) return
        val b = bounds
        c.drawRoundRect(b.left + side, b.bottom - h - 2f, b.right - side, b.bottom - 2f, h, h, paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Переключатель в стиле ГУ; вид дорожки и бегунка задаёт тема. */
class ThemedSwitch(context: Context, private val t: DashTheme) : View(context) {
    private val w = context.px(66f)
    private val h = context.px(36f)
    private val trackH = context.px(if (t.trackStroke != 0) 24f else 20f)
    private val thumbR = context.px(if (t.trackStroke != 0) 11f else 12f)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.px(2f)
        color = t.trackStroke
        setShadowLayer(context.px(3f), 0f, 0f, t.thumbGlow)
    }
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(context.px(if (t.trackStroke != 0) 7f else 3f), 0f, if (t.trackStroke != 0) 0f else context.px(1f), t.thumbGlow)
    }
    private var pos = 0f
    private var anim: ValueAnimator? = null

    var checked = false
        set(value) {
            if (field == value) return
            field = value
            anim?.cancel()
            anim = ValueAnimator.ofFloat(pos, if (value) 1f else 0f).apply {
                duration = 180
                interpolator = DecelerateInterpolator()
                addUpdateListener { pos = it.animatedValue as Float; invalidate() }
                start()
            }
        }

    /** Без анимации — для первичной установки состояния. */
    fun setNow(value: Boolean) {
        anim?.cancel()
        checked = value
        anim?.cancel()
        pos = if (value) 1f else 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(w.toInt(), h.toInt())
    }

    override fun onDraw(c: Canvas) {
        val cy = height / 2f
        val left = thumbR + context.px(3f)
        val right = width - thumbR - context.px(3f)
        val tr = RectF(left - trackH / 2 + context.px(1f), cy - trackH / 2, right + trackH / 2 - context.px(1f), cy + trackH / 2)
        track.color = blend(t.trackOff, t.trackOn, pos)
        c.drawRoundRect(tr, trackH / 2, trackH / 2, track)
        if (t.trackStroke != 0) c.drawRoundRect(tr, trackH / 2, trackH / 2, trackLine)
        val cx = left + (right - left) * pos
        val col = blend(t.thumbOff, t.thumbOn, pos)
        thumb.shader = RadialGradient(cx - thumbR * 0.3f, cy - thumbR * 0.4f, thumbR * 1.4f,
            blend(col, 0xFFFFFFFF.toInt(), 0.45f), col, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, thumbR, thumb)
    }
}

/** Мелкие линейные иконки строк. */
class IconView(context: Context, private val t: DashTheme, private val kind: Kind, sizeDp: Float = 28f) : View(context) {
    enum class Kind { CHEVRON, REFRESH, BLUETOOTH, CHECK, STOP, TRASH }

    private val size = context.px(sizeDp).toInt()
    var color: Int = if (kind == Kind.BLUETOOTH || kind == Kind.CHECK) t.accent else t.icon
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = context.px(if (t.bold) 2.6f else 2.2f)
        if (t.textGlow != 0 && t.bold) setShadowLayer(context.px(4f), 0f, 0f, t.textGlow)
    }
    private val path = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(size, size)

    override fun onDraw(c: Canvas) {
        val s = width.toFloat()
        paint.color = color
        paint.style = Paint.Style.STROKE
        path.reset()
        when (kind) {
            Kind.CHEVRON -> {
                path.moveTo(s * 0.38f, s * 0.2f); path.lineTo(s * 0.66f, s * 0.5f); path.lineTo(s * 0.38f, s * 0.8f)
            }
            Kind.CHECK -> {
                path.moveTo(s * 0.2f, s * 0.52f); path.lineTo(s * 0.42f, s * 0.74f); path.lineTo(s * 0.8f, s * 0.3f)
            }
            Kind.BLUETOOTH -> bluetooth(path, s * 0.18f, s * 0.12f, s * 0.64f, s * 0.76f)
            Kind.REFRESH -> {
                val r = s * 0.3f
                val o = RectF(s / 2 - r, s / 2 - r, s / 2 + r, s / 2 + r)
                path.addArc(o, -60f, 140f)
                path.addArc(o, 120f, 140f)
                c.drawPath(path, paint)
                path.reset()
                // стрелки на концах дуг
                arrowHead(path, s / 2 + r * 0.5f, s / 2 - r * 0.866f, s * 0.12f, 1)
                arrowHead(path, s / 2 - r * 0.5f, s / 2 + r * 0.866f, s * 0.12f, -1)
            }
            Kind.TRASH -> {
                path.moveTo(s * 0.22f, s * 0.28f); path.lineTo(s * 0.78f, s * 0.28f)
                path.moveTo(s * 0.4f, s * 0.28f); path.lineTo(s * 0.42f, s * 0.18f); path.lineTo(s * 0.58f, s * 0.18f)
                path.lineTo(s * 0.6f, s * 0.28f)
                path.moveTo(s * 0.3f, s * 0.28f); path.lineTo(s * 0.34f, s * 0.82f); path.lineTo(s * 0.66f, s * 0.82f)
                path.lineTo(s * 0.7f, s * 0.28f)
                path.moveTo(s * 0.45f, s * 0.42f); path.lineTo(s * 0.45f, s * 0.68f)
                path.moveTo(s * 0.55f, s * 0.42f); path.lineTo(s * 0.55f, s * 0.68f)
            }
            Kind.STOP -> {
                paint.style = Paint.Style.FILL
                c.drawRoundRect(s * 0.3f, s * 0.3f, s * 0.7f, s * 0.7f, s * 0.06f, s * 0.06f, paint)
                return
            }
        }
        c.drawPath(path, paint)
    }

    private fun arrowHead(p: Path, x: Float, y: Float, l: Float, dir: Int) {
        p.moveTo(x - l * dir, y - l * 0.2f * dir); p.lineTo(x, y); p.lineTo(x - l * 0.3f * dir, y + l * dir)
    }
}

/**
 * Большая объёмная иконка состояния справа (как Bluetooth / Wi-Fi на ГУ):
 * градиентная заливка, свечение и «лужица» света под ней.
 */
class HeroView(context: Context, private val t: DashTheme, private val kind: Kind) : View(context) {
    enum class Kind { BLUETOOTH, GAUGE, HEX }

    var active = true
        set(v) { if (field != v) { field = v; invalidate() } }

    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pool = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = context.px(200f).toInt()
        setMeasuredDimension(getDefaultSize(h, widthMeasureSpec), h)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = h * 0.62f
        val cx = w / 2
        val top = h * 0.08f
        val alpha = if (active) 255 else 90

        // Лужица света под иконкой.
        val py = top + s + h * 0.1f
        pool.shader = RadialGradient(cx, py, w * 0.32f, (t.heroGlow and 0x00FFFFFF) or 0xAA000000.toInt(), 0, Shader.TileMode.CLAMP)
        pool.alpha = alpha
        c.save()
        c.scale(1f, 0.28f, cx, py)
        c.drawCircle(cx, py, w * 0.32f, pool)
        c.restore()

        glyph.strokeWidth = s * 0.11f
        glyph.shader = LinearGradient(0f, top, 0f, top + s, t.heroTop, t.heroBottom, Shader.TileMode.CLAMP)
        glyph.setShadowLayer(s * 0.08f, 0f, 0f, t.heroGlow)
        glyph.alpha = alpha
        path.reset()
        when (kind) {
            Kind.BLUETOOTH -> {
                bluetooth(path, cx - s * 0.27f, top, s * 0.54f, s)
                c.drawPath(path, glyph)
            }
            Kind.HEX -> {
                val r = s * 0.5f
                val cy = top + r
                for (i in 0..5) {
                    val a = Math.toRadians(60.0 * i - 90)
                    val x = cx + r * Math.cos(a).toFloat()
                    val y = cy + r * Math.sin(a).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, glyph)
                fill.shader = glyph.shader
                fill.alpha = alpha
                c.drawCircle(cx, cy, r * 0.3f, glyph)
                c.drawCircle(cx, cy, r * 0.1f, fill)
            }
            Kind.GAUGE -> {
                val r = s * 0.5f
                val cy = top + r * 1.1f
                c.drawArc(cx - r, cy - r, cx + r, cy + r, 150f, 240f, false, glyph)
                glyph.strokeWidth = s * 0.07f
                c.drawLine(cx, cy, cx + r * 0.55f, cy - r * 0.45f, glyph)
                fill.shader = glyph.shader
                fill.alpha = alpha
                c.drawCircle(cx, cy, s * 0.08f, fill)
            }
        }
    }
}

/** Руна Bluetooth в прямоугольнике (x, y, w, h). */
private fun bluetooth(p: Path, x: Float, y: Float, w: Float, h: Float) {
    val mx = x + w / 2
    p.moveTo(x, y + h * 0.27f)
    p.lineTo(x + w, y + h * 0.73f)
    p.lineTo(mx, y + h)
    p.lineTo(mx, y)
    p.lineTo(x + w, y + h * 0.27f)
    p.lineTo(x, y + h * 0.73f)
}

private fun blend(a: Int, b: Int, f: Float): Int {
    fun ch(s: Int) = ((a shr s and 0xFF) + ((b shr s and 0xFF) - (a shr s and 0xFF)) * f).toInt() shl s
    return ch(24) or ch(16) or ch(8) or ch(0)
}
