package com.moyeon.senseguard.engine

import kotlin.math.abs

/**
 * 감각 가드 판정 엔진. 안드로이드·SDK 에 의존하지 않는다.
 *
 * - 상한을 넘는 값이 들어오면 되돌리지 않고 상한까지 깎는다(clamp).
 * - 우리가 쓴 값의 메아리 콜백은 거른다.
 * - 같은 항목을 60초에 3회 깎으면 그 항목 가드는 멈추고 알린다.
 * - 상한은 프로필의 자리에만 강제. 다른 자리는 제안만. 실내 온도는 승객(조수석) 자기 존만.
 * - 주행 중(기어 P 아님)엔 풀 수 없고, P 에서만 "이번 주행만 풀기". 다음에 P 로 돌아오면 다시 켜진다.
 * - 내 자리 열선이 켜지면 N분마다 한 단씩 자동 하강.
 * - 여름 과열: 지금 실내 온도가 [HOT_CABIN_C] 이상이 되면 한 번 알리고 내 자리 통풍·에어컨을 켜라고 한다.
 *
 * 시간은 호출하는 쪽이 now(ms) 로 넘긴다(시연 배속·테스트용).
 */
class GuardEngine(profile: GuardProfile = GuardProfile()) {

    var profile: GuardProfile = profile
        private set
    var gear: Gear = Gear.UNKNOWN
        private set
    var overrideActive: Boolean = false
        private set
    private var leftParkDuringOverride = false

    private val pendingEcho = mutableMapOf<ItemKey, Float>()
    private val clampTimes = mutableMapOf<ItemKey, ArrayDeque<Long>>()
    private val _halted = linkedSetOf<ItemKey>()
    val halted: Set<ItemKey> get() = _halted

    private val _current = linkedMapOf<ItemKey, Float>()
    /** 차에서 마지막으로 확인한(또는 우리가 쓴) 값. */
    val current: Map<ItemKey, Float> get() = _current

    private val original = mutableMapOf<ItemKey, Float>()
    private val lastWritten = mutableMapOf<ItemKey, Float>()

    /** 내 자리 열선이 지금 단계가 된 시각(자동 하강 기준). */
    var heatOnSince: Long? = null
        private set

    private val _events = ArrayDeque<GuardEvent>()
    /** 최신이 앞. */
    val events: List<GuardEvent> get() = _events.toList()

    val myHeat: ItemKey get() = ItemKey.heat(profile.seat)

    fun canEditProfile(): Boolean = gear == Gear.P || gear == Gear.UNKNOWN
    fun canOverride(): Boolean = gear == Gear.P && !overrideActive

    fun isEnforced(item: ItemKey): Boolean = when (item.kind) {
        ItemKind.SEAT_HEAT -> item.seat == profile.seat
        ItemKind.CABIN_TEMP -> item.seat == profile.seat && profile.seat == Seat.FRONT_PASSENGER
        ItemKind.CABIN_NOW -> false
    }

    fun limitFor(item: ItemKey): Float = when (item.kind) {
        ItemKind.SEAT_HEAT -> profile.maxHeatLevel.toFloat()
        ItemKind.CABIN_TEMP -> profile.cabinTempMaxC
        ItemKind.CABIN_NOW -> HOT_CABIN_C
    }

    fun reasonFor(item: ItemKey): String = when (item.kind) {
        ItemKind.SEAT_HEAT -> "내 설정 · 열선 최대 ${profile.maxHeatLevel}단"
        ItemKind.CABIN_TEMP -> "내 설정 · 실내 온도 최고 ${fmt(profile.cabinTempMaxC)}°C"
        ItemKind.CABIN_NOW -> "실내 ${fmt(HOT_CABIN_C)}°C 이상 · 좌석이 뜨거울 수 있어요"
    }

    /** P(또는 기어 확인 전)에서만 바뀐다. 바뀌면 지금 값에도 바로 적용. */
    fun updateProfile(newProfile: GuardProfile, now: Long): List<GuardAction> {
        if (!canEditProfile()) return emptyList()
        val seatChanged = newProfile.seat != profile.seat
        profile = newProfile
        if (seatChanged) heatOnSince = if ((_current[myHeat] ?: 0f) > 0f) now else null
        log(now, EventKind.INFO, null, null, null, "프로필 저장 · ${profile.seat.label} · 열선 최대 ${profile.maxHeatLevel}단")
        return enforceCurrent(now)
    }

