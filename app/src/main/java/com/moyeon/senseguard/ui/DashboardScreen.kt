package com.moyeon.senseguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeon.senseguard.GuardViewModel
import com.moyeon.senseguard.fmtValue
import com.moyeon.senseguard.engine.EventKind
import com.moyeon.senseguard.engine.Gear
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.Seat
import com.moyeon.senseguard.engine.fmt

@Composable
fun DashboardScreen(vm: GuardViewModel) {
    val ui = vm.ui
    val p = ui.profile
    val driving = ui.gear != Gear.P && ui.gear != Gear.UNKNOWN
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // 왼쪽: 지금 켜진 것 + 현재 값 + 버튼
        Column(
            Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Card(border = C.Accent) {
                SectionTitle("지금 켜진 것") {
                    Badge(if (ui.overrideActive) "이번 주행만 풀림" else "가드 켜짐", if (ui.overrideActive) C.Warn else C.Ok)
                }
                T("• 열선 최대 ${p.maxHeatLevel}단 · ${p.seat.label}만 강제 (다른 자리는 제안만)", 26.sp)
                T("• 열선 켜지면 ${p.autoStepDownMinutes}분마다 한 단씩 자동 하강", 26.sp)
                T(
                    "• 실내 온도 최고 ${fmt(p.cabinTempMaxC)}°C · " +
                        if (p.seat == Seat.FRONT_PASSENGER) "조수석 존만 강제" else "제안만 (승객 자기 존만 강제)",
                    26.sp,
                )
                T("• 알림: ${p.alertMode.label} · 주행 중엔 못 풂 · P단에서 본인만 풀기", 26.sp)
                T("화면을 닫아도 상시 서비스가 지킵니다 · 차가 켜지면 저절로 시작", 24.sp, C.Sub)
            }

            Card {
                SectionTitle(if (ui.scenario != null) "좌석 값 · 시나리오 가상 값 포함" else "차에서 읽은 값")
                val mine = ItemKey.heat(p.seat)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (seat in listOf(Seat.DRIVER, Seat.FRONT_PASSENGER, Seat.REAR_LEFT, Seat.REAR_RIGHT)) {
                        val item = ItemKey.heat(seat)
                        val v = ui.values[item]
                        Card(Modifier.weight(1f), border = if (item == mine) C.Heat else C.Line, bg = C.Surface2) {
                            T(seat.label, 24.sp, C.Sub)
                            T(v?.let { heatLabel(it) } ?: "–", 44.sp, if (item == mine) C.Heat else C.Text, FontWeight.Bold)
                            T(if (item == mine) "내 자리" else "제안만", 20.sp, C.Sub)
                        }
                    }
                }
                val temps = listOf(Seat.DRIVER, Seat.FRONT_PASSENGER).joinToString("   ") { s ->
                    "${s.label} ${ui.values[ItemKey.temp(s)]?.let { if (it > 0f) fmt(it) + "°C" else "–" } ?: "–"}"
                }
                T("실내 설정 온도: $temps", 26.sp)
                ui.stepDownRemainingSec?.let { T("자동 하강까지 ${mmss(it)}", 30.sp, C.Heat, FontWeight.Bold) }
                ui.heatBounds[p.seat]?.let { T("이 차 열선 범위: ${it.first}~${it.last}", 22.sp, C.Sub) }
            }

            for (item in ui.halted) {
                Card(border = C.Danger) {
                    T("${item.label} 가드 멈춤", 34.sp, C.Danger, FontWeight.Bold)
                    T("60초에 3번 깎였어요. 누군가 계속 올리고 있을 수 있어요.", 26.sp)
                    BigButton("다시 켜기", { vm.rearm(item) }, color = C.Danger)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton(
                    "이번 주행만 풀기", vm::requestOverride, Modifier.weight(1f),
                    enabled = ui.canOverride, color = C.Warn,
                    sub = when {
                        ui.overrideActive -> "풀려 있음 · 다음 P에서 다시 켜짐"
                        ui.gear != Gear.P -> "P단에서만 (지금 ${ui.gear.label})"
                        else -> "본인만 · 다음 P에서 다시 켜짐"
                    },
                )
                BigButton(
                    if (ui.scenario != null) "시나리오 멈춤" else "시연 시나리오 재생",
                    { if (ui.scenario != null) vm.stopScenario() else vm.startScenario() },
                    Modifier.weight(1f), color = C.Mock,
                    sub = "가상 신호 · 쓰기는 실제 SDK",
                )
            }
            if (!driving) {
                BigButton(
                    "하차 · 원래 값으로", vm::restoreOnExit, Modifier.fillMaxWidth(),
                    enabled = ui.canEdit, color = C.Surface2,
                    sub = "최선 노력: 지금 값이 가드가 쓴 값과 같을 때만",
                )
            }
        }

        // 오른쪽: 최근 기록 + SDK 상태
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Card {
                SectionTitle("최근 깎은 기록")
                val start = ui.scenarioStartMs
                val shown = ui.events.filter { it.kind != EventKind.INFO }.take(10)
                if (shown.isEmpty()) T("아직 없어요", 26.sp, C.Sub)
                for (e in shown) {
                    val color = when (e.kind) {
                        EventKind.CLAMP, EventKind.STEP_DOWN -> C.Heat
                        EventKind.HALT -> C.Danger
                        EventKind.SUGGEST -> C.Sub
                        EventKind.OVERRIDE, EventKind.OVERRIDE_END -> C.Warn
                        else -> C.Text
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        T(timeLabel(e.timeMs, start), 22.sp, C.Sub)
                        Column {
                            val change = if (e.kind != EventKind.SUGGEST && e.item != null && e.from != null && e.to != null)
                                "${e.item.label} ${fmtValue(e.item, e.from)} → ${fmtValue(e.item, e.to)}" else null
                            if (change != null) T(change, 26.sp, color, FontWeight.Bold)
                            T(e.text, 24.sp, if (change == null) color else C.Text)
                        }
                    }
                }
            }
            Card {
                SectionTitle("Vehicle SDK 호출 기록")
                for (l in ui.sdkLogs.take(8)) {
                    T("${timeLabel(l.timeMs, null)}  ${if (l.ok) "성공" else "실패"} · ${l.text}", 20.sp, if (l.ok) C.Sub else C.Danger)
                }
            }
        }
    }
}
