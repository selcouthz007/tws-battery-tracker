package com.tws.tracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class TwsSession(
    val id: Long,
    val deviceName: String,
    val startTime: Long,
    val endTime: Long,
    val startBattery: Int,
    val endBattery: Int,
    val caseBattery: Int
)

class SessionDbHelper(context: Context) : SQLiteOpenHelper(context, "tws_sessions.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_name TEXT,
                start_time INTEGER,
                end_time INTEGER,
                start_battery INTEGER,
                end_battery INTEGER,
                case_battery INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS sessions")
        onCreate(db)
    }

    fun insertSession(
        deviceName: String,
        startTime: Long,
        endTime: Long,
        startBat: Int,
        endBat: Int,
        caseBat: Int = -1
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("device_name", deviceName)
            put("start_time", startTime)
            put("end_time", endTime)
            put("start_battery", startBat)
            put("end_battery", endBat)
            put("case_battery", caseBat)
        }
        db.insert("sessions", null, values)
    }

    fun getAllSessions(): List<TwsSession> {
        val list = mutableListOf<TwsSession>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM sessions ORDER BY id DESC", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(
                    TwsSession(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        deviceName = cursor.getString(cursor.getColumnIndexOrThrow("device_name")),
                        startTime = cursor.getLong(cursor.getColumnIndexOrThrow("start_time")),
                        endTime = cursor.getLong(cursor.getColumnIndexOrThrow("end_time")),
                        startBattery = cursor.getInt(cursor.getColumnIndexOrThrow("start_battery")),
                        endBattery = cursor.getInt(cursor.getColumnIndexOrThrow("end_battery")),
                        caseBattery = cursor.getInt(cursor.getColumnIndexOrThrow("case_battery"))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM sessions")
    }
}