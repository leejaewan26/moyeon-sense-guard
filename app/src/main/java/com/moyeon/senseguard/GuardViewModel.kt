package com.moyeon.senseguard

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moyeon.senseguard.engine.GuardProfile
import com.moyeon.senseguard.engine.ItemKey
import kotlinx.coroutines.launch

/** 화면 쪽 얇은 층: 가드 런타임 상태를 받아 그리고, 버튼은 런타임 명령으로 넘긴다. 지키는 일은 [GuardService] 가 한다. */
class GuardViewModel(app: Application) : AndroidViewModel(app) {

    private val runtime = GuardRuntimes.get(app)

    var ui by mutableStateOf(runtime.state.value)
        private set

    init {
        viewModelScope.launch { runtime.state.collect { ui = it } }
    }

    fun startScenario() = runtime.startScenario()
    fun stopScenario() = runtime.stopScenario()
    fun setSpeed(s: Int) = runtime.setSpeed(s)
    fun saveProfile(p: GuardProfile) = runtime.saveProfile(p)
    fun requestOverride() = runtime.requestOverride()
    fun rearm(item: ItemKey) = runtime.rearm(item)
    fun restoreOnExit() = runtime.restoreOnExit()
    fun dismissAlert() = runtime.dismissAlert()
    fun driverCheckWheelchair() = runtime.driverCheckWheelchair()
    fun driverNewPassenger() = runtime.driverNewPassenger()
    fun driverDismissAlert() = runtime.driverDismissAlert()
}
