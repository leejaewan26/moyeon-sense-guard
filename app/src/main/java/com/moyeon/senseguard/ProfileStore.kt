package com.moyeon.senseguard

import android.content.Context
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
