package com.moyeon.senseguard

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.moyeon.senseguard.engine.AlertMode
import com.moyeon.senseguard.engine.GuardProfile
import com.moyeon.senseguard.engine.Seat

/** 규칙 프로필을 기기 안 SharedPreferences 에만 저장한다(서버 없음). */
class ProfileStore(context: Context) : ProfileRepo {
    private val prefs = context.getSharedPreferences("sense_guard_profile", Context.MODE_PRIVATE)

    override fun load(): GuardProfile {
        val d = GuardProfile()
        return GuardProfile(
            maxHeatLevel = prefs.getInt("maxHeatLevel", d.maxHeatLevel),
            autoStepDownMinutes = prefs.getInt("autoStepDownMinutes", d.autoStepDownMinutes),
            cabinTempMaxC = prefs.getFloat("cabinTempMaxC", d.cabinTempMaxC),
            alertMode = runCatching { AlertMode.valueOf(prefs.getString("alertMode", null)!!) }.getOrDefault(d.alertMode),
            seat = runCatching { Seat.valueOf(prefs.getString("seat", null)!!) }.getOrDefault(d.seat),
        )
    }

    override fun save(p: GuardProfile) {
        prefs.edit()
            .putInt("maxHeatLevel", p.maxHeatLevel)
            .putInt("autoStepDownMinutes", p.autoStepDownMinutes)
            .putFloat("cabinTempMaxC", p.cabinTempMaxC)
            .putString("alertMode", p.alertMode.name)
            .putString("seat", p.seat.name)
            .apply()
    }
}

/** 소리 알림. TextToSpeech SDK 대신 안드로이드 기본 ToneGenerator 를 쓴다. 프로세스와 수명이 같다. */
class Beeper : AlertSound {
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()

    override fun clamp() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350) }
    }

    override fun warning() {
        runCatching { tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900) }
    }
}
