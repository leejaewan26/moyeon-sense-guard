package com.moyeon.senseguard.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.moyeon.senseguard.AlertSound
import java.util.Locale

/**
 * Pleos 음성(TextToSpeech, 기기 안 방식 ON_DEVICE)으로 말한다. 초기화·말하기가 실패하면 삐 소리로 대신한다.
 * 상태 판단은 [VoicePolicy](단위 시험 있음), 이 클래스는 SDK 호출과 메인 스레드 직렬화만 한다.
 * 인터넷 권한은 매니페스트에서 뺐다 — 기기 안 방식은 없어도 된다(속편한길 2026-11-02 실측).
 * 속편한길 앱의 AlertVoice(2026-11 에뮬레이터에서 차 오디오 출력까지 확인)를 줄여 가져왔다.
 */
class PleosVoice(context: Context) : AlertSound {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val policy = VoicePolicy()
    private val waiting = mutableListOf<String>()
    private var tts: TextToSpeech? = null
    private var failure = "초기화 전"
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()

    /** 첫 알림이 음성 모델 올리기(약 8초)를 기다리지 않게 미리 초기화한다. */
    fun warmUp() = onMain { if (policy.onWarmUp()) initialize() }

    override fun speak(text: String) = onMain {
        when (policy.onSpeakRequested()) {
            VoicePolicy.Action.SPEAK -> say(text)
            VoicePolicy.Action.WAIT -> wait(text)
            VoicePolicy.Action.INITIALIZE -> { wait(text); initialize() }
            VoicePolicy.Action.BEEP -> beep(failure)
        }
    }

    override fun warning() {
        runCatching { tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900) }
    }

    private fun wait(text: String) {
        waiting += text
        // 초기화가 10초 안에 안 끝나면 기다리던 말은 삐 소리로 대신한다.
        main.postDelayed({ if (waiting.remove(text)) beep("초기화 기다림 10초 넘김") }, WAIT_MS)
    }

    private fun initialize() {
        val old = tts
        tts = null
        runCatching { old?.release() }
        Log.i(TAG, "음성 초기화 시도 ${policy.initAttempts}")
        try {
            val engine = TextToSpeech(app, Mode.ON_DEVICE)
            tts = engine
            main.postDelayed({ if (tts === engine && policy.state == VoicePolicy.State.INITIALIZING) fail("초기화 30초 넘김") }, INIT_TIMEOUT_MS)
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() { main.post { if (tts === engine) ready(engine) } }
                override fun onFailure(error: ErrorType) { main.post { if (tts === engine) fail("초기화 실패 $error") } }
            })
        } catch (e: Exception) {
            fail("초기화 예외 ${e.javaClass.simpleName}")
        }
    }

    private fun ready(engine: TextToSpeech) {
        if (policy.state != VoicePolicy.State.INITIALIZING) return
        try {
            // 초기화가 끝나기 전에 등록하면 SDK 안에서 lateinit 오류가 난다(속편한길 실측).
            engine.addEventListener(object : EventListener {
                override fun onStart() { Log.i(TAG, "음성 시작") }
                override fun onReady() {}
                override fun onDone() { Log.i(TAG, "음성 끝") }
                override fun onError(error: ErrorType) { main.post { speechError("말하기 오류 $error") } }
                override fun onStop() {}
                override fun onUpdatedRms(rms: Double) {}
            })
            engine.setLocale(Locale.KOREA)
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        } catch (e: Exception) {
            fail("설정 예외 ${e.javaClass.simpleName}")
            return
        }
        policy.onInitSuccess()
        Log.i(TAG, "음성 초기화 성공")
        val pending = waiting.toList()
        waiting.clear()
        pending.forEach { say(it) }
    }

    private fun say(text: String) {
        val engine = tts ?: return beep("엔진 없음")
        try {
            Log.i(TAG, "음성 말하기: $text")
            engine.speak(text, false) // 앞 문장을 끊지 않고 이어서
        } catch (e: Exception) {
            speechError("말하기 예외 ${e.javaClass.simpleName}")
        }
    }

    private fun fail(reason: String) {
        if (policy.state != VoicePolicy.State.INITIALIZING) return
        policy.onInitFailure()
        failure = reason
        Log.w(TAG, "음성 $reason (시도 ${policy.initAttempts})")
        val pending = waiting.toList()
        waiting.clear()
        pending.forEach { beep(reason) }
    }

    private fun speechError(reason: String) {
        if (policy.onSpeechError() == VoicePolicy.Action.BEEP) beep(reason)
    }

    private fun beep(reason: String) {
        Log.i(TAG, "음성 대신 삐 소리 · $reason")
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350) }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private companion object {
        const val TAG = "SenseGuard"
        const val WAIT_MS = 10_000L
        const val INIT_TIMEOUT_MS = 30_000L
    }
}
