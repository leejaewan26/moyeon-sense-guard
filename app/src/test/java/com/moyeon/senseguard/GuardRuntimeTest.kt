package com.moyeon.senseguard

import com.moyeon.senseguard.engine.AlertMode
import com.moyeon.senseguard.engine.Gear
import com.moyeon.senseguard.engine.GuardProfile
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 가드 런타임 시험. 가짜 차·시계·소리·저장소를 넣고 바깥 동작(차에 나간 쓰기·화면 상태)만 본다. */
class GuardRuntimeTest {

    private val driverHeat = ItemKey.heat(Seat.DRIVER)
    private val min = 60_000L

    private class FakeCar : CarPort {
        lateinit var sink: CarSink
        /** 차에 나간 쓰기(항목, 값) 순서대로. */
        val writes = mutableListOf<Pair<ItemKey, Float>>()
        override val connected = true
        override val seatHeatBounds: Map<Seat, IntRange> = emptyMap()
        override fun start(sink: CarSink) { this.sink = sink }
        override fun refresh() {}
        override fun setSeatHeat(seat: Seat, level: Int, onResult: (Boolean) -> Unit) {
            writes += ItemKey.heat(seat) to level.toFloat(); onResult(true)
        }
        override fun setCabinTemp(seat: Seat, celsius: Float, onResult: (Boolean) -> Unit) {
            writes += ItemKey.temp(seat) to celsius; onResult(true)
        }
        /** 통풍·에어컨을 켜라고 한 자리 순서대로. */
        val cooled = mutableListOf<Seat>()
        override fun startCooling(seat: Seat) { cooled += seat }
        override fun release() {}
    }

    private class FakeClock(var wall: Long = 1_700_000_000_000L, var elapsed: Long = 0L) : RuntimeClock {
        override fun wallMs() = wall
        override fun elapsedMs() = elapsed
        fun advance(ms: Long) { wall += ms; elapsed += ms }
    }

    private class CountingSound : AlertSound {
        /** 말한 문장 순서대로. */
        val spoken = mutableListOf<String>()
        var warnings = 0
        override fun speak(text: String) { spoken += text }
        override fun warning() { warnings++ }
    }

    private val lowered2 = "운전석 열선을 2단으로 낮췄어요. 내 설정이에요."

    private class MemRepo(var saved: GuardProfile = GuardProfile()) : ProfileRepo {
        var saves = 0
        override fun load() = saved
        override fun save(p: GuardProfile) { saved = p; saves++ }
    }

    private val car = FakeCar()
    private val clock = FakeClock()
    private val sound = CountingSound()

    private fun runtime(profile: GuardProfile = GuardProfile(), car: FakeCar = this.car): Pair<GuardRuntime, MemRepo> {
        val repo = MemRepo(profile)
        val rt = GuardRuntime(car, clock, sound, repo)
        rt.start()
        return rt to repo
    }

    @Test
    fun `D단에서 내 자리 열선 3단이 들어오면 차에 2단 쓰기가 나가고 화면 값도 2단이다`() {
        val (rt, _) = runtime()
        car.sink.onGear(Gear.D)
        car.sink.onValue(driverHeat, 3f)
        assertEquals(listOf(driverHeat to 2f), car.writes)
        assertEquals(2f, rt.state.value.values[driverHeat])
    }

    @Test
    fun `내 자리 열선이 켜진 채 자동 하강 시간이 지나면 tick 이 한 단 내린다`() {
        val (rt, _) = runtime(GuardProfile(autoStepDownMinutes = 10))
        car.sink.onGear(Gear.D)
        car.sink.onValue(driverHeat, 2f)
        clock.advance(9 * min)
        rt.tick()
        assertTrue("9분에는 아직 안 내림", car.writes.isEmpty())
        clock.advance(1 * min)
        rt.tick()
        assertEquals(listOf(driverHeat to 1f), car.writes)
    }

    @Test
    fun `주행 중에는 프로필이 저장되지 않고 P단에서는 저장소에 저장된다`() {
        val (rt, repo) = runtime()
        val stricter = GuardProfile(maxHeatLevel = 1)
        car.sink.onGear(Gear.D)
        rt.saveProfile(stricter)
        assertEquals(0, repo.saves)
        assertEquals(2, rt.state.value.profile.maxHeatLevel)

        car.sink.onGear(Gear.P)
        rt.saveProfile(stricter)
        assertEquals(1, repo.saves)
        assertEquals(1, repo.saved.maxHeatLevel)
        assertEquals(1, rt.state.value.profile.maxHeatLevel)
    }

