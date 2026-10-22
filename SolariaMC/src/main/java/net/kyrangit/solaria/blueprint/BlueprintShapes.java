package net.kyrangit.solaria.blueprint;

/**
 * Per-class voxel shape builders used by {@link BlueprintGenerator}.
 *
 * <p>Kept as a separate file so {@link BlueprintGenerator} stays readable and so this
 * geometry can grow (more features per class) without that file growing with it.
 * Everything here is deterministic and iteration-order safe; see
 * {@link BlueprintGenerator} for the full contract.
 */
final class BlueprintShapes {
	private BlueprintShapes() {
	}

	// --------------------------------------------------------------------- Sphere helpers

	/**
	 * Fill a solid sphere centred in a cubic grid, using a per-voxel colour chooser.
	 * The chooser receives the voxel coordinates and the normalised unit vector from
	 * the centre, and must return a palette index &gt; 0.
	 */
	static VoxelGrid solidSphere(int diameter, VoxelColourChooser chooser) {
		VoxelGrid grid = new VoxelGrid(diameter, diameter, diameter);
		double radius = diameter / 2.0;
		double centre = (diameter - 1) / 2.0;
		for (int x = 0; x < diameter; x++) {
			for (int y = 0; y < diameter; y++) {
				for (int z = 0; z < diameter; z++) {
					double dx = x - centre;
					double dy = y - centre;
					double dz = z - centre;
					double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (distance <= radius) {
						double nx = dx / radius;
						double ny = dy / radius;
						double nz = dz / radius;
						grid.set(x, y, z, chooser.choose(x, y, z, nx, ny, nz, distance / radius));
					}
				}
			}
		}
		return grid;
	}

	/** Carve a shallow spherical pit at a surface point. Used for craters on Moon and Mercury. */
	static void carveCrater(VoxelGrid grid, double cx, double cy, double cz, double craterRadius) {
		int xMin = Math.max(0, (int) Math.floor(cx - craterRadius));
		int yMin = Math.max(0, (int) Math.floor(cy - craterRadius));
		int zMin = Math.max(0, (int) Math.floor(cz - craterRadius));
		int xMax = Math.min(grid.sizeX() - 1, (int) Math.ceil(cx + craterRadius));
		int yMax = Math.min(grid.sizeY() - 1, (int) Math.ceil(cy + craterRadius));
		int zMax = Math.min(grid.sizeZ() - 1, (int) Math.ceil(cz + craterRadius));
		double radiusSq = craterRadius * craterRadius;
		for (int x = xMin; x <= xMax; x++) {
			for (int y = yMin; y <= yMax; y++) {
				for (int z = zMin; z <= zMax; z++) {
					double dx = x - cx;
					double dy = y - cy;
					double dz = z - cz;
					if (dx * dx + dy * dy + dz * dz <= radiusSq) {
						grid.set(x, y, z, 0);
					}
				}
			}
		}
	}

	/** Mark voxels within an annular shell on the surface. Used for crater rims. */
	static void ringCrater(VoxelGrid grid, double cx, double cy, double cz,
	                       double innerRadius, double outerRadius, int paletteIndex) {
		int xMin = Math.max(0, (int) Math.floor(cx - outerRadius));
		int yMin = Math.max(0, (int) Math.floor(cy - outerRadius));
		int zMin = Math.max(0, (int) Math.floor(cz - outerRadius));
		int xMax = Math.min(grid.sizeX() - 1, (int) Math.ceil(cx + outerRadius));
		int yMax = Math.min(grid.sizeY() - 1, (int) Math.ceil(cy + outerRadius));
		int zMax = Math.min(grid.sizeZ() - 1, (int) Math.ceil(cz + outerRadius));
		double innerSq = innerRadius * innerRadius;
		double outerSq = outerRadius * outerRadius;
		for (int x = xMin; x <= xMax; x++) {
			for (int y = yMin; y <= yMax; y++) {
				for (int z = zMin; z <= zMax; z++) {
					if (grid.get(x, y, z) == 0) {
						continue;
					}
					double dx = x - cx;
					double dy = y - cy;
					double dz = z - cz;
					double distanceSq = dx * dx + dy * dy + dz * dz;
					if (distanceSq >= innerSq && distanceSq <= outerSq) {
						grid.set(x, y, z, paletteIndex);
					}
				}
			}
		}
	}

