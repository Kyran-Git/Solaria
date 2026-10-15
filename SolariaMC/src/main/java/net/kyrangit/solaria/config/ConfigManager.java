package net.kyrangit.solaria.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Reload publishes a complete snapshot only after validation; invalid files are never overwritten. */
public final class ConfigManager {
	private static final int MAX_CONFIG_BYTES = 65536;
	private final Path path;
	private volatile SolariaConfig current = SolariaConfig.defaults();
	private volatile ConfigException lastError;

	public ConfigManager(Path path) {
		this.path = path.toAbsolutePath();
	}

	public SolariaConfig current() {
		return current;
	}

	public ConfigException lastError() {
		return lastError;
	}

	public synchronized ReloadResult reload() {
		try {
			if (Files.notExists(path)) {
				Files.createDirectories(path.getParent());
				Files.writeString(path, ConfigCodec.encode(SolariaConfig.defaults()), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE_NEW);
			}
			byte[] bytes;
			try (var input = Files.newInputStream(path)) {
				bytes = input.readNBytes(MAX_CONFIG_BYTES + 1);
			}
			if (bytes.length > MAX_CONFIG_BYTES) {
				throw new ConfigException("Reduce solariamod.json to at most 64 KiB.");
			}
			SolariaConfig next = ConfigCodec.parse(new String(bytes, StandardCharsets.UTF_8));
			current = next;
			lastError = null;
			return new ReloadResult(true, null);
		} catch (ConfigException e) {
			lastError = e;
		} catch (IOException | SecurityException e) {
			lastError = new ConfigException("Cannot read or create config/solariamod.json. Check file access and permissions, then reload.");
		}
		return new ReloadResult(false, lastError);
	}

	public record ReloadResult(boolean success, ConfigException error) {
	}
}