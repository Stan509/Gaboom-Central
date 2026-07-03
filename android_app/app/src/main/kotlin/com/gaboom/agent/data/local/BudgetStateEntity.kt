package com.gaboom.agent.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent state for multidimensional budget constraints.
 * All fields are stored as Long for simplicity (e.g., milliseconds for duration).
 */
@Entity(tableName = "budget_state")
data class BudgetStateEntity(
    @PrimaryKey val sessionUuid: String,
    @ColumnInfo(name = "duration_used") val durationUsed: Long = 0L,
    @ColumnInfo(name = "duration_limit") val durationLimit: Long,
    @ColumnInfo(name = "tickets_used") val ticketsUsed: Int = 0,
    @ColumnInfo(name = "tickets_limit") val ticketsLimit: Int,
    @ColumnInfo(name = "payments_used") val paymentsUsed: Int = 0,
    @ColumnInfo(name = "payments_limit") val paymentsLimit: Int,
    @ColumnInfo(name = "cancellations_used") val cancellationsUsed: Int = 0,
    @ColumnInfo(name = "cancellations_limit") val cancellationsLimit: Int,
    @ColumnInfo(name = "financial_amount_used") val financialAmountUsed: Double = 0.0,
    @ColumnInfo(name = "financial_amount_limit") val financialAmountLimit: Double,
    @ColumnInfo(name = "sync_ops_used") val syncOpsUsed: Int = 0,
    @ColumnInfo(name = "sync_ops_limit") val syncOpsLimit: Int,
    @ColumnInfo(name = "version") val version: Int = 1
)
