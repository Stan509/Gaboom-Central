package com.gaboom.agent.data.clock

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.Calendar
import java.util.TimeZone

/**
 * A secure, server-synchronized monotonic clock system that is immune to manual device system time changes,
 * bad device timezones, and reboot events.
 *
 * Preserves server time (America/Port-au-Prince) even when offline for extended periods.
 * Enforces draw closure validation based on server time and Haiti timezone.
 */
object SecuredClock {
    private const val TAG = "SecuredClock"
    private const val PREFS_NAME = "secured_clock_prefs"
    private const val HAITI_TIMEZONE_ID = "America/Port-au-Prince"

    private var lastServerTime: Long = 0L
    private var lastSyncElapsedRealtime: Long = 0L
    private var lastSyncSystemTime: Long = 0L
    private var clockOffset: Long = 0L // serverTime - systemTime at sync
    private var maxRecordedTime: Long = 0L

    // Base anchor for monotonic time within current boot session
    private var bootBaseTime: Long = 0L
    private var bootBaseElapsedRealtime: Long = 0L

    private var appContext: Context? = null

    fun init(context: Context) {
        try {
            appContext = context.applicationContext
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            lastServerTime = prefs.getLong("server_time", 0L)
            lastSyncElapsedRealtime = prefs.getLong("elapsed_realtime", 0L)
            lastSyncSystemTime = prefs.getLong("system_time", 0L)
            clockOffset = prefs.getLong("clock_offset", 0L)
            maxRecordedTime = prefs.getLong("max_recorded_time", 0L)

            val currentElapsed = SystemClock.elapsedRealtime()
            val currentSystem = System.currentTimeMillis()

            // Check if device rebooted since last sync
            if (lastSyncElapsedRealtime > 0L && currentElapsed >= lastSyncElapsedRealtime) {
                // Same boot session as last sync
                bootBaseTime = lastServerTime
                bootBaseElapsedRealtime = lastSyncElapsedRealtime
            } else {
                // Device rebooted or first init.
                // Estimate server time using device system time + last recorded offset
                val estimatedServerTime = if (lastServerTime > 0L) {
                    currentSystem + clockOffset
                } else {
                    currentSystem
                }
                // Time must NEVER go backwards compared to maxRecordedTime or lastServerTime
                val secureBase = maxOf(maxRecordedTime, lastServerTime, estimatedServerTime)
                bootBaseTime = secureBase
                bootBaseElapsedRealtime = currentElapsed
                maxRecordedTime = maxOf(maxRecordedTime, secureBase)
            }

            Log.d(TAG, "Initialized clock: serverTime=$lastServerTime, bootBaseTime=$bootBaseTime, offset=$clockOffset")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SecuredClock: ${e.message}")
        }
    }

    fun update(serverTime: Long) {
        if (serverTime <= 0L) return
        try {
            val elapsed = SystemClock.elapsedRealtime()
            val system = System.currentTimeMillis()

            lastServerTime = serverTime
            lastSyncElapsedRealtime = elapsed
            lastSyncSystemTime = system
            clockOffset = serverTime - system

            // Anchor new monotonic base
            bootBaseTime = serverTime
            bootBaseElapsedRealtime = elapsed
            if (serverTime > maxRecordedTime) {
                maxRecordedTime = serverTime
            }

            val ctx = appContext
            if (ctx != null) {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("server_time", serverTime)
                    .putLong("elapsed_realtime", elapsed)
                    .putLong("system_time", system)
                    .putLong("clock_offset", clockOffset)
                    .putLong("max_recorded_time", maxRecordedTime)
                    .apply()
            }
            Log.d(TAG, "Updated clock with serverTime=$serverTime (offset=$clockOffset ms)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update SecuredClock: ${e.message}")
        }
    }

    /**
     * Returns the current estimated server time in epoch milliseconds.
     * Guaranteed to be monotonic and will never drift backwards.
     */
    fun now(): Long {
        val currentElapsed = SystemClock.elapsedRealtime()
        val calculated = if (bootBaseTime > 0L) {
            val elapsedDiff = if (currentElapsed >= bootBaseElapsedRealtime) {
                currentElapsed - bootBaseElapsedRealtime
            } else {
                0L
            }
            bootBaseTime + elapsedDiff
        } else {
            System.currentTimeMillis()
        }

        val secureTime = maxOf(calculated, maxRecordedTime)
        if (secureTime > maxRecordedTime) {
            maxRecordedTime = secureTime
            // Persist periodically or opportunistically
            appContext?.let { ctx ->
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong("max_recorded_time", maxRecordedTime)
                    .apply()
            }
        }
        return secureTime
    }

    /**
     * Returns a Calendar configured with the current server time in Haiti timezone (America/Port-au-Prince).
     */
    fun getHaitiCalendar(): Calendar {
        val tz = TimeZone.getTimeZone(HAITI_TIMEZONE_ID)
        return Calendar.getInstance(tz).apply {
            timeInMillis = now()
        }
    }

    /**
     * Checks whether a draw is closed given its closing time string (HH:mm or HH:mm:ss).
     * Compares strictly against Haiti server time.
     */
    fun isDrawClosed(heureFermeture: String?): Boolean {
        if (heureFermeture.isNullOrBlank()) return false
        try {
            val cal = getHaitiCalendar()
            val currentSecondOfDay = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
                    cal.get(Calendar.MINUTE) * 60 +
                    cal.get(Calendar.SECOND)

            val parts = heureFermeture.trim().split(":")
            if (parts.size < 2) return false
            val closingHour = parts[0].toIntOrNull() ?: return false
            val closingMin = parts[1].toIntOrNull() ?: return false
            val closingSec = if (parts.size > 2) parts[2].toIntOrNull() ?: 0 else 0
            val closingSecondOfDay = closingHour * 3600 + closingMin * 60 + closingSec

            return currentSecondOfDay >= closingSecondOfDay
        } catch (e: Exception) {
            Log.e(TAG, "Error checking draw closing: ${e.message}")
            return false
        }
    }

    /**
     * Checks whether a draw is currently open given its opening and closing time.
     */
    fun isDrawOpen(heureOuverture: String?, heureFermeture: String?): Boolean {
        if (isDrawClosed(heureFermeture)) return false
        if (heureOuverture.isNullOrBlank()) return true
        try {
            val cal = getHaitiCalendar()
            val currentSecondOfDay = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
                    cal.get(Calendar.MINUTE) * 60 +
                    cal.get(Calendar.SECOND)

            val parts = heureOuverture.trim().split(":")
            if (parts.size < 2) return true
            val openingHour = parts[0].toIntOrNull() ?: return true
            val openingMin = parts[1].toIntOrNull() ?: return true
            val openingSec = if (parts.size > 2) parts[2].toIntOrNull() ?: 0 else 0
            val openingSecondOfDay = openingHour * 3600 + openingMin * 60 + openingSec

            return currentSecondOfDay >= openingSecondOfDay
        } catch (e: Exception) {
            return true
        }
    }
}