	/** Scatter {@code count} craters at deterministic surface points. */
	static void scatterCraters(VoxelGrid grid, int diameter, int seed, int count, int rimPaletteIndex) {
		double bodyRadius = diameter / 2.0;
		double centre = (diameter - 1) / 2.0;
		double craterRadius = Math.max(1.3, bodyRadius * 0.22);
		for (int i = 0; i < count; i++) {
			double[] direction = VoxelNoise.unitVector(seed + i * 0x9E37);
			double cx = centre + direction[0] * (bodyRadius - 0.5);
			double cy = centre + direction[1] * (bodyRadius - 0.5);
			double cz = centre + direction[2] * (bodyRadius - 0.5);
			ringCrater(grid, cx, cy, cz, craterRadius * 0.6, craterRadius, rimPaletteIndex);
			carveCrater(grid, cx, cy, cz, craterRadius * 0.55);
		}
	}

	@FunctionalInterface
	interface VoxelColourChooser {
		int choose(int x, int y, int z, double nx, double ny, double nz, double normalisedDistance);
	}

	// --------------------------------------------------------------------- Per-class generators

	static Blueprint sun(MaterialPalette palette, int diameter, double scale, int seed) {
		int glow = palette.indexOf(Palette.GLOWSTONE);
		int gold = palette.indexOf(Palette.GOLD_BLOCK);
		int yellow = palette.indexOf(Palette.YELLOW_CONCRETE);
		int orange = palette.indexOf(Palette.ORANGE_CONCRETE);
		VoxelGrid grid = solidSphere(diameter, (x, y, z, nx, ny, nz, normalised) -> {
			if (normalised < 0.35) {
				return glow;
			}
			if (normalised < 0.75) {
				return gold;
			}
			double noise = VoxelNoise.noise3(nx * 2.0, ny * 2.0, nz * 2.0, seed);
			return noise > 0.55 ? yellow : orange;
		});
		return new Blueprint(AstronomyClass.SUN, grid, palette, scale, diameter);
	}

	static Blueprint earth(MaterialPalette palette, int diameter, double scale, int seed) {
		int ocean = palette.indexOf(Palette.BLUE_CONCRETE);
		int land = palette.indexOf(Palette.GREEN_CONCRETE);
		int cloud = palette.indexOf(Palette.WHITE_CONCRETE);
		int ice = palette.indexOf(Palette.SNOW_BLOCK);
		int core = palette.indexOf(Palette.IRON_BLOCK);
		VoxelGrid grid = solidSphere(diameter, (x, y, z, nx, ny, nz, normalised) -> {
			if (normalised < 0.25) {
				return core;
			}
			// Polar caps
			if (Math.abs(ny) > 0.85) {
				return ice;
			}
			double continents = VoxelNoise.noise3(nx * 2.1, ny * 2.1, nz * 2.1, seed);
			double clouds = VoxelNoise.noise3(nx * 3.3 + 11.0, ny * 3.3, nz * 3.3, seed ^ 0x5A5A);
			if (normalised > 0.9 && clouds > 0.72) {
				return cloud;
			}
			return continents > 0.55 ? land : ocean;
		});
		return new Blueprint(AstronomyClass.EARTH, grid, palette, scale, diameter);
	}

	static Blueprint moon(MaterialPalette palette, int diameter, double scale, int seed) {
		int light = palette.indexOf(Palette.LIGHT_GRAY_CONCRETE);
		int dark = palette.indexOf(Palette.GRAY_CONCRETE);
		int core = palette.indexOf(Palette.STONE);
		VoxelGrid grid = solidSphere(diameter, (x, y, z, nx, ny, nz, normalised) -> {
			if (normalised < 0.3) {
				return core;
			}
			// Maria: darker patches of basaltic plain
			double maria = VoxelNoise.noise3(nx * 1.8, ny * 1.8, nz * 1.8, seed);
			return maria > 0.6 ? dark : light;
		});
		scatterCraters(grid, diameter, seed, 5, dark);
		return new Blueprint(AstronomyClass.MOON, grid, palette, scale, diameter);
	}

	static Blueprint mercury(MaterialPalette palette, int diameter, double scale, int seed) {
		int surface = palette.indexOf(Palette.STONE);
		int patch = palette.indexOf(Palette.BROWN_CONCRETE);
		int highlight = palette.indexOf(Palette.QUARTZ_BLOCK);
		VoxelGrid grid = solidSphere(diameter, (x, y, z, nx, ny, nz, normalised) -> {
			double n = VoxelNoise.noise3(nx * 2.4, ny * 2.4, nz * 2.4, seed);
			return n > 0.65 ? patch : surface;
		});
		// Mercury is heavily cratered; a touch more than the Moon relative to its size.
		scatterCraters(grid, diameter, seed, 6, highlight);
		return new Blueprint(AstronomyClass.MERCURY, grid, palette, scale, diameter);
	}

