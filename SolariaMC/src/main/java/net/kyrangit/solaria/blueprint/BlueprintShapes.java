package net.kyrangit.solaria.blueprint;

/**
 * Per-class voxel shape generators used by {@link BlueprintGenerator}.
 *
 * <p>Every method returns a {@link Blueprint} whose {@link Blueprint#voxelAt} is a
 * <em>pure function</em>: the block at (x, y, z) depends only on the class seed,
 * the diameter, and the coordinates. No mutable state, no stored grid.
 *
 * <p>Coordinates passed to {@code voxelAt} are relative to the planet centre.
 * Points outside the shape return 0 (air).
 *
 * <p>Determinism rules (see {@link BlueprintGenerator} for the full contract):
 * <ul>
 *   <li>No {@link java.util.Random} — all randomness via {@link VoxelNoise}.</li>
 *   <li>Same seed + same (x,y,z) = same result on every machine.</li>
 *   <li>The normalised position (nx,ny,nz) = (x,y,z) / radius is passed to noise
 *       so features scale with the planet, not with block counts.</li>
 * </ul>
 */
final class BlueprintShapes {
	private BlueprintShapes() {
	}

	// ------------------------------------------------------------------ helpers

	/** True when (x,y,z) is inside a sphere of the given radius. */
	private static boolean inSphere(int x, int y, int z, double radius) {
		return (double) x * x + (double) y * y + (double) z * z <= radius * radius;
	}

	/** Scatter crater positions from a seed; returns the centre of crater {@code i}. */
	private static double[] craterCentre(int i, int seed, double radius) {
		double[] dir = VoxelNoise.unitVector(seed ^ (i * 0x9E3779B1));
		return new double[]{dir[0] * radius, dir[1] * radius, dir[2] * radius};
	}

