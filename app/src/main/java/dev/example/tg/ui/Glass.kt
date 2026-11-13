package dev.example.tg.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.example.tg.TgApplication
import dev.example.tg.data.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Всё, что связано с библиотекой Haze (размытие), собрано здесь. Если версия Haze в вашем
// окружении называет методы иначе, править нужно только эти две функции.
// ---------------------------------------------------------------------------------------------

/** Содержимое, которое просвечивает сквозь «стеклянные» панели. */
fun Modifier.glassSource(state: HazeState): Modifier = this.hazeSource(state)

/**
 * «Стеклянная» панель: размытие того, что под ней, + лёгкая тонировка.
 * Без размытия (выключено в настройках или Android < 12) остаётся полупрозрачная заливка.
 */
@Composable
fun Modifier.glassBar(state: HazeState, tint: Color, blur: Boolean = LocalBlur.current): Modifier =
    if (blur) this.hazeEffect(state).background(tint.copy(alpha = 0.55f))
    else this.background(tint.copy(alpha = 0.92f))

val LocalBlur = androidx.compose.runtime.staticCompositionLocalOf { true }

// ---------------------------------------------------------------------------------------------
// Палитра чата
// ---------------------------------------------------------------------------------------------

class ChatPalette(
    val bgTop: Color,
    val bgBottom: Color,
    val doodle: Color,
    val inBubble: Brush,
    val outBubble: Brush,
    val inText: Color,
    val outText: Color,
    val outAccent: Color,
    val inAccent: Color,
    val glass: Color,
)

@Composable
fun chatPalette(): ChatPalette {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    return remember(cs, dark) {
        if (dark) {
            val base = cs.background
            ChatPalette(
                bgTop = lerp(base, cs.primary, 0.16f),
                bgBottom = lerp(base, Color.Black, 0.25f),
                doodle = Color.White.copy(alpha = 0.055f),
                inBubble = Brush.linearGradient(listOf(lerp(cs.surfaceContainerHigh, Color.White, 0.03f), cs.surfaceContainerHigh)),
                outBubble = Brush.linearGradient(listOf(lerp(cs.primary, Color.Black, 0.30f), lerp(cs.primary, Color.Black, 0.52f))),
                inText = cs.onSurface,
                outText = Color.White,
                outAccent = Color.White.copy(alpha = 0.85f),
                inAccent = cs.primary,
                glass = lerp(cs.surface, Color.Black, 0.15f),
            )
        } else {
            val base = cs.background
            ChatPalette(
                bgTop = lerp(base, cs.primary, 0.18f),
                bgBottom = lerp(base, cs.tertiary, 0.10f),
                doodle = cs.primary.copy(alpha = 0.10f),
                inBubble = Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.94f))),
                outBubble = Brush.linearGradient(listOf(lerp(cs.primary, Color.White, 0.35f), lerp(cs.primary, Color.White, 0.15f))),
                inText = cs.onSurface,
                outText = cs.onSurface,
                outAccent = cs.primary,
                inAccent = cs.primary,
                glass = cs.surface,
            )
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

// ---------------------------------------------------------------------------------------------
// Фон: градиент + узор из каракулей
// ---------------------------------------------------------------------------------------------

private enum class Doodle { Ring, Star, Heart, Plus, Wave, Triangle }

