package com.moyeon.senseguard

import com.moyeon.senseguard.engine.AlertMode
import com.moyeon.senseguard.engine.ItemKey

/**
 * 알림 한 건. [text] 는 화면 자막이자 말할 문장이다.
 * [speakable]: 말해도 되는 일 — 내 자리 깎음·자동 하강·가드 멈춤·다시 켜짐(명세 «음성 알림 규칙»).
 * 다른 자리 «제안만»은 자막만 낸다.
 */
data class Notice(val text: String, val speakable: Boolean)

/** 알림 문장. 화면 자막과 음성이 같은 글을 쓴다. */
object Notices {
    fun lowered(item: ItemKey, to: Float) = Notice("${loweredTo(item, to)} 내 설정이에요.", true)

    fun steppedDown(item: ItemKey, to: Float, minutes: Int) =
        Notice("${loweredTo(item, to)} ${minutes}분이 지나 한 단 내렸어요.", true)

    fun halted(item: ItemKey) = Notice("${item.label} 가드가 멈췄어요. 누군가 계속 올리고 있어요.", true)

    fun rearmed(item: ItemKey) = Notice("${item.label} 가드를 다시 켰어요.", true)

    fun suggested(item: ItemKey, limit: Float) =
        Notice("${topic(item.label)} ${spoken(item, limit)} 이하를 권해요. 내 자리가 아니라 바꾸지 않았어요.", false)

    private fun loweredTo(item: ItemKey, to: Float) = "${obj(item.label)} ${towards(spoken(item, to))} 낮췄어요."

    /** 말로 읽는 값: 열선 «2단», 온도 «26도»·«25.5도»(°C 는 읽기 어색해서). */
    private fun spoken(item: ItemKey, v: Float) = fmtValue(item, v, celsius = "도")

    // 조사: 마지막 글자의 받침으로 고른다.
    private fun obj(w: String) = w + if (jong(w) != 0) "을" else "를"
    private fun topic(w: String) = w + if (jong(w) != 0) "은" else "는"
    private fun towards(w: String) = w + jong(w).let { if (it == 0 || it == RIEUL) "로" else "으로" }

    /** 마지막 한글 글자의 받침 번호(0 = 받침 없음). 한글이 아니면 0. */
    private fun jong(w: String): Int {
        val c = w.last()
        return if (c in '가'..'힣') (c - '가') % 28 else 0
    }

    private const val RIEUL = 8
}

/**
 * 말할지, 자막만 낼지. 화면 자막은 언제나 낸다.
 * «소리» 프로필에서 말할 일만 말하고, 같은 말은 [windowMs](30초)에 한 번.
 */
class VoiceRule(private val windowMs: Long = 30_000L) {
    private val lastSpoken = HashMap<String, Long>()

    fun shouldSpeak(notice: Notice, mode: AlertMode, nowMs: Long): Boolean {
        if (!notice.speakable || mode != AlertMode.SOUND) return false
        val last = lastSpoken[notice.text]
        if (last != null && nowMs - last < windowMs) return false
        lastSpoken[notice.text] = nowMs
        return true
    }
}
