package com.gaboom.agent.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draw_cache")
data class DrawCacheEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "nom") val nom: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "heure_ouverture") val heureOuverture: String,
    @ColumnInfo(name = "heure_fermeture") val heureFermeture: String,
    @ColumnInfo(name = "heure_tirage") val heureTirage: String,
    @ColumnInfo(name = "etat") val etat: String,
    @ColumnInfo(name = "jours") val jours: String?,
    @ColumnInfo(name = "session_key") val sessionKey: String?,
    @ColumnInfo(name = "signature") val signature: String = "",
    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "checksum") val checksum: String = "",
    @ColumnInfo(name = "expiration") val expiration: Long = 0L,
    @ColumnInfo(name = "replay_token") val replayToken: String = ""
)
