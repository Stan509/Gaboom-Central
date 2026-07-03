package featureflags

import (
	"os"
	"strings"
)

// DefaultFlags map representing the state of features
var DefaultFlags = map[string]bool{
	"OFFLINE_V2":                     true,
	"SYNC_ENGINE_V2":                 true,
	"QUEUE_ENGINE":                   true,
	"LOTTERY_CLOCK":                  true,
	"SQLCIPHER":                      true,
	"GO_GATEWAY":                     true,
	"RUST_SIGNATURE":                 true,
	"DELTA_SYNC":                     true,
	"PRIORITY_QUEUE":                 true,
	"ANTI_REPLAY":                    true,
	"PHASE1_ARCHITECTURE_V2_ENABLED": true,
	"TICKET_IDENTITY_V2_ENABLED":     true,
	"CLOCK_AUTHORITY_SCORE_ENABLED":  true,
	"OFFLINE_ENGINE_ENABLED":         true,
	"OFFLINE_TICKET_SALES_ENABLED":   true,
	"SYNC_ENGINE_V2_ENABLED":         true,
	"OFFLINE_SIGNATURE_ENABLED":      true,
	"SYNC_MANAGER_V2_ENABLED":        true,
	"DELTA_SYNC_ENABLED":             true,
	"BATCH_SYNC_ENABLED":             true,
	"CONFLICT_ENGINE_ENABLED":        true,
	"ADAPTIVE_QUEUE_ENABLED":         true,
	"RUST_SYNC_VALIDATION_ENABLED":   true,
	"KEY_ROTATION_ENABLED":           true,
	"RUST_SECURITY_HARDENING_ENABLED": true,
	"DEVICE_INTEGRITY_CHECK_ENABLED":  true,
	"SECURITY_AUDIT_ENABLED":          true,
	"DISASTER_RECOVERY_ENABLED":       true,
	"ADVANCED_ACCESS_CONTROL_ENABLED": true,
	"CENTRAL_LOGGING_ENABLED":         true,
	"MONITORING_DASHBOARD_ENABLED":    true,
	"ALERT_ENGINE_ENABLED":            true,
	"DISTRIBUTED_TRACE_ENABLED":       true,
	"CICD_VALIDATION_ENABLED":         true,
	"AUTO_BACKUP_ENABLED":             true,
}

// IsEnabled checks if a specific feature flag is active
func IsEnabled(flagKey string) bool {
	upperKey := strings.ToUpper(flagKey)
	defaultValue, exists := DefaultFlags[upperKey]
	if !exists {
		return false
	}

	envVal := os.Getenv("FLAG_" + upperKey)
	if envVal != "" {
		return strings.ToLower(envVal) == "true" || envVal == "1" || strings.ToLower(envVal) == "yes"
	}

	return defaultValue
}
