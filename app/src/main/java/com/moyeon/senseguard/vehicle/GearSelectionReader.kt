package com.moyeon.senseguard.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread

/**
 * 기어 읽기. Pleos Gear 도메인은 SDK 2.3.2에서 deprecated·공개 문서 제외 → SDK 안내대로 AAOS 표준 GEAR_SELECTION(CarPropertyManager)으로 읽음.
 *
 * 차량 호출(createCar·getProperty·registerCallback)은 모두 전용 스레드에서 한다(메인 스레드 금지).
 * [onGear]·[onResult] 도 그 스레드에서 불리므로 받는 쪽이 메인으로 넘긴다.
 * 실패·미지원이면 예외를 삼키고 [onResult] 로 알리기만 한다(앱은 계속 돈다).
 */
class GearSelectionReader(
    context: Context,
    private val onGear: (rawGear: Int) -> Unit,
    private val onResult: (api: String, ok: Boolean, detail: String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val worker = HandlerThread("car-gear").apply { start() }
    private val bg = Handler(worker.looper)
    private var car: Car? = null
    private var cpm: CarPropertyManager? = null
    private var registered = false

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId != VehiclePropertyIds.GEAR_SELECTION) return
            val g = value.value as? Int ?: return
            onResult(REGISTER, true, "이벤트 ${label(g)}")
            onGear(g)
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            onResult(REGISTER, false, "onErrorEvent(area=$areaId)")
        }
    }

    fun start() {
        bg.post {
            if (cpm != null) return@post
            val m = try {
                val c = Car.createCar(appContext, bg)
                if (c == null) { onResult(CONNECT, false, "Car 서비스 없음"); return@post }
                car = c
                c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            } catch (e: Throwable) {
                onResult(CONNECT, false, e.describe()); return@post
            }
            cpm = m
            onResult(CONNECT, true, "CarPropertyManager 연결")
            readNow()
            try {
                registered = m.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
                onResult(REGISTER, registered, if (registered) "등록됨" else "등록 거부(false)")
            } catch (e: Throwable) {
                onResult(REGISTER, false, e.describe())
            }
        }
    }

    /** 지금 값을 한 번 읽는다. */
    fun refresh() {
        bg.post { readNow() }
    }

    fun stop() {
        bg.post {
            val m = cpm
            if (m != null && registered) runCatching { m.unregisterCallback(callback, VehiclePropertyIds.GEAR_SELECTION) }
            registered = false
            runCatching { car?.disconnect() }
            car = null
            cpm = null
            worker.quitSafely()
        }
    }

    private fun readNow() {
        val m = cpm ?: return
        try {
            val v = m.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)
            val g = v?.value
            if (v == null || g == null || v.status != CarPropertyValue.STATUS_AVAILABLE) {
                onResult(GET, false, "값 없음 (status=${v?.status})")
            } else {
                onResult(GET, true, "값 ${label(g)}")
                onGear(g)
            }
        } catch (e: Throwable) {
            onResult(GET, false, e.describe())
        }
    }

    companion object {
        const val CONNECT = "Car.createCar · CarPropertyManager"
        const val GET = "CarPropertyManager.getProperty(GEAR_SELECTION)"
        const val REGISTER = "CarPropertyManager.registerCallback(GEAR_SELECTION)"

        fun label(g: Int): String = when (g) {
            VehicleGear.GEAR_PARK -> "GEAR_PARK($g)"
            VehicleGear.GEAR_REVERSE -> "GEAR_REVERSE($g)"
            VehicleGear.GEAR_NEUTRAL -> "GEAR_NEUTRAL($g)"
            VehicleGear.GEAR_DRIVE -> "GEAR_DRIVE($g)"
            VehicleGear.GEAR_UNKNOWN -> "GEAR_UNKNOWN($g)"
            else -> "GEAR($g)"
        }

        private fun Throwable.describe() = "${javaClass.simpleName}: ${message ?: "-"}"
    }
}