    fun onGear(newGear: Gear, now: Long): List<GuardAction> {
        val before = gear
        gear = newGear
        if (overrideActive) {
            if (newGear != Gear.P && newGear != Gear.UNKNOWN) leftParkDuringOverride = true
            if (newGear == Gear.P && leftParkDuringOverride) {
                overrideActive = false
                leftParkDuringOverride = false
                log(now, EventKind.OVERRIDE_END, null, null, null, "주행 끝 · 가드 다시 켜짐")
                return enforceCurrent(now)
            }
        }
        if (before != newGear) log(now, EventKind.INFO, null, null, null, "기어 ${newGear.label}")
        return emptyList()
    }

    /** "이번 주행만 풀기". 기어 P 에서만 된다. */
    fun requestOverride(now: Long): Boolean {
        if (!canOverride()) return false
        overrideActive = true
        leftParkDuringOverride = false
        log(now, EventKind.OVERRIDE, null, null, null, "이번 주행만 풀기 · 다음에 P 로 돌아오면 다시 켜짐")
        return true
    }

    /** 멈춘 항목 다시 켜기(더 엄격해지는 쪽이라 기어와 상관없이 허용). */
    fun rearm(item: ItemKey, now: Long): List<GuardAction> {
        if (!_halted.remove(item)) return emptyList()
        clampTimes.remove(item)
        log(now, EventKind.INFO, item, null, null, "${item.label} 가드 다시 켜짐")
        return evaluate(item, _current[item] ?: return emptyList(), now)
    }

    /** 여름 과열 알림이 켜져 있는지(실내가 식으면 풀린다). */
    var hotCabin: Boolean = false
        private set

    /** 차에서 들어온 값(콜백 또는 처음 읽은 값). */
    fun onValue(item: ItemKey, value: Float, now: Long): List<GuardAction> {
        if (item.kind == ItemKind.CABIN_NOW) {
            _current[item] = value
            return checkHotCabin(now)
        }
        val echo = pendingEcho.remove(item)
        if (echo != null && same(echo, value)) {
            _current[item] = value
            return emptyList()
        }
        val prev = _current[item]
        _current[item] = value
        if (item == myHeat) updateHeatTimer(prev, value, now)
        return evaluate(item, value, now)
    }

    /** 주기적으로 불러 자동 하강을 처리한다. */
    fun tick(now: Long): List<GuardAction> {
        val item = myHeat
        val since = heatOnSince ?: return emptyList()
        val level = _current[item] ?: return emptyList()
        if (overrideActive || item in _halted || level <= 0f) return emptyList()
        val period = profile.autoStepDownMinutes * 60_000L
        if (period <= 0 || now - since < period) return emptyList()
        val next = (level - 1f).coerceAtLeast(0f)
        val reason = "내 설정 · ${profile.autoStepDownMinutes}분 뒤 자동 하강"
        heatOnSince = if (next > 0f) now else null
        log(now, EventKind.STEP_DOWN, item, level, next, reason)
        return listOf(write(item, level, next, reason, EventKind.STEP_DOWN))
    }

    /** SDK 쓰기가 실패하면 메아리 대기를 지운다. */
    fun onWriteFailed(item: ItemKey) {
        pendingEcho.remove(item)
    }

    /** 하차 때 복원: 지금 값이 우리가 마지막으로 쓴 값과 같을 때만 원래 값으로(최선 노력). */
    fun restorePlan(now: Long): List<GuardAction.Write> {
        val plan = mutableListOf<GuardAction.Write>()
        for ((item, written) in lastWritten) {
            val cur = _current[item] ?: continue
            val orig = original[item] ?: continue
            if (same(cur, written) && !same(cur, orig)) {
                plan += write(item, cur, orig, "하차 · 원래 값으로", EventKind.RESTORE)
                log(now, EventKind.RESTORE, item, cur, orig, "하차 · ${item.label} 원래 값으로")
            } else if (!same(cur, written)) {
                log(now, EventKind.RESTORE, item, cur, null, "하차 · ${item.label} 은 그새 바뀌어 그대로 둠")
            }
        }
        lastWritten.clear()
        return plan
    }

    // ---- 내부 ----

    private fun enforceCurrent(now: Long): List<GuardAction> =
        _current.entries.toList().filter { it.key.kind != ItemKind.CABIN_NOW }.flatMap { (item, value) -> evaluate(item, value, now) }

