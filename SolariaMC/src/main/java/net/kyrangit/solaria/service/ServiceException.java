package net.kyrangit.solaria.service;

import net.kyrangit.solaria.logging.ReasonCode;

/** Sanitised failure: never retains HTTP bodies, tokens, paths or transport causes. */
public final class ServiceException extends Exception {
	private final ReasonCode reasonCode;
	private final int retryAfterMs;

	public ServiceException(ReasonCode reasonCode) {
		this(reasonCode, 0);
	}

	public ServiceException(ReasonCode reasonCode, int retryAfterMs) {
		super(reasonCode.name());
		this.reasonCode = reasonCode;
		this.retryAfterMs = retryAfterMs;
	}

	public ReasonCode reasonCode() {
		return reasonCode;
	}

	public int retryAfterMs() {
		return retryAfterMs;
	}
}