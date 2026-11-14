package com.moyeon.senseguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import com.moyeon.senseguard.engine.fmt

/** 바우처·콜택시 기사 화면. 목업이다(배차 연동 없음). */
@Composable
fun DriverScreen(vm: GuardViewModel) {
    val ui = vm.ui
    val d = ui.driver
    val p = ui.profile
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Badge("목업", C.Mock, big = true)
            T("바우처택시 기사 화면 예시 · 배차 연동 없이 이 기기 프로필을 손님 카드로 보여 줌", 26.sp, C.Sub)
        }
        if (d.dBeforeCheckAt != null) {
            Card(border = C.Danger, bg = C.Danger.copy(alpha = 0.22f)) {
                T("휠체어 고정 체크 전 D 진입", 48.sp, C.Text, FontWeight.ExtraBold)
                T("${timeLabel(d.dBeforeCheckAt, null)} · 알림만 하고 막지는 않습니다", 26.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    BigButton("지금 체크함", vm::driverCheckWheelchair, color = C.Ok)
                    BigButton("닫기", vm::driverDismissAlert, color = C.Surface2)
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Card(Modifier.weight(1f).fillMaxHeight(), border = C.Accent) {
                T("손님 소통 카드", 36.sp, C.Accent, FontWeight.Bold)
                T("손님 설정 · 최고 ${fmt(p.cabinTempMaxC)}°C", 52.sp, C.Text, FontWeight.ExtraBold)
                T("손님 설정 · 열선 최대 ${p.maxHeatLevel}단 · ${p.autoStepDownMinutes}분 뒤 자동 하강", 34.sp, C.Text, FontWeight.Bold)
                T("손님 자리 · ${p.seat.label}", 30.sp)
                T("알림 방식 · ${p.alertMode.label}", 30.sp)
                T("카드 정보가 실제와 다를 때 기사님 책임이 아닙니다.", 22.sp, C.Sub)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Card {
                    T("휠체어 고정", 36.sp, C.Text, FontWeight.Bold)
                    T(if (d.wheelchairChecked) "이번 손님: 기사가 체크함" else "아직 체크 전", 32.sp, if (d.wheelchairChecked) C.Ok else C.Warn, FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        BigButton("고정 체크", vm::driverCheckWheelchair, enabled = !d.wheelchairChecked, color = C.Ok)
                        BigButton("새 손님", vm::driverNewPassenger, color = C.Surface2)
                    }
                }
                Card {
                    T("기록 (기사님 소유 · 평가에 쓰지 않음)", 28.sp, C.Sub)
                    if (d.checkRecords.isEmpty()) T("기록 없음", 26.sp, C.Sub)
                    for (t in d.checkRecords.take(6)) T("${timeLabel(t, null)}  기사가 체크함", 28.sp)
                }
            }
        }
    }
}
