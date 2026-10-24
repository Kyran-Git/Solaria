package net.kyrangit.solaria;

import net.kyrangit.solaria.config.ConfigCodec;
import net.kyrangit.solaria.config.ConfigException;
import net.kyrangit.solaria.config.ConfigManager;
import net.kyrangit.solaria.config.SolariaConfig;
import net.kyrangit.solaria.logging.PrivacyLog;
import net.kyrangit.solaria.logging.ReasonCode;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Dependency-free unit tests. Plumbing check (synthetic), not classroom evidence. */
public final class FoundationTests {
	private static int assertions;

	public static void main(String[] args) throws Exception {
		SolariaConfig defaults = SolariaConfig.defaults();
		check(ConfigCodec.parse("{}").equals(defaults), "Missing fields use defaults");
		check(ConfigCodec.parse(ConfigCodec.encode(defaults)).equals(defaults), "Defaults round trip");
		check(defaults.teacherPermissionLevel() == 2 && defaults.allowManualLabelOnLowConfidence()
			&& !defaults.scanFromFileEnabled(), "Required safe defaults");
		SolariaConfig custom = ConfigCodec.parse("""
			{"service_url":"https://[::1]:8443/", "connect_timeout_ms":100,
			 "request_timeout_ms":200, "teacher_permission_level":4,
			 "allow_manual_label_on_low_confidence":false, "blocks_per_tick":64,
			 "build_anchor_offset":{"x":-2,"y":3,"z":8}, "max_extent_blocks":4096,
			 "blocks_per_unit":0.5,"scan_from_file_enabled":true}
			""");
		check(custom.connectTimeoutMs() == 100 && custom.requestTimeoutMs() == 200
			&& custom.teacherPermissionLevel() == 4 && !custom.allowManualLabelOnLowConfidence()
			&& custom.blocksPerTick() == 64 && custom.maxExtentBlocks() == 4096
			&& custom.blocksPerUnit() == 0.5 && custom.scanFromFileEnabled()
			&& custom.buildAnchorOffset().equals(new SolariaConfig.AnchorOffset(-2, 3, 8)), "All custom fields parsed");
		check(ConfigCodec.parse(ConfigCodec.encode(custom)).equals(custom), "Custom round trip");
		check(ConfigCodec.parse("{\"build_anchor_offset\":{\"x\":7}}").buildAnchorOffset()
			.equals(new SolariaConfig.AnchorOffset(7, 0, 5)), "Partial anchor defaults");

		for (String json : new String[]{"", "null", "[]", "true", "{", "{} {}", "{\"blocks_per_tick\":1,}",
			"{/* comment */}", "{unquoted:1}", "{\"teacher_permission_level\":1,\"teacher_permission_level\":2}",
			"{\"build_anchor_offset\":{\"x\":1,\"x\":2}}", "{\"unknown\":1}"}) {
			reject(json);
		}
		for (String field : new String[]{"connect_timeout_ms", "request_timeout_ms", "teacher_permission_level",
			"blocks_per_tick", "max_extent_blocks", "blocks_per_unit", "build_anchor_offset", "service_url",
			"allow_manual_label_on_low_confidence", "scan_from_file_enabled"}) {
			reject("{\"" + field + "\":null}");
			reject("{\"" + field + "\":[]}");
		}
		for (String field : new String[]{"connect_timeout_ms", "request_timeout_ms", "teacher_permission_level",
			"blocks_per_tick", "max_extent_blocks"}) {
			for (String invalid : new String[]{"-1", "1.5", "2147483648", "1e100", "true", "\"2\""}) {
				reject("{\"" + field + "\":" + invalid + "}");
			}
		}
		for (String json : new String[]{"{\"connect_timeout_ms\":0}", "{\"connect_timeout_ms\":300001}",
			"{\"request_timeout_ms\":300001}", "{\"request_timeout_ms\":1}",
			"{\"teacher_permission_level\":5}", "{\"blocks_per_tick\":0}", "{\"blocks_per_tick\":4097}",
			"{\"max_extent_blocks\":0}", "{\"max_extent_blocks\":1000001}", "{\"blocks_per_unit\":0}",
			"{\"blocks_per_unit\":64.1}", "{\"blocks_per_unit\":1e999}", "{\"blocks_per_unit\":NaN}",
			"{\"blocks_per_unit\":\"1\"}", "{\"scan_from_file_enabled\":\"false\"}",
			"{\"allow_manual_label_on_low_confidence\":1}", "{\"build_anchor_offset\":{\"x\":1025}}",
			"{\"build_anchor_offset\":{\"z\":-1025}}", "{\"build_anchor_offset\":{\"y\":0.5}}",
			"{\"build_anchor_offset\":{\"w\":0}}"}) {
			reject(json);
		}
		check(ConfigCodec.parse("""
			{"connect_timeout_ms":1,"request_timeout_ms":1,"teacher_permission_level":0,
			 "blocks_per_tick":1,"max_extent_blocks":1,"blocks_per_unit":0.01,
			 "build_anchor_offset":{"x":-1024,"y":1024}}
			""").blocksPerUnit() == 0.01, "Lower bounds accepted");
		check(ConfigCodec.parse("""
			{"connect_timeout_ms":300000,"request_timeout_ms":300000,"teacher_permission_level":4,
			 "blocks_per_tick":4096,"max_extent_blocks":1000000,"blocks_per_unit":64}
			""").maxExtentBlocks() == 1000000, "Upper bounds accepted");
		for (String url : new String[]{"file:///private", "http://example.org", "http://localhost", "http://127.0.0.1:0",
			"http://127.0.0.1:65536", "http://127.0.0.1/api", "http://127.0.0.1?secret=x",
			"http://127.0.0.1#private", "http://person:password@127.0.0.1", "not a url"}) {
			reject("{\"service_url\":\"" + url + "\"}");
		}

		Path directory = Files.createTempDirectory("solaria-synthetic-");
		try {
			Path path = directory.resolve("config/solariamod.json");
			ConfigManager manager = new ConfigManager(path);
			check(manager.reload().success() && Files.exists(path), "First load creates defaults");
			check(ConfigCodec.parse(Files.readString(path)).equals(defaults), "Written defaults parse");
			Files.writeString(path, ConfigCodec.encode(custom));
			check(manager.reload().success() && manager.current().equals(custom), "Valid reload publishes snapshot");
			Files.writeString(path, "{\"service_url\":\"private student secret\"}");
			ConfigManager.ReloadResult failed = manager.reload();
			check(!failed.success() && manager.current().equals(custom), "Failed reload retains snapshot");
			check(manager.lastError() == failed.error(), "Error available to status");
			check(!failed.error().getMessage().contains("private student secret"), "Error does not echo data");
			check(Files.readString(path).contains("private student secret"), "Invalid file preserved");
			ConfigManager fresh = new ConfigManager(path);
			check(!fresh.reload().success() && fresh.current().equals(defaults), "Invalid startup keeps safe defaults");
			Files.writeString(path, ConfigCodec.encode(defaults));
			check(manager.reload().success() && manager.lastError() == null, "Recovery clears error");
			Files.writeString(path, " ".repeat(65537));
			check(!manager.reload().success() && manager.current().equals(defaults), "Oversize file rejected");
			check(!new ConfigManager(directory).reload().success(), "Directory instead of file reports I/O error");
		} finally {
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			}
		}
		for (var method : PrivacyLog.class.getDeclaredMethods()) {
			if (Modifier.isPublic(method.getModifiers())) {
				check(method.getParameterCount() == 1 && method.getParameterTypes()[0] == ReasonCode.class,
					"Logging API cannot accept arbitrary private data");
			}
		}
		System.out.println("Plumbing check (synthetic): " + assertions + " assertions passed.");
	}

	private static void reject(String json) {
		try {
			ConfigCodec.parse(json);
			throw new AssertionError("Invalid config was accepted");
		} catch (ConfigException expected) {
			check(expected.reasonCode() == ReasonCode.INPUT_INVALID && !expected.getMessage().isBlank()
				&& expected.getCause() == null, "Invalid config has safe, actionable error");
		}
	}

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}