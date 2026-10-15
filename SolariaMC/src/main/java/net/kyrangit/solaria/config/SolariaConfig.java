package net.kyrangit.solaria.config;

import java.net.URI;

/** Immutable validated snapshot. maxSizeBlocks is the maximum occupied voxel count. */
public record SolariaConfig(
	URI serviceUrl,
	int connectTimeoutMs,
	int requestTimeoutMs,
	int teacherPermissionLevel,
	boolean allowManualLabelOnLowConfidence,
	int blocksPerTick,
	AnchorOffset buildAnchorOffset,
	int maxSizeBlocks,
	double blocksPerUnit,
	boolean scanFromFileEnabled
) {
	public record AnchorOffset(int x, int y, int z) {
	}

	public static SolariaConfig defaults() {
		return new SolariaConfig(URI.create("http://127.0.0.1:8000"), 3000, 30000, 2,
			true, 256, new AnchorOffset(0, 0, 5), 32768, 1.0, false);
	}
}