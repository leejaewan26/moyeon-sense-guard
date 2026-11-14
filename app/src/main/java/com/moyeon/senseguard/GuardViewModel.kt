package com.moyeon.senseguard

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moyeon.senseguard.engine.AlertMode
import com.moyeon.senseguard.engine.BaselineCar
import com.moyeon.senseguard.engine.DemoScenario
import com.moyeon.senseguard.engine.Gear
import com.moyeon.senseguard.engine.GuardAction
import com.moyeon.senseguard.engine.GuardEngine
import com.moyeon.senseguard.engine.GuardEvent
import com.moyeon.senseguard.engine.GuardProfile
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.ItemKind
import com.moyeon.senseguard.engine.ScenarioStep
import com.moyeon.senseguard.engine.Seat
import com.moyeon.senseguard.vehicle.VehicleAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SdkLog(val timeMs: Long, val ok: Boolean, val text: String)

data class AlertBanner(val headline: String, val detail: String, val warning: Boolean, val atRealMs: Long)

data class ScenarioUi(
    val elapsedSec: Int,
    val totalSec: Int,
    val caption: String,
    val baselineLevel: Int,
    val baselineHeldMin: Int,
    val finished: Boolean,
)

data class DriverUi(
    val wheelchairChecked: Boolean = false,
    val checkRecords: List<Long> = emptyList(),
    val dBeforeCheckAt: Long? = null,
)

data class UiState(
    val profile: GuardProfile = GuardProfile(),
    val gear: Gear = Gear.UNKNOWN,
    val overrideActive: Boolean = false,
    val canEdit: Boolean = true,
    val canOverride: Boolean = false,
    val values: Map<ItemKey, Float> = emptyMap(),
    val halted: List<ItemKey> = emptyList(),
    val events: List<GuardEvent> = emptyList(),
    val stepDownRemainingSec: Int? = null,
    val alert: AlertBanner? = null,
    val sdkConnected: Boolean = false,
    val sdkLogs: List<SdkLog> = emptyList(),
    val heatBounds: Map<Seat, IntRange> = emptyMap(),
    val scenario: ScenarioUi? = null,
    val speed: Int = 30,
    val driver: DriverUi = DriverUi(),
    val nowMs: Long = 0,
)

class GuardViewModel(app: Application) : AndroidViewModel(app), VehicleAdapter.Sink {

    private val store = ProfileStore(app)
    private val live = GuardEngine(store.load())
    private val adapter = VehicleAdapter(app, this)
    private val beeper = Beeper()

    private var realGear = Gear.UNKNOWN
    private var sdkLogs = ArrayDeque<SdkLog>()
    private var alert: AlertBanner? = null
    private var driver = DriverUi()
    private var speed = 30

    /** 시나리오 재생 중이면 별도 엔진 인스턴스(같은 클래스)와 가상 시계를 쓴다. */
    private class Run(
        val engine: GuardEngine,
        val steps: List<ScenarioStep>,
        val startVirtual: Long,
        var virtualNow: Long,
        val baseline: BaselineCar,
    ) {
        var idx = 0
        var caption = "시작"
        var finishedAt: Long? = null
    }

    private var run: Run? = null
    private var lastTickReal = SystemClock.elapsedRealtime()

    var ui by mutableStateOf(UiState())
        private set

    val scenarioRunning: Boolean get() = run != null

    init {
        adapter.start()
        publish()
        viewModelScope.launch {
            while (true) {
                delay(250)
                onTick()
            }
        }
    }

    // ---- 시계 ----

    private fun liveNow() = System.currentTimeMillis()
    private fun activeEngine(): GuardEngine = run?.engine ?: live
    private fun activeNow(): Long = run?.virtualNow ?: liveNow()

    private fun onTick() {
        val real = SystemClock.elapsedRealtime()
        val dt = real - lastTickReal
        lastTickReal = real
        run?.let { r ->
            if (r.finishedAt == null) {
                r.virtualNow += dt * speed
                fireDueSteps(r)
            }
            r.baseline.tick(r.virtualNow)
            handle(r.engine.tick(r.virtualNow))
            r.finishedAt?.let { if (real - it > 8_000) stopScenario() }
        }
        handle(live.tick(liveNow()))
        publish()
    }

