package com.bratneytg.ganga.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bratneytg.ganga.core.Fmt
import com.bratneytg.ganga.core.HistoryPoint
import kotlin.math.roundToInt

private val TagShape = GenericShape { size, _ ->
    val notch = size.height * 0.42f
    moveTo(notch, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(notch, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

/** La etiqueta de precio con su agujerito: lo memorable del diseño. Verde si está bajo tu objetivo. */
@Composable
fun PriceTag(amount: Double, deal: Boolean, modifier: Modifier = Modifier) {
    val background = if (deal) Palette.deal() else Palette.tag
    val foreground = if (deal) Palette.onDeal() else Palette.ink
    val hole = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .clip(TagShape)
            .background(background)
            .drawWithContent {
                drawContent()
                drawCircle(hole, radius = size.height * 0.09f, center = Offset(size.height * 0.24f, size.height / 2f))
            }
            .padding(start = 32.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = Fmt.eur(amount),
            color = foreground,
            fontFamily = Fonts.condensedBold,
            fontSize = 32.sp,
            maxLines = 1,
        )
    }
}

/** Etiqueta de texto pequeña (por debajo del objetivo, mínimo histórico...). */
@Composable
fun Flag(text: String, highlight: Boolean = false) {
    val color = if (highlight) Palette.deal() else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = if (highlight) Palette.dealWash() else Color.Transparent,
        border = if (highlight) null else BorderStroke(1.dp, color),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Evolución del mejor precio total. Mantén el dedo encima para ver el precio de cada búsqueda. */
@Composable
fun Spark(history: List<HistoryPoint>, target: Double?, modifier: Modifier = Modifier) {
    val points = history.takeLast(120)
    if (points.size < 2) return
    var selected by remember { mutableStateOf<Int?>(null) }
    val lineColor = Palette.chart()
    val ink = MaterialTheme.colorScheme.onSurface
    val dealColor = Palette.deal()
    val sel = selected

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Canvas(
            modifier = Modifier
                .size(width = 190.dp, height = 44.dp)
                .pointerInput(points) {
                    detectTapGestures(
                        onPress = { offset ->
                            val frac = ((offset.x - 5f) / (size.width - 10f)).coerceIn(0f, 1f)
                            selected = (frac * (points.size - 1)).roundToInt()
                            tryAwaitRelease()
                            selected = null
                        },
                    )
                },
        ) {
            val pad = 5.dp.toPx()
            val values = points.map { it.landed }
            var lo = values.min()
            var hi = values.max()
            if (target != null) {
                lo = minOf(lo, target)
                hi = maxOf(hi, target)
            }
            if (hi == lo) {
                hi += 1.0
                lo -= 1.0
            }
            val xAt = { i: Int -> pad + i * (size.width - 2 * pad) / (points.size - 1) }
            val yAt = { v: Double -> (size.height - pad - ((v - lo) / (hi - lo)).toFloat() * (size.height - 2 * pad)) }

            if (target != null) {
                drawLine(
                    color = dealColor,
                    start = Offset(pad, yAt(target)),
                    end = Offset(size.width - pad, yAt(target)),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
            }
            val path = Path()
            points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(xAt(i), yAt(p.landed)) else path.lineTo(xAt(i), yAt(p.landed))
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            val last = Offset(xAt(points.size - 1), yAt(points.last().landed))
            drawCircle(Palette.tag, radius = 4.5.dp.toPx(), center = last)
            drawCircle(ink, radius = 4.5.dp.toPx(), center = last, style = Stroke(width = 2.dp.toPx()))
            if (sel != null && sel in points.indices) {
                drawCircle(ink, radius = 4.dp.toPx(), center = Offset(xAt(sel), yAt(points[sel].landed)))
            }
        }
        if (sel != null && sel in points.indices) {
            val p = points[sel]
            Text(
                text = "${Fmt.dateTime(p.t)}: ${Fmt.eur(p.landed)} en ${p.store}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Cuadrito amarillo de la cabecera, con la forma de la etiqueta. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    val hole = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .size(width = 28.dp, height = 22.dp)
            .clip(TagShape)
            .background(Palette.tag)
            .drawWithContent {
                drawContent()
                drawCircle(hole, radius = size.height * 0.1f, center = Offset(size.height * 0.26f, size.height / 2f))
            },
    )
}

@Composable
fun isDark(): Boolean = isSystemInDarkTheme()

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(8.dp).clip(CircleShape).background(color))
}
