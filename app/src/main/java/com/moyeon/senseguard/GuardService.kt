package com.moyeon.senseguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.moyeon.senseguard.vehicle.VehicleAdapter
import com.moyeon.senseguard.voice.PleosVoice

/** 프로세스에 런타임 하나. 서비스와 화면이 같은 것을 쓴다. 메인 스레드에서만 부른다. */
object GuardRuntimes {
    private var instance: GuardRuntime? = null

    fun get(context: Context): GuardRuntime = instance ?: run {
        val app = context.applicationContext
        GuardRuntime(VehicleAdapter(app), SystemRuntimeClock, PleosVoice(app).also { it.warmUp() }, ProfileStore(app)).also { instance = it }
    }
}

object SystemRuntimeClock : RuntimeClock {
    override fun wallMs() = System.currentTimeMillis()
    override fun elapsedMs() = SystemClock.elapsedRealtime()
}

/**
 * 상시 서비스: 화면이 닫혀도 가드 런타임을 켜 두고 250ms 마다 [GuardRuntime.tick] 을 부른다.
 * 알림 줄에 «감각 가드 지키는 중»을 띄운다. 죽으면 시스템이 다시 띄운다(START_STICKY).
 */
class GuardService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var runtime: GuardRuntime
    private val ticker = object : Runnable {
        override fun run() {
            runtime.tick()
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        runtime = GuardRuntimes.get(this)
        runtime.start()
        main.postDelayed(ticker, TICK_MS)
        Log.i(TAG, "가드 서비스 시작")
    }

    /** 시작 요청마다 알림을 다시 올린다 — 앱을 다시 열거나 알림 권한을 막 허용했을 때도 알림 줄에 보이게. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), FGS_TYPE)
        } catch (e: Exception) {
            // 포그라운드가 거부돼도(배경 시작 제한 등) 지키는 일은 계속한다. 앱을 열면 다시 시도한다.
            Log.w(TAG, "포그라운드 시작 실패: $e")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(ticker)
        runtime.stop()
        Log.i(TAG, "가드 서비스 끝")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "가드 상태", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("감각 가드 지키는 중")
            .setContentText("화면을 닫아도 열선·실내 온도 상한을 지켜요")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = VehicleAdapter.TAG
        private const val TICK_MS = 250L
        private const val CHANNEL_ID = "guard"
        private const val NOTIFICATION_ID = 1
        private val FGS_TYPE = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, GuardService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "가드 서비스 시작 요청 실패: $e")
            }
        }
    }
}

/** 차가 켜지면(부팅 완료) 화면을 열지 않아도 서비스를 띄운다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(VehicleAdapter.TAG, "부팅 완료 · 가드 서비스 시작")
            GuardService.start(context)
        }
    }
}
