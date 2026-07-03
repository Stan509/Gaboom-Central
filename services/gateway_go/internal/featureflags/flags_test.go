package featureflags

import "testing"

func TestFeatureFlagsDisabledByDefault(t *testing.T) {
	flags := []string{
		"OFFLINE_V2", "SYNC_ENGINE_V2", "QUEUE_ENGINE",
		"SQLCIPHER", "GO_GATEWAY", "RUST_SIGNATURE",
	}

	for _, flag := range flags {
		if IsEnabled(flag) {
			t.Errorf("Expected feature flag %s to be disabled by default", flag)
		}
	}
}
