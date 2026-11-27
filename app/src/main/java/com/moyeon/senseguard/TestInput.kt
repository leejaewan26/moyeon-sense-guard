package com.moyeon.senseguard

import com.moyeon.senseguard.engine.Gear
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.ItemKind
import com.moyeon.senseguard.engine.Seat
import com.moyeon.senseguard.engine.fmt
import com.moyeon.senseguard.engine.fmtValue
import kotlin.math.roundToInt

/**
 * 밖에서 넣는 시험 입력(디버그 빌드의 adb 방송). 해석만 여기서 하고, 해석된 신호는
 * 실제 차 신호와 같은 런타임 입구([CarSink.onGear]·[CarSink.onValue])로 들어간다.
 */
sealed interface TestInput {
    data class GearSignal(val gear: Gear) : TestInput
    data class ValueSignal(val item: ItemKey, val value: Float) : TestInput
    data class Rejected(val reason: String) : TestInput
}

const val TEST_INPUT_MARK = "시험 입력 · 실제 차 신호 아님"

/** 차가 열선 범위를 아직 안 알려 줬을 때 쓰는 범위. Pleos 에뮬레이터 실측(2026-11-25): 앞 -3~3(음수는 통풍), 2열 0~3. */
private fun defaultHeatRange(seat: Seat): IntRange = when (seat) {
    Seat.DRIVER, Seat.FRONT_PASSENGER -> -3..3
    Seat.REAR_LEFT, Seat.REAR_RIGHT -> 0..3
}

/** 실내 설정 온도로 받아 줄 범위(°C). 엉뚱한 값만 막는 정도 — 프로필 상한 선택지는 18~30. */
private val TEST_TEMP_RANGE = 16f..32f

private const val USAGE = "기어(gear: P·R·N·D) 또는 자리·종류·값(seat·kind·value) 중 하나를 넣으세요"

/**
 * adb extras 를 신호로 바꾼다. 자리는 영어 이름(driver·front_passenger·rear_left·rear_right)이나
 * 화면 이름(운전석·조수석·2열 왼쪽·2열 오른쪽, 띄어쓰기 상관없음), 종류는 heat·temp 나 열선·온도.
 * 열선은 정수 단계로, 차가 알려 준 범위([heatBounds], 없으면 실측 기본값) 안이어야 한다.
 */
fun parseTestInput(
    gear: String?,
    seat: String?,
    kind: String?,
    value: String?,
    heatBounds: Map<Seat, IntRange> = emptyMap(),
): TestInput {
    val g = gear?.trim().orEmpty()
    val seatText = seat?.trim().orEmpty()
    val kindText = kind?.trim().orEmpty()
    val valueText = value?.trim().orEmpty()
    val missing = mapOf("seat" to seatText, "kind" to kindText, "value" to valueText).filterValues { it.isEmpty() }.keys
    val anyField = missing.size < 3
    if (g.isNotEmpty() && anyField) return TestInput.Rejected("기어와 자리 값을 한 번에 넣을 수 없어요. 하나씩 넣으세요")
    if (g.isNotEmpty()) {
        val parsed = Gear.entries.firstOrNull { it != Gear.UNKNOWN && it.name.equals(g, ignoreCase = true) }
            ?: return TestInput.Rejected("모르는 기어 «$g» — P·R·N·D 중 하나")
        return TestInput.GearSignal(parsed)
    }
    if (!anyField) return TestInput.Rejected(USAGE)
    if (missing.isNotEmpty()) return TestInput.Rejected("빠진 칸: ${missing.joinToString()} — $USAGE")

    val s = parseSeat(seatText)
        ?: return TestInput.Rejected("모르는 자리 «$seatText» — driver·front_passenger·rear_left·rear_right")
    val k = parseKind(kindText)
        ?: return TestInput.Rejected("모르는 종류 «$kindText» — heat(열선)·temp(온도)")
    val v = valueText.toFloatOrNull()?.takeIf { it.isFinite() }
        ?: return TestInput.Rejected("값 «$valueText» 이 숫자가 아니에요")
    val item = ItemKey(k, s)
    val outOfRange = when (k) {
        ItemKind.SEAT_HEAT -> {
            if (v != v.roundToInt().toFloat()) return TestInput.Rejected("${item.label}은 정수 단계로 넣으세요(받은 값 ${fmt(v)})")
            val range = heatBounds[s] ?: defaultHeatRange(s)
            if (v.roundToInt() !in range) "${range.first}~${range.last}" else null
        }
        ItemKind.CABIN_TEMP ->
            if (v !in TEST_TEMP_RANGE) "${fmt(TEST_TEMP_RANGE.start)}~${fmt(TEST_TEMP_RANGE.endInclusive)}" else null
    }
    if (outOfRange != null) return TestInput.Rejected("${item.label} ${fmtValue(item, v)}은 범위 밖이에요($outOfRange)")
    return TestInput.ValueSignal(item, v)
}

private fun parseSeat(text: String): Seat? {
    val t = text.replace(" ", "")
    return Seat.entries.firstOrNull { it.name.equals(t, ignoreCase = true) || it.label.replace(" ", "") == t }
}

private fun parseKind(text: String): ItemKind? = when (text.lowercase()) {
    "heat", "열선" -> ItemKind.SEAT_HEAT
    "temp", "온도" -> ItemKind.CABIN_TEMP
    else -> null
}
