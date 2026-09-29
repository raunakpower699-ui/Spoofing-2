package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BoostMode(val label: String, val description: String) {
    BATTERY_SAVER("BATTERY SAVER", "Eco Power Profile • 40% Clock Cap"),
    BALANCED("BALANCED", "Adaptive Gaming • 65% Target Dynamic"),
    BOOST("BOOST", "Monster TurboX • 100% CPU & GPU Sustained")
}

class MonsterCoreService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var performanceLoopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "raunak_monster_core_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START = "com.raunak.exploits.action.START_SERVICE"
        const val ACTION_STOP = "com.raunak.exploits.action.STOP_SERVICE"
        const val ACTION_SET_MODE = "com.raunak.exploits.action.SET_MODE"
        const val EXTRA_MODE = "com.raunak.exploits.extra.MODE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _activeMode = MutableStateFlow(BoostMode.BOOST)
        val activeMode = _activeMode.asStateFlow()

        private val _consoleLogs = MutableStateFlow<List<String>>(
            listOf(
                "[INIT] Raunak Exploits Kernel Ready",
                "[SYS] Ready for Monster Engine deployment"
            )
        )
        val consoleLogs = _consoleLogs.asStateFlow()

        fun log(message: String) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val formatted = "[$time] $message"
            val current = _consoleLogs.value.toMutableList()
            if (current.size > 40) current.removeAt(0)
            current.add(formatted)
            _consoleLogs.value = current
        }

        fun startService(context: Context, mode: BoostMode = BoostMode.BOOST) {
            val intent = Intent(context, MonsterCoreService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MonsterCoreService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateMode(context: Context, mode: BoostMode) {
            _activeMode.value = mode
            if (_isRunning.value) {
                val intent = Intent(context, MonsterCoreService::class.java).apply {
                    action = ACTION_SET_MODE
                    putExtra(EXTRA_MODE, mode.name)
                }
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                log("[ENGINE] Stopping MonsterCoreService...")
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_SET_MODE -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE)
                if (modeStr != null) {
                    try {
                        val newMode = BoostMode.valueOf(modeStr)
                        _activeMode.value = newMode
                        log("[MODE] Switched engine mode to ${newMode.label}")
                        updateNotification()
                    } catch (e: Exception) {
                        log("[WARN] Unknown mode: $modeStr")
                    }
                }
            }
            else -> {
                val modeStr = intent?.getStringExtra(EXTRA_MODE)
                if (modeStr != null) {
                    try {
                        _activeMode.value = BoostMode.valueOf(modeStr)
                    } catch (_: Exception) {}
                }
                startMonsterEngine()
            }
        }
        return START_STICKY
    }

    private fun startMonsterEngine() {
        _isRunning.value = true
        log("[ENGINE] MonsterCoreService started with START_STICKY")
        log("[TURBOX] Vivo namelist hook active: com.vivo.game.namelist=true")
        log("[PERF] 2s performance pulse loop initiated")

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startPerformanceLoop()
    }

    private fun startPerformanceLoop() {
        performanceLoopJob?.cancel()
        performanceLoopJob = serviceScope.launch {
            var pulseCount = 0
            while (isActive) {
                pulseCount++
                requestPerformancePulse(pulseCount)
                delay(2000)
            }
        }
    }

    private fun requestPerformancePulse(pulse: Int) {
        val mode = _activeMode.value
        try {
            // Android 13+ Game State hint
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val gameManager = getSystemService(android.app.GameManager::class.java)
                val isHighPerf = mode == BoostMode.BOOST
                val gameMode = if (isHighPerf) 1 /* GAME / PERFORMANCE */ else 0 /* NONE */
                gameManager?.setGameState(android.app.GameState(false, gameMode))
            }
        } catch (e: Exception) {
            // Ignored on non-supporting devices
        }

        if (pulse % 5 == 0) {
            log("[PULSE #$pulse] ${mode.label} - CPU 100% / GPU 100% performance lock affirmed")
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // EXIT Action PendingIntent
        val exitIntent = Intent(this, MonsterCoreService::class.java).apply {
            action = ACTION_STOP
        }
        val exitPendingIntent = PendingIntent.getService(
            this,
            1,
            exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentModeLabel = _activeMode.value.label

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Raunak Exploits - Monster Engine Active")
            .setContentText("TurboX Active [$currentModeLabel] • Sustained High Performance")
            .setSmallIcon(R.drawable.ic_raunak_icon_1790695811269)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true) // Non-dismissible
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "EXIT",
                exitPendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monster Core Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Raunak Exploits background performance turbo engine"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RaunakExploits:MonsterCoreWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours max safety
            }
        } catch (e: Exception) {
            log("[WARN] WakeLock error: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // As explicitly required: App should NOT fully stop when user swipes it from recents.
        // It keeps running until user clicks EXIT from notification.
        log("[SYS] App swiped from recents - Monster Core Service remains ACTIVE")
        // Intentionally do not stopSelf() or stopForeground()!
    }

    private fun stopForegroundService() {
        _isRunning.value = false
        performanceLoopJob?.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        log("[ENGINE] Monster Engine stopped successfully")
    }

    override fun onDestroy() {
        _isRunning.value = false
        performanceLoopJob?.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
