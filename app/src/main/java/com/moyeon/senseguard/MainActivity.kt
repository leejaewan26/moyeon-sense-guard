package com.moyeon.senseguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.moyeon.senseguard.ui.SenseGuardApp
import com.moyeon.senseguard.ui.SenseGuardTheme
import com.moyeon.senseguard.ui.Tab

class MainActivity : ComponentActivity() {
    private val vm: GuardViewModel by viewModels()

    // 거절해도 서비스는 돈다. 알림 줄 표시만 빠진다. 허용하면 서비스가 알림을 다시 올리게 한 번 더 부른다.
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) GuardService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardService.start(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        // 시연·캡처용: adb shell am start ... --es tab DEMO [--ez scenario true]
        val tab = runCatching { Tab.valueOf(intent.getStringExtra("tab") ?: "") }.getOrDefault(Tab.DASHBOARD)
        // 시나리오는 실제 SDK 쓰기를 내므로, release 에서는 다른 앱이 인텐트로 켜지 못하게 디버그 빌드에서만 받는다.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("scenario", false)) vm.startScenario()
        setContent {
            SenseGuardTheme { SenseGuardApp(vm, tab) }
        }
    }
}
