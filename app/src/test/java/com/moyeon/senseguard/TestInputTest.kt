package com.moyeon.senseguard

import com.moyeon.senseguard.engine.Gear
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시험 입력 해석기: 발표자가 adb 로 넣는 글자를 기어 / (항목, 값) / 거절 이유로 바꾼다. */
class TestInputTest {

    private fun parse(
        gear: String? = null,
        seat: String? = null,
        kind: String? = null,
        value: String? = null,
        heatBounds: Map<Seat, IntRange> = emptyMap(),
    ) = parseTestInput(gear, seat, kind, value, heatBounds)

    /** 거절이고, 이유에 [words] 가 모두 들어 있어야 한다(발표자가 무엇을 고칠지 알게). */
    private fun assertRejected(input: TestInput, vararg words: String) {
        assertTrue("거절이어야 함: $input", input is TestInput.Rejected)
        val reason = (input as TestInput.Rejected).reason
        for (w in words) assertTrue("이유에 «$w» 가 있어야 함: $reason", w in reason)
    }

    @Test
    fun `기어는 대소문자와 상관없이 기어 신호가 된다`() {
        assertEquals(TestInput.GearSignal(Gear.D), parse(gear = "D"))
        assertEquals(TestInput.GearSignal(Gear.P), parse(gear = "p"))
        assertEquals(TestInput.GearSignal(Gear.R), parse(gear = "R"))
        assertEquals(TestInput.GearSignal(Gear.N), parse(gear = "n"))
    }

    @Test
    fun `자리·종류·값은 영어 이름이나 화면 이름으로 항목과 값이 된다`() {
        assertEquals(TestInput.ValueSignal(ItemKey.heat(Seat.DRIVER), 3f), parse(seat = "driver", kind = "heat", value = "3"))
        assertEquals(TestInput.ValueSignal(ItemKey.temp(Seat.FRONT_PASSENGER), 27.5f), parse(seat = "front_passenger", kind = "temp", value = "27.5"))
        assertEquals(TestInput.ValueSignal(ItemKey.heat(Seat.REAR_LEFT), 2f), parse(seat = "2열 왼쪽", kind = "열선", value = "2"))
        assertEquals(TestInput.ValueSignal(ItemKey.heat(Seat.REAR_RIGHT), 1f), parse(seat = "2열오른쪽", kind = "열선", value = "1"))
        assertEquals(TestInput.ValueSignal(ItemKey.temp(Seat.DRIVER), 28f), parse(seat = "운전석", kind = "온도", value = "28"))
    }

    @Test
    fun `모르는 기어·자리·종류는 그 말을 들어 거절한다`() {
        assertRejected(parse(gear = "X"), "기어", "X")
        assertRejected(parse(seat = "trunk", kind = "heat", value = "1"), "자리", "trunk")
        assertRejected(parse(seat = "driver", kind = "fan", value = "1"), "종류", "fan")
    }

    @Test
    fun `값이 숫자가 아니거나 범위 밖이면 범위를 들어 거절한다`() {
        assertRejected(parse(seat = "driver", kind = "heat", value = "abc"), "숫자", "abc")
        assertRejected(parse(seat = "driver", kind = "heat", value = "9"), "-3~3")
        assertRejected(parse(seat = "rear_left", kind = "heat", value = "-1"), "0~3")
        assertRejected(parse(seat = "driver", kind = "heat", value = "2.5"), "정수")
        assertRejected(parse(seat = "driver", kind = "temp", value = "40"), "16~32")
    }

    @Test
    fun `열선 범위는 차가 알려 준 범위를 따른다`() {
        val reported = mapOf(Seat.DRIVER to 0..2)
        assertRejected(parse(seat = "driver", kind = "heat", value = "3", heatBounds = reported), "0~2")
        assertEquals(
            TestInput.ValueSignal(ItemKey.heat(Seat.DRIVER), 2f),
            parse(seat = "driver", kind = "heat", value = "2", heatBounds = reported),
        )
    }

    @Test
    fun `빈 입력·빠진 칸·기어와 값을 함께 넣은 입력은 쓰는 법을 들어 거절한다`() {
        assertRejected(parse(), "gear", "seat")
        assertRejected(parse(seat = "driver", kind = "heat"), "value")
        assertRejected(parse(gear = "D", seat = "driver", kind = "heat", value = "3"), "하나")
    }
}
