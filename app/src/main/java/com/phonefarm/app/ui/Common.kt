package com.phonefarm.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun monoStyle(
    size: Int,
    color: Color = Ink.Chalk,
    weight: FontWeight = FontWeight.Normal,
    spacing: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = size.sp,
    color = color,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeight = (size + 5).sp,
)

@Composable
fun Label(text: String, color: Color = Ink.Ash, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
    )
}

@Composable
fun Hairline(color: Color = Ink.LineSoft, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Ink.Layer)
            .border(1.dp, Ink.LineSoft, RoundedCornerShape(4.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun PFSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Box(
        modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) Ink.Paper else Color.Transparent)
            .border(1.dp, if (checked) Ink.Paper else Ink.Line, CircleShape)
            .clickable(enabled = enabled) { onChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Ink.Void else Ink.Ash),
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) Ink.Paper else Ink.Layer2)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = monoStyle(11, if (enabled) Ink.Void else Ink.Dim, FontWeight.Medium, 1.8f),
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, if (enabled) Ink.Line else Ink.LineSoft, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = monoStyle(10, if (enabled) Ink.Chalk else Ink.Dim, FontWeight.Medium, 1.8f),
        )
    }
}

@Composable
fun MiniButton(text: String, active: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, if (active) Ink.Line else Ink.LineSoft, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text.uppercase(),
            style = monoStyle(9, if (active) Ink.Ash else Ink.Dim, FontWeight.Medium, 1.3f),
        )
    }
}

@Composable
fun Stepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "",
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepperKey("-") { onChange((value - 1).coerceIn(range)) }
        Text(
            "$value$suffix",
            modifier = Modifier.width(62.dp),
            textAlign = TextAlign.Center,
            style = monoStyle(14, Ink.Chalk),
        )
        StepperKey("+") { onChange((value + 1).coerceIn(range)) }
    }
}

@Composable
private fun StepperKey(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Ink.Line, RoundedCornerShape(3.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = monoStyle(15, Ink.Chalk))
    }
}

@Composable
fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(title, Ink.Dim)
        Spacer(Modifier.width(1.dp))
        Box(Modifier.height(1.dp).padding(start = 10.dp).fillMaxWidth(1f).background(Ink.LineSoft))
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            action()
        }
    }
}

@Composable
fun PulseDot(active: Boolean, modifier: Modifier = Modifier) {
    if (active) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "alpha",
        )
        Box(modifier.size(7.dp).clip(CircleShape).background(Ink.Paper.copy(alpha = alpha)))
    } else {
        Box(modifier.size(7.dp).clip(CircleShape).background(Ink.Dim))
    }
}

@Composable
fun Sparkline(
    values: List<Long>,
    active: Boolean,
    modifier: Modifier = Modifier,
    bars: Int = 36,
) {
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        val data = values.takeLast(bars)
        val slot = size.width / bars
        val barWidth = slot * 0.55f
        val peak = (data.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        for (index in 0 until bars) {
            val value = data.getOrNull(index)?.toFloat() ?: 0f
            val ratio = (value / peak).coerceIn(0f, 1f)
            val barHeight = (ratio * size.height).coerceAtLeast(1f)
            val alpha = if (active) 0.26f + 0.74f * ratio else 0.16f
            drawRect(
                color = Ink.Chalk.copy(alpha = alpha),
                topLeft = Offset(index * slot, size.height - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Label(label, Ink.Dim)
        Spacer(Modifier.height(5.dp))
        Text(value, style = monoStyle(16, Ink.Chalk, FontWeight.Medium))
    }
}
