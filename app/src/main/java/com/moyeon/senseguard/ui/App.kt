package com.moyeon.senseguard.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeon.senseguard.GuardViewModel
import com.moyeon.senseguard.engine.AlertMode

enum class Tab(val label: String, val sub: String) {
    DASHBOARD("가드", "지금 상태"),
    PROFILE("규칙 프로필", "내 몸 기준"),
    DEMO("나란히 시연", "기본 차 vs 가드"),
    DRIVER("기사 화면", "목업"),
}

@Composable
fun SenseGuardApp(vm: GuardViewModel, initialTab: Tab = Tab.DASHBOARD) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val ui = vm.ui
    Row(Modifier.fillMaxSize().background(C.Bg)) {
        // 왼쪽 큰 탭
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(C.Surface).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            T("감각 가드", 42.sp, C.Accent, FontWeight.ExtraBold)
            T("화면을 닫아도\n내 몸 기준 상한을 지킵니다", 22.sp, C.Sub)
            Spacer(Modifier.height(12.dp))
            for (t in Tab.entries) {
                val selected = t == tab
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(if (selected) C.Accent else C.Surface2)
                        .clickable { tab = t }
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                ) {
                    T(t.label, 30.sp, if (selected) C.Bg else C.Text, FontWeight.Bold)
                    T(t.sub, 22.sp, if (selected) C.Bg else C.Sub)
                }
            }
            Spacer(Modifier.weight(1f))
            Badge(if (ui.sdkConnected) "Vehicle SDK 연결됨" else "Vehicle SDK 연결 안 됨", if (ui.sdkConnected) C.Ok else C.Danger)
        }

        Column(Modifier.weight(1f).fillMaxHeight().padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            TopBar(vm)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.DASHBOARD -> DashboardScreen(vm)
                    Tab.PROFILE -> ProfileScreen(vm)
                    Tab.DEMO -> DemoScreen(vm)
                    Tab.DRIVER -> DriverScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun TopBar(vm: GuardViewModel) {
    val ui = vm.ui
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        GearBadge(ui.gear)
        if (ui.overrideActive) Badge("이번 주행만 풀림", C.Warn, big = true)
        if (ui.scenario != null) {
            val blink = rememberInfiniteTransition(label = "scn")
            val a by blink.animateFloat(1f, 0.35f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "scnA")
            Box(Modifier.alpha(a)) { Badge("시나리오 재생 중 · 가상 신호 (실제 신호 아님)", C.Mock, big = true) }
        }
        Spacer(Modifier.weight(1f))
        T("${ui.profile.roleLabel} · ${ui.profile.seat.label} 기준", 26.sp, C.Sub)
    }
    AlertStrip(vm)
}

@Composable
private fun AlertStrip(vm: GuardViewModel) {
    val ui = vm.ui
    val alert = ui.alert ?: return
    val lightText = ui.profile.alertMode == AlertMode.LIGHT_TEXT
    val color = if (alert.warning) C.Danger else C.Heat
    val blink = rememberInfiniteTransition(label = "alert")
    val pulse by blink.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "alertA")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(color.copy(alpha = if (lightText) pulse else 0.25f))
            .clickable { vm.dismissAlert() }
            .padding(horizontal = 32.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f)) {
            T(alert.headline, 44.sp, C.Text, FontWeight.ExtraBold)
            T(alert.detail, 28.sp, C.Text)
        }
        T(if (lightText) "빛·글자 알림" else "소리 알림", 22.sp, C.Text)
        T("닫기", 26.sp, C.Text, FontWeight.Bold)
    }
}
