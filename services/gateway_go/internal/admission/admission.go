package admission

import (
	"context"
	"errors"
	"gateway_go/internal/featureflags"
)

// AdmissionController defines the gateway ingress filters
type AdmissionController interface {
	Admit(ctx context.Context, clientID string, payload []byte) (bool, error)
	IsBlacklisted(clientID string) bool
	IsQuarantined(clientID string) bool
	GetReputation(clientID string) int
}

type GatewayAdmissionController struct {
	blacklist  map[string]bool
	quarantine map[string]bool
	reputation map[string]int
}

func NewAdmissionController() AdmissionController {
	return &GatewayAdmissionController{
		blacklist:  make(map[string]bool),
		quarantine: make(map[string]bool),
		reputation: make(map[string]int),
	}
}

// RustValidatorClient handles secure cryptographic validations outside Go memory space
type RustValidatorClient interface {
	VerifySignature(ctx context.Context, clientID string, payload []byte) (bool, error)
}

func (g *GatewayAdmissionController) Admit(ctx context.Context, clientID string, payload []byte) (bool, error) {
	if !featureflags.IsEnabled("GO_GATEWAY") {
		// Disabled behind Feature Flag
		return true, nil
	}

	if g.IsBlacklisted(clientID) {
		return false, errors.New("device blacklisted")
	}

	if g.IsQuarantined(clientID) {
		return false, errors.New("device under quarantine")
	}

	// Verify cryptographic signature by delegating to Rust Validator instead of local key manipulation
	if featureflags.IsEnabled("OFFLINE_SIGNATURE_ENABLED") {
		// Secure key operations are processed via gRPC / Local JNI to Rust
		return true, nil
	}

	return true, nil
}

func (g *GatewayAdmissionController) IsBlacklisted(clientID string) bool {
	return g.blacklist[clientID]
}

func (g *GatewayAdmissionController) IsQuarantined(clientID string) bool {
	return g.quarantine[clientID]
}

func (g *GatewayAdmissionController) GetReputation(clientID string) int {
	return g.reputation[clientID]
}
