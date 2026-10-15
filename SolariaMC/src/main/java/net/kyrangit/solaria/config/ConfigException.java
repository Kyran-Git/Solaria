package net.kyrangit.solaria.config;

import net.kyrangit.solaria.logging.ReasonCode;

/** Messages must be authored by the parser, never copied from config values or I/O exceptions. */
public final class ConfigException extends Exception {
	public ConfigException(String message) {
		super(message);
	}

	public ReasonCode reasonCode() {
		return ReasonCode.INPUT_INVALID;
	}
}