package com.moyeon.senseguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeon.senseguard.GuardViewModel
import com.moyeon.senseguard.engine.AlertMode
import com.moyeon.senseguard.engine.Seat
import com.moyeon.senseguard.engine.fmt

@Composable
fun ProfileScreen(vm: GuardViewModel) {
    val ui = vm.ui
    var draft by remember { mutableStateOf(ui.profile) }
    LaunchedEffect(ui.profile) { draft = ui.profile }
    val enabled = ui.canEdit
    val dirty = draft != ui.profile

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (!enabled) {
            Card(border = C.Warn) {
                T(
                    if (ui.scenario != null) "시나리오 재생 중에는 바꿀 수 없어요"
                    else "주행 중에는 규칙을 바꿀 수 없어요 · 기어 P에서 바꿔 주세요",
                    34.sp, C.Warn, FontWeight.Bold,
                )
            }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Label("적용 좌석")
                for (s in Seat.entries) Chip(s.label, draft.seat == s, enabled) { draft = draft.copy(seat = s) }
            }
            T(
                if (draft.seat == Seat.DRIVER) "운전자: 운전석 열선만 강제. 실내 온도와 다른 자리는 제안만."
                else "승객: ${draft.seat.label} 열선만 강제" + if (draft.seat == Seat.FRONT_PASSENGER) ", 실내 온도는 조수석 존만 강제." else ". 실내 온도는 제안만.",
                24.sp, C.Sub,
            )
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Label("열선 최대 단계")
                for (lv in 0..3) Chip(if (lv == 0) "끔" else "${lv}단", draft.maxHeatLevel == lv, enabled) { draft = draft.copy(maxHeatLevel = lv) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Label("자동 하강")
                for (m in listOf(5, 10, 15, 20, 30)) Chip("${m}분", draft.autoStepDownMinutes == m, enabled) { draft = draft.copy(autoStepDownMinutes = m) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Label("실내 온도 상한")
                Chip("−", false, enabled && draft.cabinTempMaxC > 18f) { draft = draft.copy(cabinTempMaxC = draft.cabinTempMaxC - 0.5f) }
                T("${fmt(draft.cabinTempMaxC)}°C", 48.sp, C.Accent, FontWeight.Bold, Modifier.width(200.dp))
                Chip("+", false, enabled && draft.cabinTempMaxC < 30f) { draft = draft.copy(cabinTempMaxC = draft.cabinTempMaxC + 0.5f) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Label("알림 방식")
                for (m in AlertMode.entries) Chip(m.label, draft.alertMode == m, enabled) { draft = draft.copy(alertMode = m) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigButton("저장", { vm.saveProfile(draft) }, Modifier.weight(1f), enabled = enabled && dirty, sub = "이 기기 안에만 저장 (서버 없음)")
            BigButton("되돌리기", { draft = ui.profile }, Modifier.weight(1f), enabled = dirty, color = C.Surface2)
        }
    }
}

@Composable
private fun Label(text: String) = T(text, 30.sp, C.Sub, FontWeight.SemiBold, Modifier.width(240.dp))
