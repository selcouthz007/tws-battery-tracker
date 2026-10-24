package com.tws.tracker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
    private val btReceiver = BluetoothReceiver()

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

        // Register dynamic Bluetooth listener so events are never missed while app is alive
        val btFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(btReceiver, btFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(btReceiver, btFilter)
        }

        checkBluetoothPermissions()

        btnSimulate.setOnClickListener {
            val now = System.currentTimeMillis()
            val fakeStart = now - (85 * 60 * 1000L)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(btReceiver)
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.tws.tracker.UPDATE_UI")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uiReceiver, filter)
        }
        syncCurrentBluetoothState()
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(uiReceiver)
        } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        syncCurrentBluetoothState()
        refreshUi()
    }

    private fun hasBtPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // Checks if a TWS/Headset is ALREADY connected right now
    private fun syncCurrentBluetoothState() {
        if (!hasBtPermission()) return
        try {
            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter? = btManager?.adapter
            if (adapter == null || !adapter.isEnabled) return

            // Check bonded devices that are currently connected via reflection or profile
            val prefs = getSharedPreferences("tws_active_state", Context.MODE_PRIVATE)
            val bondedDevices = adapter.bondedDevices ?: emptySet()

            var connectedTws: BluetoothDevice? = null
            for (device in bondedDevices) {
                val isConnected = try {
                    val method = device.javaClass.getMethod("isConnected")
                    method.invoke(device) as? Boolean ?: false
                } catch (_: Exception) {
                    false
                }
                if (isConnected) {
                    connectedTws = device
                    break
                }
            }

            if (connectedTws != null) {
                val name = BluetoothReceiver.getSafeDeviceName(this, connectedTws)
                val liveBat = BluetoothReceiver.getDeviceBatteryLevel(connectedTws)
                val alreadyTracking = prefs.getBoolean("is_connected", false)

                val editor = prefs.edit()
                editor.putBoolean("is_connected", true)
                editor.putString("device_name", name)
                if (!alreadyTracking) {
                    editor.putLong("start_time", System.currentTimeMillis())
                    editor.putInt("start_battery", liveBat)
                }
                if (liveBat != -1) {
                    if (prefs.getInt("start_battery", -1) == -1) {
                        editor.putInt("start_battery", liveBat)
                    }
                    editor.putInt("last_battery", liveBat)
                }
                editor.apply()
                refreshUi()
            } else {
                // Also check Headset / A2DP profile as fallback
                adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        try {
                            val devices = proxy.connectedDevices
                            if (devices.isNotEmpty()) {
                                val dev = devices[0]
                                val name = BluetoothReceiver.getSafeDeviceName(this@MainActivity, dev)
                                val liveBat = BluetoothReceiver.getDeviceBatteryLevel(dev)
                                val already = prefs.getBoolean("is_connected", false)
                                val editor = prefs.edit()
                                editor.putBoolean("is_connected", true)
                                editor.putString("device_name", name)
                                if (!already) {
                                    editor.putLong("start_time", System.currentTimeMillis())
                                    editor.putInt("start_battery", liveBat)
                                }
                                if (liveBat != -1) {
                                    editor.putInt("last_battery", liveBat)
                                }
                                editor.apply()
                                runOnUiThread { refreshUi() }
                            }
                        } catch (_: Exception) {}
                        adapter.closeProfileProxy(profile, proxy)
                    }
                    override fun onServiceDisconnected(profile: Int) {}
                }, BluetoothProfile.HEADSET)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshUi() {
        val prefs = getSharedPreferences("tws_active_state", Context.MODE_PRIVATE)
        val isConnected = prefs.getBoolean("is_connected", false)
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

        if (!hasBtPermission()) {
            tvLiveStatus.text = "⚠️ Permission Required"
            tvLiveDetails.text = "Please allow 'Nearby Devices' permission in App Settings to track TWS."
        } else if (isConnected) {
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