package com.moyeon.senseguard

import com.moyeon.senseguard.engine.AlertMode
import com.moyeon.senseguard.engine.BaselineCar
import com.moyeon.senseguard.engine.DemoScenario
import com.moyeon.senseguard.engine.Gear
import com.moyeon.senseguard.engine.GuardAction
import com.moyeon.senseguard.engine.GuardEngine
import com.moyeon.senseguard.engine.GuardProfile
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.ItemKind
import com.moyeon.senseguard.engine.ScenarioStep
import com.moyeon.senseguard.engine.Seat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** 차 연결. 실제 구현은 Pleos Vehicle SDK 어댑터. */
interface CarPort {
    val connected: Boolean
    val seatHeatBounds: Map<Seat, IntRange>
    fun start(sink: CarSink)
    fun refresh()
    fun setSeatHeat(seat: Seat, level: Int, onResult: (Boolean) -> Unit)
    fun setCabinTemp(seat: Seat, celsius: Float, onResult: (Boolean) -> Unit)
    fun release()
}

/** 차에서 들어오는 신호의 입구. SDK 콜백도 시험 입력도 이리로 들어온다. */
interface CarSink {
    fun onGear(gear: Gear)
    fun onValue(item: ItemKey, value: Float)
    fun onSdkLog(ok: Boolean, text: String)
}

/** wallMs: 엔진 시각(벽시계). elapsedMs: 배속 시연·알림 묶음용 단조 시계. */
interface RuntimeClock {
    fun wallMs(): Long
    fun elapsedMs(): Long
}

/** 알림 소리. 프로세스와 수명이 같다(따로 해제하지 않는다). */
interface AlertSound {
    fun clamp()
    fun warning()
}

interface ProfileRepo {
    fun load(): GuardProfile
    fun save(p: GuardProfile)
}

/**
 * 가드 런타임: 지키는 일 전부(엔진·차 연결·알림·SDK 기록·시나리오 재생·기사 화면 상태).
 * 안드로이드에 의존하지 않는다. 시간 흐름은 바깥(서비스)이 [tick] 을 불러 준다.
 * 모든 호출은 한 스레드(메인)에서 온다고 가정한다.
 */