	static Blueprint mars(MaterialPalette palette, int diameter, double scale, int seed) {
		int surface = palette.indexOf(Palette.RED_TERRACOTTA);
		int dark = palette.indexOf(Palette.BROWN_CONCRETE);
		int polar = palette.indexOf(Palette.WHITE_CONCRETE);
		int core = palette.indexOf(Palette.IRON_BLOCK);
		VoxelGrid grid = solidSphere(diameter, (x, y, z, nx, ny, nz, normalised) -> {
			if (normalised < 0.3) {
				return core;
			}
			if (Math.abs(ny) > 0.82) {
				return polar;
			}
			double patch = VoxelNoise.noise3(nx * 2.6, ny * 2.6, nz * 2.6, seed);
			return patch > 0.6 ? dark : surface;
		});
		return new Blueprint(AstronomyClass.MARS, grid, palette, scale, diameter);
	}

	static Blueprint venus(MaterialPalette palette, int diameter, double scale, int seed) {
		int cloud = palette.indexOf(Palette.YELLOW_CONCRETE);
		int pale = palette.indexOf(Palette.WHITE_CONCRETE);
		int core = palette.indexOf(Palette.GOLD_BLOCK);
		VoxelGrid grid = solidSphere(diameter, (x, y, z, nx, ny, nz, normalised) -> {
			if (normalised < 0.3) {
				return core;
			}
			double swirl = VoxelNoise.noise3(nx * 2.0 + ny * 1.2, ny * 1.8, nz * 2.0, seed);
			return swirl > 0.55 ? pale : cloud;
		});
		return new Blueprint(AstronomyClass.VENUS, grid, palette, scale, diameter);
	}

	static Blueprint jupiter(MaterialPalette palette, int diameter, double scale, int seed) {
		int lightBand = palette.indexOf(Palette.ORANGE_CONCRETE);
		int darkBand = palette.indexOf(Palette.BROWN_CONCRETE);
		int creamBand = palette.indexOf(Palette.WHITE_CONCRETE);
		int redSpot = palette.indexOf(Palette.RED_CONCRETE);
		int core = palette.indexOf(Palette.DIAMOND_BLOCK);
		// Great Red Spot: deterministic unit vector pinned near the equator.
		double[] spotDir = VoxelNoise.unitVector(seed ^ 0x6705);
		double spotLat = Math.max(-0.3, Math.min(0.3, spotDir[1]));
		double planarLen = Math.sqrt(spotDir[0] * spotDir[0] + spotDir[2] * spotDir[2]);
		double equatorialScale = Math.sqrt(1 - spotLat * spotLat) / Math.max(1e-9, planarLen);
		double sx = spotDir[0] * equatorialScale;
		double sz = spotDir[2] * equatorialScale;
		VoxelGrid grid = solidSphere(diameter, (x, y, z, nx, ny, nz, normalised) -> {
			if (normalised < 0.3) {
				return core;
			}
			double dot = nx * sx + ny * spotLat + nz * sz;
			if (normalised > 0.85 && dot > 0.9) {
				return redSpot;
			}
			int bandIndex = (int) Math.floor(ny * 4.5 + 4.5);
			int bandHash = VoxelNoise.hash(bandIndex, 0, 0, seed) & 0x3;
			return switch (bandHash) {
				case 0 -> creamBand;
				case 1 -> darkBand;
				default -> lightBand;
			};
		});
		return new Blueprint(AstronomyClass.JUPITER, grid, palette, scale, diameter);
	}

	static Blueprint neptune(MaterialPalette palette, int diameter, double scale, int seed) {
		int body = palette.indexOf(Palette.BLUE_CONCRETE);
		int streak = palette.indexOf(Palette.LIGHT_BLUE_CONCRETE);
		int storm = palette.indexOf(Palette.RED_CONCRETE);
		int core = palette.indexOf(Palette.DIAMOND_BLOCK);
		double[] stormDir = VoxelNoise.unitVector(seed ^ 0x4201);
		VoxelGrid grid = solidSphere(diameter, (x, y, z, nx, ny, nz, normalised) -> {
			if (normalised < 0.3) {
				return core;
			}
			double dot = nx * stormDir[0] + ny * stormDir[1] + nz * stormDir[2];
			if (normalised > 0.85 && dot > 0.92) {
				return storm;
			}
			double streakNoise = VoxelNoise.noise3(nx * 1.0, ny * 3.5, nz * 1.0, seed);
			return streakNoise > 0.72 ? streak : body;
		});
		return new Blueprint(AstronomyClass.NEPTUNE, grid, palette, scale, diameter);
	}

