package net.kyrangit.solaria.dimension;

import net.kyrangit.solaria.SolariaMod;
import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * ResourceKey constants for the ten Solaria planet dimensions.
 *
 * <p>Each class in {@link AstronomyClass} gets its own dedicated dimension
 * {@code solaria:<class-id>} (e.g. {@code solaria:earth}, {@code solaria:mercury}).
 * The dimension data files are at
 * {@code data/solariamod/dimension/<class-id>.json}.
 *
 * <p>Dimensions are registered via data-pack JSON; no additional code
 * registration is required for the server to load them. The keys here are
 * used by teleport commands and status reporting only.
 */
public final class SolariaDimensions {

	/** Map from AstronomyClass to its dimension Level key. Immutable. */
	private static final Map<AstronomyClass, ResourceKey<Level>> KEYS;

	static {
		Map<AstronomyClass, ResourceKey<Level>> map = new EnumMap<>(AstronomyClass.class);
		for (AstronomyClass cls : AstronomyClass.values()) {
			map.put(cls, ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, SolariaMod.id(cls.id())));
		}
		KEYS = Collections.unmodifiableMap(map);
	}

	private SolariaDimensions() {
	}

	/**
	 * Returns the {@link ResourceKey} for the dimension belonging to {@code cls}.
	 * The key is {@code solaria:<cls.id()>}.
	 */
	public static ResourceKey<Level> keyFor(AstronomyClass cls) {
		return KEYS.get(cls);
	}

	/**
	 * Resolves the {@link AstronomyClass} whose dimension id matches {@code dimensionId},
	 * or {@code null} if it is not a Solaria planet dimension.
	 *
	 * <p>The id must be the full namespace-qualified string, e.g. {@code "solariamod:earth"}.
	 */
	public static AstronomyClass classFromDimensionId(String dimensionId) {
		for (Map.Entry<AstronomyClass, ResourceKey<Level>> entry : KEYS.entrySet()) {
			if (entry.getValue().identifier().toString().equals(dimensionId)) {
				return entry.getKey();
			}
		}
		return null;
	}
}
