package net.kyrangit.solaria.service;

import net.kyrangit.solaria.logging.ReasonCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Host-only credential loading. Never creates credentials or discovers them over HTTP. */
public final class HostServiceToken {
	private HostServiceToken() {}

	public static String load(Map<String, String> environment) throws ServiceException {
		String explicit = environment.get("SOLARIA_STARTUP_TOKEN");
		if (explicit != null && !explicit.isEmpty()) return validate(explicit);
		String filename = environment.get("SOLARIA_STARTUP_TOKEN_FILE");
		if (filename == null || filename.isBlank()) throw missing();
		try (var input = Files.newInputStream(Path.of(filename))) {
			byte[] bytes = input.readNBytes(1025);
			if (bytes.length > 1024) throw missing();
			return validate(new String(bytes, StandardCharsets.US_ASCII).strip());
		} catch (IOException | RuntimeException e) {
			// Do not disclose file paths, contents or filesystem errors to students/logs.
			throw missing();
		}
	}

	private static String validate(String token) throws ServiceException {
		if (token.isBlank() || token.length() > 256 || !token.equals(token.strip())
			|| token.chars().anyMatch(c -> c < 32 || c > 126)) throw missing();
		return token;
	}

	private static ServiceException missing() {
		return new ServiceException(ReasonCode.SERVICE_TOKEN_MISSING);
	}
}
