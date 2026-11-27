package com.moyeon.senseguard.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import android.car.VehicleGear
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.moyeon.senseguard.CarPort
import com.moyeon.senseguard.CarSink
import com.moyeon.senseguard.engine.Gear
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.Seat

/**
 * Pleos Vehicle SDK 2.3.2 연결. 콜백은 모두 메인 스레드로 넘긴다.
 * SDK 호출(초기화·등록·읽기·쓰기·해제)은 전용 스레드 하나에서 차례로 한다 — 에뮬레이터에서 초기화·등록이
 * 메인 스레드를 약 7초 막아 화면이 «응답 없음»(ANR)이 됐다(2026-11-25 실측). [vehicle]·등록 목록은 그 스레드에서만 만진다.
 * 실패해도 앱이 죽지 않게 모든 호출을 감싸고, 결과를 [CarSink.onSdkLog] 로 화면에 남긴다.
 * 기어: Pleos Gear 도메인은 SDK 2.3.2에서 deprecated·공개 문서 제외 → SDK 안내대로 AAOS 표준 GEAR_SELECTION(CarPropertyManager)으로 읽음([GearSelectionReader]).
 */
class VehicleAdapter(context: Context) : CarPort {

    /** [start] 에서 받는다. 그 전에 온 콜백·기록은 버린다. */
    private var sink: CarSink? = null
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val sdk = Handler(HandlerThread("car-sdk").apply { start() }.looper)

    /** SDK 스레드에서만 만진다. */
    private var vehicle: Vehicle? = null

    /** 메인 스레드에서만 바꾼다. SDK 스레드는 읽기만 한다(연결 중에 해제됐는지·다시 시도할지). */
    @Volatile private var started = false

    /** 메인 스레드에서만 만진다. */
    override var connected: Boolean = false
        private set

    /** 열선 단계 범위(차가 알려 준 값). 모르면 null. */
    override var seatHeatBounds: Map<Seat, IntRange> = emptyMap()
        private set

    private var lastGearLog: String? = null
    private val gearReader = GearSelectionReader(
        appContext,
        onGear = { raw -> post { sink?.onGear(raw.toGear()) } },
        onResult = { api, ok, detail ->
            // 같은 값 이벤트가 반복되면 기록을 한 번만 남긴다.
            val line = "$api: $detail"
            if (line != lastGearLog) { lastGearLog = line; log(ok, line) }
        },
    )

    private val heatListeners: Map<Seat, HvacSeatTemperatureListener> = HEAT_SEATS.associateWith { seat ->
        object : HvacSeatTemperatureListener {
            override fun onHvacSeatTemperatureUpdated(level: Int?) {
                Log.i(TAG, "열선 콜백 ${seat.label} = $level")
                if (level != null) post { sink?.onValue(ItemKey.heat(seat), level.toFloat()) }
            }
            override fun onFailed(e: Exception) = post { log(false, "${seat.label} 열선 콜백 실패: ${e.msg()}") }
        }
    }

    private val tempListeners: Map<Seat, HvacTemperatureListener> = TEMP_SEATS.associateWith { seat ->
        object : HvacTemperatureListener {
            override fun onHvacTemperatureUpdated(value: Float?) {
                if (value != null) post { sink?.onValue(ItemKey.temp(seat), value) }
            }
            override fun onFailed(e: Exception) = post { log(false, "${seat.label} 온도 콜백 실패: ${e.msg()}") }
        }
    }

    /** 지금 실내 온도(설정값이 아니라 실제 온도). 여름 과열 판단에 쓴다. */
    private val nowListeners: Map<Seat, HvacTemperatureListener> = TEMP_SEATS.associateWith { seat ->
        object : HvacTemperatureListener {
            override fun onHvacTemperatureUpdated(value: Float?) {
                if (value != null) post { sink?.onValue(ItemKey.now(seat), value) }
            }
            override fun onFailed(e: Exception) = post { log(false, "${seat.label} 지금 실내 온도 콜백 실패: ${e.msg()}") }
        }
    }

