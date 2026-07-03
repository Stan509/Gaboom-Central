package com.gaboom.agent.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.gaboom.agent.data.local.IntegritySeverity

/**
 * Simple domain event bus using a MutableSharedFlow with replay = 1 for sticky events.
 * All events are published on the Default dispatcher.
 */
object DomainEventBus {
    private val _events = MutableSharedFlow<Any>(replay = 1)
    val events = _events.asSharedFlow()

    /** Publish an event. */
    fun publish(event: Any) {
        CoroutineScope(Dispatchers.Default).launch {
            _events.emit(event)
        }
    }
}

/**
 * Base class for all events – marker interface.
 */
interface Event

// Define concrete events (expand as needed)
data class RecoveryNeeded(val reason: String) : Event
data class SessionExpired(val sessionId: String) : Event
data class ClockTampered(val details: String) : Event
data class IntegrityBroken(val severity: IntegritySeverity, val details: String) : Event
data class BudgetExceeded(val metric: String, val limit: Int) : Event
