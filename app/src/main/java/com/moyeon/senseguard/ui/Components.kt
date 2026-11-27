package com.moyeon.senseguard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeon.senseguard.engine.Gear
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 색은 뜻으로만 쓴다. Guard = 지키는 중, Heat = 열선·낮춤, Warn = 풀림·주행 중, Danger = 멈춤·경고,
 * Mock = 가상 신호·목업 표시. 그 밖의 조작부는 무채색이다.
 */
object C {
    val Bg = Color(0xFF0B0F14)
    val Surface = Color(0xFF151B23)
    val Surface2 = Color(0xFF1E2630)
    val Surface3 = Color(0xFF2A3440)
    val Line = Color(0xFF34404D)
    val Text = Color(0xFFF2F4F7)
    val Sub = Color(0xFFA7B1BE)
    val Dim = Color(0xFF7C8896)
    val Guard = Color(0xFF3DDC97)
    val Heat = Color(0xFFFF8A5B)
    val Warn = Color(0xFFFFC857)
    val Danger = Color(0xFFFF5A5F)
    val Mock = Color(0xFFB9A7FF)
}

@Composable
fun SenseGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Guard, background = C.Bg, surface = C.Surface,
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
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    text, modifier = modifier, fontSize = size, color = color, fontWeight = weight,
    lineHeight = size * 1.3f, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
)