    private val registeredHeat = mutableSetOf<Seat>()
    private val registeredTemp = mutableSetOf<Seat>()
    private val registeredNow = mutableSetOf<Seat>()

    /** SDK 스레드에서만 만진다. 차가 알려 준 통풍 최대 단계(자리별)와 에어컨을 켤 수 있는 구역. */
    private var ventMax: Map<Seat, Int> = emptyMap()
    private var acAreas: List<SeatArea> = emptyList()

    override fun start(sink: CarSink) {
        this.sink = sink
        if (started) return
        started = true
        gearReader.start()
        sdk.post { connectOnSdk() }
    }

    private fun connectOnSdk() {
        // 해제됐거나, 다시 시도 예약과 새 start 가 겹쳐 이미 붙었으면 건너뛴다.
        if (!started || vehicle != null) return
        val v = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (e: Throwable) {
            // 부팅 직후엔 차량 서비스가 아직 안 떴을 수 있다 → 켜져 있는 동안 계속 다시 시도한다.
            log(false, "Vehicle.initialize() 실패: ${e.msg()} — ${RETRY_MS / 1000}초 뒤 다시 시도")
            sdk.postDelayed({ connectOnSdk() }, RETRY_MS)
            return
        }
        if (!started) {
            // 초기화하는 사이 해제됐다 → 붙잡지 않고 바로 놓는다.
            call("Vehicle.release") { v.release() }
            return
        }
        vehicle = v
        post { if (started) connected = true }
        log(true, "Vehicle.initialize() 성공")

        call("Seat.checkSeatTemperatureCapabilities") {
            v.getSeat().checkSeatTemperatureCapabilities(
                { bounds: Map<SeatArea, AreaBounds<Int>> -> post { onHeatBounds(bounds) } },
                { e: Exception -> post { log(false, "checkSeatTemperatureCapabilities 실패: ${e.msg()}") } },
            )
        }
        for (seat in HEAT_SEATS) {
            call("Seat.registerHvacSeatTemperature(${seat.label})") {
                v.getSeat().registerHvacSeatTemperature(seat.area(), heatListeners.getValue(seat))
                registeredHeat += seat
            }
        }
        for (seat in TEMP_SEATS) {
            call("HVAC.registerHvacTemperature(${seat.label})") {
                v.getHVAC().registerHvacTemperature(seat.area(), tempListeners.getValue(seat))
                registeredTemp += seat
            }
            call("HVAC.registerHvacTemperatureCurrent(${seat.label})") {
                v.getHVAC().registerHvacTemperatureCurrent(seat.area(), nowListeners.getValue(seat))
                registeredNow += seat
            }
        }
        call("Seat.checkSeatVentilationCapabilities") {
            v.getSeat().checkSeatVentilationCapabilities(
                { bounds: Map<SeatArea, AreaBounds<Int>> ->
                    val m = bounds.mapNotNull { (area, b) -> area.values.firstOrNull()?.toSeat()?.let { s -> b.maxValue?.let { s to it } } }.toMap()
                    sdk.post { ventMax = m }
                    log(true, "통풍 범위: " + m.entries.joinToString { "${it.key.label} 0~${it.value}" })
                },
                { e: Exception -> post { log(false, "checkSeatVentilationCapabilities 실패: ${e.msg()}") } },
            )
        }
        call("HVAC.checkHvacAcCapabilities") {
            v.getHVAC().checkHvacAcCapabilities(
                { areas: List<SeatArea> -> sdk.post { acAreas = areas }; log(true, "에어컨 구역 ${areas.size}곳") },
                { e: Exception -> post { log(false, "checkHvacAcCapabilities 실패: ${e.msg()}") } },
            )
        }
        readOnSdk()
    }

    /** 지금 값을 한 번 읽는다(get 계열). */
    override fun refresh() {
        gearReader.refresh()
        sdk.post { readOnSdk() }
    }

