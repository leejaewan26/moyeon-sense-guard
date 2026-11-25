package com.moyeon.senseguard

import com.moyeon.senseguard.engine.Gear
import com.moyeon.senseguard.engine.GuardEvent
import com.moyeon.senseguard.engine.GuardProfile
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.ItemKind
import com.moyeon.senseguard.engine.Seat
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
    /** 시나리오 중 이벤트 시각을 "시연 +mm:ss" 로 보여 주기 위한 기준. 재생 중이 아니면 null. */
    val scenarioStartMs: Long? = null,
)

fun fmtValue(item: ItemKey, v: Float): String = when (item.kind) {
    ItemKind.SEAT_HEAT -> "${v.roundToInt()}단"
    ItemKind.CABIN_TEMP -> "${if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)}°C"
}