	static Blueprint saturn(MaterialPalette palette, int diameter, double scale, int seed) {
		int lightBand = palette.indexOf(Palette.ORANGE_CONCRETE);
		int paleBand = palette.indexOf(Palette.WHITE_CONCRETE);
		int goldBand = palette.indexOf(Palette.GOLD_BLOCK);
		int core = palette.indexOf(Palette.DIAMOND_BLOCK);
		int ringMain = palette.indexOf(Palette.QUARTZ_BLOCK);
		int ringEdge = palette.indexOf(Palette.LIGHT_GRAY_CONCRETE);

		// Flat equatorial ring, outer radius ~1.9 x body radius.
		// The ring stays inside the grid bounds and counts against the size cap (plan M2.3).
		double bodyRadius = diameter / 2.0;
		double ringOuter = bodyRadius * 1.9;
		double ringInner = bodyRadius * 1.25;
		int gridSize = (int) Math.ceil(ringOuter * 2.0) + 1;
		if ((gridSize & 1) == 0) {
			gridSize++;
		}
		VoxelGrid grid = new VoxelGrid(gridSize, gridSize, gridSize);
		double centre = (gridSize - 1) / 2.0;

		for (int x = 0; x < gridSize; x++) {
			for (int y = 0; y < gridSize; y++) {
				for (int z = 0; z < gridSize; z++) {
					double dx = x - centre;
					double dy = y - centre;
					double dz = z - centre;
					double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (distance <= bodyRadius) {
						if (distance < bodyRadius * 0.3) {
							grid.set(x, y, z, core);
							continue;
						}
						double ny = dy / bodyRadius;
						int bandIndex = (int) Math.floor(ny * 4.5 + 4.5);
						int bandHash = VoxelNoise.hash(bandIndex, 0, 0, seed) & 0x3;
						int bandBlock = switch (bandHash) {
							case 0 -> paleBand;
							case 1 -> goldBand;
							default -> lightBand;
						};
						grid.set(x, y, z, bandBlock);
						continue;
					}
					// Ring: flat annulus in the Y = centre plane, one block tall.
					if (Math.abs(dy) < 0.6) {
						double planar = Math.sqrt(dx * dx + dz * dz);
						if (planar >= ringInner && planar <= ringOuter) {
							boolean edge = planar < ringInner + 1.1 || planar > ringOuter - 1.1;
							grid.set(x, y, z, edge ? ringEdge : ringMain);
						}
					}
				}
			}
		}
		return new Blueprint(AstronomyClass.SATURN, grid, palette, scale, diameter);
	}

	static Blueprint uranus(MaterialPalette palette, int diameter, double scale, int seed) {
		int body = palette.indexOf(Palette.LIGHT_BLUE_CONCRETE);
		int shading = palette.indexOf(Palette.GRAY_CONCRETE);
		int core = palette.indexOf(Palette.DIAMOND_BLOCK);
		int ringBlock = palette.indexOf(Palette.QUARTZ_BLOCK);

		// Uranus spins on its side, so its ring is near-vertical (plan M2.3).
		double bodyRadius = diameter / 2.0;
		double ringOuter = bodyRadius * 1.6;
		double ringInner = bodyRadius * 1.2;
		int gridSize = (int) Math.ceil(ringOuter * 2.0) + 1;
		if ((gridSize & 1) == 0) {
			gridSize++;
		}
		VoxelGrid grid = new VoxelGrid(gridSize, gridSize, gridSize);
		double centre = (gridSize - 1) / 2.0;

		for (int x = 0; x < gridSize; x++) {
			for (int y = 0; y < gridSize; y++) {
				for (int z = 0; z < gridSize; z++) {
					double dx = x - centre;
					double dy = y - centre;
					double dz = z - centre;
					double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (distance <= bodyRadius) {
						if (distance < bodyRadius * 0.3) {
							grid.set(x, y, z, core);
							continue;
						}
						double ny = dy / bodyRadius;
						double nx = dx / bodyRadius;
						double nz = dz / bodyRadius;
						double tint = VoxelNoise.noise3(nx * 1.4, ny * 1.4, nz * 1.4, seed);
						grid.set(x, y, z, tint > 0.7 ? shading : body);
						continue;
					}
					// Near-vertical ring: thin in X, extends in Y and Z.
					if (Math.abs(dx) < 0.6) {
						double planar = Math.sqrt(dy * dy + dz * dz);
						if (planar >= ringInner && planar <= ringOuter) {
							grid.set(x, y, z, ringBlock);
						}
					}
				}
			}
		}
		return new Blueprint(AstronomyClass.URANUS, grid, palette, scale, diameter);
	}
}
