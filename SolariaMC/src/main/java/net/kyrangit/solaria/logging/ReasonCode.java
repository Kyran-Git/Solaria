package net.kyrangit.solaria.logging;

/** Stable Appendix A.5 codes used by the foundation. */
public enum ReasonCode {
	INPUT_INVALID,
	PERMISSION_DENIED,
	// Raised by the blueprint generators when a structure cannot be produced
	// within the configured size cap. No automatic shrinking (blueprint v2).
	CAP_EXCEEDED,
	// Raised when the service reports a class that has no blueprint (M3 health check).
	BLUEPRINT_MISSING,
	// Webcam missing, unsupported, busy, disconnected, or otherwise unavailable.
	CAPTURE_UNAVAILABLE,
	// Phase 4 scanner: the chunked upload path to the LAN host is not wired yet,
	// so a captured frame cannot be relayed. Used to prevent silent failures.
	SCAN_UPLOAD_UNAVAILABLE,
	// Phase 4 scanner: the AI service was not reachable
	// or returned a transport-level failure. Student-facing message is generic.
	CLASSIFIER_UNAVAILABLE,
	SERVICE_UNAVAILABLE,
	SERVICE_TOKEN_MISSING,
	SERVICE_PROTOCOL_ERROR,
	CLASS_SET_MISMATCH,
	UNAUTHORIZED,
	NOT_FOUND,
	INPUT_TOO_LARGE,
	LABEL_NOT_IN_CLASSES,
	QUEUE_FULL,
	MODEL_UNAVAILABLE,
	SCAN_TIMEOUT,
	INTERNAL_ERROR,
	REVIEW_CONFLICT,
	QUALITY_REJECTED
}
