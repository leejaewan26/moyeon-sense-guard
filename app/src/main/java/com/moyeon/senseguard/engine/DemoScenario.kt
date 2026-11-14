package com.moyeon.senseguard.engine

/** 시연 시나리오의 한 걸음. 시각은 가상 초(배속 적용 전). */
sealed interface ScenarioStep {
    val atSec: Int
    val caption: String

    data class SetGear(override val atSec: Int, val gear: Gear, override val caption: String) : ScenarioStep
    data class Request(override val atSec: Int, val item: ItemKey, val value: Float, override val caption: String) : ScenarioStep
    data class Override(override val atSec: Int, override val caption: String) : ScenarioStep
    data class Caption(override val atSec: Int, override val caption: String) : ScenarioStep
}

/**
 * 30초 시연용 가상 신호 목록. 에뮬레이터 VHAL 패널을 만지지 않아도 같은 엔진으로 재현된다.
 * 30배속이면 약 33초.
 */
object DemoScenario {
    fun steps(profile: GuardProfile): List<ScenarioStep> {
        val mine = ItemKey.heat(profile.seat)
        val otherSeat = if (profile.seat == Seat.DRIVER) Seat.FRONT_PASSENGER else Seat.DRIVER
        val stepDownAt = 30 + profile.autoStepDownMinutes * 60
        return listOf(
            ScenarioStep.SetGear(0, Gear.P, "주차 상태 · 출발 준비"),
            ScenarioStep.SetGear(5, Gear.D, "기어 D · 주행 시작"),
            ScenarioStep.Request(30, mine, 3f, "${mine.label} 3단 요청"),
            ScenarioStep.Request(60, ItemKey.heat(otherSeat), 3f, "${otherSeat.label} 열선 3단 (다른 자리 → 제안만)"),
            ScenarioStep.Caption(stepDownAt, "${profile.autoStepDownMinutes}분 경과 · 자동 하강"),
            ScenarioStep.Request(stepDownAt + 90, mine, 3f, "주행 중 다시 3단 시도"),
            ScenarioStep.SetGear(stepDownAt + 270, Gear.P, "정차 · 기어 P"),
            ScenarioStep.Override(stepDownAt + 300, "P단에서 본인이 \"이번 주행만 풀기\""),
            ScenarioStep.Request(stepDownAt + 330, mine, 3f, "풀린 상태 · 3단 그대로"),
            ScenarioStep.Caption(stepDownAt + 360, "시연 끝"),
        )
    }

    fun durationSec(profile: GuardProfile): Int = steps(profile).last().atSec
}

/**
 * 나란히 시연의 왼쪽 '기본 차'. 시간 기준 하강(30분 뒤 한 단), 누구나 다시 올림, 주행 중에도 막지 않음.
 */
class BaselineCar(private val holdMinutes: Int = 30) {
    var level: Int = 0
        private set
    private var since: Long = 0

    fun request(value: Int, now: Long) {
        level = value
        since = now
    }

    fun tick(now: Long) {
        if (level > 0 && now - since >= holdMinutes * 60_000L) {
            level -= 1
            since = now
        }
    }

    fun minutesHeld(now: Long): Int = if (level > 0) ((now - since) / 60_000L).toInt() else 0
}
