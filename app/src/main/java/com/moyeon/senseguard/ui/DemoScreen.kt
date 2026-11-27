package com.moyeon.senseguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeon.senseguard.GuardViewModel
import com.moyeon.senseguard.engine.EventKind
import com.moyeon.senseguard.engine.ItemKey

@Composable
fun DemoScreen(vm: GuardViewModel) {
    val ui = vm.ui
    val scn = ui.scenario
    val p = ui.profile
    val mine = ItemKey.heat(p.seat)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // 재생 조작
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActionButton(
                if (scn != null) "멈춤" else "시연 재생",
                { if (scn != null) vm.stopScenario() else vm.startScenario() },
                Modifier.width(250.dp), color = C.Mock,
                sub = if (scn != null) null else "약 ${33 * 30 / ui.speed}초",
            )
            Segmented(listOf(1, 30, 60).map { it to "${it}배" }, ui.speed, { vm.setSpeed(it) }, Modifier.width(330.dp), textSize = 25.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("가상 시각", 22.sp, C.Sub, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    T(
                        if (scn != null) "${mmss(scn.elapsedSec)} / ${mmss(scn.totalSec)}" else "재생 전",
                        26.sp, if (scn != null) C.Mock else C.Dim, FontWeight.Bold, maxLines = 1,
                    )
                }
                ProgressBar(if (scn != null && scn.totalSec > 0) scn.elapsedSec / scn.totalSec.toFloat() else 0f, C.Mock)
            }
        }

        // 지금 장면. 재생 전에도 같은 높이를 차지해 아래 카드가 움직이지 않는다.
        Panel(Modifier.fillMaxWidth(), border = if (scn != null) C.Mock.copy(alpha = 0.6f) else null, gap = 6.dp) {
            T(scn?.caption ?: "같은 요청을 두 차에 동시에 보냅니다", 36.sp, C.Text, FontWeight.ExtraBold, maxLines = 1)
            T(
                if (scn != null) "기어와 열선 요청은 가상 신호예요. 가드가 낮추는 쓰기는 실제 Vehicle SDK 호출입니다."
                else "왼쪽은 시간으로만 내리는 기본 차, 오른쪽은 내 몸 기준으로 지키는 감각 가드예요.",
                22.sp, if (scn != null) C.Mock else C.Sub, maxLines = 1,
            )
        }

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // 기본 차
            CompareCard(
                Modifier.weight(1f).fillMaxHeight(),
                title = "기본 차", titleColor = C.Sub, subtitle = "시간 기준 · 누구나 다시 올림",
                value = "${scn?.baselineLevel ?: 0}단", valueColor = if (scn != null) C.Text else C.Dim,
                level = scn?.baselineLevel ?: 0, limit = null, gaugeColor = C.Sub, border = null,
                lines = listOf(
                    (scn?.let { if (it.baselineLevel > 0) "${it.baselineHeldMin}분째 그대로예요" else "꺼져 있어요" } ?: "재생하면 시작해요") to C.Text,
                    "30분이 지나야 한 단 내려가요" to C.Sub,
                    "주행 중에도 누구나 다시 올릴 수 있어요" to C.Sub,
                ),
            )
            // 감각 가드
            val v = ui.values[mine]
            val last = ui.events.firstOrNull { it.kind == EventKind.CLAMP || it.kind == EventKind.STEP_DOWN }
            CompareCard(
                Modifier.weight(1f).fillMaxHeight(),
                title = "감각 가드", titleColor = C.Heat, subtitle = "내 몸 기준 · ${p.seat.label}",
                value = v?.let { heatLabel(it) } ?: "–", valueColor = C.Heat,
                level = v?.toInt()?.coerceAtLeast(0) ?: 0, limit = if (ui.overrideActive) null else p.maxHeatLevel,
                gaugeColor = C.Heat, border = C.Heat.copy(alpha = 0.7f),
                lines = listOfNotNull(
                    (last?.text ?: "최대 ${p.maxHeatLevel}단까지만 올라가요") to C.Text,
                    ui.stepDownRemainingSec?.let { "${mmss(it)} 뒤 한 단 내려요" to C.Heat }
                        ?: ("${p.autoStepDownMinutes}분마다 한 단씩 내려요" to C.Sub),
                    if (ui.overrideActive) "이번 주행만 풀림 (본인이 P단에서)" to C.Warn else "주행 중에는 풀 수 없어요" to C.Sub,
                ),
            )
        }
    }
}

@Composable
private fun CompareCard(
    modifier: Modifier,
    title: String,
    titleColor: Color,
    subtitle: String,
    value: String,
    valueColor: Color,
    level: Int,
    limit: Int?,
    gaugeColor: Color,
    border: Color?,
    lines: List<Pair<String, Color>>,
) {
    Panel(modifier, border = border, gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(title, 34.sp, titleColor, FontWeight.ExtraBold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            T(subtitle, 22.sp, C.Sub, maxLines = 1)
        }
        Box(Modifier.weight(1f))
        T(value, if (value.length <= 3) 180.sp else 110.sp, valueColor, FontWeight.ExtraBold, maxLines = 1)
        HeatGauge(level, limit, 3, Modifier.fillMaxWidth(), color = gaugeColor, height = 64.dp, labels = true)
        Box(Modifier.weight(1f))
        lines.forEachIndexed { i, (text, color) ->
            T(text, if (i == 0) 28.sp else 23.sp, color, if (i == 0) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
        }
    }
}
