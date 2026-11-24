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

	/**
	 * Quarter-sphere cutaway wedge: the +X/+Z quadrant exposes the planet's interior.
	 * Keeps the origin voxel and the x=0 and z=0 planes solid, so centre-solid and
	 * ring-survival invariants are preserved.
	 */
	private static boolean cutaway(int x, int z) {
		return x > 0 && z > 0;
	}

	/** Keep at least the outer two-voxel crust available for surface detail at tiny test scales. */
	private static double interiorLimit(double radius) {
		return Math.min(0.88, Math.max(0.0, (radius - 2.0) / radius));
	}

	private record TangentFeature(
		double cx, double cy, double cz,
		double t1x, double t1y, double t1z,
		double t2x, double t2y, double t2z,
		double radiusA, double radiusB, int seed
	) {
		static TangentFeature of(double x, double y, double z, double radiusA, double radiusB, int seed) {
			double len = Math.sqrt(x * x + y * y + z * z);
			double cx = x / len, cy = y / len, cz = z / len;
			double hLen = Math.hypot(cx, cz);
			double t1x = hLen > 1e-6 ? -cz / hLen : 1.0;
			double t1y = 0.0;
			double t1z = hLen > 1e-6 ? cx / hLen : 0.0;
			double t2x = cy * t1z - cz * t1y;
			double t2y = cz * t1x - cx * t1z;
			double t2z = cx * t1y - cy * t1x;
			return new TangentFeature(cx, cy, cz, t1x, t1y, t1z, t2x, t2y, t2z, radiusA, radiusB, seed);
		}
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
	 * Sun: convective granulation, active sunspot belts with surrounding faculae, and bright limb.
	 */
	static Blueprint sun(MaterialPalette palette, int diameter, double scale, int seed) {
		// NASA Solar Physics (https://science.nasa.gov/sun/facts/): ~1,000 km convective granulation
		// cells, dark magnetic sunspots in active belts (~5-40 deg N/S), and bright surrounding faculae.
		int core    = palette.indexOf(Palette.GLOWSTONE);
		int radA    = palette.indexOf(Palette.GOLD_BLOCK);
		int radB    = palette.indexOf(Palette.OCHRE_FROGLIGHT);
		int convA   = palette.indexOf(Palette.SHROOMLIGHT);
		int convB   = palette.indexOf(Palette.ORANGE_CONCRETE);
		MaterialRamp plasmaRamp = MaterialRamp.of(palette,
			Palette.RED_CONCRETE,
			Palette.REDSTONE_BLOCK,
			Palette.ORANGE_CONCRETE,
			Palette.SHROOMLIGHT,
			Palette.HONEYCOMB_BLOCK,
			Palette.RAW_GOLD_BLOCK,
			Palette.YELLOW_CONCRETE,
			Palette.GOLD_BLOCK,
			Palette.OCHRE_FROGLIGHT,
			Palette.GLOWSTONE);
		MaterialRamp spotRamp = MaterialRamp.of(palette,
			Palette.COAL_BLOCK,
			Palette.BLACKSTONE,
			Palette.RED_NETHER_BRICKS,
			Palette.NETHERRACK,
			Palette.RED_CONCRETE);

		TangentFeature[] sunspots = new TangentFeature[10];
		// Pin two prominent active groups onto the +X arrival face and distribute the rest in ±5..40 deg belts.
		sunspots[0] = TangentFeature.of(0.90, 0.25, -0.35, 0.062, 0.048, seed ^ 0x5900);
		sunspots[1] = TangentFeature.of(0.88, -0.28, 0.38, 0.056, 0.044, seed ^ 0x5901);
		for (int i = 2; i < sunspots.length; i++) {
			int fs = seed ^ (i * 0x9E3779B1);
			double[] v = VoxelNoise.unitVector(fs);
			double cy = Math.copySign(0.10 + Math.abs(v[1]) * 0.48, v[1]);
			double horiz = Math.sqrt(Math.max(1e-6, 1.0 - cy * cy));
			double oldHoriz = Math.hypot(v[0], v[2]);
			double cx = oldHoriz > 1e-6 ? v[0] / oldHoriz * horiz : horiz;
			double cz = oldHoriz > 1e-6 ? v[2] / oldHoriz * horiz : 0.0;
			double rA = 0.032 + 0.030 * VoxelNoise.hashUnit(i, 1, 0, seed ^ 0x5907);
			double rB = rA * (0.75 + 0.30 * VoxelNoise.hashUnit(i, 2, 0, seed ^ 0x5907));
			sunspots[i] = TangentFeature.of(cx, cy, cz, rA, rB, fs ^ 0x7105);
		}

		double radius = diameter / 2.0;
		double innerLimit = interiorLimit(radius);
		return new Blueprint(AstronomyClass.SUN,
			(x, y, z) -> {
				double distSq = (double) x * x + (double) y * y + (double) z * z;
				if (distSq > radius * radius) return 0;
				double dist = Math.sqrt(distSq);
				double norm = dist / radius;
				double inv = dist > 0.0 ? 1.0 / dist : 0.0;
				double ux = x * inv;
				double uy = dist > 0.0 ? y * inv : 1.0;
				double uz = z * inv;

				// Interior stellar structure (kept strictly inside outer photosphere shell so no cutaway seam marks the surface).
				if (norm < innerLimit) {
					if (norm < 0.28) return core;
					if (cutaway(x, z)) {
						double tex = SurfacePainter.sphereFbm(ux, uy, uz, 4.0, 2, seed ^ 0x501A);
						if (norm < 0.65) return tex > 0.55 ? radB : radA;
						return tex > 0.50 ? convA : convB;
					}
					if (norm < 0.65) return radA;
					return convA;
				}

				double macro = SurfacePainter.warpedSphereFbm(ux, uy, uz, 3.8, 3, 2.4, 0.38, seed);
				double gran = SurfacePainter.sphereFbm(ux, uy, uz, 16.0, 3, 0.55, 2.1, seed ^ 0x64A4);
				double cells = SurfacePainter.smoothstep(0.24, 0.78, gran);

				double temp = 0.10 + 0.44 * macro + 0.26 * cells;
				if (Math.abs(uy) < 0.65) {
					double plage = SurfacePainter.warpedSphereFbm(ux, uy, uz, 5.0, 2, 2.8, 0.42, seed ^ 0xAC71);
					if (plage > 0.54) {
						temp += (plage - 0.54) * 0.90;
					}
				}
				if (norm > 0.965) {
					temp += 0.14 * cells;
				}

				double faculaeBoost = 0.0;
				TangentFeature hitSpot = null;
				double hitRNorm = Double.MAX_VALUE;
				for (TangentFeature f : sunspots) {
					double dot = ux * f.cx + uy * f.cy + uz * f.cz;
					if (dot <= 0.94) continue;
					double dx = ux - f.cx;
					double dy = uy - f.cy;
					double dz = uz - f.cz;
					double du = dx * f.t1x + dy * f.t1y + dz * f.t1z;
					double dv = dx * f.t2x + dy * f.t2y + dz * f.t2z;
					double rNorm = Math.hypot(du / f.radiusA, dv / f.radiusB)
						+ (macro - 0.5) * 0.65 + (gran - 0.5) * 0.45;
					if (rNorm < 1.15 && rNorm < hitRNorm) {
						hitRNorm = rNorm;
						hitSpot = f;
					} else if (rNorm >= 1.0 && rNorm < 1.85) {
						double boost = 0.24 * (1.0 - (rNorm - 1.0) / 0.85);
						if (boost > faculaeBoost) faculaeBoost = boost;
					}
				}

				int plasmaMat = plasmaRamp.sampleDithered(temp + faculaeBoost, 0.45, x, y, z, seed ^ 0x5011);
				if (hitSpot != null) {
					int spotMat = spotRamp.sampleDithered(Math.max(0.0, hitRNorm), 0.40, x, y, z, hitSpot.seed);
					return SurfacePainter.blend(spotMat, plasmaMat, hitRNorm, 0.90, 0.45, x, y, z, hitSpot.seed ^ 0x44);
				}
				return plasmaMat;
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
		int inner    = palette.indexOf(Palette.GOLD_BLOCK);
		int outer    = palette.indexOf(Palette.IRON_BLOCK);
		int outerAlt = palette.indexOf(Palette.RAW_IRON_BLOCK);
		int mantle   = palette.indexOf(Palette.NETHERRACK);
		int mantleAlt= palette.indexOf(Palette.GRANITE);
		int crust    = palette.indexOf(Palette.STONE);
		double radius = diameter / 2.0;
		return new Blueprint(AstronomyClass.EARTH,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (dist > radius) return 0;
				double norm = dist / radius;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				// Quarter-sphere cutaway: layered interior (inner core → crust).
				if (cutaway(x, z)) {
					double tex = VoxelNoise.noise3(nx * 3.5, ny * 3.5, nz * 3.5, seed ^ 0xEA12);
					if (norm < 0.20) return inner;
					if (norm < 0.55) return tex > 0.50 ? outerAlt : outer;
					if (norm < 0.88) return tex > 0.55 ? mantleAlt : mantle;
					if (norm < 0.94) return crust;
					// fall through to surface shell so continents/oceans are visible at the rim
				}
				// Polar ice caps: |ny| > 0.82
				double absy = Math.abs(ny);
				if (absy > 0.82) return (norm > 0.94 && absy > 0.9) ? glacier : ice;
				// Thin cloud shell (outermost 4%) — only render clouds where noise says so; otherwise
				// fall through so continents and oceans beneath remain visible.
				if (norm > 0.96) {
					double c = VoxelNoise.noise3(nx * 4.5, ny * 4.5, nz * 4.5, seed ^ 0xCCDD);
					if (c > 0.62) return cloud;
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
	 * Moon: dark basaltic maria against bright anorthositic highlands with rayed impact craters.
	 */
	static Blueprint moon(MaterialPalette palette, int diameter, double scale, int seed) {
		// NASA LRO / Moon Facts (https://science.nasa.gov/moon/facts/): dark basaltic maria
		// (~16% globally, concentrated on near side), bright anorthositic highlands, and
		// rayed impact craters such as Tycho (43.4 deg S).
		int ironCore  = palette.indexOf(Palette.IRON_BLOCK);
		int mantle    = palette.indexOf(Palette.DEEPSLATE);
		int crustIn   = palette.indexOf(Palette.STONE);
		int craterFloor = palette.indexOf(Palette.GRAY_CONCRETE);
		int craterPeak  = palette.indexOf(Palette.CALCITE);

		MaterialRamp mareRamp = MaterialRamp.of(palette,
			Palette.BLACKSTONE,
			Palette.SMOOTH_BASALT,
			Palette.DEEPSLATE,
			Palette.GRAY_CONCRETE,
			Palette.TUFF);
		MaterialRamp highlandRamp = MaterialRamp.of(palette,
			Palette.STONE,
			Palette.ANDESITE,
			Palette.LIGHT_GRAY_CONCRETE,
			Palette.SMOOTH_STONE,
			Palette.DIORITE,
			Palette.CALCITE);
		MaterialRamp rayRamp = MaterialRamp.of(palette,
			Palette.SMOOTH_STONE,
			Palette.DIORITE,
			Palette.CALCITE,
			Palette.WHITE_CONCRETE);

		// 6 major mare basins: 4 clustered across the +X near-side hemisphere, 2 smaller on far side.
		TangentFeature[] maria = {
			TangentFeature.of(0.86, 0.36, -0.35, 0.38, 0.38, seed ^ 0x101),
			TangentFeature.of(0.92, 0.14, 0.36, 0.34, 0.34, seed ^ 0x102),
			TangentFeature.of(0.76, -0.18, 0.62, 0.30, 0.30, seed ^ 0x103),
			TangentFeature.of(0.72, 0.50, 0.10, 0.28, 0.28, seed ^ 0x104),
			TangentFeature.of(-0.82, 0.22, -0.52, 0.24, 0.24, seed ^ 0x105),
			TangentFeature.of(-0.70, -0.30, 0.64, 0.22, 0.22, seed ^ 0x106)
		};

		// 4 bright ray craters: radiusA = crater radius, radiusB = ejecta ray reach.
		TangentFeature[] rayCraters = {
			TangentFeature.of(0.70, -0.68, -0.20, 0.065, 0.58, seed ^ 0x201), // Tycho-like
			TangentFeature.of(0.94, 0.17, -0.30, 0.055, 0.48, seed ^ 0x202),  // Copernicus-like
			TangentFeature.of(-0.78, 0.35, 0.52, 0.060, 0.52, seed ^ 0x203),
			TangentFeature.of(-0.62, -0.45, -0.64, 0.052, 0.46, seed ^ 0x204)
		};

		// 28 painted background impact craters.
		TangentFeature[] smallCraters = new TangentFeature[28];
		for (int i = 0; i < smallCraters.length; i++) {
			int fs = seed ^ ((i + 1) * 0x9E3779B1);
			double[] v = VoxelNoise.unitVector(fs);
			double r = 0.042 + 0.068 * VoxelNoise.hashUnit(i, 3, 0, seed ^ 0xC4A7);
			smallCraters[i] = TangentFeature.of(v[0], v[1], v[2], r, r, fs ^ 0x7711);
		}

		double radius = diameter / 2.0;
		double innerLimit = interiorLimit(radius);
		return new Blueprint(AstronomyClass.MOON,
			(x, y, z) -> {
				double distSq = (double) x * x + (double) y * y + (double) z * z;
				if (distSq > radius * radius) return 0;
				double dist = Math.sqrt(distSq);
				double norm = dist / radius;
				double inv = dist > 0.0 ? 1.0 / dist : 0.0;
				double ux = x * inv;
				double uy = dist > 0.0 ? y * inv : 1.0;
				double uz = z * inv;

				if (norm < innerLimit) {
					if (norm < 0.25) return ironCore;
					if (norm < 0.85) return mantle;
					return crustIn;
				}

				double mareNoise = SurfacePainter.warpedSphereFbm(ux, uy, uz, 2.4, 3, 2.0, 0.38, seed ^ 0x1A2B);
				double hTex = SurfacePainter.warpedSphereFbm(ux, uy, uz, 5.5, 3, 2.5, 0.25, seed ^ 0xF00F);
				double micro = SurfacePainter.sphereFbm(ux, uy, uz, 16.0, 2, seed ^ 0x3C4D);
				double regolithField = SurfacePainter.smoothstep(0.28, 0.72, 0.60 * hTex + 0.40 * micro);

				double basinScore = 0.0;
				for (TangentFeature m : maria) {
					double dot = ux * m.cx + uy * m.cy + uz * m.cz;
					if (dot > 0.70) {
						double ang = Math.sqrt(Math.max(0.0, 2.0 * (1.0 - dot)));
						double s = SurfacePainter.smoothstep(m.radiusA * 1.25, m.radiusA * 0.20,
							ang + (mareNoise - 0.5) * 0.30);
						if (s > basinScore) basinScore = s;
					}
				}
				double mareField = SurfacePainter.clamp01(basinScore * 0.48 + mareNoise * 0.66);
				int mareMat = mareRamp.sampleDithered(regolithField, 0.40,
					x, y, z, seed ^ 0x4A2E);
				int highMat = highlandRamp.sampleDithered(regolithField, 0.45,
					x, y, z, seed ^ 0x4164);
				int mat = SurfacePainter.blend(highMat, mareMat, mareField, 0.58, 0.22, x, y, z, seed ^ 0x5402);

				for (TangentFeature sc : smallCraters) {
					double dot = ux * sc.cx + uy * sc.cy + uz * sc.cz;
					if (dot <= 0.97) continue;
					double ang = Math.sqrt(Math.max(0.0, 2.0 * (1.0 - dot)));
					double rNorm = ang / sc.radiusA;
					if (rNorm < 0.72) {
						mat = SurfacePainter.blend(mat, craterFloor, 1.0 - rNorm / 0.72, 0.40, 0.30, x, y, z, sc.seed);
					} else if (rNorm < 1.08) {
						double rimPeak = 1.0 - Math.abs(rNorm - 0.90) / 0.18;
						mat = SurfacePainter.blend(mat, craterPeak, rimPeak, 0.55, 0.35, x, y, z, sc.seed ^ 0x1B);
					}
				}

				for (int i = 0; i < rayCraters.length; i++) {
					TangentFeature rc = rayCraters[i];
					double dot = ux * rc.cx + uy * rc.cy + uz * rc.cz;
					if (dot <= 0.76) continue;
					double ang = Math.sqrt(Math.max(0.0, 2.0 * (1.0 - dot)));
					double craterR = rc.radiusA;
					double rayReach = rc.radiusB;
					if (ang >= rayReach) continue;

					if (ang < craterR * 0.70) {
						if (ang < craterR * 0.22) {
							mat = craterPeak;
						} else {
							mat = SurfacePainter.blend(mat, mareRamp.at(2), 0.80, 0.50, 0.30, x, y, z, rc.seed);
						}
						continue;
					}
					if (ang < craterR * 1.25) {
						double t = 1.0 - Math.abs(ang - craterR * 0.95) / (craterR * 0.30);
						int rim = rayRamp.sampleDithered(0.45 + 0.50 * t, 0.35, x, y, z, rc.seed);
						mat = SurfacePainter.blend(mat, rim, t, 0.45, 0.30, x, y, z, rc.seed ^ 0x2C);
						continue;
					}
					double dx = ux - rc.cx;
					double dy = uy - rc.cy;
					double dz = uz - rc.cz;
					double du = dx * rc.t1x + dy * rc.t1y + dz * rc.t1z;
					double dv = dx * rc.t2x + dy * rc.t2y + dz * rc.t2z;
					double phi = Math.atan2(dv, du);
					int rayCount = 7 + (i % 3) * 2;
					double halo = 0.72 * SurfacePainter.smoothstep(craterR * 2.8, craterR * 0.9, ang);
					double fan = Math.pow(0.5 + 0.5 * Math.sin(phi * rayCount + hTex * 2.2), 1.6)
						* (0.45 + 0.55 * Math.pow(0.5 + 0.5 * Math.cos(phi * (rayCount + 2) - micro * 1.8), 1.4))
						* SurfacePainter.smoothstep(rayReach, craterR, ang);
					double ejecta = SurfacePainter.clamp01(Math.max(halo, fan * 0.95));
					if (ejecta > 0.12) {
						int rayMat = rayRamp.sampleDithered(ejecta, 0.45, x, y, z, rc.seed ^ 0x7A);
						mat = SurfacePainter.blend(mat, rayMat, ejecta, 0.44, 0.42, x, y, z, rc.seed ^ 0x99);
					}
				}
				return mat;
			},
			palette, scale, diameter);
	}

	// ================================================================== MARS

	/**
	 * Mars: rust-red plains, dark basaltic albedo provinces, Valles Marineris, Hellas Basin, and polar caps.
	 */
	static Blueprint mars(MaterialPalette palette, int diameter, double scale, int seed) {
		// NASA Mars Facts / MRO (https://science.nasa.gov/mars/facts/): rusty iron-oxide plains,
		// dark basaltic albedo regions, Valles Marineris (~3,000-4,000 km canyon at 0-20 deg S),
		// Hellas Planitia (~2,200 km basin at ~35-42 deg S), and polar ice caps.
		int ironCore   = palette.indexOf(Palette.RAW_IRON_BLOCK);
		int mantleIn   = palette.indexOf(Palette.NETHERRACK);
		int crustIn    = palette.indexOf(Palette.STONE);
		int duneCollar = palette.indexOf(Palette.GRAY_TERRACOTTA);
		int polarEdge  = palette.indexOf(Palette.CALCITE);
		int polarMid   = palette.indexOf(Palette.WHITE_CONCRETE);
		int polarCore  = palette.indexOf(Palette.SNOW_BLOCK);

		MaterialRamp rustRamp = MaterialRamp.of(palette,
			Palette.RED_TERRACOTTA,
			Palette.GRANITE,
			Palette.RED_SANDSTONE,
			Palette.RAW_COPPER_BLOCK,
			Palette.COPPER_BLOCK,
			Palette.TERRACOTTA,
			Palette.ORANGE_TERRACOTTA,
			Palette.SMOOTH_RED_SANDSTONE,
			Palette.RAW_IRON_BLOCK);
		MaterialRamp darkRamp = MaterialRamp.of(palette,
			Palette.RED_NETHER_BRICKS,
			Palette.NETHERRACK,
			Palette.BROWN_CONCRETE,
			Palette.BROWN_TERRACOTTA,
			Palette.MUD_BRICKS,
			Palette.PACKED_MUD,
			Palette.GRAY_TERRACOTTA);
		MaterialRamp canyonRamp = MaterialRamp.of(palette,
			Palette.BLACKSTONE,
			Palette.RED_NETHER_BRICKS,
			Palette.NETHERRACK,
			Palette.BROWN_CONCRETE,
			Palette.RED_TERRACOTTA);
		MaterialRamp hellasRamp = MaterialRamp.of(palette,
			Palette.RED_TERRACOTTA,
			Palette.TERRACOTTA,
			Palette.ORANGE_TERRACOTTA,
			Palette.SMOOTH_RED_SANDSTONE,
			Palette.RAW_IRON_BLOCK);

		TangentFeature valles  = TangentFeature.of(0.95, -0.18, -0.25, 0.46, 0.055, seed ^ 0xCA47);
		TangentFeature hellas  = TangentFeature.of(-0.28, -0.62, -0.73, 0.28, 0.22, seed ^ 0x4E11);
		TangentFeature tharsis = TangentFeature.of(0.55, 0.14, 0.82, 0.36, 0.32, seed ^ 0x74A2);

		double radius = diameter / 2.0;
		double innerLimit = interiorLimit(radius);
		return new Blueprint(AstronomyClass.MARS,
			(x, y, z) -> {
				double distSq = (double) x * x + (double) y * y + (double) z * z;
				if (distSq > radius * radius) return 0;
				double dist = Math.sqrt(distSq);
				double norm = dist / radius;
				double inv = dist > 0.0 ? 1.0 / dist : 0.0;
				double ux = x * inv;
				double uy = dist > 0.0 ? y * inv : 1.0;
				double uz = z * inv;

				if (norm < innerLimit) {
					if (norm < 0.45) return ironCore;
					if (norm < 0.85) return mantleIn;
					return crustIn;
				}

				double macro = SurfacePainter.warpedSphereFbm(ux, uy, uz, 2.3, 3, 2.0, 0.42, seed);
				double detail = SurfacePainter.sphereFbm(ux, uy, uz, 7.5, 3, 0.5, 2.0, seed ^ 0x9191);
				double tharsisBoost = 0.0;
				if (ux * tharsis.cx + uy * tharsis.cy + uz * tharsis.cz > 0.82) {
					double dx = ux - tharsis.cx, dy = uy - tharsis.cy, dz = uz - tharsis.cz;
					double du = (dx * tharsis.t1x + dy * tharsis.t1y + dz * tharsis.t1z) / tharsis.radiusA;
					double dv = (dx * tharsis.t2x + dy * tharsis.t2y + dz * tharsis.t2z) / tharsis.radiusB;
					tharsisBoost = 0.20 * SurfacePainter.smoothstep(1.25, 0.25, Math.hypot(du, dv));
				}

				double albedo = SurfacePainter.smoothstep(0.24, 0.78, macro * 0.70 + detail * 0.30 + tharsisBoost);
				int rustMat = rustRamp.sampleDithered(albedo, 0.45, x, y, z, seed ^ 0x2057);
				int darkMat = darkRamp.sampleDithered(detail, 0.45, x, y, z, seed ^ 0xDA2C);
				int mat = SurfacePainter.blend(darkMat, rustMat, macro + tharsisBoost * 0.5, 0.40, 0.18,
					x, y, z, seed ^ 0xB1E4);

				// Hellas Planitia: bright dust-filled basin with a darker rim collar.
				if (ux * hellas.cx + uy * hellas.cy + uz * hellas.cz > 0.85) {
					double dx = ux - hellas.cx, dy = uy - hellas.cy, dz = uz - hellas.cz;
					double du = (dx * hellas.t1x + dy * hellas.t1y + dz * hellas.t1z) / hellas.radiusA;
					double dv = (dx * hellas.t2x + dy * hellas.t2y + dz * hellas.t2z) / hellas.radiusB;
					double eR = Math.hypot(du, dv) + (detail - 0.5) * 0.14;
					if (eR < 1.0) {
						int basinMat = hellasRamp.sampleDithered(1.0 - eR * 0.85, 0.40, x, y, z, hellas.seed);
						mat = SurfacePainter.blend(mat, basinMat, 1.0 - eR, 0.25, 0.34, x, y, z, hellas.seed ^ 0x11);
					} else if (eR < 1.25) {
						mat = SurfacePainter.blend(mat, darkRamp.at(3), 1.25 - eR, 0.15, 0.20,
							x, y, z, hellas.seed ^ 0x22);
					}
				}

				// Valles Marineris: tapered equatorial-south canyon chasm.
				if (ux * valles.cx + uy * valles.cy + uz * valles.cz > 0.70) {
					double dx = ux - valles.cx, dy = uy - valles.cy, dz = uz - valles.cz;
					double du = dx * valles.t1x + dy * valles.t1y + dz * valles.t1z;
					double dv = dx * valles.t2x + dy * valles.t2y + dz * valles.t2z;
					if (Math.abs(du) < valles.radiusA) {
						double spine = dv + (detail - 0.5) * 0.055 + 0.025 * Math.sin(du * 9.0);
						double uNorm = du / valles.radiusA;
						double halfW = valles.radiusB * (1.0 - Math.min(1.0, uNorm * uNorm)) * (0.7 + 0.6 * detail);
						double distSpine = Math.abs(spine);
						if (halfW > 1e-4 && distSpine < halfW * 1.8) {
							double cNorm = distSpine / (halfW * 1.8);
							int cMat = canyonRamp.sampleDithered(cNorm, 0.35, x, y, z, valles.seed);
							mat = SurfacePainter.blend(cMat, mat, cNorm, 0.68, 0.32, x, y, z, valles.seed ^ 0x33);
						}
					}
				}

				// Polar ice caps with dark circumpolar sand collar.
				double wLat = SurfacePainter.warpedLatitude(ux, uy, uz, 5.5, 0.045, 3, seed ^ 0x901A);
				double polar = SurfacePainter.latitudeGradient(wLat, 0.86, 0.94);
				if (polar > 0.02) {
					if (polar < 0.35) {
						double collar = 1.0 - Math.abs(polar - 0.18) / 0.17;
						mat = SurfacePainter.blend(mat, duneCollar, collar, 0.50, 0.35, x, y, z, seed ^ 0xD04E);
					}
					int iceMat = SurfacePainter.blend(polarEdge, polarMid, polar, 0.52, 0.24, x, y, z, seed ^ 0x1CE1);
					iceMat = SurfacePainter.blend(iceMat, polarCore, polar, 0.78, 0.22, x, y, z, seed ^ 0x1CE2);
					mat = SurfacePainter.blend(mat, iceMat, polar, 0.42, 0.24, x, y, z, seed ^ 0x1CE3);
				}
				return mat;
			},
			palette, scale, diameter);
	}

	// ================================================================== JUPITER

	/**
	 * Jupiter: alternating belts and zones with turbulent edges, swirling Great Red Spot, and white ovals.
	 */
	static Blueprint jupiter(MaterialPalette palette, int diameter, double scale, int seed) {
		// NASA Hubble / Juno (https://science.nasa.gov/jupiter/facts/): alternating rust-brown belts
		// and cream zones with turbulent wavy boundaries, anticyclonic Great Red Spot in the southern
		// hemisphere (~22 deg S, pinned for arrival view), and temperate white ovals.
		int rockCore = palette.indexOf(Palette.DEEPSLATE);
		int metalH   = palette.indexOf(Palette.IRON_BLOCK);
		int molecH   = palette.indexOf(Palette.LAPIS_BLOCK);
		int redEye   = palette.indexOf(Palette.RED_CONCRETE);
		int ovalCore = palette.indexOf(Palette.WHITE_CONCRETE);
		int ovalMid  = palette.indexOf(Palette.SMOOTH_QUARTZ);
		int ovalRim  = palette.indexOf(Palette.WHITE_TERRACOTTA);

		MaterialRamp bandRamp = MaterialRamp.of(palette,
			Palette.BROWN_CONCRETE,
			Palette.BROWN_TERRACOTTA,
			Palette.RAW_COPPER_BLOCK,
			Palette.TERRACOTTA,
			Palette.ORANGE_TERRACOTTA,
			Palette.RAW_IRON_BLOCK,
			Palette.YELLOW_TERRACOTTA,
			Palette.WHITE_TERRACOTTA,
			Palette.SMOOTH_QUARTZ,
			Palette.WHITE_CONCRETE);
		MaterialRamp polarRamp = MaterialRamp.of(palette,
			Palette.GRAY_TERRACOTTA,
			Palette.MUD_BRICKS,
			Palette.PACKED_MUD,
			Palette.LIGHT_GRAY_TERRACOTTA,
			Palette.TERRACOTTA,
			Palette.WHITE_TERRACOTTA);
		MaterialRamp grsRamp = MaterialRamp.of(palette,
			Palette.RED_CONCRETE,
			Palette.RED_TERRACOTTA,
			Palette.ORANGE_CONCRETE,
			Palette.COPPER_BLOCK,
			Palette.ORANGE_TERRACOTTA,
			Palette.RAW_IRON_BLOCK);

		// Great Red Spot: pinned to the +X arrival face at ~15 deg S, matching PlanetWorldTests.checkPinnedFeatures.
		TangentFeature grs = TangentFeature.of(0.9384, -0.2528, -0.2357, 0.185, 0.120, seed ^ 0x6250);
		TangentFeature[] whiteOvals = {
			TangentFeature.of(0.84, -0.51, 0.18, 0.068, 0.044, seed ^ 0x7001),
			TangentFeature.of(0.62, -0.56, -0.55, 0.068, 0.044, seed ^ 0x7002),
			TangentFeature.of(0.76, 0.50, -0.41, 0.068, 0.044, seed ^ 0x7003),
			TangentFeature.of(-0.72, -0.54, 0.43, 0.068, 0.044, seed ^ 0x7004),
			TangentFeature.of(-0.68, 0.52, -0.52, 0.068, 0.044, seed ^ 0x7005)
		};

		double radius = diameter / 2.0;
		double innerLimit = interiorLimit(radius);
		return new Blueprint(AstronomyClass.JUPITER,
			(x, y, z) -> {
				double distSq = (double) x * x + (double) y * y + (double) z * z;
				if (distSq > radius * radius) return 0;
				double dist = Math.sqrt(distSq);
				double norm = dist / radius;
				double inv = dist > 0.0 ? 1.0 / dist : 0.0;
				double ux = x * inv;
				double uy = dist > 0.0 ? y * inv : 1.0;
				double uz = z * inv;

				if (norm < innerLimit) {
					if (norm < 0.15) return rockCore;
					if (cutaway(x, z)) {
						double tex = SurfacePainter.sphereFbm(ux, uy, uz, 3.0, 2, seed ^ 0x3A91);
						if (norm < 0.60) return tex > 0.55 ? metalH : rockCore;
						return tex > 0.50 ? molecH : metalH;
					}
					return norm < 0.60 ? metalH : molecH;
				}

				double eddy = SurfacePainter.warpedSphereFbm(ux, uy, uz, 4.5, 3, 2.8, 0.35, seed ^ 0xED1E);
				double wLat = SurfacePainter.warpedLatitude(ux, uy, uz, 4.0, 0.080, 3, seed)
					+ (eddy - 0.5) * 0.085;

				double eqZone = 0.35 * Math.exp(-(wLat * wLat) / (0.11 * 0.11));
				double neb = 0.38 * Math.exp(-((wLat - 0.22) * (wLat - 0.22)) / (0.09 * 0.09));
				double seb = 0.36 * Math.exp(-((wLat + 0.24) * (wLat + 0.24)) / (0.10 * 0.10));
				double absW = Math.abs(wLat);
				double tropZones = 0.38 * Math.exp(-((absW - 0.40) * (absW - 0.40)) / (0.075 * 0.075));
				double tempBands = 0.24 * Math.cos(wLat * Math.PI * 7.5) * (0.75 + 0.50 * eddy);
				double bandVal = SurfacePainter.clamp01(
					0.52 + eqZone - neb - seb + tropZones + tempBands + (eddy - 0.5) * 0.28);

				int mat = bandRamp.sampleDithered(bandVal, 0.42, x, y, z, seed ^ 0xBA4D);

				// Smooth stormy polar hood above ~43 deg latitude, eliminating polar wedges.
				double polar = SurfacePainter.latitudeGradient(uy, 0.68, 0.86);
				if (polar > 0.01) {
					int pMat = polarRamp.sampleDithered(eddy, 0.45, x, y, z, seed ^ 0x901A);
					mat = SurfacePainter.blend(mat, pMat, polar, 0.48, 0.30, x, y, z, seed ^ 0x901B);
				}

				// Great Red Spot with cream hollow collar and swirling spiral eye.
				double gDot = ux * grs.cx + uy * grs.cy + uz * grs.cz;
				if (gDot > 0.85) {
					double dx = ux - grs.cx, dy = uy - grs.cy, dz = uz - grs.cz;
					double du = dx * grs.t1x + dy * grs.t1y + dz * grs.t1z;
					double dv = dx * grs.t2x + dy * grs.t2y + dz * grs.t2z;
					double ex = du / grs.radiusA;
					double ey = dv / grs.radiusB;
					double rawRNorm = Math.hypot(ex, ey);
					if (rawRNorm < 0.30) {
						return redEye;
					}
					double wx = ex + (eddy - 0.5) * 0.12;
					double wy = ey + (eddy - 0.5) * 0.12;
					double rNorm = Math.hypot(wx, wy);
					if (rNorm < 1.32) {
						int collar = SurfacePainter.blend(mat, ovalRim, 1.32 - rNorm, 0.30, 0.42,
							x, y, z, grs.seed ^ 0xC0);
						if (rNorm < 1.0) {
							double twist = 3.4 * (1.0 - rNorm);
							double cosT = Math.cos(twist);
							double sinT = Math.sin(twist);
							double sx = wx * cosT - wy * sinT;
							double sy = wx * sinT + wy * cosT;
							double sw = SurfacePainter.fbm(sx * 3.0 + 1.7, sy * 3.0 - 2.3, rNorm * 4.0, 2, grs.seed);
							double t = SurfacePainter.clamp01(rNorm * 0.72 + (sw - 0.5) * 0.42);
							int spotMat = grsRamp.sampleDithered(t, 0.40, x, y, z, grs.seed);
							return SurfacePainter.blend(spotMat, collar, rNorm, 0.88, 0.28,
								x, y, z, grs.seed ^ 0x50);
						}
						return collar;
					}
				}

				// Compact anticyclonic white ovals.
				for (TangentFeature oval : whiteOvals) {
					double dot = ux * oval.cx + uy * oval.cy + uz * oval.cz;
					if (dot <= 0.96) continue;
					double dx = ux - oval.cx, dy = uy - oval.cy, dz = uz - oval.cz;
					double du = dx * oval.t1x + dy * oval.t1y + dz * oval.t1z;
					double dv = dx * oval.t2x + dy * oval.t2y + dz * oval.t2z;
					double rNorm = Math.hypot(du / oval.radiusA, dv / oval.radiusB);
					if (rNorm < 1.0) {
						int coreMat = rNorm < 0.58 ? ovalCore : ovalMid;
						int ovalMat = SurfacePainter.blend(coreMat, ovalRim, rNorm, 0.65, 0.28, x, y, z, oval.seed);
						return SurfacePainter.blend(ovalMat, mat, rNorm, 0.86, 0.26, x, y, z, oval.seed ^ 0x71);
					}
				}
				return mat;
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
		int ironCore  = palette.indexOf(Palette.IRON_BLOCK);
		int mantleIn  = palette.indexOf(Palette.GRANITE);
		int crustIn   = palette.indexOf(Palette.BLACKSTONE);
		double radius = diameter / 2.0;
		return new Blueprint(AstronomyClass.VENUS,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (dist > radius) return 0;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				double norm = dist / radius;
				// Quarter-sphere cutaway: iron core → granite mantle → blackstone crust.
				if (cutaway(x, z)) {
					if (norm < 0.50) return ironCore;
					if (norm < 0.82) return mantleIn;
					if (norm < 0.90) return crustIn;
					// fall through to cloud deck at rim
				}
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
	 * Saturn: subtle pale butter-gold bands, gentle polar tone shift + north hexagon, and equatorial rings.
	 */
	static Blueprint saturn(MaterialPalette palette, int diameter, double scale, int seed) {
		// NASA Cassini / Hubble (https://science.nasa.gov/saturn/facts/): subtle pale butter-gold
		// and cream atmospheric bands with soft edges, a dusty bluish-grey polar tone shift, and
		// the north polar hexagonal jet stream (~75-80 deg N).
		int ringMain = palette.indexOf(Palette.QUARTZ_BLOCK);
		int ringBrt  = palette.indexOf(Palette.SMOOTH_QUARTZ);
		int ringEdge = palette.indexOf(Palette.LIGHT_GRAY_CONCRETE);
		int ringGap  = palette.indexOf(Palette.GRAY_TERRACOTTA);
		int rockCore = palette.indexOf(Palette.DEEPSLATE);
		int metalH   = palette.indexOf(Palette.IRON_BLOCK);
		int molecH   = palette.indexOf(Palette.LAPIS_BLOCK);

		MaterialRamp bodyRamp = MaterialRamp.of(palette,
			Palette.PACKED_MUD,
			Palette.TERRACOTTA,
			Palette.RAW_IRON_BLOCK,
			Palette.YELLOW_TERRACOTTA,
			Palette.RAW_GOLD_BLOCK,
			Palette.WHITE_TERRACOTTA,
			Palette.END_STONE_BRICKS,
			Palette.END_STONE,
			Palette.SMOOTH_SANDSTONE);
		MaterialRamp polarRamp = MaterialRamp.of(palette,
			Palette.CYAN_TERRACOTTA,
			Palette.TUFF,
			Palette.LIGHT_GRAY_TERRACOTTA,
			Palette.PACKED_MUD,
			Palette.WHITE_TERRACOTTA);

		double bodyRadius = diameter / 2.0;
		double innerLimit = interiorLimit(bodyRadius);
		double ringInner  = bodyRadius * 1.25;
		double ringOuter  = bodyRadius * 1.9;
		return new Blueprint(AstronomyClass.SATURN,
			(x, y, z) -> {
				double dist2 = (double) x * x + (double) y * y + (double) z * z;
				double dist = Math.sqrt(dist2);
				if (inSphere(x, y, z, bodyRadius)) {
					double norm = dist / bodyRadius;
					double inv = dist > 0.0 ? 1.0 / dist : 0.0;
					double ux = x * inv;
					double uy = dist > 0.0 ? y * inv : 1.0;
					double uz = z * inv;

					if (norm < innerLimit) {
						if (norm < 0.15) return rockCore;
						if (cutaway(x, z)) {
							double tex = SurfacePainter.sphereFbm(ux, uy, uz, 3.0, 2, seed ^ 0x5A77);
							if (norm < 0.60) return tex > 0.55 ? metalH : rockCore;
							return tex > 0.50 ? molecH : metalH;
						}
						return norm < 0.60 ? metalH : molecH;
					}

					double wLat = SurfacePainter.warpedLatitude(ux, uy, uz, 2.8, 0.04, 2, seed);
					double haze = SurfacePainter.sphereFbm(ux, uy, uz, 4.5, 3, 0.5, 2.0, seed ^ 0x5A70);
					double eqBright = 0.25 * Math.exp(-(wLat * wLat) / (0.22 * 0.22));
					double waves = 0.22 * Math.cos(wLat * Math.PI * 5.0)
						+ 0.12 * Math.sin(wLat * Math.PI * 9.0);
					double bandVal = SurfacePainter.clamp01(0.52 + eqBright + waves + (haze - 0.5) * 0.18);
					int goldMat = bodyRamp.sampleDithered(bandVal, 0.65, x, y, z, seed ^ 0x5A11);

					double hexWave = (uy > 0.70)
						? 0.018 * Math.cos(6.0 * Math.atan2(uz, ux)) * SurfacePainter.smoothstep(0.995, 0.94, uy)
						: 0.0;
					double polar = SurfacePainter.latitudeGradient(uy + hexWave, 0.72, 0.93);
					if (polar > 0.01) {
						int polarMat = polarRamp.sampleDithered(
							SurfacePainter.clamp01(0.25 + 0.55 * haze - 0.25 * polar),
							0.55, x, y, z, seed ^ 0x901E);
						return SurfacePainter.blend(goldMat, polarMat, polar, 0.55, 0.42, x, y, z, seed ^ 0x5A90);
					}
					return goldMat;
				}
				// Ring: flat annulus in the y ≈ 0 plane (|y| < 0.5) — left for Steps 6-7.
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
		// Neptune's Great Dark Spot is a near-black storm, not red.
		int storm      = palette.indexOf(Palette.BLACKSTONE);
		int cloudTop   = palette.indexOf(Palette.WHITE_CONCRETE);
		int rockCore   = palette.indexOf(Palette.DEEPSLATE);
		int iceMantle  = palette.indexOf(Palette.PACKED_ICE);
		int iceMantle2 = palette.indexOf(Palette.BLUE_ICE);
		double radius = diameter / 2.0;
		// Pin the Great Dark Spot to a fixed direction so it's always visible from arrival.
		// Normalised (1, 0.2, -0.3): on the +X hemisphere the arrival camera sees, just outside the cutaway.
		final double stormX = 0.9407208683835973;
		final double stormY = 0.18814417367671946;
		final double stormZ = -0.2822162605150792;
		return new Blueprint(AstronomyClass.NEPTUNE,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (dist > radius) return 0;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				double norm = dist / radius;
				// Quarter-sphere cutaway: ice-giant interior.
				if (cutaway(x, z)) {
					double tex = VoxelNoise.noise3(nx * 3.0, ny * 3.0, nz * 3.0, seed ^ 0x9EC0);
					if (norm < 0.25) return rockCore;
					if (norm < 0.75) return tex > 0.50 ? iceMantle2 : iceMantle;
					// fall through to outer atmosphere
				}
				// Dark spot
				double ds = nx * stormX + ny * stormY + nz * stormZ;
				if (ds > 0.90) return storm;
				// Cloud top band at the outer 5%
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
		int rockCore = palette.indexOf(Palette.DEEPSLATE);
		int iceMantle = palette.indexOf(Palette.BLUE_ICE);
		double bodyRadius = diameter / 2.0;
		double ringInner  = bodyRadius * 1.2;
		double ringOuter  = bodyRadius * 1.6;
		return new Blueprint(AstronomyClass.URANUS,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (inSphere(x, y, z, bodyRadius)) {
					double nx = x / bodyRadius, ny = y / bodyRadius, nz = z / bodyRadius;
					double norm = dist / bodyRadius;
					// Quarter-sphere cutaway: ice-giant interior. The near-vertical ring
					// sits outside the body sphere so this branch does not affect it.
					if (cutaway(x, z)) {
						if (norm < 0.25) return rockCore;
						if (norm < 0.75) return iceMantle;
					}
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
		int ironCore = palette.indexOf(Palette.IRON_BLOCK);
		int mantleIn = palette.indexOf(Palette.GRANITE);
		int crustIn  = palette.indexOf(Palette.STONE);
		double radius = diameter / 2.0;
		int craterCount = 24;
		return new Blueprint(AstronomyClass.MERCURY,
			(x, y, z) -> {
				double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
				if (dist > radius) return 0;
				double nx = x / radius, ny = y / radius, nz = z / radius;
				double norm = dist / radius;
				// Quarter-sphere cutaway: Mercury has an unusually large iron core.
				if (cutaway(x, z)) {
					if (norm < 0.70) return ironCore;
					if (norm < 0.88) return mantleIn;
					if (norm < 0.95) return crustIn;
				}
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
