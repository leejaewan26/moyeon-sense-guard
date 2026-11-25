package com.moyeon.senseguard.engine

/** 좌석(= SDK SeatArea 하나). 안드로이드·SDK 의존 없이 엔진에서 쓰는 이름. */
enum class Seat(val label: String) {
    DRIVER("운전석"),
    FRONT_PASSENGER("조수석"),
    REAR_LEFT("2열 왼쪽"),
    REAR_RIGHT("2열 오른쪽"),
}

enum class Gear(val label: String) {
    P("P"), R("R"), N("N"), D("D"), UNKNOWN("확인 안 됨");

    val isPark: Boolean get() = this == P
}

enum class AlertMode(val label: String) {
    SOUND("소리"),
    LIGHT_TEXT("빛·글자"),
}

enum class ItemKind { SEAT_HEAT, CABIN_TEMP }

/** 가드가 지키는 항목 하나: (종류, 자리). */
data class ItemKey(val kind: ItemKind, val seat: Seat) {
    val label: String
        get() = when (kind) {
            ItemKind.SEAT_HEAT -> "${seat.label} 열선"
            ItemKind.CABIN_TEMP -> "${seat.label} 실내 온도"
        }

    companion object {
        fun heat(seat: Seat) = ItemKey(ItemKind.SEAT_HEAT, seat)
        fun temp(seat: Seat) = ItemKey(ItemKind.CABIN_TEMP, seat)
    }
}

/** 규칙 프로필. 기기 안에만 저장한다(서버 없음). */
data class GuardProfile(
    val maxHeatLevel: Int = 2,
    val autoStepDownMinutes: Int = 10,
    val cabinTempMaxC: Float = 26f,
    val alertMode: AlertMode = AlertMode.LIGHT_TEXT,
    /** 이 프로필이 강제되는 자리. 운전석이면 운전자, 그 밖이면 승객. */
    val seat: Seat = Seat.DRIVER,
) {
    val isDriver: Boolean get() = seat == Seat.DRIVER
    val roleLabel: String get() = if (isDriver) "운전자" else "승객"
}

enum class EventKind { CLAMP, STEP_DOWN, SUGGEST, HALT, OVERRIDE, OVERRIDE_END, RESTORE, INFO }

data class GuardEvent(
    val timeMs: Long,
    val kind: EventKind,
    val item: ItemKey?,
    val from: Float?,
    val to: Float?,
    val text: String,
)

/** 엔진이 어댑터에게 내리는 지시. 엔진은 직접 쓰지 않는다. */
sealed interface GuardAction {
    /** [kind]: 깎음(CLAMP)·자동 하강(STEP_DOWN)·하차 복원(RESTORE) — 알림 문장을 고르는 데 쓴다. */
    data class Write(val item: ItemKey, val value: Float, val reason: String, val kind: EventKind = EventKind.CLAMP) : GuardAction
    data class Suggest(val item: ItemKey, val requested: Float, val suggested: Float, val reason: String) : GuardAction
    data class Halted(val item: ItemKey, val message: String) : GuardAction
}

fun fmt(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
