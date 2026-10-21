package net.kyrangit.solaria.logging;

/** Stable Appendix A.5 codes used by the foundation. */
public enum ReasonCode {
	INPUT_INVALID,
	PERMISSION_DENIED,
	// Raised by the blueprint generators when a structure cannot be produced
	// within the configured size cap, even after scaling down (M2).
	CAP_EXCEEDED,
	// Raised when the service reports a class that has no blueprint (M3 health check).
	BLUEPRINT_MISSING
}