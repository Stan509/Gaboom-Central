package com.gaboom.agent.data.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.agentConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_config")

/**
 * DataStore for agent/borlette configuration including offline mode settings.
 * Phase I-A: Stores allow_offline_print and device credentials.
 */
@Singleton
class AgentConfigDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.agentConfigDataStore

    companion object {
        val ALLOW_OFFLINE_PRINT = booleanPreferencesKey("allow_offline_print")
        val FREE_MARIAGE_ENABLED = booleanPreferencesKey("free_mariage_enabled")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_SECRET = stringPreferencesKey("device_secret")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val CACHED_TIRAGES = stringPreferencesKey("cached_tirages")
        val CACHED_RESULTATS = stringPreferencesKey("cached_resultats")
        val CACHED_DASHBOARD = stringPreferencesKey("cached_dashboard")
        val CACHED_TICKET_LIST = stringPreferencesKey("cached_ticket_list")
        // Phase 3 — Local-First
        val LAST_SERVER_CONTACT_AT = longPreferencesKey("last_server_contact_at")
        val SERVER_IN_MAINTENANCE = booleanPreferencesKey("server_in_maintenance")
        val RANGE_NEEDS_EXTENSION = booleanPreferencesKey("range_needs_extension")
    }

    suspend fun saveCachedTirages(tirages: List<com.gaboom.agent.data.model.Tirage>) {
        val json = com.google.gson.Gson().toJson(tirages)
        dataStore.edit { prefs ->
            prefs[CACHED_TIRAGES] = json
        }
    }

    suspend fun getCachedTirages(): List<com.gaboom.agent.data.model.Tirage> {
        val json = dataStore.data.map { prefs -> prefs[CACHED_TIRAGES] }.first() ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<com.gaboom.agent.data.model.Tirage>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveCachedResultats(resultats: List<com.gaboom.agent.data.model.ResultatTirage>) {
        val json = com.google.gson.Gson().toJson(resultats)
        dataStore.edit { prefs ->
            prefs[CACHED_RESULTATS] = json
        }
    }

    suspend fun getCachedResultats(): List<com.gaboom.agent.data.model.ResultatTirage> {
        val json = dataStore.data.map { prefs -> prefs[CACHED_RESULTATS] }.first() ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<com.gaboom.agent.data.model.ResultatTirage>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveCachedDashboard(dashboard: com.gaboom.agent.data.model.DashboardResponse) {
        val json = com.google.gson.Gson().toJson(dashboard)
        dataStore.edit { prefs ->
            prefs[CACHED_DASHBOARD] = json
        }
    }

    suspend fun getCachedDashboard(): com.gaboom.agent.data.model.DashboardResponse? {
        val json = dataStore.data.map { prefs -> prefs[CACHED_DASHBOARD] }.first() ?: return null
        return try {
            com.google.gson.Gson().fromJson(json, com.gaboom.agent.data.model.DashboardResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveCachedTicketList(tickets: List<com.gaboom.agent.data.model.TicketListItem>) {
        val json = com.google.gson.Gson().toJson(tickets)
        dataStore.edit { prefs ->
            prefs[CACHED_TICKET_LIST] = json
        }
    }

    suspend fun getCachedTicketList(): List<com.gaboom.agent.data.model.TicketListItem> {
        val json = dataStore.data.map { prefs -> prefs[CACHED_TICKET_LIST] }.first() ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<com.gaboom.agent.data.model.TicketListItem>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ─── Free Marriage Option ─────────────────────────────────────────────────

    val freeMariageEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FREE_MARIAGE_ENABLED] ?: true  // FORCED TO TRUE for testing
    }

    suspend fun getFreeMariageEnabled(): Boolean {
        return freeMariageEnabled.first()
    }

    suspend fun setFreeMariageEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[FREE_MARIAGE_ENABLED] = enabled
        }
    }

    // ─── Offline Print Policy ─────────────────────────────────────────────────

    val allowOfflinePrint: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ALLOW_OFFLINE_PRINT] ?: false
    }

    suspend fun getAllowOfflinePrint(): Boolean {
        return allowOfflinePrint.first()
    }

    suspend fun setAllowOfflinePrint(allowed: Boolean) {
        dataStore.edit { prefs ->
            prefs[ALLOW_OFFLINE_PRINT] = allowed
        }
    }

    // ─── Device Credentials (for HMAC signing) ─────────────────────────────────

    val deviceId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[DEVICE_ID]
    }

    val deviceSecret: Flow<String?> = dataStore.data.map { prefs ->
        prefs[DEVICE_SECRET]
    }

    val hasDeviceCredentials: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DEVICE_ID] != null && prefs[DEVICE_SECRET] != null
    }

    suspend fun saveDeviceCredentials(deviceId: String, deviceSecret: String, deviceName: String = "") {
        dataStore.edit { prefs ->
            prefs[DEVICE_ID] = deviceId
            prefs[DEVICE_SECRET] = deviceSecret
            prefs[DEVICE_NAME] = deviceName
        }
    }

    suspend fun getDeviceCredentials(): DeviceCredentials? {
        return dataStore.data.map { prefs ->
            val id = prefs[DEVICE_ID]
            val secret = prefs[DEVICE_SECRET]
            if (id != null && secret != null) {
                DeviceCredentials(id, secret, prefs[DEVICE_NAME] ?: "")
            } else null
        }.first()
    }

    suspend fun clearDeviceCredentials() {
        dataStore.edit { prefs ->
            prefs.remove(DEVICE_ID)
            prefs.remove(DEVICE_SECRET)
            prefs.remove(DEVICE_NAME)
        }
    }

    // ─── Ticket Number Range Sequence ─────────────────────────────────────────

    suspend fun saveTicketNumberRange(start: Long, end: Long, current: Long) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("ticket_number_start")] = start.toString()
            prefs[stringPreferencesKey("ticket_number_end")] = end.toString()
            prefs[stringPreferencesKey("ticket_number_current")] = current.toString()
        }
    }

    suspend fun getAndIncrementTicketNumber(): Long {
        var current: Long = 0L
        var rangeExhausted = false
        dataStore.edit { prefs ->
            val startVal = prefs[stringPreferencesKey("ticket_number_start")]?.toLongOrNull() ?: 5000000001L
            val endVal = prefs[stringPreferencesKey("ticket_number_end")]?.toLongOrNull() ?: 5000999999L
            val currVal = prefs[stringPreferencesKey("ticket_number_current")]?.toLongOrNull() ?: startVal

            current = currVal

            val nextVal = currVal + 1
            if (nextVal > endVal) {
                // Range exhausted — keep current at end, flag for server re-allocation
                rangeExhausted = true
                prefs[RANGE_NEEDS_EXTENSION] = true
            } else {
                prefs[stringPreferencesKey("ticket_number_current")] = nextVal.toString()
            }
        }
        return current
    }

    suspend fun rangeNeedsExtension(): Boolean {
        return dataStore.data.map { prefs -> prefs[RANGE_NEEDS_EXTENSION] ?: false }.first()
    }

    suspend fun clearRangeExtensionFlag() {
        dataStore.edit { prefs -> prefs[RANGE_NEEDS_EXTENSION] = false }
    }

    // ─── Server Contact Tracking (Phase 3) ───────────────────────────────────

    suspend fun updateServerContact() {
        dataStore.edit { prefs ->
            prefs[LAST_SERVER_CONTACT_AT] = System.currentTimeMillis()
            prefs[SERVER_IN_MAINTENANCE] = false
        }
    }

    suspend fun recordServerMaintenance() {
        dataStore.edit { prefs ->
            prefs[SERVER_IN_MAINTENANCE] = true
        }
    }

    suspend fun getLastServerContactAt(): Long {
        return dataStore.data.map { prefs -> prefs[LAST_SERVER_CONTACT_AT] ?: 0L }.first()
    }

    suspend fun isServerInMaintenance(): Boolean {
        return dataStore.data.map { prefs -> prefs[SERVER_IN_MAINTENANCE] ?: false }.first()
    }

    // ─── App Start Time (for grace period) ───────────────────────────────────

    suspend fun setAppStartTime(timeMs: Long) {
        dataStore.edit { prefs ->
            prefs[longPreferencesKey("app_start_time")] = timeMs
        }
    }

    suspend fun getAppStartTime(): Long {
        return dataStore.data.map { prefs -> prefs[longPreferencesKey("app_start_time")] ?: 0L }.first()
    }

    // ─── Clear All ─────────────────────────────────────────────────────────────

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}

data class DeviceCredentials(
    val deviceId: String,
    val deviceSecret: String,
    val deviceName: String
)
