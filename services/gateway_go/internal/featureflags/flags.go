package featureflags

import (
	"os"
	"strings"
)

// DefaultFlags map representing the state of features
var DefaultFlags = map[string]bool{
	"OFFLINE_V2":                     false,
	"SYNC_ENGINE_V2":                 false,
	"QUEUE_ENGINE":                   false,
	"LOTTERY_CLOCK":                  false,
	"SQLCIPHER":                      false,
	"GO_GATEWAY":                     false,
	"RUST_SIGNATURE":                 false,
	"DELTA_SYNC":                     false,
	"PRIORITY_QUEUE":                 false,
	"ANTI_REPLAY":                    false,
	"PHASE1_ARCHITECTURE_V2_ENABLED": false,
	"TICKET_IDENTITY_V2_ENABLED":     false,
	"CLOCK_AUTHORITY_SCORE_ENABLED":  false,
	"OFFLINE_ENGINE_ENABLED":         false,
	"OFFLINE_TICKET_SALES_ENABLED":   false,
	"SYNC_ENGINE_V2_ENABLED":         false,
	"OFFLINE_SIGNATURE_ENABLED":      false,
	"SYNC_MANAGER_V2_ENABLED":        false,
	"DELTA_SYNC_ENABLED":             false,
	"BATCH_SYNC_ENABLED":             false,
	"CONFLICT_ENGINE_ENABLED":        false,
	"ADAPTIVE_QUEUE_ENABLED":         false,
	"RUST_SYNC_VALIDATION_ENABLED":   false,
	"KEY_ROTATION_ENABLED":           false,
	"RUST_SECURITY_HARDENING_ENABLED": false,
	"DEVICE_INTEGRITY_CHECK_ENABLED":  false,
	"SECURITY_AUDIT_ENABLED":          false,
	"DISASTER_RECOVERY_ENABLED":       false,
	"ADVANCED_ACCESS_CONTROL_ENABLED": false,
	"CENTRAL_LOGGING_ENABLED":         false,
	"MONITORING_DASHBOARD_ENABLED":    false,
	"ALERT_ENGINE_ENABLED":            false,
	"DISTRIBUTED_TRACE_ENABLED":       false,
	"CICD_VALIDATION_ENABLED":         false,
	"AUTO_BACKUP_ENABLED":             false,
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
