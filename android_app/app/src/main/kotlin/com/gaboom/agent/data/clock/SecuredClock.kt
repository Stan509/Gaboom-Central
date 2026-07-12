package com.gaboom.agent.data.clock

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * A secure, server-synchronized monotonic clock system that is immune to manual device system time changes.
 * Uses SystemClock.elapsedRealtime() to measure elapsed time monotonically.
 * Persists status to survive restarts and reboots.
 */
object SecuredClock {
    private var lastServerTime: Long = 0L
    private var lastSyncElapsedRealtime: Long = 0L
    private var lastSyncSystemTime: Long = 0L
    
    private var appContext: Context? = null
    
    fun init(context: Context) {
        try {
            appContext = context.applicationContext
            val prefs = context.getSharedPreferences("secured_clock_prefs", Context.MODE_PRIVATE)
            lastServerTime = prefs.getLong("server_time", 0L)
            lastSyncElapsedRealtime = prefs.getLong("elapsed_realtime", 0L)
            lastSyncSystemTime = prefs.getLong("system_time", 0L)
            
            // If the device has been rebooted, elapsedRealtime is reset and will be smaller than last sync
            if (SystemClock.elapsedRealtime() < lastSyncElapsedRealtime) {
                lastSyncElapsedRealtime = 0L
            }
            Log.d("SecuredClock", "Initialized clock: serverTime=$lastServerTime systemTime=$lastSyncSystemTime")
        } catch (e: Exception) {
            Log.e("SecuredClock", "Failed to initialize SecuredClock: ${e.message}")
        }
    }
    
    fun update(serverTime: Long) {
        try {
            val elapsed = SystemClock.elapsedRealtime()
            val system = System.currentTimeMillis()
            
            if (lastSyncSystemTime > 0 && system < lastSyncSystemTime) {
                Log.w("SecuredClock", "Clock tamper warning: system time was changed backwards!")
            }
            
            lastServerTime = serverTime
            lastSyncElapsedRealtime = elapsed
            lastSyncSystemTime = system
            
            val ctx = appContext
            if (ctx != null) {
                val prefs = ctx.getSharedPreferences("secured_clock_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("server_time", serverTime)
                    .putLong("elapsed_realtime", elapsed)
                    .putLong("system_time", system)
                    .apply()
            }
            Log.d("SecuredClock", "Updated clock with serverTime=$serverTime")
        } catch (e: Exception) {
            Log.e("SecuredClock", "Failed to update SecuredClock: ${e.message}")
        }
    }
    
    fun now(): Long {
        return System.currentTimeMillis()
    }
}
