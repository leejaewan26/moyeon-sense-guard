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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeon.senseguard.GuardViewModel
import com.moyeon.senseguard.engine.AlertMode

enum class Tab(val label: String, val sub: String) {
    DASHBOARD("가드", "지금 상태"),
    PROFILE("내 규칙", "내 몸 기준"),
    DEMO("나란히 시연", "기본 차와 비교"),
    DRIVER("기사 화면", "목업"),
}

@Composable
fun SenseGuardApp(vm: GuardViewModel, initialTab: Tab = Tab.DASHBOARD) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val ui = vm.ui
    Row(Modifier.fillMaxSize().background(C.Bg)) {
        // 왼쪽 메뉴
        Column(
            Modifier.width(280.dp).fillMaxHeight().background(C.Surface).padding(horizontal = 18.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("감각 가드", 40.sp, C.Text, FontWeight.ExtraBold)
                T("화면을 닫아도\n내 몸 기준 상한을 지켜요", 21.sp, C.Sub)
            }
            Spacer(Modifier.height(22.dp))
            for (t in Tab.entries) {
                val selected = t == tab
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 104.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (selected) C.Surface3 else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab) { tab = t }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TabIcon(t, if (selected) C.Guard else C.Dim)
                    Column {
                        T(t.label, 28.sp, if (selected) C.Text else C.Sub, if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                        T(t.sub, 20.sp, if (selected) C.Sub else C.Dim, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (ui.sdkConnected) C.Guard else C.Danger))
                T(if (ui.sdkConnected) "차와 연결됨" else "차와 연결 안 됨", 21.sp, if (ui.sdkConnected) C.Sub else C.Danger, maxLines = 1)
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            TopBar(vm)
            AlertStrip(vm)
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
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GearPill(ui.gear)
        if (ui.overrideActive) Pill("이번 주행만 풀림", C.Warn, big = true, dot = true)
        if (ui.scenario != null) {
            val blink = rememberInfiniteTransition(label = "scn")
            val a by blink.animateFloat(1f, 0.45f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scnA")
            Box(Modifier.alpha(a)) { Pill("시나리오 재생 중 · 가상 신호 (실제 신호 아님)", C.Mock, big = true) }
        }
        ui.testInput?.let { Pill(it, C.Mock, big = true) }
        Spacer(Modifier.weight(1f))
        T("${ui.profile.roleLabel} · ${ui.profile.seat.label} 기준", 24.sp, C.Sub, maxLines = 1)
    }
}

/**
 * 방금 한 일을 알리는 줄. «빛·글자» 프로필이면 밝게 깜빡여 소리 없이도 눈에 띄게 한다.
 * 글자는 언제나 어두운 색이라 깜빡이는 동안에도 읽힌다.
 */
@Composable
private fun AlertStrip(vm: GuardViewModel) {
    val ui = vm.ui
    val alert = ui.alert ?: return
    val color = if (alert.warning) C.Danger else C.Heat
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(color).clickable(role = Role.Button, onClickLabel = "알림 닫기") { vm.dismissAlert() }) {
        if (ui.profile.alertMode == AlertMode.LIGHT_TEXT) {
            val blink = rememberInfiniteTransition(label = "alert")
            val flash by blink.animateFloat(0f, 0.38f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "alertA")
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = flash)))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(alert.headline, 36.sp, C.Bg, FontWeight.ExtraBold, maxLines = 1)
                T(alert.detail, 26.sp, C.Bg, FontWeight.Medium, maxLines = 2)
            }
            T(if (alert.spoken) "말로 알림" else "자막 알림", 22.sp, C.Bg.copy(alpha = 0.75f), FontWeight.Bold, maxLines = 1)
            T("닫기", 26.sp, C.Bg, FontWeight.Bold, maxLines = 1)
        }
    }
}