    // ---- 시나리오 ----

    fun startScenario() {
        if (run != null) return
        val profile = live.profile
        val eng = GuardEngine(profile)
        val start = liveNow()
        val r = Run(eng, DemoScenario.steps(profile), start, start, BaselineCar())
        // 시연 출발점: 내 자리 열선 꺼짐(가상)
        eng.onValue(ItemKey.heat(profile.seat), 0f, start)
        run = r
        alert = null
        driver = driver.copy(dBeforeCheckAt = null)
        log(true, "시나리오 재생 시작 · ${speed}배속 (가상 신호, 쓰기는 실제 SDK)")
        publish()
    }

    fun stopScenario() {
        if (run == null) return
        run = null
        log(true, "시나리오 끝 · 실제 신호로 돌아감")
        handle(live.onGear(realGear, liveNow()))
        adapter.refresh()
        publish()
    }

    fun setSpeed(s: Int) {
        speed = s
        publish()
    }

    private fun fireDueSteps(r: Run) {
        val elapsedSec = ((r.virtualNow - r.startVirtual) / 1000).toInt()
        while (r.idx < r.steps.size && r.steps[r.idx].atSec <= elapsedSec) {
            val step = r.steps[r.idx]
            val at = r.startVirtual + step.atSec * 1000L
            r.caption = step.caption
            // 큰 시간 점프가 와도 순서가 지켜지도록 걸음 시각까지 먼저 흘린다
            handle(r.engine.tick(at))
            r.baseline.tick(at)
            when (step) {
                is ScenarioStep.SetGear -> {
                    val before = r.engine.gear
                    handle(r.engine.onGear(step.gear, at))
                    onDriverGear(before, step.gear)
                }
                is ScenarioStep.Request -> {
                    if (step.item == ItemKey.heat(r.engine.profile.seat)) r.baseline.request(step.value.roundToInt(), at)
                    handle(r.engine.onValue(step.item, step.value, at))
                }
                is ScenarioStep.Override -> r.engine.requestOverride(at)
                is ScenarioStep.Caption -> Unit
            }
            r.idx++
            if (r.idx == r.steps.size) r.finishedAt = SystemClock.elapsedRealtime()
        }
    }

    // ---- 사용자 조작 ----

    fun saveProfile(p: GuardProfile) {
        if (run != null || !live.canEditProfile()) return
        store.save(p)
        handle(live.updateProfile(p, liveNow()))
        publish()
    }

    fun requestOverride() {
        if (run != null) return
        live.requestOverride(liveNow())
        publish()
    }

    fun rearm(item: ItemKey) {
        val eng = activeEngine()
        handle(eng.rearm(item, activeNow()))
        publish()
    }

    fun restoreOnExit() {
        if (run != null || !live.canEditProfile()) return
        val plan = live.restorePlan(liveNow())
        if (plan.isEmpty()) log(true, "하차 복원: 되돌릴 값 없음(우리가 쓴 값이 아니거나 이미 원래 값)")
        handle(plan, silent = true)
        publish()
    }

    fun dismissAlert() {
        alert = null
        publish()
    }

    fun driverCheckWheelchair() {
        driver = driver.copy(wheelchairChecked = true, checkRecords = listOf(liveNow()) + driver.checkRecords, dBeforeCheckAt = null)
        publish()
    }

    fun driverNewPassenger() {
        driver = driver.copy(wheelchairChecked = false, dBeforeCheckAt = null)
        publish()
    }

    fun driverDismissAlert() {
        driver = driver.copy(dBeforeCheckAt = null)
        publish()
    }

    // ---- SDK 콜백 ----

    override fun onGear(gear: Gear) {
        realGear = gear
        if (run != null) return
        val before = live.gear
        handle(live.onGear(gear, liveNow()))
        onDriverGear(before, gear)
        publish()
    }

    override fun onValue(item: ItemKey, value: Float) {
        val r = run
        if (r != null) handle(r.engine.onValue(item, value, r.virtualNow))
        else handle(live.onValue(item, value, liveNow()))
        publish()
    }

    override fun onSdkLog(ok: Boolean, text: String) {
        log(ok, text)
        publish()
    }

    // ---- 내부 ----

