package com.moyeon.senseguard

import com.moyeon.senseguard.engine.AlertMode
import com.moyeon.senseguard.engine.ItemKey
import com.moyeon.senseguard.engine.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 알림 문장(화면 자막 = 말할 글)과 «말할지, 자막만 낼지» 규칙. */
class VoiceRuleTest {

    private val driverHeat = ItemKey.heat(Seat.DRIVER)
    private val passengerHeat = ItemKey.heat(Seat.FRONT_PASSENGER)
    private val passengerTemp = ItemKey.temp(Seat.FRONT_PASSENGER)

    @Test
    fun `내 자리 깎음 문장은 무엇을 몇으로 낮췄는지와 내 설정임을 말한다`() {
        assertEquals(Notice("운전석 열선을 2단으로 낮췄어요. 내 설정이에요.", speakable = true), Notices.lowered(driverHeat, 2f))
        assertEquals("조수석 실내 온도를 26도로 낮췄어요. 내 설정이에요.", Notices.lowered(passengerTemp, 26f).text)
        assertEquals("조수석 실내 온도를 25.5도로 낮췄어요. 내 설정이에요.", Notices.lowered(passengerTemp, 25.5f).text)
    }

    @Test
    fun `자동 하강·가드 멈춤·다시 켜짐은 말할 일이고 다른 자리 제안은 자막만이다`() {
        assertEquals(Notice("운전석 열선을 1단으로 낮췄어요. 10분이 지나 한 단 내렸어요.", true), Notices.steppedDown(driverHeat, 1f, 10))
        assertEquals(Notice("운전석 열선 가드가 멈췄어요. 누군가 계속 올리고 있어요.", true), Notices.halted(driverHeat))
        assertEquals(Notice("운전석 열선 가드를 다시 켰어요.", true), Notices.rearmed(driverHeat))
        assertEquals(Notice("조수석 열선은 2단 이하를 권해요. 내 자리가 아니라 바꾸지 않았어요.", false), Notices.suggested(passengerHeat, 2f))
        assertEquals("조수석 실내 온도는 26도 이하를 권해요. 내 자리가 아니라 바꾸지 않았어요.", Notices.suggested(passengerTemp, 26f).text)
    }

    @Test
    fun `소리 프로필은 말할 일을 말하고 빛·글자 프로필과 제안은 자막만 낸다`() {
        val mine = Notices.lowered(driverHeat, 2f)
        assertTrue(VoiceRule().shouldSpeak(mine, AlertMode.SOUND, 0))
        assertFalse(VoiceRule().shouldSpeak(mine, AlertMode.LIGHT_TEXT, 0))
        assertFalse(VoiceRule().shouldSpeak(Notices.suggested(passengerHeat, 2f), AlertMode.SOUND, 0))
    }

    @Test
    fun `같은 말은 30초에 한 번이고 다른 말은 바로 말한다`() {
        val rule = VoiceRule()
        val lowered = Notices.lowered(driverHeat, 2f)
        val halted = Notices.halted(driverHeat)
        assertTrue(rule.shouldSpeak(lowered, AlertMode.SOUND, 0))
        assertFalse("30초 안 같은 말은 자막만", rule.shouldSpeak(lowered, AlertMode.SOUND, 29_999))
        assertTrue("다른 말은 바로", rule.shouldSpeak(halted, AlertMode.SOUND, 1_000))
        assertTrue("30초가 지나면 다시 말함", rule.shouldSpeak(lowered, AlertMode.SOUND, 30_000))
    }

    @Test
    fun `빛·글자 프로필에서 자막만 낸 말은 소리 프로필로 바꾸면 바로 말한다`() {
        val rule = VoiceRule()
        val lowered = Notices.lowered(driverHeat, 2f)
        assertFalse(rule.shouldSpeak(lowered, AlertMode.LIGHT_TEXT, 0))
        assertTrue(rule.shouldSpeak(lowered, AlertMode.SOUND, 1_000))
    }
}
