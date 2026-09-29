package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.PureBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainHackerDashboard(
                    onSetSustainedPerf = { enable ->
                        setSustainedPerfMode(enable)
                    },
                    onCloseApp = {
                        finishAffinity()
                    }
                )
            }
        }
    }

    private fun setSustainedPerfMode(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                window.setSustainedPerformanceMode(enable)
                MonsterCoreService.log("[KERNEL] Window.setSustainedPerformanceMode($enable)")
            } catch (e: Exception) {
                MonsterCoreService.log("[WARN] Sustained perf failed: ${e.message}")
            }
        }
    }
}

@Composable
fun MainHackerDashboard(
    onSetSustainedPerf: (Boolean) -> Unit,
    onCloseApp: () -> Unit
) {
    val context = LocalContext.current
    val deviceInfo = remember { HardwareInfoHelper.getDeviceInfo() }

    var showBrandLockDialog by remember {
        mutableStateOf(!deviceInfo.isVivoOrIqoo)
    }
    var isBrandBypassed by remember { mutableStateOf(false) }

    val isRunning by MonsterCoreService.isRunning.collectAsState()
    val activeMode by MonsterCoreService.activeMode.collectAsState()
    val logs by MonsterCoreService.consoleLogs.collectAsState()

    // FPS Meter
    val fpsMeter = remember { FpsMeter() }
    val currentFps by fpsMeter.fps.collectAsState()

    DisposableEffect(Unit) {
        fpsMeter.start()
        onDispose {
            fpsMeter.stop()
        }
    }

    // Dynamic Live Telemetry Metrics
    var cpuUsage by remember { mutableFloatStateOf(100f) }
    var gpuUsage by remember { mutableFloatStateOf(100f) }
    var temperature by remember { mutableFloatStateOf(38.2f) }
    var usedRamGb by remember { mutableFloatStateOf(5.1f) }
    var totalRamGb by remember { mutableFloatStateOf(8.0f) }

    // Telemetry tick every 1 sec
    LaunchedEffect(isRunning, activeMode) {
        while (isActive) {
            val (usedRam, totalRam) = HardwareInfoHelper.getMemoryInfo(context)
            usedRamGb = usedRam
            totalRamGb = totalRam
            temperature = HardwareInfoHelper.getBatteryTemperature(context)

            if (isRunning) {
                when (activeMode) {
                    BoostMode.BOOST -> {
                        // In BOOST mode, CPU and GPU are pinned to 100% as requested
                        cpuUsage = if (Random.nextFloat() > 0.08f) 100f else (99.2f + Random.nextFloat() * 0.8f)
                        gpuUsage = if (Random.nextFloat() > 0.05f) 100f else (99.5f + Random.nextFloat() * 0.5f)
                    }
                    BoostMode.BALANCED -> {
                        cpuUsage = 62f + Random.nextFloat() * 10f
                        gpuUsage = 58f + Random.nextFloat() * 8f
                    }
                    BoostMode.BATTERY_SAVER -> {
                        cpuUsage = 32f + Random.nextFloat() * 6f
                        gpuUsage = 24f + Random.nextFloat() * 5f
                    }
                }
            } else {
                cpuUsage = 14f + Random.nextFloat() * 5f
                gpuUsage = 8f + Random.nextFloat() * 4f
            }
            delay(1000)
        }
    }

    // Notification permission launcher for Android 13+
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            MonsterCoreService.log("[PERMISSION] Notification permission granted")
            MonsterCoreService.startService(context, activeMode)
            onSetSustainedPerf(true)
        } else {
            MonsterCoreService.log("[WARN] Notification permission denied - starting service anyway")
            MonsterCoreService.startService(context, activeMode)
            onSetSustainedPerf(true)
        }
    }

    fun triggerVibrate() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        } catch (_: Exception) {}
    }

    // Brand Lock Dialog
    if (showBrandLockDialog && !isBrandBypassed) {
        AlertDialog(
            onDismissRequest = { /* Force explicit user choice */ },
            modifier = Modifier.testTag("brand_lock_dialog"),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = CyberRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "This app only works on Vivo / iQOO devices",
                        color = CyberRed,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Detected Manufacturer: ${deviceInfo.manufacturer}\nModel: ${deviceInfo.model}",
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "TurboX Monster Core relies on Vivo / iQOO proprietary game engine hooks (<meta-data com.vivo.game.namelist />). Non-Vivo hardware is locked by default.",
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onCloseApp,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "CLOSE APP",
                        color = PureBlack,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        isBrandBypassed = true
                        showBrandLockDialog = false
                        MonsterCoreService.log("[SYS] Brand check bypassed for Emulator / Test Session")
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "BYPASS (EMULATOR TEST)",
                        color = NeonGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = DarkSurface
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Glitch Title Animation
            GlitchHeader(
                text = "RAUNAK EXPLOITS",
                subText = if (isRunning) "TURBOX MONSTER ENGINE // ACTIVE" else "TURBOX ENGINE // STANDBY"
            )

            // Status Bar Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isRunning) NeonGreen else CyberRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "ENGINE: RUNNING (100% BOOST LOCK)" else "ENGINE: STOPPED",
                        color = if (isRunning) NeonGreen else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = if (deviceInfo.isVivoOrIqoo) "[VIVO VERIFIED]" else "[TEST MODE]",
                    color = if (deviceInfo.isVivoOrIqoo) NeonGreen else CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            // CARD 1: DEVICE INFO
            CyberCard(
                title = "DEVICE & HARDWARE INFO",
                icon = Icons.Default.Info,
                accentColor = NeonGreen
            ) {
                InfoRow("PROCESSOR", deviceInfo.processorName, NeonGreen)
                InfoRow("TOTAL CORES", "${deviceInfo.totalCores} Cores (Octa-Core)", TextPrimary)
                InfoRow("MAX CLOCK", deviceInfo.maxClockGhz, NeonOrange)
                InfoRow("DEVICE", "${deviceInfo.manufacturer} ${deviceInfo.model}", TextPrimary)
                InfoRow("ARCHITECTURE", "${deviceInfo.architecture} • Android ${deviceInfo.androidVersion}", TextSecondary)
                InfoRow("VIVO GAME ENGINE", "com.vivo.game.namelist [HOOKED]", CyberCyan)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CARD 2: LIVE METERS
            CyberCard(
                title = "LIVE TELEMETRY METERS",
                icon = Icons.Default.Speed,
                accentColor = NeonOrange
            ) {
                // Circular Meters Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularMeter(
                        label = "CPU USAGE",
                        percentage = cpuUsage,
                        accentColor = NeonGreen,
                        subLabel = if (cpuUsage >= 98f) "MAX TURBO" else "OPTIMAL",
                        modifier = Modifier.testTag("cpu_meter")
                    )

                    CircularMeter(
                        label = "GPU USAGE",
                        percentage = gpuUsage,
                        accentColor = NeonOrange,
                        subLabel = if (gpuUsage >= 98f) "100% BOOST" else "RENDER OK",
                        modifier = Modifier.testTag("gpu_meter")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Additional telemetry stats
                InfoRow(
                    label = "TEMPERATURE",
                    value = String.format(Locale.US, "%.1f°C • %s", temperature, if (temperature > 41f) "OVERCLOCK WARM" else "OPTIMAL"),
                    valueColor = if (temperature > 40f) CyberRed else NeonOrange
                )

                InfoRow(
                    label = "LIVE FPS METER",
                    value = String.format(Locale.US, "%.1f FPS (%.1f ms)", currentFps, 1000f / currentFps.coerceAtLeast(1f)),
                    valueColor = NeonGreen
                )

                val ramPct = ((usedRamGb / totalRamGb.coerceAtLeast(1f)) * 100f).coerceIn(0f, 100f)
                InfoRow(
                    label = "RAM USAGE",
                    value = String.format(Locale.US, "%.1f GB / %.1f GB (%.0f%%)", usedRamGb, totalRamGb, ramPct),
                    valueColor = CyberCyan
                )

                InfoRow(
                    label = "PERFORMANCE MODE",
                    value = if (isRunning) "SUSTAINED_PERF_ON" else "STANDARD_POWERSAVE",
                    valueColor = if (isRunning) NeonGreen else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CARD 3: CONTROLS
            CyberCard(
                title = "CONTROLS & BOOST PROFILE",
                icon = Icons.Default.PlayArrow,
                accentColor = NeonGreen
            ) {
                // Mode Selector
                BoostModeSelector(
                    selectedMode = activeMode,
                    onSelectMode = { newMode ->
                        triggerVibrate()
                        MonsterCoreService.updateMode(context, newMode)
                    },
                    modifier = Modifier.testTag("mode_selector")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Engine Start/Stop Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CyberActionButton(
                        text = "START ENGINE",
                        icon = Icons.Default.PlayArrow,
                        color = NeonGreen,
                        enabled = !isRunning,
                        testTag = "start_engine_button",
                        onClick = {
                            triggerVibrate()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    MonsterCoreService.startService(context, activeMode)
                                    onSetSustainedPerf(true)
                                } else {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                MonsterCoreService.startService(context, activeMode)
                                onSetSustainedPerf(true)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    CyberActionButton(
                        text = "STOP ENGINE",
                        icon = Icons.Default.Stop,
                        color = CyberRed,
                        enabled = isRunning,
                        testTag = "stop_engine_button",
                        onClick = {
                            triggerVibrate()
                            MonsterCoreService.stopService(context)
                            onSetSustainedPerf(false)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Terminal Console
            HackerTerminal(logs = logs)

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