    @Test
    fun `시나리오 재생 중 들어온 실제 기어는 재생 화면에 안 섞이고 재생이 끝나면 반영된다`() {
        val (rt, _) = runtime()
        car.sink.onGear(Gear.P)
        rt.startScenario()
        car.sink.onGear(Gear.D)
        assertNotEquals(Gear.D, rt.state.value.gear)
        assertNotNull(rt.state.value.scenario)

        rt.stopScenario()
        assertEquals(Gear.D, rt.state.value.gear)
        assertEquals(null, rt.state.value.scenario)
    }

    @Test
    fun `소리 알림 프로필은 깎을 때 그 문장을 한 번 말하고 같은 글을 자막으로 띄운다`() {
        val (rt, _) = runtime(GuardProfile(alertMode = AlertMode.SOUND))
        car.sink.onGear(Gear.D)
        car.sink.onValue(driverHeat, 3f)
        assertEquals(listOf(lowered2), sound.spoken)
        val alert = rt.state.value.alert!!
        assertEquals("내 설정 · 열선 최대 2단", alert.headline)
        assertEquals(lowered2, alert.detail)
        assertTrue(alert.spoken)
    }

    @Test
    fun `빛·글자 알림 프로필은 깎아도 말하지 않고 자막만 띄운다`() {
        val (rt, _) = runtime(GuardProfile(alertMode = AlertMode.LIGHT_TEXT))
        car.sink.onGear(Gear.D)
        car.sink.onValue(driverHeat, 3f)
        assertTrue(sound.spoken.isEmpty())
        assertEquals(lowered2, rt.state.value.alert?.detail)
        assertFalse(rt.state.value.alert!!.spoken)
    }

    @Test
    fun `30초 안에 같은 깎음이 또 일어나면 자막만 내고 30초가 지나면 다시 말한다`() {
        val (rt, _) = runtime(GuardProfile(alertMode = AlertMode.SOUND))
        car.sink.onGear(Gear.D)
        car.sink.onValue(driverHeat, 3f)
        clock.advance(10_000)
        car.sink.onValue(driverHeat, 3f)
        assertEquals(listOf(lowered2), sound.spoken)
        assertEquals(lowered2, rt.state.value.alert?.detail)
        assertFalse(rt.state.value.alert!!.spoken)

        clock.advance(60_000)
        car.sink.onValue(driverHeat, 3f)
        assertEquals(listOf(lowered2, lowered2), sound.spoken)
    }

    @Test
    fun `자동 하강 때도 왜 낮췄는지 말한다`() {
        val (rt, _) = runtime(GuardProfile(alertMode = AlertMode.SOUND, autoStepDownMinutes = 10))
        car.sink.onGear(Gear.D)
        car.sink.onValue(driverHeat, 2f)
        clock.advance(10 * min)
        rt.tick()
        assertEquals(listOf("운전석 열선을 1단으로 낮췄어요. 10분이 지나 한 단 내렸어요."), sound.spoken)
    }

    @Test
    fun `가드가 멈추면 말하고 다시 켜면 다시 켰다고 말한다`() {
        val (rt, _) = runtime(GuardProfile(alertMode = AlertMode.SOUND))
        car.sink.onGear(Gear.D)
        repeat(3) {
            car.sink.onValue(driverHeat, 3f)
            clock.advance(10_000)
        }
        val halted = "운전석 열선 가드가 멈췄어요. 누군가 계속 올리고 있어요."
        assertEquals(listOf(lowered2, halted), sound.spoken)

        rt.rearm(driverHeat)
        assertEquals("운전석 열선 가드를 다시 켰어요.", sound.spoken.last())
    }

    @Test
    fun `다른 자리 제안은 소리 프로필이어도 말하지 않고 자막만 띄운다`() {
        val (rt, _) = runtime(GuardProfile(alertMode = AlertMode.SOUND))
        car.sink.onGear(Gear.D)
        car.sink.onValue(ItemKey.heat(Seat.FRONT_PASSENGER), 3f)
        assertTrue(sound.spoken.isEmpty())
        assertEquals("조수석 열선은 2단 이하를 권해요. 내 자리가 아니라 바꾸지 않았어요.", rt.state.value.alert?.detail)
    }

    @Test
    fun `하차 복원은 P단에서 가드가 깎은 값을 원래 값으로 되돌린다`() {
        val (rt, _) = runtime()
        car.sink.onGear(Gear.D)
        car.sink.onValue(driverHeat, 3f)
        car.sink.onGear(Gear.P)
        rt.restoreOnExit()
        assertEquals(listOf(driverHeat to 2f, driverHeat to 3f), car.writes)
    }

    // ---- 시험 입력(티켓 02) ----

