package com.moyeon.senseguard.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.moyeon.senseguard.engine.Gear
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.Seat

/**
 * Pleos Vehicle SDK 2.3.2 연결. 콜백은 모두 메인 스레드로 넘긴다.
 * 실패해도 앱이 죽지 않게 모든 호출을 감싸고, 결과를 [Sink.onSdkLog] 로 화면에 남긴다.
 */
class VehicleAdapter(context: Context, private val sink: Sink) {

    interface Sink {
        fun onGear(gear: Gear)
        fun onValue(item: ItemKey, value: Float)
        fun onSdkLog(ok: Boolean, text: String)
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    var connected: Boolean = false
        private set

    /** 열선 단계 범위(차가 알려 준 값). 모르면 null. */
    var seatHeatBounds: Map<Seat, IntRange> = emptyMap()
        private set

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = post { sink.onGear(gear.toGear()) }
        override fun onFailed(e: Exception) = post { log(false, "registerGearSelection 콜백 실패: ${e.msg()}") }
    }

    private val heatListeners: Map<Seat, HvacSeatTemperatureListener> = HEAT_SEATS.associateWith { seat ->
        object : HvacSeatTemperatureListener {
            override fun onHvacSeatTemperatureUpdated(level: Int?) {
                Log.i(TAG, "열선 콜백 ${seat.label} = $level")
                if (level != null) post { sink.onValue(ItemKey.heat(seat), level.toFloat()) }
            }
            override fun onFailed(e: Exception) = post { log(false, "${seat.label} 열선 콜백 실패: ${e.msg()}") }
        }
    }

    private val tempListeners: Map<Seat, HvacTemperatureListener> = TEMP_SEATS.associateWith { seat ->
        object : HvacTemperatureListener {
            override fun onHvacTemperatureUpdated(value: Float?) {
                if (value != null) post { sink.onValue(ItemKey.temp(seat), value) }
            }
            override fun onFailed(e: Exception) = post { log(false, "${seat.label} 온도 콜백 실패: ${e.msg()}") }
        }
    }

    private val registeredHeat = mutableSetOf<Seat>()
    private val registeredTemp = mutableSetOf<Seat>()
    private var gearRegistered = false

    fun start() {
        if (connected) return
        val v = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (e: Throwable) {
            log(false, "Vehicle.initialize() 실패: ${e.msg()}")
            return
        }
        vehicle = v
        connected = true
        log(true, "Vehicle.initialize() 성공")

        call("Gear.registerGearSelection") {
            v.getGear().registerGearSelection(gearListener)
            gearRegistered = true
        }
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
        }
        refresh()
    }

    /** 지금 값을 한 번 읽는다(get 계열). */
    fun refresh() {
        val v = vehicle ?: return
        call("Gear.getCurrentGearSelectionState") {
            v.getGear().getCurrentGearSelectionState(
                { g: VehicleGear? -> post { log(true, "기어 읽음: ${g?.name}"); sink.onGear(g.toGear()) } },
                { e: Exception -> post { log(false, "getCurrentGearSelectionState 실패: ${e.msg()}") } },
            )
        }
        for (seat in HEAT_SEATS) {
            call("Seat.getHvacSeatTemperature(${seat.label})") {
                v.getSeat().getHvacSeatTemperature(
                    seat.area(),
                    { level: Int? -> Log.i(TAG, "열선 읽음 ${seat.label} = $level"); if (level != null) post { sink.onValue(ItemKey.heat(seat), level.toFloat()) } },
                    { e: Exception -> post { log(false, "getHvacSeatTemperature(${seat.label}) 실패: ${e.msg()}") } },
                )
            }
        }
        for (seat in TEMP_SEATS) {
            call("HVAC.getHvacTemperature(${seat.label})") {
                v.getHVAC().getHvacTemperature(
                    seat.area(), TemperatureUnit.CELSIUS,
                    { t: Float? -> if (t != null) post { sink.onValue(ItemKey.temp(seat), t) } },
                    { e: Exception -> post { log(false, "getHvacTemperature(${seat.label}) 실패: ${e.msg()}") } },
                )
            }
        }
    }

    /** 열선 쓰기. 결과는 onResult(성공 여부). */
    fun setSeatHeat(seat: Seat, level: Int, onResult: (Boolean) -> Unit) {
        val v = vehicle ?: return onResult(false)
        val ok = call("Seat.setHvacSeatTemperature(${seat.label}, $level)") {
            v.getSeat().setHvacSeatTemperature(
                seat.area(), level,
                { _: SeatArea?, value: Int? -> post { log(true, "setHvacSeatTemperature(${seat.label}) → $value"); onResult(true) } },
                { e: Exception -> post { log(false, "setHvacSeatTemperature(${seat.label}, $level) 실패: ${e.msg()}"); onResult(false) } },
            )
        }
        if (!ok) onResult(false)
    }

    fun setCabinTemp(seat: Seat, celsius: Float, onResult: (Boolean) -> Unit) {
        val v = vehicle ?: return onResult(false)
        val ok = call("HVAC.setHvacTemperature(${seat.label}, $celsius)") {
            v.getHVAC().setHvacTemperature(
                seat.area(), celsius, TemperatureUnit.CELSIUS,
                { _: SeatArea?, value: Float? -> post { log(true, "setHvacTemperature(${seat.label}) → $value"); onResult(true) } },
                { e: Exception -> post { log(false, "setHvacTemperature(${seat.label}, $celsius) 실패: ${e.msg()}"); onResult(false) } },
            )
        }
        if (!ok) onResult(false)
    }

    fun release() {
        val v = vehicle ?: return
        if (gearRegistered) call("Gear.unregisterGearSelection") { v.getGear().unregisterGearSelection(gearListener) }
        for (seat in registeredHeat) call("Seat.unregisterHvacSeatTemperature") {
            v.getSeat().unregisterHvacSeatTemperature(seat.area(), heatListeners.getValue(seat))
        }
        for (seat in registeredTemp) call("HVAC.unregisterHvacTemperature") {
            v.getHVAC().unregisterHvacTemperature(seat.area(), tempListeners.getValue(seat))
        }
        registeredHeat.clear(); registeredTemp.clear(); gearRegistered = false
        call("Vehicle.release") { v.release() }
        vehicle = null
        connected = false
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
        post { sink.onSdkLog(ok, text) }
    }

    companion object {
        const val TAG = "SenseGuard"
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

        fun VehicleGear?.toGear(): Gear = when (this) {
            VehicleGear.GEAR_PARK -> Gear.P
            VehicleGear.GEAR_REVERSE -> Gear.R
            VehicleGear.GEAR_NEUTRAL -> Gear.N
            null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            else -> Gear.D
        }

        private fun Throwable.msg(): String =
            "${this::class.java.simpleName}: ${(message ?: "(메시지 없음)").replace("android.car.hardware.property.", "")}"
    }
}
