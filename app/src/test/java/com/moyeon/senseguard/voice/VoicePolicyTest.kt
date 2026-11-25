package com.moyeon.senseguard.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** 음성 엔진 상태 규칙: 초기화·대기·말하기·삐 소리 대체. (속편한길 앱에서 가져온 규칙과 시험) */
class VoicePolicyTest {

    @Test
    fun `첫 요청은 초기화를 시작하고 초기화 중 요청은 기다린다`() {
        val policy = VoicePolicy()
        assertEquals(VoicePolicy.State.IDLE, policy.state)

        assertEquals(VoicePolicy.Action.INITIALIZE, policy.onSpeakRequested())
        assertEquals(VoicePolicy.State.INITIALIZING, policy.state)
        assertEquals(1, policy.initAttempts)

        assertEquals(VoicePolicy.Action.WAIT, policy.onSpeakRequested())
        assertEquals(1, policy.initAttempts)
    }

    @Test
    fun `초기화에 성공하면 시도 수가 0이 되고 말한다`() {
        val policy = VoicePolicy()
        policy.onSpeakRequested()
        policy.onInitSuccess()

        assertEquals(VoicePolicy.State.READY, policy.state)
        assertEquals(0, policy.initAttempts)
        assertEquals(VoicePolicy.Action.SPEAK, policy.onSpeakRequested())
    }

    @Test
    fun `말하기 오류 한 번은 삐 소리로 대신하고 다음 요청은 다시 말한다`() {
        val policy = VoicePolicy()
        policy.onSpeakRequested()
        policy.onInitSuccess()

        assertEquals(VoicePolicy.Action.BEEP, policy.onSpeechError())
        assertEquals(VoicePolicy.State.READY, policy.state)
        assertEquals(VoicePolicy.Action.SPEAK, policy.onSpeakRequested())
    }

    @Test
    fun `초기화는 세 번까지 다시 시도하고 그 뒤로는 삐 소리만 낸다`() {
        val policy = VoicePolicy()
        repeat(3) { attempt ->
            assertEquals(VoicePolicy.Action.INITIALIZE, policy.onSpeakRequested())
            assertEquals(attempt + 1, policy.initAttempts)
            policy.onInitFailure()
            assertEquals(VoicePolicy.State.FAILED, policy.state)
        }
        repeat(2) {
            assertEquals(VoicePolicy.Action.BEEP, policy.onSpeakRequested())
            assertEquals(3, policy.initAttempts)
        }
    }

    @Test
    fun `미리 데우기는 처음 한 번만 초기화를 시작한다`() {
        val policy = VoicePolicy()
        assertEquals(true, policy.onWarmUp())
        assertEquals(VoicePolicy.State.INITIALIZING, policy.state)
        assertEquals(false, policy.onWarmUp())
        assertEquals(1, policy.initAttempts)
    }
}
