package net.kyrangit.solaria.config;

import java.net.URI;

/**
 * Immutable validated configuration snapshot.
 *
 * <p>{@code maxExtentBlocks} is the maximum bounding-box edge length for any blueprint.
 * A shape whose edge length exceeds this is rejected with CAP_EXCEEDED (blueprint_v2).
 * Default is 1536, matching the dimension height.
 */
public record SolariaConfig(
	URI serviceUrl,
	int connectTimeoutMs,
	int requestTimeoutMs,
	int teacherPermissionLevel,
	boolean allowManualLabelOnLowConfidence,
	int blocksPerTick,
	AnchorOffset buildAnchorOffset,
	int maxExtentBlocks,
	double blocksPerUnit,
	boolean scanFromFileEnabled
) {
	public record AnchorOffset(int x, int y, int z) {
	}

	public static SolariaConfig defaults() {
		return new SolariaConfig(URI.create("http://127.0.0.1:8000"), 3000, 30000, 2,
			true, 256, new AnchorOffset(0, 0, 5), 1536, 1.0, false);
	}
}
