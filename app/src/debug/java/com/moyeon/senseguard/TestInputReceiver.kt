package com.moyeon.senseguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.moyeon.senseguard.vehicle.VehicleAdapter

/**
 * 디버그 빌드 전용 시험 입력. release 빌드에는 이 파일도 매니페스트 줄도 없다.
 * 보내는 쪽이 DUMP 권한을 가져야 받는다(매니페스트) — adb shell 은 갖고, 보통 앱은 못 갖는다.
 *
 *   adb shell am broadcast -n com.moyeon.senseguard/.TestInputReceiver --es gear D
 *   adb shell am broadcast -n com.moyeon.senseguard/.TestInputReceiver --es seat driver --es kind heat --es value 3
 */
class TestInputReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val gear = intent.getStringExtra("gear")
        val seat = intent.getStringExtra("seat")
        val kind = intent.getStringExtra("kind")
        val value = intent.getStringExtra("value")
        Log.i(VehicleAdapter.TAG, "시험 입력 받음: gear=$gear seat=$seat kind=$kind value=$value")
        GuardRuntimes.get(context).onTestInput(gear, seat, kind, value)
    }
}
