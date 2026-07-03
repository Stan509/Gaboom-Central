package queue

import "time"

// BatchQueue manages in-memory buffering of tickets
type BatchQueue interface {
	Push(ticketID string, data []byte) error
	Pop() (string, []byte, error)
	Size() int
}

// PriorityQueue schedules ticket delivery close to draw close times
type PriorityQueue interface {
	EnqueueWithPriority(ticketID string, data []byte, priority int) error
	Dequeue() (string, []byte, error)
}

// CircuitBreaker manages communication safety limits to Django
type CircuitBreaker interface {
	Call(f func() error) error
	IsOpen() bool
	Reset()
}

// RetryPolicy manages exponential backoff triggers
type RetryPolicy interface {
	ExecuteWithRetry(f func() error) error
	GetMaxRetries() int
	GetBackoffDuration(attempt int) time.Duration
}
