package net.kyrangit.solaria.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** No free-form messages, objects, exception causes, paths, URLs, names, or image data. */
public final class PrivacyLog {
	private static final Logger LOGGER = LoggerFactory.getLogger("solariamod");

	private PrivacyLog() {
	}

	public static void failure(ReasonCode reasonCode) {
		LOGGER.warn("reason_code={}", reasonCode.name());
	}
}