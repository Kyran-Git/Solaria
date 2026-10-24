package net.kyrangit.solaria.logging;

/** Stable Appendix A.5 codes used by the foundation. */
public enum ReasonCode {
	INPUT_INVALID,
	PERMISSION_DENIED,
	// Raised by the blueprint generators when a structure cannot be produced
	// within the configured size cap, even after scaling down (M2).
	CAP_EXCEEDED,
	// Raised when the service reports a class that has no blueprint (M3 health check).
	BLUEPRINT_MISSING,
	// Phase 4 scanner: webcam capture is not available on this installation
	// (no capture backend bundled with the mod yet). The scanner screen states this.
	CAPTURE_UNAVAILABLE,
	// Phase 4 scanner: the chunked upload path to the LAN host is not wired yet,
	// so a captured frame cannot be relayed. Used to prevent silent failures.
	SCAN_UPLOAD_UNAVAILABLE,
	// Phase 4 scanner: the AI service was not reachable
	// or returned a transport-level failure. Student-facing message is generic.
	CLASSIFIER_UNAVAILABLE
}