private fun doodlePath(kind: Doodle, s: Float): Path = Path().apply {
    when (kind) {
        Doodle.Ring -> addOval(androidx.compose.ui.geometry.Rect(-s / 2, -s / 2, s / 2, s / 2))
        Doodle.Plus -> { moveTo(-s / 2, 0f); lineTo(s / 2, 0f); moveTo(0f, -s / 2); lineTo(0f, s / 2) }
        Doodle.Triangle -> { moveTo(0f, -s / 2); lineTo(s / 2, s / 2); lineTo(-s / 2, s / 2); close() }
        Doodle.Wave -> {
            moveTo(-s / 2, 0f)
            cubicTo(-s / 4, -s / 2, 0f, s / 2, s / 4, 0f)
            cubicTo(s / 3, -s / 6, s / 2, -s / 6, s / 2, 0f)
        }
        Doodle.Star -> {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) s / 2 else s / 4.2f
                val a = Math.toRadians((i * 36 - 90).toDouble())
                val x = (r * Math.cos(a)).toFloat()
                val y = (r * Math.sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        Doodle.Heart -> {
            moveTo(0f, s / 2.2f)
            cubicTo(-s / 1.2f, -s / 8f, -s / 3f, -s / 1.8f, 0f, -s / 5f)
            cubicTo(s / 3f, -s / 1.8f, s / 1.2f, -s / 8f, 0f, s / 2.2f)
            close()
        }
    }
}

/** Градиентный фон с узором. Узор рисуется один раз на размер экрана, дёшево. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, pattern: Boolean = true) {
    val pal = chatPalette()
    val kinds = remember { Doodle.values() }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(pal.bgTop, pal.bgBottom)))
        if (!pattern) return@Canvas
        val cell = 74.dp.toPx()
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val rnd = Random(7)
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        for (r in 0 until rows) for (c in 0 until cols) {
            val kind = kinds[rnd.nextInt(kinds.size)]
            val s = (16 + rnd.nextInt(12)).dp.toPx()
            val x = c * cell + cell * (0.2f + 0.6f * rnd.nextFloat()) + if (r % 2 == 0) 0f else cell / 2f
            val y = r * cell + cell * (0.2f + 0.6f * rnd.nextFloat())
            val ang = rnd.nextInt(360).toFloat()
            translate(x, y) { rotate(ang, Offset.Zero) { drawPath(doodlePath(kind, s), pal.doodle, style = stroke) } }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Пузырь сообщения с хвостиком
// ---------------------------------------------------------------------------------------------

/**
 * Скруглённый прямоугольник; у крайнего снизу в группе сообщения со стороны автора — «хвостик».
 * Хвостик выходит за границу на [tail], поэтому рисовать надо через background(brush, shape), а не clip.
 */
class BubbleShape(
    private val out: Boolean,
    private val withTail: Boolean,
    private val radius: Dp = 18.dp,
    private val tail: Dp = 6.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val t = with(density) { tail.toPx() }
        val small = with(density) { 3.dp.toPx() }
        val body = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = 0f, top = 0f, right = size.width, bottom = size.height,
                    topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                    topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                    bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(if (out && withTail) small else r),
                    bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(if (!out && withTail) small else r),
                ),
            )
        }
        if (!withTail) return Outline.Generic(body)
        val w = size.width
        val h = size.height
        val d = with(density) { 1.dp.toPx() }
        val hook = Path().apply {
            if (out) {
                moveTo(w - 2 * d, h - 14 * d)
                cubicTo(w, h - 5 * d, w + 1.5f * d, h - 1 * d, w + t, h)
                quadraticBezierTo(w - 4 * d, h + 0.5f * d, w - 12 * d, h)
                close()
            } else {
                moveTo(2 * d, h - 14 * d)
                cubicTo(0f, h - 5 * d, -1.5f * d, h - 1 * d, -t, h)
                quadraticBezierTo(4 * d, h + 0.5f * d, 12 * d, h)
                close()
            }
        }
        val both = Path.combine(PathOperation.Union, body, hook)
        return Outline.Generic(both)
    }
}

/** Фон, подписанный на настройки размытия — оборачивает экран и отдаёт LocalBlur. */
@Composable
fun rememberSettings(): AppSettings = (LocalContext.current.applicationContext as TgApplication).settings

@Composable
fun GlassHost(content: @Composable () -> Unit) {
    val st = rememberSettings()
    val blur by st.blur.collectAsState()
    androidx.compose.runtime.CompositionLocalProvider(LocalBlur provides blur, content = content)
}

@Composable
fun BackgroundLayer() {
    val st = rememberSettings()
    val pattern by st.pattern.collectAsState()
    AppBackground(pattern = pattern)
}
