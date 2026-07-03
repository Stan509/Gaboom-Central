package com.gaboom.agent.data.config

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

/**
 * Simple configuration cache manager using Jetpack DataStore Preferences.
 * Stores key‑value pairs required by the offline engine (e.g., last sync timestamp,
 * feature toggles, server clock offset, etc.). All calls should be guarded by
 * [OfflineFeatureGuard] before performing any write/read.
 */
class ConfigCacheManager private constructor(private val context: Context) {
    private val Context.configDataStore by preferencesDataStore(name = "offline_config")

    suspend fun setString(key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        context.configDataStore.edit { it[prefKey] = value }
    }

    suspend fun getString(key: String, default: String = ""): String {
        val prefKey = stringPreferencesKey(key)
        val prefs = context.configDataStore.data.first()
        return prefs[prefKey] ?: default
    }

    suspend fun setInt(key: String, value: Int) {
        val prefKey = intPreferencesKey(key)
        context.configDataStore.edit { it[prefKey] = value }
    }

    suspend fun getInt(key: String, default: Int = 0): Int {
        val prefKey = intPreferencesKey(key)
        val prefs = context.configDataStore.data.first()
        return prefs[prefKey] ?: default
    }

    companion object {
        @Volatile private var INSTANCE: ConfigCacheManager? = null
        fun getInstance(context: Context): ConfigCacheManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConfigCacheManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
