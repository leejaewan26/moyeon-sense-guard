package com.moyeon.senseguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeon.senseguard.engine.Gear
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object C {
    val Bg = Color(0xFF10141A)
    val Surface = Color(0xFF1A2029)
    val Surface2 = Color(0xFF232B36)
    val Line = Color(0xFF2F3946)
    val Text = Color(0xFFECEFF4)
    val Sub = Color(0xFF9AA5B4)
    val Accent = Color(0xFF4FC3F7)
    val Ok = Color(0xFF66BB6A)
    val Warn = Color(0xFFFFB74D)
    val Danger = Color(0xFFEF5350)
    val Mock = Color(0xFFCE93D8)
    val Heat = Color(0xFFFF8A65)
}

@Composable
fun SenseGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, background = C.Bg, surface = C.Surface,
            onBackground = C.Text, onSurface = C.Text,
        ),
        content = content,
    )
}

@Composable
fun T(
    text: String,
    size: TextUnit = 28.sp,
    color: Color = C.Text,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
) = Text(text, modifier = modifier, fontSize = size, color = color, fontWeight = weight, lineHeight = size * 1.3f, textAlign = align)

@Composable
fun Card(
    modifier: Modifier = Modifier,
    border: Color = C.Line,
    bg: Color = C.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(24.dp))
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun BigButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = C.Accent,
    sub: String? = null,
) {
    Column(
        modifier
            .defaultMinSize(minHeight = 100.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) color else C.Surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 20.dp)
            .alpha(if (enabled) 1f else 0.6f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val fg = if (!enabled) C.Sub else if (color == C.Surface2) C.Text else C.Bg
        T(label, 30.sp, fg, FontWeight.Bold, align = TextAlign.Center)
        if (sub != null) T(sub, 22.sp, if (enabled) fg.copy(alpha = 0.8f) else C.Sub, align = TextAlign.Center)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 120.dp, minHeight = 88.dp)
            .clip(RoundedCornerShape(44.dp))
            .background(if (selected) C.Accent else C.Surface2)
            .border(2.dp, if (selected) C.Accent else C.Line, RoundedCornerShape(44.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 16.dp)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        T(label, 28.sp, if (selected) C.Bg else C.Text, FontWeight.SemiBold)
    }
}

@Composable
fun Badge(label: String, color: Color, big: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.18f))
            .border(2.dp, color, RoundedCornerShape(14.dp))
            .padding(horizontal = if (big) 24.dp else 16.dp, vertical = if (big) 10.dp else 6.dp),
    ) {
        T(label, if (big) 30.sp else 22.sp, color, FontWeight.Bold)
    }
}

@Composable
fun GearBadge(gear: Gear) {
    val driving = gear != Gear.P && gear != Gear.UNKNOWN
    Badge(
        if (driving) "기어 ${gear.label} · 주행 중" else "기어 ${gear.label}",
        if (gear == Gear.P) C.Ok else if (driving) C.Warn else C.Sub,
        big = true,
    )
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        T(text, 32.sp, C.Text, FontWeight.Bold)
        if (trailing != null) trailing()
    }
}

private val clock = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

fun timeLabel(ms: Long, scenarioStart: Long?): String =
    if (scenarioStart != null) {
        val s = ((ms - scenarioStart) / 1000).coerceAtLeast(0)
        "시연 +%d:%02d".format(s / 60, s % 60)
    } else clock.format(Date(ms))

/** 차가 음수를 통풍 단계로 돌려줄 수 있다(에뮬레이터 앞좌석 범위 -3~3). */
fun heatLabel(v: Float): String = if (v < 0f) "통풍 ${(-v).toInt()}" else "${v.toInt()}단"

fun mmss(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
