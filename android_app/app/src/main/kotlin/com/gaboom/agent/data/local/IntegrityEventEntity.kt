package com.gaboom.agent.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

enum class IntegritySeverity { INFO, WARN, CRITICAL }

/**
 * Represents an integrity monitoring event stored in Room.
 */
@Entity(tableName = "integrity_event")
data class IntegrityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val severity: IntegritySeverity = IntegritySeverity.INFO,
    val message: String,
    val metadata: String? = null // optional JSON payload for extra info
)