/** 내용을 묶는 판. 테두리는 상태를 알릴 때만 준다. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = C.Surface,
    border: Color? = null,
    pad: Dp = 28.dp,
    gap: Dp = 16.dp,
    arrangement: Arrangement.Vertical? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .clip(shape)
            .background(color)
            .then(if (border != null) Modifier.border(2.dp, border, shape) else Modifier)
            .padding(pad),
        verticalArrangement = arrangement ?: Arrangement.spacedBy(gap),
        content = content,
    )
}

enum class Btn { Filled, Tonal, Outline }

/** 큰 버튼. 꺼져 있을 때도 [sub] 로 이유를 보여 준다. */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = C.Guard,
    sub: String? = null,
    style: Btn = Btn.Filled,
) {
    val shape = RoundedCornerShape(24.dp)
    val bg = when {
        !enabled -> C.Surface2
        style == Btn.Filled -> color
        style == Btn.Tonal -> C.Surface3
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> C.Dim
        style == Btn.Filled -> C.Bg
        style == Btn.Tonal -> C.Text
        else -> color
    }
    Column(
        modifier
            .defaultMinSize(minHeight = 104.dp)
            .clip(shape)
            .background(bg)
            .then(if (enabled && style == Btn.Outline) Modifier.border(2.dp, color, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        T(label, 30.sp, fg, FontWeight.Bold, align = TextAlign.Center, maxLines = 1)
        if (sub != null) T(sub, 21.sp, if (enabled) fg.copy(alpha = 0.78f) else C.Sub, align = TextAlign.Center, maxLines = 1)
    }
}

/** 하나만 고르는 조작부. 고른 칸은 밝게 채운다. */
@Composable
fun <V> Segmented(
    options: List<Pair<V, String>>,
    selected: V,
    onSelect: (V) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 84.dp,
    textSize: TextUnit = 28.sp,
) {
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(C.Surface2).padding(6.dp).alpha(if (enabled) 1f else 0.45f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((value, label) in options) {
            val on = value == selected
            val bg by animateColorAsState(if (on) C.Text else Color.Transparent, tween(160), label = "seg")
            Box(
                Modifier.weight(1f).height(height - 12.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                T(label, textSize, if (on) C.Bg else C.Sub, if (on) FontWeight.Bold else FontWeight.Medium, align = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** 상태를 알리는 작은 표시. 색만이 아니라 글로도 말한다. */
@Composable
fun Pill(label: String, color: Color, big: Boolean = false, dot: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = if (big) 22.dp else 16.dp, vertical = if (big) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (dot) Box(Modifier.size(if (big) 14.dp else 10.dp).clip(CircleShape).background(color))
        T(label, if (big) 26.sp else 21.sp, color, FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun GearPill(gear: Gear) {
    val driving = gear != Gear.P && gear != Gear.UNKNOWN
    when {
        driving -> Pill("기어 ${gear.label} · 주행 중 · 잠김", C.Warn, big = true, dot = true)
        gear == Gear.P -> Pill("기어 P · 정차", C.Guard, big = true, dot = true)
        else -> Pill("기어 확인 안 됨", C.Sub, big = true, dot = true)
    }
}

/**
 * 열선 단계 막대. [level] 까지 채우고, [limit] 을 넘는 칸은 빗금으로 잠겼음을 보인다.
 * [limit] 이 null 이면(풀린 상태) 잠긴 칸이 없다.
 */
@Composable
fun HeatGauge(
    level: Int,
    limit: Int?,
    max: Int,
    modifier: Modifier = Modifier,
    color: Color = C.Heat,
    height: Dp = 52.dp,
    labels: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 1..max) {
                val on = level >= i
                val locked = limit != null && i > limit
                val fill by animateColorAsState(if (on) color else if (locked) C.Surface2 else C.Surface3, tween(250), label = "heat")
                Box(
                    Modifier.weight(1f).height(height).clip(RoundedCornerShape(16.dp)).background(fill)
                        .then(
                            if (locked && !on) Modifier.drawBehind {
                                val step = 18.dp.toPx()
                                var x = -size.height
                                while (x < size.width) {
                                    drawLine(C.Line, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 3.dp.toPx())
                                    x += step
                                }
                            } else Modifier,
                        ),
                )
            }
        }
        if (labels) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 1..max) {
                    val locked = limit != null && i > limit
                    T(
                        when {
                            locked -> "${i}단 · 잠김"
                            limit == i -> "${i}단 · 상한"
                            else -> "${i}단"
                        },
                        21.sp, if (locked) C.Dim else C.Sub, modifier = Modifier.weight(1f), align = TextAlign.Center, maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 큰 숫자 하나와 그 이름. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = C.Text) {
    Panel(modifier, color = C.Surface2, pad = 22.dp, gap = 6.dp) {
        T(label, 22.sp, C.Sub, maxLines = 1)
        T(value, 46.sp, valueColor, FontWeight.ExtraBold, maxLines = 1)
    }
}

/** 남은 양을 보이는 가는 막대. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(C.Surface3)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

/** 왼쪽 메뉴 그림. 글과 함께 쓰는 장식이라 따로 읽어 주지 않는다. */
@Composable
fun TabIcon(tab: Tab, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(40.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            Tab.DASHBOARD -> { // 방패
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f)
                    lineTo(w * 0.88f, h * 0.22f)
                    lineTo(w * 0.88f, h * 0.5f)
                    quadraticBezierTo(w * 0.86f, h * 0.8f, w * 0.5f, h * 0.94f)
                    quadraticBezierTo(w * 0.14f, h * 0.8f, w * 0.12f, h * 0.5f)
                    lineTo(w * 0.12f, h * 0.22f)
                    close()
                }
                drawPath(p, color, style = stroke)
            }
            Tab.PROFILE -> { // 조절 막대
                val ys = listOf(0.22f, 0.5f, 0.78f)
                val knobs = listOf(0.68f, 0.32f, 0.58f)
                for (i in 0..2) {
                    drawLine(color, Offset(w * 0.1f, h * ys[i]), Offset(w * 0.9f, h * ys[i]), stroke.width, StrokeCap.Round)
                    drawCircle(color, w * 0.1f, Offset(w * knobs[i], h * ys[i]))
                }
            }
            Tab.DEMO -> { // 높이가 다른 두 막대
                drawLine(color, Offset(w * 0.3f, h * 0.9f), Offset(w * 0.3f, h * 0.12f), w * 0.2f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.7f, h * 0.9f), Offset(w * 0.7f, h * 0.45f), w * 0.2f, StrokeCap.Round)
            }
            Tab.DRIVER -> { // 사람
                drawCircle(color, w * 0.19f, Offset(w * 0.5f, h * 0.28f), style = stroke)
                val p = Path().apply {
                    moveTo(w * 0.12f, h * 0.92f)
                    quadraticBezierTo(w * 0.12f, h * 0.58f, w * 0.5f, h * 0.58f)
                    quadraticBezierTo(w * 0.88f, h * 0.58f, w * 0.88f, h * 0.92f)
                }
                drawPath(p, color, style = stroke)
            }
        }
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
