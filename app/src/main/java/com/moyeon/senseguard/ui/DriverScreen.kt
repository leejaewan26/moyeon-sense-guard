package com.moyeon.senseguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeon.senseguard.GuardViewModel
import com.moyeon.senseguard.engine.AlertMode
import com.moyeon.senseguard.engine.fmt

/** 바우처·콜택시 기사 화면. 목업이다(배차 연동 없음). */
@Composable
fun DriverScreen(vm: GuardViewModel) {
    val ui = vm.ui
    val d = ui.driver
    val p = ui.profile
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Pill("목업", C.Mock, big = true)
            T("바우처택시 기사 화면 예시예요. 배차 연동 없이 이 기기의 규칙을 손님 카드로 보여 줍니다.", 22.sp, C.Sub, maxLines = 1)
        }
        if (d.dBeforeCheckAt != null) {
            Panel(Modifier.fillMaxWidth(), color = C.Danger.copy(alpha = 0.14f), border = C.Danger, gap = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T("휠체어 고정을 확인하기 전에 출발했어요", 36.sp, C.Text, FontWeight.ExtraBold, maxLines = 1)
                        T("${timeLabel(d.dBeforeCheckAt, null)} · 알려 드리기만 하고 막지는 않아요", 23.sp, C.Sub, maxLines = 1)
                    }
                    ActionButton("지금 확인했어요", vm::driverCheckWheelchair)
                    ActionButton("닫기", vm::driverDismissAlert, style = Btn.Tonal)
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // 손님 카드: 묻지 않아도 되는 숫자
            Panel(Modifier.weight(1.2f).fillMaxHeight(), gap = 18.dp) {
                T("손님 카드", 24.sp, C.Sub, FontWeight.Bold)
                T("묻지 않아도 되는 손님 설정", 36.sp, C.Text, FontWeight.ExtraBold, maxLines = 1)
                BigRow("실내 온도", "최고", "${fmt(p.cabinTempMaxC)}°C", C.Text, Modifier.weight(1f))
                BigRow("열선", "최대", if (p.maxHeatLevel == 0) "끔" else "${p.maxHeatLevel}단", C.Heat, Modifier.weight(1f))
                BigRow("자동 하강", "한 단씩", "${p.autoStepDownMinutes}분마다", C.Text, Modifier.weight(1f))
                Info("손님 자리", p.seat.label)
                Info("알림", if (p.alertMode == AlertMode.SOUND) "소리로 말해요" else "빛·글자로만")
                T("차가 이 설정대로 지켜요. 카드가 실제와 달라도 기사님 책임이 아니에요.", 21.sp, C.Sub, maxLines = 1)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                val color = if (d.wheelchairChecked) C.Guard else C.Warn
                Panel(Modifier.fillMaxWidth(), border = color.copy(alpha = 0.55f), gap = 16.dp) {
                    T("휠체어 고정", 24.sp, C.Sub, FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(color))
                        T(if (d.wheelchairChecked) "기사가 확인했어요" else "아직 확인 전이에요", 34.sp, color, FontWeight.ExtraBold, maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton("고정 확인", vm::driverCheckWheelchair, Modifier.weight(1.3f), enabled = !d.wheelchairChecked)
                        ActionButton("새 손님", vm::driverNewPassenger, Modifier.weight(1f), style = Btn.Tonal)
                    }
                }
                Panel(Modifier.weight(1f).fillMaxWidth(), gap = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("내 기록", 28.sp, C.Text, FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        T("기사님 소유 · 평가에 쓰지 않아요", 20.sp, C.Sub, maxLines = 1)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (d.checkRecords.isEmpty()) T("아직 기록이 없어요", 24.sp, C.Dim)
                        for (t in d.checkRecords.take(8)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(C.Guard))
                                T("기사가 확인함", 25.sp, C.Text, modifier = Modifier.weight(1f), maxLines = 1)
                                T(timeLabel(t, null), 21.sp, C.Dim, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 기사가 한눈에 읽는 큰 숫자 한 줄. */
@Composable
private fun BigRow(label: String, qualifier: String, value: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(C.Surface2).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(label, 30.sp, C.Text, FontWeight.Bold, maxLines = 1)
            T(qualifier, 22.sp, C.Sub, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        T(value, 76.sp, valueColor, FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(label, 24.sp, C.Sub, maxLines = 1)
        Spacer(Modifier.weight(1f))
        T(value, 28.sp, C.Text, FontWeight.Bold, maxLines = 1)
    }
}
