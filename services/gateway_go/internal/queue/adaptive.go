package queue

import (
	"context"
	"gateway_go/internal/featureflags"
	"time"
)

type Decision string

const (
	Accept     Decision = "ACCEPT"
	Delay      Decision = "DELAY"
	Prioritize Decision = "PRIORITIZE"
	Retry      Decision = "RETRY"
)

type QueueMetrics struct {
	ActiveDevicesCount int
	ServerLoadPercent  int
	TimeToDrawClose    time.Duration
	NetworkLatencyMs   int
	TransactionPriority int
}

type AdaptiveQueue interface {
	Evaluate(ctx context.Context, metrics QueueMetrics) (Decision, error)
}

type GatewayAdaptiveQueue struct{}

func NewAdaptiveQueue() AdaptiveQueue {
	return &GatewayAdaptiveQueue{}
}

func (q *GatewayAdaptiveQueue) Evaluate(ctx context.Context, metrics QueueMetrics) (Decision, error) {
	if !featureflags.IsEnabled("ADAPTIVE_QUEUE_ENABLED") {
		return Accept, nil
	}

	// Dynamic capacity allocation calculations (ADR-014):
	if metrics.ServerLoadPercent > 90 {
		return Retry, nil
	}

	if metrics.TimeToDrawClose < 5*time.Minute && metrics.TransactionPriority > 5 {
		return Prioritize, nil
	}

	if metrics.ActiveDevicesCount > 8000 {
		return Delay, nil
	}

	return Accept, nil
}
