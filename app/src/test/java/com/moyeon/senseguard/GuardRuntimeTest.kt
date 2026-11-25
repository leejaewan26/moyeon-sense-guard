package com.moyeon.senseguard

import com.moyeon.senseguard.engine.AlertMode
import com.moyeon.senseguard.engine.Gear
import com.moyeon.senseguard.engine.GuardProfile
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
        override fun release() {}
    }

    private class FakeClock(var wall: Long = 1_700_000_000_000L, var elapsed: Long = 0L) : RuntimeClock {
        override fun wallMs() = wall
        override fun elapsedMs() = elapsed
        fun advance(ms: Long) { wall += ms; elapsed += ms }
    }

    private class CountingSound : AlertSound {
        var clamps = 0
        var warnings = 0
        override fun clamp() { clamps++ }
        override fun warning() { warnings++ }
    }

    private class MemRepo(var saved: GuardProfile = GuardProfile()) : ProfileRepo {
        var saves = 0
        override fun load() = saved
        override fun save(p: GuardProfile) { saved = p; saves++ }
    }

    private val car = FakeCar()
    private val clock = FakeClock()
    private val sound = CountingSound()

    private fun runtime(profile: GuardProfile = GuardProfile()): Pair<GuardRuntime, MemRepo> {
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
    fun `소리 알림 프로필은 깎을 때 삐 소리가 나고 화면에 이유가 뜬다`() {
        val (rt, _) = runtime(GuardProfile(alertMode = AlertMode.SOUND))
        car.sink.onGear(Gear.D)
        car.sink.onValue(driverHeat, 3f)
        assertEquals(1, sound.clamps)
        assertEquals("내 설정 · 열선 최대 2단", rt.state.value.alert?.headline)
    }

    @Test
    fun `빛·글자 알림 프로필은 깎아도 소리가 나지 않는다`() {
        runtime(GuardProfile(alertMode = AlertMode.LIGHT_TEXT))
        car.sink.onGear(Gear.D)
        car.sink.onValue(driverHeat, 3f)
        assertEquals(0, sound.clamps)
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
}
