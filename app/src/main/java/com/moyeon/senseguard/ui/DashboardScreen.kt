package com.moyeon.senseguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeon.senseguard.GuardViewModel
import com.moyeon.senseguard.UiState
import com.moyeon.senseguard.engine.AlertMode
import com.moyeon.senseguard.engine.EventKind
import com.moyeon.senseguard.engine.Gear
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.Seat
import com.moyeon.senseguard.engine.fmt
import com.moyeon.senseguard.engine.fmtValue

@Composable
fun DashboardScreen(vm: GuardViewModel) {
    val ui = vm.ui
    val driving = ui.gear != Gear.P && ui.gear != Gear.UNKNOWN
    var showSdk by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // 왼쪽: 내 자리가 지금 어떤 상태인지
            Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                MySeatCard(ui, Modifier.weight(1f).fillMaxWidth(), vm::rearm)
                RuleTiles(ui)
            }
            // 오른쪽: 차 전체와 기록
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SeatMap(ui)
                Panel(Modifier.weight(1f).fillMaxWidth(), gap = 14.dp) {
                    Segmented(
                        listOf(false to "최근 기록", true to "차와 주고받은 기록"),
                        showSdk, { showSdk = it }, Modifier.fillMaxWidth(), height = 68.dp, textSize = 23.sp,
                    )
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (showSdk) SdkLogList(ui) else EventList(ui)
                    }
                }
            }
        }

        // 아래 버튼 줄은 언제나 같은 자리에 있다(알림이 떠도 밀려나지 않는다).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ActionButton(
                if (ui.overrideActive) "이번 주행만 풀려 있어요" else "이번 주행만 풀기", vm::requestOverride, Modifier.weight(1.2f),
                enabled = ui.canOverride, color = C.Warn,
                sub = when {
                    ui.overrideActive -> "다음에 P단으로 돌아오면 다시 지켜요"
                    ui.gear != Gear.P -> "P단에서만 풀 수 있어요 (지금 ${ui.gear.label})"
                    else -> "본인만 · 다음 P단에서 다시 지켜요"
                },
            )
            ActionButton(
                "내릴 때 원래 값으로", vm::restoreOnExit, Modifier.weight(1f),
                enabled = ui.canEdit && !driving, style = Btn.Tonal,
                sub = if (driving) "정차한 뒤에 할 수 있어요" else "가드가 낮춘 값만 되돌려요",
            )
            ActionButton(
                if (ui.scenario != null) "시연 멈춤" else "시연 재생",
                { if (ui.scenario != null) vm.stopScenario() else vm.startScenario() },
                Modifier.weight(0.8f), color = C.Mock, style = Btn.Outline,
                sub = "가상 신호 · 쓰기는 실제 SDK",
            )
        }
    }
}

