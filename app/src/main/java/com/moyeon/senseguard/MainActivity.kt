package com.moyeon.senseguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.moyeon.senseguard.ui.SenseGuardApp
import com.moyeon.senseguard.ui.SenseGuardTheme
import com.moyeon.senseguard.ui.Tab

class MainActivity : ComponentActivity() {
    private val vm: GuardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시연·캡처용: adb shell am start ... --es tab DEMO
        val tab = runCatching { Tab.valueOf(intent.getStringExtra("tab") ?: "") }.getOrDefault(Tab.DASHBOARD)
        if (intent.getBooleanExtra("scenario", false)) vm.startScenario()
        setContent {
            SenseGuardTheme { SenseGuardApp(vm, tab) }
        }
    }
}
