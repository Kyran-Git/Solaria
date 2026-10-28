package net.kyrangit.solaria;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.dimension.DimensionType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Phase 2 regression check for the dimension data files.
 *
 * <p>This exists because the first dimension-type file shipped with the
 * pre-26 schema ({@code effects}, {@code natural}, {@code piglin_safe},
 * {@code bed_works}, {@code has_raids}, {@code ultrawarm}, flat
 * {@code fixed_time}). Minecraft 26.x rejects that at registry load with
 * {@code No key has_ender_dragon_fight}, which aborts world creation and
 * world loading for every player. Tests that only read JSON text cannot
 * catch that, so this check runs Minecraft's own
 * {@link DimensionType#DIRECT_CODEC} over the real files.
 *
 * <p>The check deliberately does not spin up a world: it decodes each
 * dimension type on its own, then cross-checks the dimension files against
 * them. That is exactly the decode that failed at runtime.
 */
public final class DimensionDataTests {

	/** Dimension-type keys that 26.x removed; their presence means an old file. */
	private static final List<String> LEGACY_KEYS = List.of(
		"effects", "natural", "ultrawarm", "piglin_safe", "bed_works",
		"respawn_anchor_works", "has_raids", "fixed_time"
	);

	private static int assertions;
	private static Path dataRoot;

	public static void main(String[] args) throws IOException {
		dataRoot = Path.of(System.getProperty("solariamod.dataDir", "src/main/resources/data/solariamod"));

		// DimensionType's codec touches the attribute and timeline registries
		// during class initialisation, so bootstrap before decoding.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		List<Path> typeFiles = jsonFiles(dataRoot.resolve("dimension_type"));
		List<Path> dimensionFiles = jsonFiles(dataRoot.resolve("dimension"));

		check(!typeFiles.isEmpty(), "found at least one dimension_type file");
		check(!dimensionFiles.isEmpty(), "found at least one dimension file");

		Set<String> declaredTypes = new LinkedHashSet<>();
		for (Path file : typeFiles) {
			declaredTypes.add(strippedName(file));
			checkDimensionType(file);
		}
		for (Path file : dimensionFiles) {
			checkDimension(file, declaredTypes);
		}

		System.out.println("Dimension data check (synthetic, real Minecraft codec): "
			+ assertions + " assertions passed.");
	}

	// ------------------------------------------------------------------ types

	private static void checkDimensionType(Path file) throws IOException {
		String name = file.getFileName().toString();
		JsonObject json = readObject(file);

		// Real decode first: Minecraft's own codec is the authority. On failure it
		// reports exactly what the client logs, e.g. "No key has_ender_dragon_fight".
		DataResult<DimensionType> result = DimensionType.DIRECT_CODEC.parse(JsonOps.INSTANCE, json);
		if (result.error().isPresent()) {
			throw new AssertionError(name + ": 26.x DimensionType codec rejected the file -> "
				+ result.error().get().message());
		}
		assertions++;
		DimensionType type = result.result().orElseThrow();

		// Then call out schema drift explicitly, so a legacy file gets a clearer
		// message than the codec's "No key ..." when both apply.
		for (String legacy : LEGACY_KEYS) {
			check(!json.has(legacy),
				name + ": legacy pre-26 key '" + legacy + "' must be removed");
		}

		// The exact field whose absence crashed registry load.
		check(json.has("has_ender_dragon_fight"),
			name + ": declares has_ender_dragon_fight (26.x required field)");

		check(type.height() == 512 && type.minY() == -256, name + ": centred 512-block vertical range");
		for (var cls : net.kyrangit.solaria.blueprint.AstronomyClass.values()) {
			var blueprint = net.kyrangit.solaria.blueprint.BlueprintGenerator.generate(cls, 1.0);
			check(blueprint.diameter() / 2 < type.minY() + type.height()
				&& -blueprint.diameter() / 2 >= type.minY(), name + ": default body fits: " + cls.id());
		}
		check(type.height() % 16 == 0, name + ": height is a multiple of 16");
		check(type.minY() % 16 == 0, name + ": min_y is a multiple of 16");
		check(type.logicalHeight() <= type.height(),
			name + ": logical_height does not exceed height");
		check(type.minY() + type.height() <= 2032,
			name + ": min_y + height stays inside the world ceiling");
	}

	// ------------------------------------------------------------- dimensions

	private static void checkDimension(Path file, Set<String> declaredTypes) throws IOException {
		String name = file.getFileName().toString();
		JsonObject json = readObject(file);

		check(json.has("type"), name + ": declares a type");
		check(json.has("generator"), name + ": declares a generator");

		String typeId = json.get("type").getAsString();
		String localType = typeId.contains(":") ? typeId.substring(typeId.indexOf(':') + 1) : typeId;
		check(declaredTypes.contains(localType),
			name + ": type '" + typeId + "' resolves to a bundled dimension_type file");

		JsonObject generator = json.getAsJsonObject("generator");
		check(generator != null && generator.has("type"), name + ": generator declares a type");
		check(generator.get("type").getAsString().equals("minecraft:flat"),
			name + ": uses the flat generator (no worldgen cost in a build dimension)");

		JsonObject settings = generator.getAsJsonObject("settings");
		check(settings != null, name + ": flat generator declares settings");
		check(settings.has("biome"), name + ": declares a biome");
		check(settings.getAsJsonArray("layers").isEmpty(),
			name + ": flat layers are empty so the build dimension stays void");
	}

	// ------------------------------------------------------------------ utils

	private static JsonObject readObject(Path file) throws IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8);
		JsonElement element = JsonParser.parseString(text);
		check(element.isJsonObject(), file.getFileName() + ": parses as a JSON object");
		return element.getAsJsonObject();
	}

	private static List<Path> jsonFiles(Path dir) throws IOException {
		check(Files.isDirectory(dir), "data directory exists: " + dir);
		try (Stream<Path> stream = Files.list(dir)) {
			return stream
				.filter(p -> p.getFileName().toString().endsWith(".json"))
				.sorted()
				.collect(Collectors.toCollection(ArrayList::new));
		}
	}

	private static String strippedName(Path file) {
		String n = file.getFileName().toString();
		return n.substring(0, n.length() - ".json".length());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Dimension data check failed: " + message);
		}
		assertions++;
	}
}