/** 내 자리 열선: 지금 몇 단이고, 어디까지 허용되고, 언제 내려가는지. */
@Composable
private fun MySeatCard(ui: UiState, modifier: Modifier, onRearm: (ItemKey) -> Unit) {
    val p = ui.profile
    val mine = ItemKey.heat(p.seat)
    val value = ui.values[mine]
    val level = value?.toInt()?.coerceAtLeast(0) ?: 0
    val max = maxOf(3, ui.heatBounds[p.seat]?.last ?: 3)
    val halted = mine in ui.halted
    val (statusText, statusColor) = when {
        halted -> "가드가 멈췄어요" to C.Danger
        ui.overrideActive -> "이번 주행만 풀림" to C.Warn
        else -> "지키는 중" to C.Guard
    }
    Panel(modifier, border = statusColor.copy(alpha = 0.55f), gap = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(statusColor))
            T(statusText, 36.sp, statusColor, FontWeight.ExtraBold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            T("${p.seat.label} · 내 자리", 24.sp, C.Sub, maxLines = 1)
        }
        for (item in ui.halted) HaltedRow(item) { onRearm(item) }
        Box(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            val text = value?.let { heatLabel(it) } ?: "–"
            T(text, if (text.length <= 3) 150.sp else 96.sp, if (level > 0) C.Heat else C.Text, FontWeight.ExtraBold, maxLines = 1)
            Column(Modifier.padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T("지금 열선", 24.sp, C.Sub)
                T(
                    if (p.maxHeatLevel == 0) "열선을 켜지 않아요" else "최대 ${p.maxHeatLevel}단까지",
                    32.sp, C.Text, FontWeight.Bold, maxLines = 1,
                )
                T(
                    when {
                        ui.overrideActive -> "지금은 풀려 있어 그대로 둬요"
                        halted -> "멈춘 동안은 낮추지 않아요"
                        else -> "더 올리면 ${p.maxHeatLevel}단으로 낮춰요"
                    },
                    23.sp, C.Sub, maxLines = 1,
                )
            }
        }
        HeatGauge(level, if (ui.overrideActive || halted) null else p.maxHeatLevel, max, Modifier.fillMaxWidth(), height = 64.dp, labels = true)
        Box(Modifier.weight(1f))

        val remain = ui.stepDownRemainingSec
        if (remain != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("${mmss(remain)} 뒤 한 단 내려요", 30.sp, C.Heat, FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    T("${p.autoStepDownMinutes}분마다", 22.sp, C.Sub, maxLines = 1)
                }
                ProgressBar(remain / (p.autoStepDownMinutes * 60f), C.Heat)
            }
        } else {
            T("열선이 켜지면 ${p.autoStepDownMinutes}분마다 한 단씩 내려요", 24.sp, C.Sub, maxLines = 1)
        }
    }
}

/** 적용 중인 규칙 네 가지. */
@Composable
private fun RuleTiles(ui: UiState) {
    val p = ui.profile
    val tempNote = if (p.seat == Seat.FRONT_PASSENGER) "조수석 강제" else "권하기만"
    val tiles = listOf(
        "열선 상한" to if (p.maxHeatLevel == 0) "끔" else "${p.maxHeatLevel}단",
        "자동 하강" to "${p.autoStepDownMinutes}분마다",
        "실내 온도 · $tempNote" to "${fmt(p.cabinTempMaxC)}°C", // «까지»를 붙이면 «조수석 강제»·«권하기만» 칸에서 글자가 붙는다
        "알림" to if (p.alertMode == AlertMode.SOUND) "소리로" else "빛·글자로",
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (pair in tiles.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((label, value) in pair) {
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(C.Surface).padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(label, 22.sp, C.Sub, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        T(value, 30.sp, C.Text, FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 차를 위에서 본 자리 배치. 내 자리만 강제하고 다른 자리는 권하기만 한다는 것을 한눈에 보인다. */
@Composable
private fun SeatMap(ui: UiState) {
    val mine = ui.profile.seat
    Panel(Modifier.fillMaxWidth(), gap = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T("자리별 열선", 28.sp, C.Text, FontWeight.Bold, maxLines = 1)
            when {
                ui.scenario != null -> Pill("가상 값 포함", C.Mock)
                ui.testInput != null -> Pill("시험 입력 포함", C.Mock)
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 72.dp, topEnd = 72.dp, bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(C.Surface2)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(150.dp).height(6.dp).clip(RoundedCornerShape(50)).background(C.Line))
            T("앞", 19.sp, C.Dim)
            for (row in listOf(listOf(Seat.DRIVER, Seat.FRONT_PASSENGER), listOf(Seat.REAR_LEFT, Seat.REAR_RIGHT))) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (seat in row) {
                        val v = ui.values[ItemKey.heat(seat)]
                        val isMine = seat == mine
                        val shape = RoundedCornerShape(22.dp)
                        Column(
                            Modifier.weight(1f).clip(shape)
                                .background(if (isMine) C.Heat.copy(alpha = 0.14f) else C.Surface3)
                                .then(if (isMine) Modifier.border(2.dp, C.Heat, shape) else Modifier)
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T(seat.label, 21.sp, C.Sub, maxLines = 1)
                                Spacer(Modifier.weight(1f))
                                T(if (isMine) "내 자리" else "권하기만", 19.sp, if (isMine) C.Heat else C.Dim, FontWeight.Bold, maxLines = 1)
                            }
                            if (v != null) T(heatLabel(v), 40.sp, if (isMine) C.Heat else C.Text, FontWeight.ExtraBold, maxLines = 1)
                            else T("값 없음", 26.sp, C.Dim, FontWeight.Medium, Modifier.padding(vertical = 9.dp), maxLines = 1)
                        }
                    }
                }
            }
        }
        val temps = listOf(Seat.DRIVER, Seat.FRONT_PASSENGER).joinToString("   ") { s ->
            "${s.label} ${ui.values[ItemKey.temp(s)]?.let { if (it > 0f) fmt(it) + "°C" else "–" } ?: "–"}"
        }
        T("실내 설정 온도   $temps", 21.sp, C.Sub, maxLines = 1)
    }
}

/** 60초에 3번 낮춰 멈춘 항목. 다시 지키게 하는 버튼을 바로 옆에 둔다. */
@Composable
private fun HaltedRow(item: ItemKey, onRearm: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Danger.copy(alpha = 0.14f)).padding(start = 22.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T("${item.label} 가드가 멈췄어요", 26.sp, C.Danger, FontWeight.ExtraBold, maxLines = 1)
            T("60초에 3번 낮췄어요. 누군가 계속 올리고 있을 수 있어요.", 20.sp, C.Text, maxLines = 2)
        }
        ActionButton("다시 지키기", onRearm, Modifier.width(210.dp), color = C.Danger)
    }
}