    private fun onDriverGear(before: Gear, after: Gear) {
        val leavingPark = (before == Gear.P || before == Gear.UNKNOWN) && (after == Gear.D || after == Gear.R)
        if (leavingPark && !driver.wheelchairChecked) {
            driver = driver.copy(dBeforeCheckAt = liveNow())
            beeper.warning()
        }
    }

    private fun handle(actions: List<GuardAction>, silent: Boolean = false) {
        val eng = activeEngine()
        for (a in actions) {
            when (a) {
                is GuardAction.Write -> {
                    if (!silent) raise(a.reason, "${a.item.label} → ${fmtValue(a.item, a.value)}", warning = false)
                    write(eng, a)
                }
                is GuardAction.Suggest ->
                    if (alert == null || SystemClock.elapsedRealtime() - alert!!.atRealMs > 4_000) {
                        raise("제안 · ${a.item.label} ${fmtValue(a.item, a.suggested)} 이하 권장", "내 자리가 아니라 바꾸지 않았어요", warning = false, sound = false)
                    }
                is GuardAction.Halted -> raise(a.message, "같은 항목을 60초에 3번 깎아 이 항목 가드를 멈췄어요", warning = true)
            }
        }
    }

    private fun write(eng: GuardEngine, a: GuardAction.Write) {
        val onResult: (Boolean) -> Unit = { ok -> if (!ok) eng.onWriteFailed(a.item) }
        when (a.item.kind) {
            ItemKind.SEAT_HEAT -> adapter.setSeatHeat(a.item.seat, a.value.roundToInt(), onResult)
            ItemKind.CABIN_TEMP -> adapter.setCabinTemp(a.item.seat, a.value, onResult)
        }
    }

    private fun raise(headline: String, detail: String, warning: Boolean, sound: Boolean = true) {
        alert = AlertBanner(headline, detail, warning, SystemClock.elapsedRealtime())
        if (sound && activeEngine().profile.alertMode == AlertMode.SOUND) {
            if (warning) beeper.warning() else beeper.clamp()
        }
    }

    private fun log(ok: Boolean, text: String) {
        sdkLogs.addFirst(SdkLog(liveNow(), ok, text))
        while (sdkLogs.size > 40) sdkLogs.removeLast()
    }

    private fun publish() {
        val eng = activeEngine()
        val now = activeNow()
        val r = run
        val stepRemain = eng.heatOnSince?.let { since ->
            val level = eng.current[eng.myHeat] ?: 0f
            if (level > 0f && !eng.overrideActive && eng.myHeat !in eng.halted) {
                ((since + eng.profile.autoStepDownMinutes * 60_000L - now) / 1000).toInt().coerceAtLeast(0)
            } else null
        }
        ui = UiState(
            profile = eng.profile,
            gear = eng.gear,
            overrideActive = eng.overrideActive,
            canEdit = r == null && live.canEditProfile(),
            canOverride = r == null && live.canOverride(),
            values = eng.current.toMap(),
            halted = eng.halted.toList(),
            events = eng.events,
            stepDownRemainingSec = stepRemain,
            alert = alert,
            sdkConnected = adapter.connected,
            sdkLogs = sdkLogs.toList(),
            heatBounds = adapter.seatHeatBounds,
            scenario = r?.let {
                ScenarioUi(
                    elapsedSec = ((it.virtualNow - it.startVirtual) / 1000).toInt(),
                    totalSec = it.steps.last().atSec,
                    caption = it.caption,
                    baselineLevel = it.baseline.level,
                    baselineHeldMin = it.baseline.minutesHeld(it.virtualNow),
                    finished = it.finishedAt != null,
                )
            },
            speed = speed,
            driver = driver,
            nowMs = now,
        )
    }

    /** 시나리오 중 이벤트 시각을 "시연 +mm:ss" 로 보여 주기 위한 기준. */
    fun scenarioStartMs(): Long? = run?.startVirtual

    override fun onCleared() {
        adapter.release()
        beeper.release()
        super.onCleared()
    }

    companion object {
        fun fmtValue(item: ItemKey, v: Float): String = when (item.kind) {
            ItemKind.SEAT_HEAT -> "${v.roundToInt()}단"
            ItemKind.CABIN_TEMP -> "${if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)}°C"
        }
    }
}