	/**
	 * Returns the crater-rim palette index if (x,y,z) falls on a crater rim,
	 * or 0 if it falls inside a crater pit (air), or -1 if unaffected.
	 * craterRadius is expressed as a fraction of the body radius.
	 */
	private static int craterEffect(int x, int y, int z, int count, int seed,
	                                double bodyRadius, double craterFrac, int rimPalette) {
		double cr = bodyRadius * craterFrac;
		double rimWidth = Math.max(1.0, cr * 0.3);
		for (int i = 0; i < count; i++) {
			double[] c = craterCentre(i, seed ^ 0xC4A7, bodyRadius > 0 ? (int) bodyRadius : 1);
			double dx = x - c[0];
			double dy = y - c[1];
			double dz = z - c[2];
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist <= cr) {
				return 0; // air — pit
			}
			if (dist <= cr + rimWidth) {
				return rimPalette; // raised rim
			}
		}
		return -1; // unaffected
	}

	// ================================================================== SUN

	/**
	 * Sun: glowing gold sphere with granulation noise on the outer shell.
	 * Three concentric colour zones: core (glowstone), shell (gold), corona (yellow/ochre).
	 */
	static Blueprint sun(MaterialPalette palette, int diameter, double scale, int seed) {
		int core    = palette.indexOf(Palette.GLOWSTONE);
		int surface = palette.indexOf(Palette.GOLD_BLOCK);
		int corona  = palette.indexOf(Palette.YELLOW_CONCRETE);
		int gran1   = palette.indexOf(Palette.SHROOMLIGHT);
		int gran2   = palette.indexOf(Palette.OCHRE_FROGLIGHT);
		int honey   = palette.indexOf(Palette.HONEYCOMB_BLOCK);
		double radius = diameter / 2.0;
		return new Blueprint(AstronomyClass.SUN,
			(x, y, z) -> {
				double r2 = (double) x * x + (double) y * y + (double) z * z;
				double dist = Math.sqrt(r2);
				if (dist > radius) return 0;
				double norm = dist / radius;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				if (norm < 0.35) return core;
				if (norm < 0.78) return surface;
				// Outer shell: granulation noise
				double g = VoxelNoise.noise3(nx * 6.0, ny * 6.0, nz * 6.0, seed);
				if (norm > 0.92) {
					// Corona fringe
					return g > 0.55 ? honey : corona;
				}
				if (g > 0.68) return gran1;
				if (g > 0.54) return gran2;
				return surface;
			},
			palette, scale, diameter);
	}

	// ================================================================== EARTH

	/**
	 * Earth: blue oceans, green/brown land, white polar caps, and thin cloud layer.
	 */
	static Blueprint earth(MaterialPalette palette, int diameter, double scale, int seed) {
		int ocean    = palette.indexOf(Palette.BLUE_CONCRETE);
		int deep     = palette.indexOf(Palette.DARK_PRISMARINE);
		int shallow  = palette.indexOf(Palette.PRISMARINE);
		int land     = palette.indexOf(Palette.GREEN_CONCRETE);
		int forest   = palette.indexOf(Palette.GREEN_TERRACOTTA);
		int grass    = palette.indexOf(Palette.LIME_CONCRETE);
		int dry      = palette.indexOf(Palette.LIME_TERRACOTTA);
		int desert   = palette.indexOf(Palette.TERRACOTTA);
		int ice      = palette.indexOf(Palette.SNOW_BLOCK);
		int glacier  = palette.indexOf(Palette.BLUE_ICE);
		int cloud    = palette.indexOf(Palette.WHITE_CONCRETE);
		double radius = diameter / 2.0;
		return new Blueprint(AstronomyClass.EARTH,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (dist > radius) return 0;
				double norm = dist / radius;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				// Polar ice caps: |ny| > 0.82
				double absy = Math.abs(ny);
				if (absy > 0.82) return (norm > 0.94 && absy > 0.9) ? glacier : ice;
				// Thin cloud shell (outermost 4%)
				if (norm > 0.96) {
					double c = VoxelNoise.noise3(nx * 4.5, ny * 4.5, nz * 4.5, seed ^ 0xCCDD);
					return c > 0.58 ? cloud : ocean;
				}
				// Continent noise
				double continent = VoxelNoise.noise3(nx * 1.8, ny * 1.8, nz * 1.8, seed);
				if (continent > 0.54) {
					// Land biome
					double biome = VoxelNoise.noise3(nx * 3.5, ny * 3.5, nz * 3.5, seed ^ 0xAB12);
					if (biome > 0.65) return forest;
					if (biome > 0.52) return grass;
					if (biome > 0.42) return dry;
					if (biome > 0.30) return desert;
					return land;
				}
				// Ocean depth
				double depth = VoxelNoise.noise3(nx * 2.5, ny * 2.5, nz * 2.5, seed ^ 0x5F5F);
				return depth > 0.58 ? shallow : (depth < 0.35 ? deep : ocean);
			},
			palette, scale, diameter);
	}

	// ================================================================== MOON

	/**
	 * Moon: grey highlands with dark maria patches and scattered impact craters.
	 */
	static Blueprint moon(MaterialPalette palette, int diameter, double scale, int seed) {
		int surface  = palette.indexOf(Palette.LIGHT_GRAY_CONCRETE);
		int highland = palette.indexOf(Palette.ANDESITE);
		int bright   = palette.indexOf(Palette.SMOOTH_STONE);
		int dusty    = palette.indexOf(Palette.LIGHT_GRAY_TERRACOTTA);
		int mare     = palette.indexOf(Palette.SMOOTH_BASALT);
		int mareAlt  = palette.indexOf(Palette.GRAY_CONCRETE);
		int rimBlock = palette.indexOf(Palette.CALCITE);
		double radius = diameter / 2.0;
		int craterCount = 18;
		return new Blueprint(AstronomyClass.MOON,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (dist > radius) return 0;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				// Check craters first
				int ce = craterEffect(x, y, z, craterCount, seed, radius, 0.08, rimBlock);
				if (ce == 0) return 0;
				if (ce > 0) return ce;
				// Maria (dark smooth plains)
				double mare3 = VoxelNoise.noise3(nx * 1.4, ny * 1.4, nz * 1.4, seed ^ 0x1A2B);
				if (mare3 > 0.64) {
					return (VoxelNoise.noise3(nx * 3.0, ny * 3.0, nz * 3.0, seed ^ 0x3C4D) > 0.52) ? mare : mareAlt;
				}
				// Highlands texture
				double tex = VoxelNoise.noise3(nx * 5.0, ny * 5.0, nz * 5.0, seed ^ 0xF00F);
				if (tex > 0.68) return bright;
				if (tex > 0.55) return highland;
				if (tex < 0.32) return dusty;
				return surface;
			},
			palette, scale, diameter);
	}

	// ================================================================== MARS

	/**
	 * Mars: rust-red with dark volcanic regions, white polar caps, and canyon shadows.
	 */
	static Blueprint mars(MaterialPalette palette, int diameter, double scale, int seed) {
		int surface  = palette.indexOf(Palette.RED_TERRACOTTA);
		int dust     = palette.indexOf(Palette.ORANGE_TERRACOTTA);
		int rock     = palette.indexOf(Palette.RED_SANDSTONE);
		int smooth   = palette.indexOf(Palette.SMOOTH_RED_SANDSTONE);
		int highland = palette.indexOf(Palette.GRANITE);
		int dark     = palette.indexOf(Palette.NETHERRACK);
		int canyon   = palette.indexOf(Palette.RED_NETHER_BRICKS);
		int polar    = palette.indexOf(Palette.WHITE_CONCRETE);
		int polarDry = palette.indexOf(Palette.SNOW_BLOCK);
		double radius = diameter / 2.0;
		return new Blueprint(AstronomyClass.MARS,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (dist > radius) return 0;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				double absy = Math.abs(ny);
				// Polar caps
				if (absy > 0.82) return absy > 0.9 ? polarDry : polar;
				// Surface noise
				double base = VoxelNoise.noise3(nx * 2.0, ny * 2.0, nz * 2.0, seed);
				double detail = VoxelNoise.noise3(nx * 5.5, ny * 5.5, nz * 5.5, seed ^ 0x9191);
				if (base < 0.28) {
					// Deep canyon / dark volcanic lowland
					return detail > 0.55 ? canyon : dark;
				}
				if (base < 0.42) return smooth;
				if (base > 0.72) return highland;
				if (detail > 0.60) return rock;
				if (detail < 0.30) return dust;
				return surface;
			},
			palette, scale, diameter);
	}

	// ================================================================== JUPITER

	/**
	 * Jupiter: horizontal bands with an equator-pinned Great Red Spot.
	 */
	static Blueprint jupiter(MaterialPalette palette, int diameter, double scale, int seed) {
		int paleBand  = palette.indexOf(Palette.WHITE_CONCRETE);
		int lightBand = palette.indexOf(Palette.ORANGE_CONCRETE);
		int darkBand  = palette.indexOf(Palette.BROWN_CONCRETE);
		int tan       = palette.indexOf(Palette.TERRACOTTA);
		int cream     = palette.indexOf(Palette.WHITE_TERRACOTTA);
		int warm      = palette.indexOf(Palette.RAW_COPPER_BLOCK);
		int peach     = palette.indexOf(Palette.RAW_IRON_BLOCK);
		int redSpot   = palette.indexOf(Palette.RED_CONCRETE);
		double radius = diameter / 2.0;
		// Great Red Spot: deterministic position on equator
		double[] spotDir = VoxelNoise.unitVector(seed ^ 0x47A1);
		double rawSpotX = spotDir[0];
		double rawSpotZ = spotDir[2];
		double spotLen = Math.sqrt(rawSpotX * rawSpotX + rawSpotZ * rawSpotZ);
		final double spotX = spotLen > 1e-9 ? rawSpotX / spotLen : 1.0;
		final double spotZ = spotLen > 1e-9 ? rawSpotZ / spotLen : 0.0;
		double spotSize = 0.18; // angular half-width
		return new Blueprint(AstronomyClass.JUPITER,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (dist > radius) return 0;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				// Latitude-based bands (ny = sin(latitude))
				double lat = ny; // -1 to 1
				double bandNoise = VoxelNoise.noise3(nx * 0.5, ny * 8.0, nz * 0.5, seed);
				double bandPos = lat + bandNoise * 0.10;
				// Great Red Spot: oval near ny ≈ -0.25 (southern equatorial belt)
				double dotSpot = nx * spotX + nz * spotZ;
				double spotDist = Math.sqrt((dotSpot - 1.0) * (dotSpot - 1.0) * 0.5 + (ny + 0.25) * (ny + 0.25));
				if (spotDist < spotSize) return redSpot;
				// Band colouring
				int bandIdx = (int) Math.floor((bandPos + 1.0) * 9.0);
				int detail = VoxelNoise.hash(bandIdx, (int) (nx * 50), (int) (nz * 50), seed) & 0x3;
				return switch (bandIdx % 6) {
					case 0, 2 -> detail == 0 ? cream : (detail == 1 ? peach : lightBand);
					case 1, 3 -> detail == 0 ? tan   : (detail == 1 ? warm  : darkBand);
					case 4    -> detail == 0 ? cream : paleBand;
					default   -> detail == 0 ? peach : tan;
				};
			},
			palette, scale, diameter);
	}

	// ================================================================== VENUS

	/**
	 * Venus: pale gold swirling cloud deck with amber and cream tones.
	 */
	static Blueprint venus(MaterialPalette palette, int diameter, double scale, int seed) {
		int cloud1 = palette.indexOf(Palette.YELLOW_CONCRETE);
		int cloud2 = palette.indexOf(Palette.GOLD_BLOCK);
		int cloud3 = palette.indexOf(Palette.RAW_GOLD_BLOCK);
		int amber  = palette.indexOf(Palette.HONEYCOMB_BLOCK);
		int cream  = palette.indexOf(Palette.END_STONE_BRICKS);
		int pale   = palette.indexOf(Palette.OCHRE_FROGLIGHT);
		int base   = palette.indexOf(Palette.SMOOTH_SANDSTONE);
		double radius = diameter / 2.0;
		return new Blueprint(AstronomyClass.VENUS,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (dist > radius) return 0;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				// Swirling cloud noise at two frequencies
				double swirl1 = VoxelNoise.noise3(nx * 3.0 + ny * 1.5, ny * 3.0, nz * 3.0 + nx, seed);
				double swirl2 = VoxelNoise.noise3(nx * 6.0, ny * 6.0 + nz, nz * 5.0, seed ^ 0xBEEF);
				double combined = swirl1 * 0.6 + swirl2 * 0.4;
				if (combined > 0.72) return pale;
				if (combined > 0.62) return cream;
				if (combined > 0.52) return cloud1;
				if (combined > 0.42) return amber;
				if (combined > 0.32) return cloud3;
				if (combined > 0.20) return cloud2;
				return base;
			},
			palette, scale, diameter);
	}

	// ================================================================== SATURN

	/**
	 * Saturn: pale gold banded body with a flat equatorial quartz-block ring.
	 */
	static Blueprint saturn(MaterialPalette palette, int diameter, double scale, int seed) {
		int body     = palette.indexOf(Palette.YELLOW_TERRACOTTA);
		int paleBand = palette.indexOf(Palette.WHITE_TERRACOTTA);
		int goldBand = palette.indexOf(Palette.SMOOTH_SANDSTONE);
		int creamBand = palette.indexOf(Palette.END_STONE_BRICKS);
		int ringMain = palette.indexOf(Palette.QUARTZ_BLOCK);
		int ringBrt  = palette.indexOf(Palette.SMOOTH_QUARTZ);
		int ringEdge = palette.indexOf(Palette.LIGHT_GRAY_CONCRETE);
		int ringGap  = palette.indexOf(Palette.GRAY_TERRACOTTA);
		double bodyRadius = diameter / 2.0;
		double ringInner  = bodyRadius * 1.25;
		double ringOuter  = bodyRadius * 1.9;
		return new Blueprint(AstronomyClass.SATURN,
			(x, y, z) -> {
				double dist2 = (double) x * x + (double) y * y + (double) z * z;
				double dist = Math.sqrt(dist2);
				if (inSphere(x, y, z, bodyRadius)) {
					// Body bands keyed by latitude
					double ny = y / bodyRadius;
					double bandNoise = VoxelNoise.noise3(nx(x, bodyRadius) * 0.8, ny * 7.0,
						nz(z, bodyRadius) * 0.8, seed);
					double bpos = ny + bandNoise * 0.08;
					int bi = (int) Math.floor((bpos + 1.0) * 8.0);
					return switch (bi % 4) {
						case 0 -> paleBand;
						case 1 -> body;
						case 2 -> goldBand;
						default -> creamBand;
					};
				}
				// Ring: flat annulus in the y ≈ 0 plane (|y| < 0.5)
				if (Math.abs(y) < 0.6 && dist > bodyRadius) {
					double planar = Math.sqrt((double) x * x + (double) z * z);
					if (planar >= ringInner && planar <= ringOuter) {
						// Cassini-like gap at ~1.55× body radius
						if (planar > bodyRadius * 1.52 && planar < bodyRadius * 1.58) return ringGap;
						boolean edge = planar < ringInner + 1.2 || planar > ringOuter - 1.2;
						if (edge) return ringEdge;
						double rn = VoxelNoise.noise3(x * 0.05, 0, z * 0.05, seed ^ 0x5A5A);
						return rn > 0.55 ? ringBrt : ringMain;
					}
				}
				return 0;
			},
			palette, scale, diameter);
	}

	// ================================================================== NEPTUNE

	/**
	 * Neptune: deep blue with latitudinal streaks and a dark storm spot.
	 */
	static Blueprint neptune(MaterialPalette palette, int diameter, double scale, int seed) {
		int bodyDeep   = palette.indexOf(Palette.LAPIS_BLOCK);
		int bodyMain   = palette.indexOf(Palette.BLUE_CONCRETE);
		int bodyLight  = palette.indexOf(Palette.CYAN_CONCRETE);
		int streak     = palette.indexOf(Palette.LIGHT_BLUE_CONCRETE);
		int haze       = palette.indexOf(Palette.LIGHT_BLUE_TERRACOTTA);
		int storm      = palette.indexOf(Palette.RED_CONCRETE);
		int cloudTop   = palette.indexOf(Palette.WHITE_CONCRETE);
		double radius = diameter / 2.0;
		double[] stormDir = VoxelNoise.unitVector(seed ^ 0x9E31);
		double stormX = stormDir[0], stormY = stormDir[1], stormZ = stormDir[2];
		return new Blueprint(AstronomyClass.NEPTUNE,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (dist > radius) return 0;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				// Dark spot
				double ds = nx * stormX + ny * stormY + nz * stormZ;
				if (ds > 0.90) return storm;
				// Cloud top band at the outer 5%
				double norm = dist / radius;
				double cn = VoxelNoise.noise3(nx * 5.0, ny * 5.0, nz * 5.0, seed ^ 0xAE00);
				if (norm > 0.95 && cn > 0.60) return cloudTop;
				// Latitudinal streaks
				double streakN = VoxelNoise.noise3(nx * 1.0, ny * 9.0, nz * 1.0, seed ^ 0x1234);
				if (streakN > 0.68) return streak;
				if (streakN > 0.58) return haze;
				// Depth noise
				double depth = VoxelNoise.noise3(nx * 2.5, ny * 2.5, nz * 2.5, seed ^ 0x7777);
				if (depth > 0.60) return bodyLight;
				if (depth < 0.32) return bodyDeep;
				return bodyMain;
			},
			palette, scale, diameter);
	}

	// ================================================================== URANUS

	/**
	 * Uranus: pale cyan with subtle shading and a near-vertical quartz ring.
	 */
	static Blueprint uranus(MaterialPalette palette, int diameter, double scale, int seed) {
		int body     = palette.indexOf(Palette.CYAN_CONCRETE);
		int pale     = palette.indexOf(Palette.PACKED_ICE);
		int teal     = palette.indexOf(Palette.OXIDIZED_COPPER);
		int shading  = palette.indexOf(Palette.CYAN_TERRACOTTA);
		int ringBlk  = palette.indexOf(Palette.QUARTZ_BLOCK);
		double bodyRadius = diameter / 2.0;
		double ringInner  = bodyRadius * 1.2;
		double ringOuter  = bodyRadius * 1.6;
		return new Blueprint(AstronomyClass.URANUS,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (inSphere(x, y, z, bodyRadius)) {
					double nx = x / bodyRadius, ny = y / bodyRadius, nz = z / bodyRadius;
					double tint = VoxelNoise.noise3(nx * 2.0, ny * 2.0, nz * 2.0, seed);
					if (tint > 0.70) return shading;
					if (tint > 0.58) return teal;
					if (tint < 0.28) return pale;
					return body;
				}
				// Near-vertical ring: thin in X, extends in Y and Z
				if (Math.abs(x) < 0.6) {
					double planar = Math.sqrt((double) y * y + (double) z * z);
					if (planar >= ringInner && planar <= ringOuter) return ringBlk;
				}
				return 0;
			},
			palette, scale, diameter);
	}

	// ================================================================== MERCURY

	/**
	 * Mercury: heavily cratered grey silicate world with dark volcanic lowlands.
	 * No atmosphere — the most cratered planet in the inner solar system.
	 */
	static Blueprint mercury(MaterialPalette palette, int diameter, double scale, int seed) {
		int surface  = palette.indexOf(Palette.STONE);
		int dusty    = palette.indexOf(Palette.LIGHT_GRAY_TERRACOTTA);
		int dark     = palette.indexOf(Palette.BROWN_CONCRETE);
		int darkCr   = palette.indexOf(Palette.DEEPSLATE);
		int rimBlock = palette.indexOf(Palette.LIGHT_GRAY_CONCRETE);
		double radius = diameter / 2.0;
		int craterCount = 24;
		return new Blueprint(AstronomyClass.MERCURY,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (dist > radius) return 0;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				// Craters
				int ce = craterEffect(x, y, z, craterCount, seed, radius, 0.06, rimBlock);
				if (ce == 0) return 0;
				if (ce > 0) return ce;
				// Dark volcanic lowlands
				double base = VoxelNoise.noise3(nx * 2.6, ny * 2.6, nz * 2.6, seed);
				if (base > 0.65) return darkCr;
				if (base > 0.52) return dark;
				double tex = VoxelNoise.noise3(nx * 6.0, ny * 6.0, nz * 6.0, seed ^ 0x4E4E);
				return tex > 0.55 ? dusty : surface;
			},
			palette, scale, diameter);
	}

	// ------------------------------------------------------------------ private coord helpers

	private static double nx(int x, double r) { return x / r; }
	private static double nz(int z, double r) { return z / r; }
}
