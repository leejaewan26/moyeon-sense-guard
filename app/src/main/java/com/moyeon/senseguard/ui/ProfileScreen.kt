package com.moyeon.senseguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeon.senseguard.GuardViewModel
import com.moyeon.senseguard.engine.AlertMode
import com.moyeon.senseguard.engine.GuardProfile
import com.moyeon.senseguard.engine.Seat
import com.moyeon.senseguard.engine.fmt

@Composable
fun ProfileScreen(vm: GuardViewModel) {
    val ui = vm.ui
    var draft by remember { mutableStateOf(ui.profile) }
    LaunchedEffect(ui.profile) { draft = ui.profile }
    val enabled = ui.canEdit
    val dirty = draft != ui.profile

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        // 왼쪽: 고르는 곳
        Panel(Modifier.weight(1.45f).fillMaxHeight(), arrangement = Arrangement.SpaceBetween) {
            Setting("누구 자리인가요", "이 자리의 열선만 강제해요") {
                SeatPicker(draft.seat, enabled) { draft = draft.copy(seat = it) }
            }
            Divider()
            Setting("열선은 몇 단까지", "넘으면 이 단계로 낮춰요") {
                Segmented(
                    (0..3).map { it to if (it == 0) "끔" else "${it}단" },
                    draft.maxHeatLevel, { draft = draft.copy(maxHeatLevel = it) }, Modifier.fillMaxWidth(), enabled,
                )
            }
            Divider()
            Setting("몇 분마다 내릴까요", "켜 두면 한 단씩 내려요") {
                Segmented(
                    listOf(5, 10, 15, 20, 30).map { it to "${it}분" },
                    draft.autoStepDownMinutes, { draft = draft.copy(autoStepDownMinutes = it) }, Modifier.fillMaxWidth(), enabled,
                )
            }
            Divider()
            Setting("실내 온도는 몇 도까지", "18~30°C") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ActionButton(
                        "−", { draft = draft.copy(cabinTempMaxC = draft.cabinTempMaxC - 0.5f) }, Modifier.width(120.dp),
                        enabled = enabled && draft.cabinTempMaxC > 18f, style = Btn.Tonal,
                    )
                    T("${fmt(draft.cabinTempMaxC)}°C", 52.sp, C.Text, FontWeight.ExtraBold, Modifier.weight(1f), TextAlign.Center, maxLines = 1)
                    ActionButton(
                        "+", { draft = draft.copy(cabinTempMaxC = draft.cabinTempMaxC + 0.5f) }, Modifier.width(120.dp),
                        enabled = enabled && draft.cabinTempMaxC < 30f, style = Btn.Tonal,
                    )
                }
            }
            Divider()
            Setting("어떻게 알릴까요", "낮출 때마다 알려요") {
                Segmented(
                    listOf(AlertMode.SOUND to "소리로 말해요", AlertMode.LIGHT_TEXT to "빛·글자로만"),
                    draft.alertMode, { draft = draft.copy(alertMode = it) }, Modifier.fillMaxWidth(), enabled,
                )
            }
        }

        // 오른쪽: 고른 결과를 말로 풀어서
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Panel(Modifier.weight(1f).fillMaxWidth(), border = if (enabled) null else C.Warn, gap = 18.dp) {
                if (!enabled) {
                    Pill(
                        if (ui.scenario != null) "시연 중에는 바꿀 수 없어요" else "주행 중에는 바꿀 수 없어요 · P단에서",
                        C.Warn, big = true, dot = true,
                    )
                }
                T("이렇게 지켜요", 32.sp, C.Text, FontWeight.ExtraBold)
                HeatGauge(draft.maxHeatLevel, draft.maxHeatLevel, 3, Modifier.fillMaxWidth(), labels = true)
                for (line in summary(draft)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.width(6.dp).height(34.dp).clip(RoundedCornerShape(50)).background(C.Guard))
                        T(line, 26.sp, C.Text, modifier = Modifier.weight(1f))
                    }
                }
                Box(Modifier.weight(1f))
                T("이 기기 안에만 저장해요. 진단명은 묻지 않아요.", 21.sp, C.Sub)
            }
            ActionButton(
                if (dirty) "저장" else "저장됨", { vm.saveProfile(draft) }, Modifier.fillMaxWidth(),
                enabled = enabled && dirty,
                sub = if (!enabled) "P단에서 저장할 수 있어요" else if (dirty) "바로 적용돼요" else "바꾼 것이 없어요",
            )
            ActionButton("되돌리기", { draft = ui.profile }, Modifier.fillMaxWidth(), enabled = dirty, style = Btn.Tonal)
        }
    }
}

private fun summary(p: GuardProfile): List<String> = listOf(
    if (p.maxHeatLevel == 0) "${p.seat.label} 열선은 켜지지 않아요."
    else "${p.seat.label} 열선은 ${p.maxHeatLevel}단까지만 올라가요.",
    "켜 두면 ${p.autoStepDownMinutes}분마다 한 단씩 내려가요.",
    if (p.seat == Seat.FRONT_PASSENGER) "조수석 실내 온도는 ${fmt(p.cabinTempMaxC)}°C까지만 올라가요."
    else "실내 온도는 ${fmt(p.cabinTempMaxC)}°C 이하를 권해요. 바꾸지는 않아요.",
    if (p.alertMode == AlertMode.SOUND) "낮출 때 목소리와 글자로 알려요." else "낮출 때 깜빡이는 빛과 글자로 알려요.",
    "다른 자리는 바꾸지 않고 권하기만 해요.",
)

@Composable
private fun Setting(title: String, hint: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(title, 27.sp, C.Text, FontWeight.Bold)
            T(hint, 20.sp, C.Sub)
        }
        Box(Modifier.weight(1f)) { control() }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(2.dp).background(C.Surface2))

/** 차 안 자리 배치대로 고르는 자리 선택. */
@Composable
private fun SeatPicker(selected: Seat, enabled: Boolean, onSelect: (Seat) -> Unit) {
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in listOf(listOf(Seat.DRIVER, Seat.FRONT_PASSENGER), listOf(Seat.REAR_LEFT, Seat.REAR_RIGHT))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (seat in row) {
                    val on = seat == selected
                    val shape = RoundedCornerShape(20.dp)
                    Box(
                        Modifier.weight(1f).height(76.dp).clip(shape)
                            .background(if (on) C.Text else C.Surface2)
                            .then(if (on) Modifier else Modifier.border(2.dp, C.Line, shape))
                            .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onSelect(seat) },
                        contentAlignment = Alignment.Center,
                    ) {
                        T(seat.label, 26.sp, if (on) C.Bg else C.Sub, if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}
