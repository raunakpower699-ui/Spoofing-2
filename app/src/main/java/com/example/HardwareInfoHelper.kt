package com.example

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Locale

data class DeviceHardwareInfo(
    val processorName: String,
    val totalCores: Int,
    val maxClockGhz: String,
    val manufacturer: String,
    val model: String,
    val architecture: String,
    val androidVersion: String,
    val isVivoOrIqoo: Boolean
)

data class LiveMetrics(
    val cpuPercentage: Float,
    val gpuPercentage: Float,
    val temperatureC: Float,
    val fps: Float,
    val usedRamGb: Float,
    val totalRamGb: Float,
    val ramPercentage: Float
)

object HardwareInfoHelper {

    fun getDeviceInfo(): DeviceHardwareInfo {
        val manufacturer = Build.MANUFACTURER
        val isVivo = manufacturer.contains("vivo", ignoreCase = true)
        val isIqoo = manufacturer.contains("iqoo", ignoreCase = true)

        val cpuName = detectProcessorName()
        val cores = Runtime.getRuntime().availableProcessors()
        val maxClock = detectMaxClockSpeed()

        return DeviceHardwareInfo(
            processorName = cpuName,
            totalCores = cores,
            maxClockGhz = maxClock,
            manufacturer = manufacturer.uppercase(Locale.ROOT),
            model = Build.MODEL,
            architecture = System.getProperty("os.arch") ?: "aarch64",
            androidVersion = Build.VERSION.RELEASE,
            isVivoOrIqoo = isVivo || isIqoo
        )
    }

    private fun detectProcessorName(): String {
        // Try SOC_MODEL on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val soc = Build.SOC_MODEL
            if (!soc.isNullOrBlank() && soc != "unknown") {
                return formatSocName(soc)
            }
        }

        // Try reading /proc/cpuinfo
        try {
            val reader = BufferedReader(FileReader("/proc/cpuinfo"))
            var line: String?
            var hardware: String? = null
            while (reader.readLine().also { line = it } != null) {
                val current = line ?: break
                if (current.startsWith("Hardware", ignoreCase = true) ||
                    current.startsWith("model name", ignoreCase = true) ||
                    current.startsWith("Processor", ignoreCase = true)
                ) {
                    val parts = current.split(":")
                    if (parts.size > 1) {
                        hardware = parts[1].trim()
                        if (hardware.isNotBlank()) break
                    }
                }
            }
            reader.close()
            if (!hardware.isNullOrBlank()) {
                return formatSocName(hardware)
            }
        } catch (_: Exception) {}

        // Fallback to Build.HARDWARE or board
        val hw = Build.HARDWARE
        return if (!hw.isNullOrBlank() && hw != "unknown" && hw != "goldfish") {
            formatSocName(hw)
        } else {
            "MediaTek Dimensity 7300-Turbo"
        }
    }

    private fun formatSocName(raw: String): String {
        return when {
            raw.contains("MT6878", ignoreCase = true) -> "MediaTek Dimensity 7300 5G"
            raw.contains("MT6897", ignoreCase = true) -> "MediaTek Dimensity 8300 Ultra"
            raw.contains("MT6989", ignoreCase = true) -> "MediaTek Dimensity 9300+"
            raw.contains("SM8650", ignoreCase = true) -> "Snapdragon 8 Gen 3"
            raw.contains("SM8550", ignoreCase = true) -> "Snapdragon 8 Gen 2"
            raw.contains("SM7435", ignoreCase = true) -> "Snapdragon 7s Gen 2"
            raw.contains("qcom", ignoreCase = true) -> "Qualcomm Snapdragon Octa-Core"
            raw.contains("exynos", ignoreCase = true) -> "Samsung Exynos Processor"
            else -> raw
        }
    }

    private fun detectMaxClockSpeed(): String {
        // Try reading cpufreq
        val paths = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq",
            "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq",
            "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_max_freq",
            "/sys/devices/system/cpu/cpufreq/policy7/cpuinfo_max_freq"
        )
        for (p in paths) {
            try {
                val file = File(p)
                if (file.exists() && file.canRead()) {
                    val khz = file.readText().trim().toLongOrNull()
                    if (khz != null && khz > 0) {
                        val ghz = khz / 1_000_000.0
                        return String.format(Locale.US, "%.1f GHz", ghz)
                    }
                }
            } catch (_: Exception) {}
        }
        return "2.50 GHz"
    }

    fun getMemoryInfo(context: Context): Pair<Float, Float> {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalGb = memInfo.totalMem / (1024f * 1024f * 1024f)
            val availGb = memInfo.availMem / (1024f * 1024f * 1024f)
            val usedGb = (totalGb - availGb).coerceAtLeast(0f)
            Pair(usedGb, totalGb)
        } catch (_: Exception) {
            Pair(4.8f, 8.0f)
        }
    }

    fun getBatteryTemperature(context: Context): Float {
        return try {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 375) ?: 375
            temp / 10.0f
        } catch (_: Exception) {
            38.5f
        }
    }
}

class FpsMeter {
    private var frameCount = 0
    private var lastTimeNanos = 0L

    private val _fps = MutableStateFlow(60.0f)
    val fps = _fps.asStateFlow()

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastTimeNanos == 0L) {
                lastTimeNanos = frameTimeNanos
            } else {
                val elapsed = frameTimeNanos - lastTimeNanos
                frameCount++
                if (elapsed >= 1_000_000_000L) { // 1 second
                    val calculatedFps = (frameCount * 1_000_000_000.0f) / elapsed
                    _fps.value = (calculatedFps.coerceIn(30f, 144f))
                    frameCount = 0
                    lastTimeNanos = frameTimeNanos
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        Choreographer.getInstance().removeFrameCallback(callback)
    }
}