@Composable
private fun EventList(ui: UiState) {
    val start = ui.scenarioStartMs
    val shown = ui.events.filter { it.kind != EventKind.INFO }.take(10)
    if (shown.isEmpty()) {
        T("아직 낮춘 적이 없어요", 26.sp, C.Sub)
        T("상한을 넘는 값이 들어오면 여기에 남아요.", 22.sp, C.Dim)
    }
    for (e in shown) {
        val color = when (e.kind) {
            EventKind.CLAMP, EventKind.STEP_DOWN -> C.Heat
            EventKind.HALT -> C.Danger
            EventKind.SUGGEST -> C.Sub
            EventKind.OVERRIDE, EventKind.OVERRIDE_END -> C.Warn
            else -> C.Text
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.padding(top = 12.dp).size(12.dp).clip(CircleShape).background(color))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val change = if (e.kind != EventKind.SUGGEST && e.item != null && e.from != null && e.to != null)
                    "${e.item.label} ${fmtValue(e.item, e.from)} → ${fmtValue(e.item, e.to)}" else null
                if (change != null) T(change, 25.sp, color, FontWeight.Bold, maxLines = 1)
                T(e.text, 22.sp, if (change == null) color else C.Sub, maxLines = 2)
            }
            T(timeLabel(e.timeMs, start), 19.sp, C.Dim, maxLines = 1)
        }
    }
}

/** 차(Vehicle SDK)와 주고받은 호출 기록. 실패도 숨기지 않고 그대로 보인다. */
@Composable
private fun SdkLogList(ui: UiState) {
    ui.heatBounds[ui.profile.seat]?.let { T("이 차 열선 범위 ${it.first}~${it.last}", 21.sp, C.Sub, maxLines = 1) }
    if (ui.sdkLogs.isEmpty()) T("아직 기록이 없어요", 24.sp, C.Sub)
    for (l in ui.sdkLogs.take(12)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T(if (l.ok) "성공" else "실패", 19.sp, if (l.ok) C.Guard else C.Danger, FontWeight.Bold, maxLines = 1)
            T(l.text, 19.sp, if (l.ok) C.Sub else Color(0xFFFFA8AB), modifier = Modifier.weight(1f), maxLines = 3)
            T(timeLabel(l.timeMs, null), 18.sp, C.Dim, maxLines = 1)
        }
    }
}