    private fun readOnSdk() {
        val v = vehicle ?: return
        for (seat in HEAT_SEATS) {
            call("Seat.getHvacSeatTemperature(${seat.label})") {
                v.getSeat().getHvacSeatTemperature(
                    seat.area(),
                    { level: Int? -> Log.i(TAG, "열선 읽음 ${seat.label} = $level"); if (level != null) post { sink?.onValue(ItemKey.heat(seat), level.toFloat()) } },
                    { e: Exception -> post { log(false, "getHvacSeatTemperature(${seat.label}) 실패: ${e.msg()}") } },
                )
            }
        }
        for (seat in TEMP_SEATS) {
            call("HVAC.getHvacTemperatureCurrent(${seat.label})") {
                v.getHVAC().getHvacTemperatureCurrent(
                    seat.area(), TemperatureUnit.CELSIUS,
                    { t: Float? -> if (t != null) post { sink?.onValue(ItemKey.now(seat), t) } },
                    { e: Exception -> post { log(false, "getHvacTemperatureCurrent(${seat.label}) 실패: ${e.msg()}") } },
                )
            }
            call("HVAC.getHvacTemperature(${seat.label})") {
                v.getHVAC().getHvacTemperature(
                    seat.area(), TemperatureUnit.CELSIUS,
                    { t: Float? -> if (t != null) post { sink?.onValue(ItemKey.temp(seat), t) } },
                    { e: Exception -> post { log(false, "getHvacTemperature(${seat.label}) 실패: ${e.msg()}") } },
                )
            }
        }
    }

    /** 열선 쓰기. 결과는 메인 스레드에서 onResult(성공 여부). */
    override fun setSeatHeat(seat: Seat, level: Int, onResult: (Boolean) -> Unit) {
        sdk.post {
            val v = vehicle
            val ok = v != null && call("Seat.setHvacSeatTemperature(${seat.label}, $level)") {
                v.getSeat().setHvacSeatTemperature(
                    seat.area(), level,
                    { _: SeatArea?, value: Int? -> post { log(true, "setHvacSeatTemperature(${seat.label}) → $value"); onResult(true) } },
                    { e: Exception -> post { log(false, "setHvacSeatTemperature(${seat.label}, $level) 실패: ${e.msg()}"); onResult(false) } },
                )
            }
            if (!ok) post { onResult(false) }
        }
    }

    override fun setCabinTemp(seat: Seat, celsius: Float, onResult: (Boolean) -> Unit) {
        sdk.post {
            val v = vehicle
            val ok = v != null && call("HVAC.setHvacTemperature(${seat.label}, $celsius)") {
                v.getHVAC().setHvacTemperature(
                    seat.area(), celsius, TemperatureUnit.CELSIUS,
                    { _: SeatArea?, value: Float? -> post { log(true, "setHvacTemperature(${seat.label}) → $value"); onResult(true) } },
                    { e: Exception -> post { log(false, "setHvacTemperature(${seat.label}, $celsius) 실패: ${e.msg()}"); onResult(false) } },
                )
            }
            if (!ok) post { onResult(false) }
        }
    }

    /** 여름 과열: 그 자리 통풍을 차가 허락하는 최대 단계로, 에어컨을 켤 수 있는 구역은 모두 켠다. 없는 기능은 건너뛰고 기록만 남긴다. */
    override fun startCooling(seat: Seat) {
        sdk.post {
            val v = vehicle ?: return@post
            val vent = ventMax[seat]
            if (vent == null || vent <= 0) log(false, "${seat.label} 통풍 시트 없음 · 통풍은 건너뜀")
            else call("Seat.setHvacSeatVentilation(${seat.label}, $vent)") {
                v.getSeat().setHvacSeatVentilation(
                    seat.area(), vent,
                    { _: SeatArea?, value: Int? -> log(true, "setHvacSeatVentilation(${seat.label}) → $value") },
                    { e: Exception -> log(false, "setHvacSeatVentilation(${seat.label}, $vent) 실패: ${e.msg()}") },
                )
            }
            if (acAreas.isEmpty()) log(false, "에어컨 구역을 차가 알려 주지 않음 · 에어컨은 건너뜀")
            for (area in acAreas) call("HVAC.setHvacAc(on)") {
                v.getHVAC().setHvacAc(
                    area, true,
                    { _: SeatArea?, on: Boolean? -> log(true, "setHvacAc → $on") },
                    { e: Exception -> log(false, "setHvacAc 실패: ${e.msg()}") },
                )
            }
        }
    }

