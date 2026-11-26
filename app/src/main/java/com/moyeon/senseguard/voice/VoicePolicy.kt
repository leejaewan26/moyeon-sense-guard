package com.moyeon.senseguard.voice

/**
 * 음성 엔진 상태 규칙(안드로이드 없음). 첫 요청에 초기화하고, 초기화 중 요청은 기다리고,
 * 실패하면 세 번까지 다시 시도한 뒤 삐 소리로 대신한다. 속편한길 앱(2026-11)에서 시험과 함께 가져왔다.
 */
class VoicePolicy(private val maxInitAttempts: Int = 3) {
    enum class State { IDLE, INITIALIZING, READY, FAILED }
    enum class Action { SPEAK, WAIT, BEEP, INITIALIZE }

    var state: State = State.IDLE
        private set

    var initAttempts: Int = 0
        private set

    fun onSpeakRequested(): Action = when (state) {
        State.READY -> Action.SPEAK
        State.INITIALIZING -> Action.WAIT
        State.IDLE, State.FAILED -> {
            if (initAttempts < maxInitAttempts) {
                beginAttempt()
                Action.INITIALIZE
            } else {
                Action.BEEP
            }
        }
    }

    fun onWarmUp(): Boolean {
        if (state != State.IDLE) return false
        beginAttempt()
        return true
    }

    fun onInitSuccess() {
        state = State.READY
        initAttempts = 0
    }

    fun onInitFailure() {
        state = State.FAILED
    }

    fun onSpeechError(): Action = Action.BEEP

    private fun beginAttempt() {
        state = State.INITIALIZING
        initAttempts++
    }
}
