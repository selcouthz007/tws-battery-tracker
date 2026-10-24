package com.tws.tracker

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action ?: return
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }

            val prefs = context.getSharedPreferences("tws_active_state", Context.MODE_PRIVATE)
            val safeName = getSafeDeviceName(context, device)

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val battery = getDeviceBatteryLevel(device)
                    val now = System.currentTimeMillis()

                    prefs.edit()
                        .putBoolean("is_connected", true)
                        .putString("device_name", safeName)
                        .putLong("start_time", now)
                        .putInt("start_battery", battery)
                        .putInt("last_battery", battery)
                        .apply()

                    notifyUiUpdate(context)
                }

                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    if (level != -1) {
                        val isConnected = prefs.getBoolean("is_connected", false)
                        val startBat = prefs.getInt("start_battery", -1)

                        val editor = prefs.edit()
                        if (!isConnected) {
                            editor.putBoolean("is_connected", true)
                            editor.putString("device_name", safeName)
                            editor.putLong("start_time", System.currentTimeMillis())
                        }
                        if (startBat == -1) {
                            editor.putInt("start_battery", level)
                        }
                        editor.putInt("last_battery", level)
                        editor.apply()

                        notifyUiUpdate(context)
                    }
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val isConnected = prefs.getBoolean("is_connected", false)
                    if (isConnected) {
                        val name = prefs.getString("device_name", safeName) ?: safeName
                        val startTime = prefs.getLong("start_time", System.currentTimeMillis())
                        val endTime = System.currentTimeMillis()
                        val startBat = prefs.getInt("start_battery", -1)
                        val endBat = prefs.getInt("last_battery", startBat)

                        val db = SessionDbHelper(context)
                        db.insertSession(
                            deviceName = name,
                            startTime = startTime,
                            endTime = endTime,
                            startBat = startBat,
                            endBat = endBat,
                            caseBat = -1
                        )

                        prefs.edit().clear().apply()
                        notifyUiUpdate(context)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun getSafeDeviceName(context: Context, device: BluetoothDevice?): String {
            if (device == null) return "TWS Earbuds"
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasPerm) return "TWS Earbuds"
                }
                device.name ?: "TWS Earbuds"
            } catch (e: SecurityException) {
                "TWS Earbuds"
            } catch (e: Exception) {
                "TWS Earbuds"
            }
        }

        fun getDeviceBatteryLevel(device: BluetoothDevice?): Int {
            if (device == null) return -1
            return try {
                val method = device.javaClass.getMethod("getBatteryLevel")
                method.invoke(device) as? Int ?: -1
            } catch (e: Exception) {
                -1
            }
        }
    }

    private fun notifyUiUpdate(context: Context) {
        val updateIntent = Intent("com.tws.tracker.UPDATE_UI")
        updateIntent.setPackage(context.packageName)
        context.sendBroadcast(updateIntent)
    }
}