    /** 가장 뜨거운 실내 온도로 판단한다. 넘을 때 한 번만 알리고, [HOT_RELEASE_C] 이하로 식으면 다시 알릴 준비를 한다. */
    private fun checkHotCabin(now: Long): List<GuardAction> {
        val hottest = _current.filterKeys { it.kind == ItemKind.CABIN_NOW }.values.maxOrNull() ?: return emptyList()
        if (!hotCabin && hottest >= HOT_CABIN_C - EPS) {
            hotCabin = true
            val reason = "실내 ${fmt(hottest)}°C · 좌석이 뜨거울 수 있어요"
            if (overrideActive) {
                log(now, EventKind.COOL, null, null, null, "$reason (이번 주행만 풀려 있어 켜지 않음)")
                return emptyList()
            }
            log(now, EventKind.COOL, null, null, null, "$reason · ${profile.seat.label} 통풍·에어컨 켬")
            return listOf(GuardAction.CoolDown(profile.seat, hottest, reason))
        }
        if (hotCabin && hottest <= HOT_RELEASE_C) {
            hotCabin = false
            log(now, EventKind.INFO, null, null, null, "실내 ${fmt(hottest)}°C · 과열 알림 풀림")
        }
        return emptyList()
    }

    private fun evaluate(item: ItemKey, value: Float, now: Long): List<GuardAction> {
        val limit = limitFor(item)
        if (value <= limit + EPS) return emptyList()
        val reason = reasonFor(item)
        if (!isEnforced(item)) {
            log(now, EventKind.SUGGEST, item, value, limit, "제안만 · ${item.label} ${fmt(limit)} 이하 권장 (내 자리 아님)")
            return listOf(GuardAction.Suggest(item, value, limit, reason))
        }
        if (overrideActive || item in _halted) return emptyList()

        val times = clampTimes.getOrPut(item) { ArrayDeque() }
        while (times.isNotEmpty() && now - times.first() >= WINDOW_MS) times.removeFirst()
        times.addLast(now)

        log(now, EventKind.CLAMP, item, value, limit, reason)
        val actions = mutableListOf<GuardAction>(write(item, value, limit, reason, EventKind.CLAMP))
        if (times.size >= MAX_CLAMPS) {
            _halted += item
            val msg = "${item.label} 가드 멈춤 · 60초에 ${MAX_CLAMPS}번 깎였어요. 누군가 계속 올리고 있어요"
            log(now, EventKind.HALT, item, null, null, msg)
            actions += GuardAction.Halted(item, msg)
        }
        return actions
    }

    private fun write(item: ItemKey, from: Float, to: Float, reason: String, kind: EventKind): GuardAction.Write {
        original.putIfAbsent(item, from)
        lastWritten[item] = to
        pendingEcho[item] = to
        _current[item] = to
        return GuardAction.Write(item, to, reason, kind)
    }

    private fun updateHeatTimer(prev: Float?, value: Float, now: Long) {
        when {
            value <= 0f -> heatOnSince = null
            prev == null || prev <= 0f -> heatOnSince = now
        }
    }

    private fun log(now: Long, kind: EventKind, item: ItemKey?, from: Float?, to: Float?, text: String) {
        _events.addFirst(GuardEvent(now, kind, item, from, to, text))
        while (_events.size > MAX_EVENTS) _events.removeLast()
    }

    private fun same(a: Float, b: Float) = abs(a - b) < EPS

    companion object {
        const val WINDOW_MS = 60_000L
        const val MAX_CLAMPS = 3

        /**
         * 여름 과열 기준(실내 공기). 근거 두 가지:
         * - 피부 화상은 44°C 에서 시작한다(약 6시간, Moritz & Henriques 1947).
         * - 햇볕·그늘에 세운 차 모두 좌석 표면이 실내 공기보다 약 3~4°C 높았다
         *   (그늘 38.3 → 41.1, 햇볕 46.7 → 50.6, Vanos 외 2018).
         * 그래서 실내 공기가 41°C 이면 좌석은 이미 44°C 언저리라고 본다.
         */
        const val HOT_CABIN_C = 41f
        /** 이 온도 이하로 식으면 알림을 푼다(문턱 근처에서 켜졌다 꺼졌다 하지 않게). */
        const val HOT_RELEASE_C = 38f
        private const val EPS = 0.05f
        private const val MAX_EVENTS = 60
    }
}
