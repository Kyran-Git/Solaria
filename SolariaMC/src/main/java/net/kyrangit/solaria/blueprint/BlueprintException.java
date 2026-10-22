package net.kyrangit.solaria.blueprint;

import net.kyrangit.solaria.logging.ReasonCode;

/**
 * Thrown when a blueprint cannot be produced. Carries a stable Appendix A.5
 * reason code so callers never invent ad-hoc error strings.
 */
public final class BlueprintException extends RuntimeException {
	private final ReasonCode reasonCode;

	public BlueprintException(ReasonCode reasonCode, String message) {
		super(message);
		this.reasonCode = reasonCode;
	}

	public ReasonCode reasonCode() {
		return reasonCode;
	}
}
