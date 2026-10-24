package com.tws.tracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: SessionDbHelper
    private lateinit var tvLiveStatus: TextView
    private lateinit var tvLiveDetails: TextView
    private lateinit var tvLogs: TextView

    private val uiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = SessionDbHelper(this)
        tvLiveStatus = findViewById(R.id.tvLiveStatus)
        tvLiveDetails = findViewById(R.id.tvLiveDetails)
        tvLogs = findViewById(R.id.tvLogs)

        val btnSimulate: Button = findViewById(R.id.btnSimulate)
        val btnClear: Button = findViewById(R.id.btnClear)

        checkBluetoothPermissions()

        btnSimulate.setOnClickListener {
            val now = System.currentTimeMillis()
            val fakeStart = now - (85 * 60 * 1000L) // 1 hr 25 mins ago
            db.insertSession(
                deviceName = "Mustang Nitro TWS",
                startTime = fakeStart,
                endTime = now,
                startBat = 90,
                endBat = 65,
                caseBat = 80
            )
            refreshUi()
        }

        btnClear.setOnClickListener {
            db.clearAll()
            refreshUi()
        }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.tws.tracker.UPDATE_UI")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uiReceiver, filter)
        }
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(uiReceiver)
    }

    private fun refreshUi() {
        val prefs = getSharedPreferences("tws_active_state", Context.MODE_PRIVATE)
        val isConnected = prefs.getBoolean("is_connected", false)
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

        if (isConnected) {
            val name = prefs.getString("device_name", "TWS Earbuds")
            val startBat = prefs.getInt("start_battery", -1)
            val lastBat = prefs.getInt("last_battery", -1)
            val startTime = prefs.getLong("start_time", System.currentTimeMillis())
            val activeMins = ((System.currentTimeMillis() - startTime) / 60000).coerceAtLeast(0)

            tvLiveStatus.text = "🟢 Connected: $name"
            val batInfo = if (lastBat != -1) "$lastBat% (Started at $startBat%)" else "Reading battery..."
            tvLiveDetails.text = "Connected at ${timeFmt.format(Date(startTime))} • Active: ${activeMins}m\nLive Battery: $batInfo"
        } else {
            tvLiveStatus.text = "⚪ Disconnected"
            tvLiveDetails.text = "Connect your TWS to automatically start tracking battery drain."
        }

        val sessions = db.getAllSessions()
        if (sessions.isEmpty()) {
            tvLogs.text = "No sessions recorded yet.\n\nConnect & disconnect your TWS or tap '+ Test Log' to preview."
            return
        }

        val sb = StringBuilder()
        sessions.forEachIndexed { index, s ->
            val durationMins = ((s.endTime - s.startTime) / 60000).coerceAtLeast(1)
            val hrs = durationMins / 60
            val mins = durationMins % 60
            val durationStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"

            val drain = if (s.startBattery != -1 && s.endBattery != -1) {
                (s.startBattery - s.endBattery).coerceAtLeast(0)
            } else -1

            val drainRate = if (drain >= 0 && durationMins > 0) {
                String.format(Locale.US, "%.1f%%/hr", (drain * 60.0) / durationMins)
            } else "N/A"

            sb.append("#${sessions.size - index} • ${s.deviceName}\n")
            sb.append("📅 ${dateFmt.format(Date(s.startTime))} -> ${timeFmt.format(Date(s.endTime))}\n")
            sb.append("⏱️ Duration : $durationStr\n")
            if (s.startBattery != -1 && s.endBattery != -1) {
                sb.append("🔋 Buds Bat : ${s.startBattery}% -> ${s.endBattery}% (Drain: $drain%)\n")
                sb.append("⚡ Drain Rate: $drainRate\n")
            } else {
                sb.append("🔋 Buds Bat : Battery % not reported by protocol\n")
            }
            if (s.caseBattery != -1) {
                sb.append("📦 Case Bat : ${s.caseBattery}%\n")
            }
            sb.append("──────────────────────────────────\n")
        }
        tvLogs.text = sb.toString()
    }

    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val needed = perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
            }
        }
    }
}