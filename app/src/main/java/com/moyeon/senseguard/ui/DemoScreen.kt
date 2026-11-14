package com.moyeon.senseguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val mine = ItemKey.heat(ui.profile.seat)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BigButton(
                if (scn != null) "멈춤" else "시연 시나리오 재생",
                { if (scn != null) vm.stopScenario() else vm.startScenario() },
                color = C.Mock,
            )
            T("배속", 30.sp, C.Sub)
            for (s in listOf(1, 30, 60)) Chip("${s}배", ui.speed == s) { vm.setSpeed(s) }
            if (scn != null) {
                T("가상 시각 +${mmss(scn.elapsedSec)} / ${mmss(scn.totalSec)}", 30.sp, C.Mock, FontWeight.Bold)
            }
        }
        if (scn != null) {
            Card(border = C.Mock) {
                T(scn.caption, 40.sp, C.Text, FontWeight.Bold)
                T("시나리오 재생 중 · 기어·열선 요청은 가상 신호, 가드가 깎는 쓰기는 실제 Vehicle SDK 로 호출", 22.sp, C.Mock)
            }
        } else {
            Card {
                T("왼쪽 '기본 차': 열선 3단이 30분 유지, 누구나 다시 올림", 28.sp)
                T("오른쪽 '가드': 내 몸 기준 ${ui.profile.maxHeatLevel}단 상한에 깎이고 ${ui.profile.autoStepDownMinutes}분 뒤 자동 하강, 주행 중엔 못 풂", 28.sp)
                T("30배속이면 약 30초. 실제 신호(VHAL 패널)로도 오른쪽은 똑같이 움직입니다.", 22.sp, C.Sub)
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // 기본 차
            Card(Modifier.weight(1f).fillMaxHeight(), border = C.Line) {
                T("기본 차", 40.sp, C.Sub, FontWeight.Bold)
                T("시간 기준 하강 · 누구나 다시 올림", 24.sp, C.Sub)
                T(scn?.let { "${it.baselineLevel}단" } ?: "–", 140.sp, C.Text, FontWeight.ExtraBold)
                T(scn?.let { if (it.baselineLevel > 0) "${it.baselineHeldMin}분째 그대로 (30분 기준)" else "꺼짐" } ?: "재생하면 시작", 30.sp, C.Sub)
                T("주행 중에도 아무나 3단으로", 26.sp, C.Sub)
            }
            // 가드
            Card(Modifier.weight(1f).fillMaxHeight(), border = C.Heat) {
                T("감각 가드", 40.sp, C.Heat, FontWeight.Bold)
                T("내 몸 기준 · ${ui.profile.seat.label}", 24.sp, C.Sub)
                val v = ui.values[mine]
                T(v?.let { heatLabel(it) } ?: "–", 140.sp, C.Heat, FontWeight.ExtraBold)
                ui.stepDownRemainingSec?.let { T("자동 하강까지 ${mmss(it)}", 30.sp, C.Heat) }
                if (ui.overrideActive) T("이번 주행만 풀림 (본인이 P에서)", 30.sp, C.Warn, FontWeight.Bold)
                val last = ui.events.firstOrNull { it.kind == EventKind.CLAMP || it.kind == EventKind.STEP_DOWN }
                if (last != null) T(last.text, 34.sp, C.Text, FontWeight.Bold)
            }
        }
    }
}
