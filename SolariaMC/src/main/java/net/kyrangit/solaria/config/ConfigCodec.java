package net.kyrangit.solaria.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.Set;

/** Strict types and bounded resource limits; never echo untrusted JSON in diagnostics. */
public final class ConfigCodec {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Set<String> FIELDS = Set.of("service_url", "connect_timeout_ms", "request_timeout_ms",
		"teacher_permission_level", "allow_manual_label_on_low_confidence", "blocks_per_tick",
		"build_anchor_offset", "max_extent_blocks", "blocks_per_unit", "scan_from_file_enabled");

	private ConfigCodec() {
	}

	public static SolariaConfig parse(String json) throws ConfigException {
		JsonObject object;
		try (JsonReader reader = new JsonReader(new StringReader(json))) {
			reader.setStrictness(Strictness.STRICT);
			// Read members explicitly so duplicate settings cannot silently replace earlier values.
			object = readObject(reader);
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw new ConfigException("Use exactly one JSON object in solariamod.json.");
			}
		} catch (IOException | RuntimeException e) {
			throw new ConfigException("Fix the JSON syntax in solariamod.json; comments and trailing commas are not allowed.");
		}
		checkFields(object, FIELDS);
		SolariaConfig defaults = SolariaConfig.defaults();
		URI url = serviceUrl(object, defaults.serviceUrl());
		int connect = integer(object, "connect_timeout_ms", defaults.connectTimeoutMs(), 1, 300000);
		int request = integer(object, "request_timeout_ms", defaults.requestTimeoutMs(), 1, 300000);
		if (request < connect) {
			throw new ConfigException("request_timeout_ms must be at least connect_timeout_ms.");
		}
		SolariaConfig.AnchorOffset anchor = defaults.buildAnchorOffset();
		if (object.has("build_anchor_offset")) {
			JsonElement element = object.get("build_anchor_offset");
			if (!element.isJsonObject()) {
				throw new ConfigException("build_anchor_offset must be an object with integer x, y, z fields.");
			}
			JsonObject offset = element.getAsJsonObject();
			checkFields(offset, Set.of("x", "y", "z"));
			anchor = new SolariaConfig.AnchorOffset(integer(offset, "x", anchor.x(), -1024, 1024),
				integer(offset, "y", anchor.y(), -1024, 1024), integer(offset, "z", anchor.z(), -1024, 1024));
		}
		return new SolariaConfig(url, connect, request,
			integer(object, "teacher_permission_level", defaults.teacherPermissionLevel(), 0, 4),
			bool(object, "allow_manual_label_on_low_confidence", defaults.allowManualLabelOnLowConfidence()),
			integer(object, "blocks_per_tick", defaults.blocksPerTick(), 1, 4096), anchor,
			integer(object, "max_extent_blocks", defaults.maxExtentBlocks(), 1, 1000000),
			number(object, "blocks_per_unit", defaults.blocksPerUnit(), 0.01, 64),
			bool(object, "scan_from_file_enabled", defaults.scanFromFileEnabled()));
	}

	private static JsonObject readObject(JsonReader reader) throws IOException, ConfigException {
		JsonObject object = new JsonObject();
		reader.beginObject();
		while (reader.hasNext()) {
			String key = reader.nextName();
			if (object.has(key)) {
				throw new ConfigException("Remove duplicate settings from solariamod.json.");
			}
			object.add(key, key.equals("build_anchor_offset") && reader.peek() == JsonToken.BEGIN_OBJECT
				? readObject(reader) : JsonParser.parseReader(reader));
		}
		reader.endObject();
		return object;
	}

	private static void checkFields(JsonObject object, Set<String> allowed) throws ConfigException {
		if (!allowed.containsAll(object.keySet())) {
			throw new ConfigException("Remove unknown settings from solariamod.json; see docs/CONFIGURATION.md for supported fields.");
		}
	}

	private static URI serviceUrl(JsonObject object, URI fallback) throws ConfigException {
		if (!object.has("service_url")) {
			return fallback;
		}
		JsonElement value = object.get("service_url");
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
			try {
				URI uri = URI.create(value.getAsString());
				// Literal loopback only: no DNS resolution or accidental external service calls.
				if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
					&& ("127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost()))
					&& uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
					&& (uri.getPort() == -1 || uri.getPort() >= 1 && uri.getPort() <= 65535)
					&& (uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
					return uri;
				}
			} catch (IllegalArgumentException ignored) {
				// Do not expose the URI or parser exception (could contain credentials or names).
			}
		}
		throw new ConfigException("service_url must be an HTTP(S) loopback base URL (127.0.0.1 or [::1]), with a valid port and no credentials, path, query, or fragment.");
	}

	private static int integer(JsonObject object, String key, int fallback, int min, int max) throws ConfigException {
		if (!object.has(key)) {
			return fallback;
		}
		JsonElement value = object.get(key);
		try {
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				int result = value.getAsBigDecimal().intValueExact();
				if (result >= min && result <= max) {
					return result;
				}
			}
		} catch (ArithmeticException | NumberFormatException ignored) {
		}
		throw new ConfigException(key + " must be an integer from " + min + " to " + max + ".");
	}

	private static double number(JsonObject object, String key, double fallback, double min, double max) throws ConfigException {
		if (!object.has(key)) {
			return fallback;
		}
		JsonElement value = object.get(key);
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			double result = value.getAsDouble();
			if (Double.isFinite(result) && result >= min && result <= max) {
				return result;
			}
		}
		throw new ConfigException(key + " must be a finite number from " + min + " to " + max + ".");
	}

	private static boolean bool(JsonObject object, String key, boolean fallback) throws ConfigException {
		if (!object.has(key)) {
			return fallback;
		}
		JsonElement value = object.get(key);
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
			return value.getAsBoolean();
		}
		throw new ConfigException(key + " must be true or false (not a string).");
	}

	public static String encode(SolariaConfig config) {
		JsonObject object = new JsonObject();
		object.addProperty("service_url", config.serviceUrl().toString());
		object.addProperty("connect_timeout_ms", config.connectTimeoutMs());
		object.addProperty("request_timeout_ms", config.requestTimeoutMs());
		object.addProperty("teacher_permission_level", config.teacherPermissionLevel());
		object.addProperty("allow_manual_label_on_low_confidence", config.allowManualLabelOnLowConfidence());
		object.addProperty("blocks_per_tick", config.blocksPerTick());
		JsonObject anchor = new JsonObject();
		anchor.addProperty("x", config.buildAnchorOffset().x());
		anchor.addProperty("y", config.buildAnchorOffset().y());
		anchor.addProperty("z", config.buildAnchorOffset().z());
		object.add("build_anchor_offset", anchor);
		object.addProperty("max_extent_blocks", config.maxExtentBlocks());
		object.addProperty("blocks_per_unit", config.blocksPerUnit());
		object.addProperty("scan_from_file_enabled", config.scanFromFileEnabled());
		return GSON.toJson(object) + "\n";
	}
}