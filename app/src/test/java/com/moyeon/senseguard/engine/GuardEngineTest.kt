package com.moyeon.senseguard.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardEngineTest {

    private val driverHeat = ItemKey.heat(Seat.DRIVER)
    private val passengerHeat = ItemKey.heat(Seat.FRONT_PASSENGER)
    private val min = 60_000L

    private fun engine(profile: GuardProfile = GuardProfile()) = GuardEngine(profile)

    private fun writes(actions: List<GuardAction>) = actions.filterIsInstance<GuardAction.Write>()

    @Test
    fun `상한을 넘으면 되돌리지 않고 상한까지 깎는다`() {
        val e = engine()
        e.onValue(driverHeat, 0f, 0)
        val out = e.onValue(driverHeat, 3f, 1_000)
        val w = writes(out).single()
        assertEquals(2f, w.value)
        assertEquals("내 설정 · 열선 최대 2단", w.reason)
        assertEquals(EventKind.CLAMP, e.events.first().kind)
    }

    @Test
    fun `상한 이하 값은 건드리지 않는다`() {
        val e = engine()
        assertTrue(e.onValue(driverHeat, 2f, 0).isEmpty())
        assertTrue(e.onValue(driverHeat, 1f, 10).isEmpty())
    }

    @Test
    fun `우리가 쓴 값의 메아리 콜백은 거른다`() {
        val e = engine()
        e.onValue(driverHeat, 3f, 0)
        val echo = e.onValue(driverHeat, 2f, 100)
        assertTrue(echo.isEmpty())
        assertEquals(1, e.events.count { it.kind == EventKind.CLAMP })
    }

    @Test
    fun `60초에 3회 깎으면 그 항목 가드는 멈춘다`() {
        val e = engine()
        repeat(3) { i ->
            e.onValue(driverHeat, 3f, i * 10_000L)
            e.onValue(driverHeat, 2f, i * 10_000L + 100) // 메아리
        }
        assertTrue(driverHeat in e.halted)
        assertEquals(EventKind.HALT, e.events.first().kind)
        // 멈춘 뒤에는 깎지 않는다
        assertTrue(writes(e.onValue(driverHeat, 3f, 35_000)).isEmpty())
        // 다른 항목은 영향 없음
        assertFalse(passengerHeat in e.halted)
    }

    @Test
    fun `60초가 지나 띄엄띄엄 깎이면 멈추지 않는다`() {
        val e = engine()
        repeat(3) { i ->
            e.onValue(driverHeat, 3f, i * 61_000L)
            e.onValue(driverHeat, 2f, i * 61_000L + 100)
        }
        assertTrue(e.halted.isEmpty())
    }

    @Test
    fun `멈춘 항목은 다시 켜면 바로 깎는다`() {
        val e = engine()
        repeat(3) { i -> e.onValue(driverHeat, 3f, i * 1_000L); e.onValue(driverHeat, 2f, i * 1_000L + 1) }
        e.onValue(driverHeat, 3f, 5_000)
        val out = e.rearm(driverHeat, 6_000)
        assertEquals(2f, writes(out).single().value)
        assertTrue(e.halted.isEmpty())
    }

    @Test
    fun `다른 자리는 깎지 않고 제안만 한다`() {
        val e = engine(GuardProfile(seat = Seat.DRIVER))
        val out = e.onValue(passengerHeat, 3f, 0)
        assertTrue(writes(out).isEmpty())
        assertTrue(out.single() is GuardAction.Suggest)
    }

    @Test
    fun `승객 프로필이면 그 좌석만 강제한다`() {
        val e = engine(GuardProfile(seat = Seat.FRONT_PASSENGER))
        assertEquals(2f, writes(e.onValue(passengerHeat, 3f, 0)).single().value)
        assertTrue(writes(e.onValue(driverHeat, 3f, 0)).isEmpty())
    }

    @Test
    fun `실내 온도 상한은 승객 자기 존만 강제한다`() {
        val passenger = engine(GuardProfile(seat = Seat.FRONT_PASSENGER, cabinTempMaxC = 26f))
        val w = writes(passenger.onValue(ItemKey.temp(Seat.FRONT_PASSENGER), 29f, 0)).single()
        assertEquals(26f, w.value)
        assertEquals("내 설정 · 실내 온도 최고 26°C", w.reason)
        // 운전석 존은 제안만
        assertTrue(writes(passenger.onValue(ItemKey.temp(Seat.DRIVER), 29f, 0)).isEmpty())

        // 운전자 프로필이면 실내 온도는 어느 존이든 제안만
        val driver = engine(GuardProfile(seat = Seat.DRIVER))
        assertTrue(writes(driver.onValue(ItemKey.temp(Seat.DRIVER), 29f, 0)).isEmpty())
    }

    @Test
    fun `주행 중에는 풀 수 없고 P에서만 이번 주행만 풀기`() {
        val e = engine()
        e.onGear(Gear.D, 0)
        assertFalse(e.requestOverride(1))
        assertFalse(e.canEditProfile())
        e.onGear(Gear.P, 2)
        assertTrue(e.requestOverride(3))
        // 풀린 동안은 깎지 않는다(주행 중에도)
        e.onGear(Gear.D, 4)
        assertTrue(writes(e.onValue(driverHeat, 3f, 5)).isEmpty())
    }

    @Test
    fun `이번 주행만 풀기는 다음 P에서 끝나고 지금 값을 다시 깎는다`() {
        val e = engine()
        e.onGear(Gear.P, 0)
        e.requestOverride(1)
        e.onGear(Gear.D, 2)
        e.onValue(driverHeat, 3f, 3)
        val out = e.onGear(Gear.P, 4)
        assertFalse(e.overrideActive)
        assertEquals(2f, writes(out).single().value)
    }

    @Test
    fun `P에서 풀고 바로 P 신호가 다시 와도 풀린 상태는 유지된다`() {
        val e = engine()
        e.onGear(Gear.P, 0)
        e.requestOverride(1)
        e.onGear(Gear.P, 2)
        assertTrue(e.overrideActive)
    }

    @Test
    fun `주행 중에는 프로필을 바꿀 수 없다`() {
        val e = engine()
        e.onGear(Gear.D, 0)
        e.updateProfile(GuardProfile(maxHeatLevel = 3), 1)
        assertEquals(2, e.profile.maxHeatLevel)
        e.onGear(Gear.P, 2)
        e.updateProfile(GuardProfile(maxHeatLevel = 1), 3)
        assertEquals(1, e.profile.maxHeatLevel)
    }

    @Test
    fun `자동 하강은 N분마다 한 단씩 내린다`() {
        val e = engine(GuardProfile(autoStepDownMinutes = 10))
        e.onValue(driverHeat, 2f, 0)
        assertTrue(e.tick(9 * min).isEmpty())
        val first = writes(e.tick(10 * min)).single()
        assertEquals(1f, first.value)
        assertEquals("내 설정 · 10분 뒤 자동 하강", first.reason)
        e.onValue(driverHeat, 1f, 10 * min + 100) // 메아리
        assertTrue(e.tick(19 * min).isEmpty())
        assertEquals(0f, writes(e.tick(20 * min)).single().value)
        assertNull(e.heatOnSince)
        assertTrue(e.tick(40 * min).isEmpty())
    }

    @Test
    fun `깎인 뒤에도 자동 하강 시계는 처음 켠 시각부터 간다`() {
        val e = engine(GuardProfile(autoStepDownMinutes = 10))
        e.onValue(driverHeat, 0f, 0)
        e.onValue(driverHeat, 3f, 1 * min)
        e.onValue(driverHeat, 2f, 1 * min + 100)
        assertEquals(1f, writes(e.tick(11 * min)).single().value)
    }

    @Test
    fun `풀린 동안에는 자동 하강도 하지 않는다`() {
        val e = engine()
        e.onGear(Gear.P, 0)
        e.requestOverride(0)
        e.onValue(driverHeat, 3f, 0)
        assertTrue(e.tick(60 * min).isEmpty())
    }

    @Test
    fun `하차 복원은 지금 값이 우리가 쓴 값과 같을 때만`() {
        val e = engine()
        e.onValue(driverHeat, 3f, 0)          // 원래 3 → 2 로 깎음
        e.onValue(driverHeat, 2f, 1)          // 메아리
        e.onValue(passengerHeat, 3f, 0)       // 남의 자리, 안 씀
        val plan = e.restorePlan(10)
        assertEquals(1, plan.size)
        assertEquals(3f, plan.single().value)

        val e2 = engine()
        e2.onValue(driverHeat, 3f, 0)
        e2.onValue(driverHeat, 2f, 1)
        e2.onValue(driverHeat, 1f, 2)         // 그새 누군가 1 로 바꿈
        assertTrue(e2.restorePlan(10).isEmpty())
    }

    @Test
    fun `쓰기 실패 뒤 같은 값이 들어오면 메아리로 거르지 않는다`() {
        val e = engine()
        e.onValue(driverHeat, 3f, 0)
        e.onWriteFailed(driverHeat)
        // 실패했으니 차는 여전히 3 → 다시 들어오면 다시 깎는다
        assertEquals(2f, writes(e.onValue(driverHeat, 3f, 10)).single().value)
    }

    @Test
    fun `시연 시나리오를 엔진에 흘리면 깎임·자동 하강·제안·풀기가 모두 나온다`() {
        val profile = GuardProfile()
        val e = GuardEngine(profile)
        val base = BaselineCar()
        e.onValue(driverHeat, 0f, 0)
        val steps = DemoScenario.steps(profile)
        var idx = 0
        val end = DemoScenario.durationSec(profile)
        for (sec in 0..end) {
            val now = sec * 1_000L
            while (idx < steps.size && steps[idx].atSec <= sec) {
                when (val s = steps[idx]) {
                    is ScenarioStep.SetGear -> e.onGear(s.gear, now)
                    is ScenarioStep.Request -> {
                        if (s.item == driverHeat) base.request(s.value.toInt(), now)
                        e.onValue(s.item, s.value, now)
                    }
                    is ScenarioStep.Override -> assertTrue(e.requestOverride(now))
                    is ScenarioStep.Caption -> Unit
                }
                idx++
            }
            e.tick(now)
            base.tick(now)
        }
        val kinds = e.events.map { it.kind }
        assertEquals(2, kinds.count { it == EventKind.CLAMP })
        assertEquals(1, kinds.count { it == EventKind.STEP_DOWN })
        assertTrue(EventKind.SUGGEST in kinds)
        assertTrue(EventKind.OVERRIDE in kinds)
        assertTrue(e.halted.isEmpty())
        assertEquals(3f, e.current[driverHeat])   // 풀린 뒤 3단
        assertEquals(3, base.level)                // 기본 차는 30분 안 돼서 3단 유지
    }

    private val driverNow = ItemKey.now(Seat.DRIVER)
    private val passengerNow = ItemKey.now(Seat.FRONT_PASSENGER)

    @Test
    fun `지금 실내 온도가 41도 미만이면 아무것도 하지 않는다`() {
        val e = engine()
        assertTrue(e.onValue(driverNow, 40.9f, 0).isEmpty())
        assertFalse(e.hotCabin)
    }

    @Test
    fun `41도 이상이면 내 자리 식히기를 한 번 내고, 38도 이하로 식은 뒤 다시 넘으면 또 낸다`() {
        val e = engine(GuardProfile(seat = Seat.FRONT_PASSENGER))
        val first = e.onValue(passengerNow, 47f, 0).filterIsInstance<GuardAction.CoolDown>().single()
        assertEquals(Seat.FRONT_PASSENGER, first.seat)
        assertEquals(47f, first.celsius)
        assertEquals(EventKind.COOL, e.events.first().kind)
        assertTrue(e.onValue(passengerNow, 45f, 1_000).isEmpty())
        assertTrue(e.onValue(passengerNow, 39f, 2_000).isEmpty())
        assertTrue(e.hotCabin)
        assertTrue(e.onValue(passengerNow, 38f, 3_000).isEmpty())
        assertFalse(e.hotCabin)
        assertEquals(1, e.onValue(passengerNow, 42f, 4_000).filterIsInstance<GuardAction.CoolDown>().size)
    }

    @Test
    fun `가장 뜨거운 구역으로 판단한다`() {
        val e = engine()
        e.onValue(driverNow, 30f, 0)
        val out = e.onValue(passengerNow, 44f, 1_000)
        assertEquals(Seat.DRIVER, out.filterIsInstance<GuardAction.CoolDown>().single().seat)
    }

    @Test
    fun `이번 주행만 풀려 있으면 식히기를 내지 않고 기록만 남긴다`() {
        val e = engine()
        e.onGear(Gear.P, 0)
        assertTrue(e.requestOverride(0))
        assertTrue(e.onValue(driverNow, 50f, 1_000).isEmpty())
        assertEquals(EventKind.COOL, e.events.first().kind)
    }

    @Test
    fun `지금 실내 온도는 상한 판정이나 쓰기 대상이 아니다`() {
        val e = engine()
        e.onValue(driverNow, 60f, 0)
        e.onGear(Gear.P, 1_000)
        val out = e.updateProfile(GuardProfile(maxHeatLevel = 1), 2_000)
        assertTrue(writes(out).none { it.item.kind == ItemKind.CABIN_NOW })
        assertEquals(60f, e.current[driverNow])
    }
}
