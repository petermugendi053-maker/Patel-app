package com.example.mt5assistant

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    TradingAssistantDashboard(this)
                }
            }
        }
    }
}

data class NetworkMetrics(val host: String, val latencyMs: Long, val isSuccess: Boolean)
data class TradeLog(
    val timestamp: String,
    val latency: Long,
    val ramAvailableGb: String,
    val status: String
)

@Composable
fun TradingAssistantDashboard(context: Context) {
    val coroutineScope = rememberCoroutineScope()

    var localPing by remember { mutableStateOf("Not tested") }
    var brokerPing by remember { mutableStateOf("Not tested") }
    var rawLatency by remember { mutableStateOf(0L) }
    var availableRam by remember { mutableStateOf("Calculating...") }
    var batteryStatus by remember { mutableStateOf("Checking...") }
    var thermalState by remember { mutableStateOf("Normal") }
    var isTesting by remember { mutableStateOf(false) }

    val executionLogs = remember { mutableStateListOf<TradeLog>() }

    fun runDiagnostics() {
        if (isTesting) return
        isTesting = true
        coroutineScope.launch {
            val localResult = executePing("1.1.1.1")
            val brokerResult = executePing("104.18.27.100")

            localPing = if (localResult.isSuccess) "${localResult.latencyMs} ms" else "Failed"
            brokerPing = if (brokerResult.isSuccess) "${brokerResult.latencyMs} ms" else "Timeout"
            rawLatency = if (brokerResult.isSuccess) brokerResult.latencyMs else 0L

            val actManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val freeGb = String.format(
                Locale.US, "%.2f",
                memInfo.availMem.toDouble() / (1024 * 1024 * 1024)
            )
            val totalGb = String.format(
                Locale.US, "%.2f",
                memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
            )
            availableRam = "$freeGb GB / $totalGb GB"

            val batteryIntent =
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            batteryStatus =
                if (level >= 0 && scale > 0) "${(level * 100 / scale)}%" else "Unknown"

            thermalState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val powerManager =
                    context.getSystemService(Context.POWER_SERVICE) as PowerManager
                when (powerManager.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_SEVERE,
                    PowerManager.THERMAL_STATUS_CRITICAL,
                    PowerManager.THERMAL_STATUS_EMERGENCY,
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Throttling active"
                    else -> "Normal"
                }
            } else {
                "Unavailable"
            }

            isTesting = false
        }
    }

    LaunchedEffect(Unit) {
        runDiagnostics()
    }

    val warning = when {
        thermalState.contains("Throttling") ->
            "Phone is overheating. CPU throttling may affect trading."
        rawLatency > 150 ->
            "High latency detected. Avoid latency-sensitive short-term trades."
        else ->
            "System state optimal for trading."
    }

    val warningBg =
        if (rawLatency > 150 || thermalState.contains("Throttling"))
            Color(0xFFB71C1C)
        else
            Color(0xFF1B5E20)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "MT5 Trading Assistant",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "Connection Stability & Device Readiness",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(warningBg, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                warning,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "NETWORK METRICS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )
                Spacer(Modifier.height(8.dp))
                MetricRow("Carrier Edge Ping:", localPing)
                MetricRow("Broker Latency:", brokerPing)
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "HARDWARE STATUS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )
                Spacer(Modifier.height(8.dp))
                MetricRow("Available RAM:", availableRam)
                MetricRow("Battery Level:", batteryStatus)
                MetricRow("Thermal State:", thermalState)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { runDiagnostics() },
                enabled = !isTesting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
            ) {
                Text(
                    if (isTesting) "Scanning..." else "Run Diagnostics",
                    color = Color.White
                )
            }

            Button(
                onClick = {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val time = sdf.format(Date())
                    val status = when {
                        rawLatency == 0L -> "Unknown"
                        rawLatency > 150 -> "High Delay"
                        else -> "Normal"
                    }
                    executionLogs.add(
                        0,
                        TradeLog(time, rawLatency, availableRam, status)
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
            ) {
                Text("Log Execution", color = Color.White)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Trade Execution Log",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(executionLogs) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF252525)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(log.timestamp, fontSize = 12.sp, color = Color.LightGray)
                            Text(
                                "RAM: ${log.ramAvailableGb}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${log.latency} ms",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                log.status,
                                fontSize = 11.sp,
                                color = if (log.status == "High Delay")
                                    Color.Red else Color.Green
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(
            value,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

suspend fun executePing(host: String): NetworkMetrics =
    withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("ping", "-c", "1", "-W", "2", host)
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var rtt = -1L

            while (reader.readLine().also { line = it } != null) {
                val text = line ?: continue
                val marker = "time="
                val index = text.indexOf(marker)
                if (index >= 0) {
                    val value = text.substring(index + marker.length)
                        .trim()
                        .split(" ", "ms")
                        .firstOrNull()
                    rtt = value?.toFloatOrNull()?.toLong() ?: -1L
                }
            }

            val exitCode = process.waitFor()
            if (exitCode == 0 && rtt >= 0) {
                NetworkMetrics(host, rtt, true)
            } else {
                NetworkMetrics(
                    host,
                    System.currentTimeMillis() - startTime,
                    false
                )
            }
        } catch (_: Exception) {
            NetworkMetrics(host, 0L, false)
        }
    }