    @Test
    fun `시험 입력은 SDK 콜백과 같은 쓰기와 같은 이벤트를 낸다`() {
        val sdkCar = FakeCar()
        val (viaSdk, _) = runtime(car = sdkCar)
        sdkCar.sink.onGear(Gear.D)
        sdkCar.sink.onValue(driverHeat, 3f)

        val testCar = FakeCar()
        val (viaTest, _) = runtime(car = testCar)
        viaTest.onTestInput(gear = "D", seat = null, kind = null, value = null)
        viaTest.onTestInput(gear = null, seat = "driver", kind = "heat", value = "3")

        assertEquals(listOf(driverHeat to 2f), testCar.writes)
        assertEquals(sdkCar.writes, testCar.writes)
        assertEquals(viaSdk.state.value.events, viaTest.state.value.events)
        assertEquals(viaSdk.state.value.values, viaTest.state.value.values)
    }

    @Test
    fun `시험 입력이 들어오면 화면과 SDK 기록에 시험 입력 표시가 남는다`() {
        val (rt, _) = runtime()
        assertNull(rt.state.value.testInput)
        rt.onTestInput(gear = null, seat = "driver", kind = "heat", value = "3")

        assertEquals("시험 입력 · 실제 차 신호 아님 · 운전석 열선 3단", rt.state.value.testInput)
        val line = rt.state.value.sdkLogs.first { "시험 입력" in it.text }
        assertEquals("시험 입력 · 실제 차 신호 아님 · 운전석 열선 3단", line.text)
    }

    @Test
    fun `잘못된 시험 입력은 엔진에 안 들어가고 거절 이유가 SDK 기록에 남는다`() {
        val (rt, _) = runtime()
        car.sink.onGear(Gear.D)
        rt.onTestInput(gear = null, seat = "driver", kind = "heat", value = "9")

        assertTrue(car.writes.isEmpty())
        assertNull(rt.state.value.values[driverHeat])
        assertNull(rt.state.value.testInput)
        val first = rt.state.value.sdkLogs.first()
        assertFalse(first.ok)
        assertTrue(first.text, first.text.startsWith("시험 입력 거절") && "-3~3" in first.text)
    }

    @Test
    fun `시나리오 재생 중에는 시험 입력을 받지 않는다`() {
        val (rt, _) = runtime()
        car.sink.onGear(Gear.D)
        rt.startScenario()
        rt.onTestInput(gear = null, seat = "driver", kind = "heat", value = "3")

        assertTrue(car.writes.isEmpty())
        val first = rt.state.value.sdkLogs.first()
        assertFalse(first.ok)
        assertTrue(first.text, first.text.startsWith("시험 입력 거절") && "시나리오" in first.text)
    }

    @Test
    fun `가드가 꺼져 있으면 시험 입력을 거절하고 이유를 남긴다`() {
        val (rt, _) = runtime()
        rt.stop()
        rt.onTestInput(gear = null, seat = "driver", kind = "heat", value = "3")

        assertTrue(car.writes.isEmpty())
        assertNull(rt.state.value.testInput)
        val first = rt.state.value.sdkLogs.first()
        assertFalse(first.ok)
        assertTrue(first.text, first.text.startsWith("시험 입력 거절") && "꺼져" in first.text)
    }

    @Test
    fun `가드를 멈춘 뒤 늦게 온 차 신호는 판정하지 않고 다시 켜면 다시 지킨다`() {
        val (rt, _) = runtime()
        val sink = car.sink
        sink.onGear(Gear.D)
        rt.stop()
        sink.onValue(driverHeat, 3f)
        assertTrue("멈춘 뒤에는 쓰기 없음", car.writes.isEmpty())
        assertEquals(null, rt.state.value.values[driverHeat])

        rt.start()
        car.sink.onValue(driverHeat, 3f)
        assertEquals(listOf(driverHeat to 2f), car.writes)
    }

    @Test
    fun `지금 실내가 41도를 넘으면 경고를 띄우고 내 자리 통풍과 에어컨을 한 번만 켠다`() {
        val (rt, _) = runtime(GuardProfile(alertMode = AlertMode.SOUND))
        car.sink.onValue(ItemKey.now(Seat.DRIVER), 30f)
        assertTrue(car.cooled.isEmpty())
        car.sink.onValue(ItemKey.now(Seat.DRIVER), 48f)
        assertEquals(listOf(Seat.DRIVER), car.cooled)
        val alert = rt.state.value.alert
        assertNotNull(alert)
        assertTrue(alert!!.warning)
        assertEquals("실내가 48도예요. 좌석이 뜨거울 수 있어요. 운전석 통풍과 에어컨을 켰어요.", sound.spoken.single())
        car.sink.onValue(ItemKey.now(Seat.DRIVER), 50f)
        assertEquals(1, car.cooled.size)
        assertTrue(car.writes.isEmpty())
    }
}