class GuardRuntime(
    private val car: CarPort,
    private val clock: RuntimeClock,
    private val sound: AlertSound,
    private val repo: ProfileRepo,
) : CarSink {

    private val live = GuardEngine(repo.load())

    /** 시나리오 재생 중에 들어온 실제 기어. 재생이 끝나면 실제 엔진에 넣는다. */
    private var realGear = Gear.UNKNOWN
    private val sdkLogs = ArrayDeque<SdkLog>()
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
    private var started = false
    private var lastTick = clock.elapsedMs()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        publish()
    }

    // ---- 켜고 끄기 ----

    /** 차 연결을 켠다. 여러 번 불러도 한 번만 켠다. */
    fun start() {
        if (started) return
        started = true
        lastTick = clock.elapsedMs()
        car.start(this)
        publish()
    }

    fun stop() {
        if (!started) return
        started = false
        car.release()
        publish()
    }

    /** 250ms 마다 바깥에서 부른다: 시나리오 가상 시계, 자동 하강. */
    fun tick() {
        val real = clock.elapsedMs()
        val dt = real - lastTick
        lastTick = real
        run?.let { r ->
            if (r.finishedAt == null) {
                r.virtualNow += dt * speed
                fireDueSteps(r)
            }
            r.baseline.tick(r.virtualNow)
            handle(r.engine, r.engine.tick(r.virtualNow))
            r.finishedAt?.let { if (real - it > 8_000) stopScenario() }
        }
        handle(live, live.tick(clock.wallMs()))
        publish()
    }

    // ---- 시나리오 ----

    fun startScenario() {
        if (run != null) return
        val profile = live.profile
        val eng = GuardEngine(profile)
        val start = clock.wallMs()
        // 시연 출발점: 내 자리 열선 꺼짐(가상)
        eng.onValue(ItemKey.heat(profile.seat), 0f, start)
        run = Run(eng, DemoScenario.steps(profile), start, start, BaselineCar())
        alert = null
        driver = driver.copy(dBeforeCheckAt = null)
        log(true, "시나리오 재생 시작 · ${speed}배속 (가상 신호, 쓰기는 실제 SDK)")
        publish()
    }

    fun stopScenario() {
        if (run == null) return
        run = null
        log(true, "시나리오 끝 · 실제 신호로 돌아감")
        handle(live, live.onGear(realGear, clock.wallMs()))
        car.refresh()
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
            handle(r.engine, r.engine.tick(at))
            r.baseline.tick(at)
            when (step) {
                is ScenarioStep.SetGear -> {
                    val before = r.engine.gear
                    handle(r.engine, r.engine.onGear(step.gear, at))
                    onDriverGear(before, step.gear)
                }
                is ScenarioStep.Request -> {
                    if (step.item == ItemKey.heat(r.engine.profile.seat)) r.baseline.request(step.value.roundToInt(), at)
                    handle(r.engine, r.engine.onValue(step.item, step.value, at))
                }
                is ScenarioStep.Override -> r.engine.requestOverride(at)
                is ScenarioStep.Caption -> Unit
            }
            r.idx++
            if (r.idx == r.steps.size) r.finishedAt = clock.elapsedMs()
        }
    }

    // ---- 사용자 조작 ----

    /** 프로필 저장은 이 한 곳으로만 간다. 재생 중이거나 주행 중(P 아님)이면 무시한다. */
    fun saveProfile(p: GuardProfile) {
        if (run != null || !live.canEditProfile()) return
        repo.save(p)
        handle(live, live.updateProfile(p, clock.wallMs()))
        publish()
    }

    fun requestOverride() {
        if (run != null) return
        live.requestOverride(clock.wallMs())
        publish()
    }

    fun rearm(item: ItemKey) {
        val r = run
        if (r != null) handle(r.engine, r.engine.rearm(item, r.virtualNow))
        else handle(live, live.rearm(item, clock.wallMs()))
        publish()
    }

    fun restoreOnExit() {
        if (run != null || !live.canEditProfile()) return
        val plan = live.restorePlan(clock.wallMs())
        if (plan.isEmpty()) log(true, "하차 복원: 되돌릴 값 없음(우리가 쓴 값이 아니거나 이미 원래 값)")
        handle(live, plan, silent = true)
        publish()
    }

    fun dismissAlert() {
        alert = null
        publish()
    }

    fun driverCheckWheelchair() {
        driver = driver.copy(wheelchairChecked = true, checkRecords = listOf(clock.wallMs()) + driver.checkRecords, dBeforeCheckAt = null)
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

    // ---- 차에서 들어오는 신호 ----

    override fun onGear(gear: Gear) {
        realGear = gear
        if (run != null) return
        val before = live.gear
        handle(live, live.onGear(gear, clock.wallMs()))
        onDriverGear(before, gear)
        publish()
    }

    /** 재생 중에는 재생 엔진으로 보낸다: 재생 엔진이 쓴 값의 메아리를 거르기 위해서다. */
    override fun onValue(item: ItemKey, value: Float) {
        val r = run
        if (r != null) handle(r.engine, r.engine.onValue(item, value, r.virtualNow))
        else handle(live, live.onValue(item, value, clock.wallMs()))
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
            driver = driver.copy(dBeforeCheckAt = clock.wallMs())
            sound.warning()
        }
    }

    private fun handle(eng: GuardEngine, actions: List<GuardAction>, silent: Boolean = false) {
        for (a in actions) {
            when (a) {
                is GuardAction.Write -> {
                    if (!silent) raise(eng, a.reason, "${a.item.label} → ${fmtValue(a.item, a.value)}", warning = false)
                    write(eng, a)
                }
                is GuardAction.Suggest -> {
                    val last = alert
                    if (last == null || clock.elapsedMs() - last.atRealMs > 4_000) {
                        raise(eng, "제안 · ${a.item.label} ${fmtValue(a.item, a.suggested)} 이하 권장", "내 자리가 아니라 바꾸지 않았어요", warning = false, sound = false)
                    }
                }
                is GuardAction.Halted -> raise(eng, a.message, "같은 항목을 60초에 3번 깎아 이 항목 가드를 멈췄어요", warning = true)
            }
        }
    }

    private fun write(eng: GuardEngine, a: GuardAction.Write) {
        val onResult: (Boolean) -> Unit = { ok -> if (!ok) eng.onWriteFailed(a.item) }
        when (a.item.kind) {
            ItemKind.SEAT_HEAT -> car.setSeatHeat(a.item.seat, a.value.roundToInt(), onResult)
            ItemKind.CABIN_TEMP -> car.setCabinTemp(a.item.seat, a.value, onResult)
        }
    }

    private fun raise(eng: GuardEngine, headline: String, detail: String, warning: Boolean, sound: Boolean = true) {
        alert = AlertBanner(headline, detail, warning, clock.elapsedMs())
        if (sound && eng.profile.alertMode == AlertMode.SOUND) {
            if (warning) this.sound.warning() else this.sound.clamp()
        }
    }

    private fun log(ok: Boolean, text: String) {
        sdkLogs.addFirst(SdkLog(clock.wallMs(), ok, text))
        while (sdkLogs.size > 40) sdkLogs.removeLast()
    }

    private fun publish() {
        val r = run
        val eng = r?.engine ?: live
        val now = r?.virtualNow ?: clock.wallMs()
        val stepRemain = eng.heatOnSince?.let { since ->
            val level = eng.current[eng.myHeat] ?: 0f
            if (level > 0f && !eng.overrideActive && eng.myHeat !in eng.halted) {
                ((since + eng.profile.autoStepDownMinutes * 60_000L - now) / 1000).toInt().coerceAtLeast(0)
            } else null
        }
        _state.value = UiState(
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
            sdkConnected = car.connected,
            sdkLogs = sdkLogs.toList(),
            heatBounds = car.seatHeatBounds,
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
            scenarioStartMs = r?.startVirtual,
        )
    }
}