    override fun release() {
        if (!started) return
        started = false
        connected = false
        gearReader.stop()
        sdk.post {
            val v = vehicle ?: return@post
            for (seat in registeredHeat) call("Seat.unregisterHvacSeatTemperature") {
                v.getSeat().unregisterHvacSeatTemperature(seat.area(), heatListeners.getValue(seat))
            }
            for (seat in registeredTemp) call("HVAC.unregisterHvacTemperature") {
                v.getHVAC().unregisterHvacTemperature(seat.area(), tempListeners.getValue(seat))
            }
            for (seat in registeredNow) call("HVAC.unregisterHvacTemperatureCurrent") {
                v.getHVAC().unregisterHvacTemperatureCurrent(seat.area(), nowListeners.getValue(seat))
            }
            registeredHeat.clear(); registeredTemp.clear(); registeredNow.clear()
            call("Vehicle.release") { v.release() }
            vehicle = null
        }
    }

    private fun onHeatBounds(bounds: Map<SeatArea, AreaBounds<Int>>) {
        val mapped = mutableMapOf<Seat, IntRange>()
        for ((area, b) in bounds) {
            val seat = area.values.firstOrNull()?.toSeat() ?: continue
            val lo = b.minValue ?: continue
            val hi = b.maxValue ?: continue
            mapped[seat] = lo..hi
        }
        seatHeatBounds = mapped
        log(true, "열선 범위: " + mapped.entries.joinToString { "${it.key.label} ${it.value.first}~${it.value.last}" })
    }

    private inline fun call(name: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: Throwable) {
        log(false, "$name 실패: ${e.msg()}")
        false
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun log(ok: Boolean, text: String) {
        if (ok) Log.i(TAG, text) else Log.w(TAG, text)
        post { sink?.onSdkLog(ok, text) }
    }

    companion object {
        const val TAG = "SenseGuard"
        private const val RETRY_MS = 5_000L
        val HEAT_SEATS = listOf(Seat.DRIVER, Seat.FRONT_PASSENGER, Seat.REAR_LEFT, Seat.REAR_RIGHT)
        val TEMP_SEATS = listOf(Seat.DRIVER, Seat.FRONT_PASSENGER)

        fun Seat.area(): SeatArea = SeatArea(
            when (this) {
                Seat.DRIVER -> SeatValue.ROW_1_LEFT
                Seat.FRONT_PASSENGER -> SeatValue.ROW_1_RIGHT
                Seat.REAR_LEFT -> SeatValue.ROW_2_LEFT
                Seat.REAR_RIGHT -> SeatValue.ROW_2_RIGHT
            }
        )

        fun SeatValue.toSeat(): Seat? = when (this) {
            SeatValue.ROW_1_LEFT -> Seat.DRIVER
            SeatValue.ROW_1_RIGHT -> Seat.FRONT_PASSENGER
            SeatValue.ROW_2_LEFT -> Seat.REAR_LEFT
            SeatValue.ROW_2_RIGHT -> Seat.REAR_RIGHT
            else -> null
        }

        /** android.car.VehicleGear(Int) → 엔진 기어. GEAR_DRIVE·GEAR_1..9 는 D. */
        fun Int.toGear(): Gear = when (this) {
            VehicleGear.GEAR_PARK -> Gear.P
            VehicleGear.GEAR_REVERSE -> Gear.R
            VehicleGear.GEAR_NEUTRAL -> Gear.N
            VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            else -> Gear.D
        }

        private fun Throwable.msg(): String =
            "${this::class.java.simpleName}: ${(message ?: "(메시지 없음)").replace("android.car.hardware.property.", "")}"
    }
}
