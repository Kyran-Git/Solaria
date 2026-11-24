package net.kyrangit.solaria.blueprint;

import java.io.IOException;
import java.io.InputStream;

/**
 * Bundled 128×64 equirectangular Earth land/elevation and biome mask.
 *
 * <p>Derived from public-domain NASA Visible Earth / GIBS Blue Marble: Next Generation
 * imagery (NASA Goddard Space Flight Center, Reto Stöckli and team:
 * {@code BlueMarble_NextGeneration} and {@code BlueMarble_ShadedRelief_Bathymetry},
 * https://visibleearth.nasa.gov/collection/1484/blue-marble).
 *
 * <p>Stored as 128×64 big-endian pairs of unsigned bytes {@code [landElev, biome]}:
 * row 0 is +90° N to row 63 at -90° S; col 0 is -180° W to col 127 at +180° E.
 * {@code landElev >= 0.5} denotes continental/island land rising with elevation;
 * {@code landElev < 0.5} denotes ocean bathymetry rising toward shallow shelves.
 */
final class EarthMask {
	static final int WIDTH = 128;
	static final int HEIGHT = 64;
	private static final String RESOURCE_PATH = "/data/solariamod/blueprint/earth_land_mask.dat";
	private static final int EXPECTED_BYTES = WIDTH * HEIGHT * 2;
	private static final byte[] DATA = load();

	private EarthMask() {
	}

	private static byte[] load() {
		try (InputStream in = EarthMask.class.getResourceAsStream(RESOURCE_PATH)) {
			if (in == null) {
				throw new IllegalStateException("Missing bundled Earth mask resource: " + RESOURCE_PATH);
			}
			byte[] bytes = in.readAllBytes();
			if (bytes.length != EXPECTED_BYTES) {
				throw new IllegalStateException(
					"Expected " + EXPECTED_BYTES + " bytes in " + RESOURCE_PATH + ", got " + bytes.length);
			}
			return bytes;
		} catch (IOException e) {
			throw new IllegalStateException("Failed reading Earth mask resource: " + RESOURCE_PATH, e);
		}
	}

	/** Bilinear ocean-depth / land-elevation sample in {@code [0, 1]} ({@code >= 0.5} is land). */
	static double sampleLandElev(double ux, double uy, double uz) {
		return sampleChannel(ux, uy, uz, 0);
	}

	/** Bilinear land-biome sample in {@code [0, 1]} (forest ~0.1, grassland ~0.4, desert ~0.75, ice ~0.94). */
	static double sampleBiome(double ux, double uy, double uz) {
		return sampleChannel(ux, uy, uz, 1);
	}

	private static double sampleChannel(double ux, double uy, double uz, int channel) {
		double lon = Math.atan2(uz, ux);
		double clampedUy = uy < -1.0 ? -1.0 : (uy > 1.0 ? 1.0 : uy);
		double lat = Math.asin(clampedUy);

		double gx = ((lon + Math.PI) / (2.0 * Math.PI)) * WIDTH - 0.5;
		if (gx < 0.0) {
			gx += WIDTH;
		}
		double gy = ((0.5 * Math.PI - lat) / Math.PI) * HEIGHT - 0.5;
		if (gy < 0.0) {
			gy = 0.0;
		} else if (gy > HEIGHT - 1.0) {
			gy = HEIGHT - 1.0;
		}

		int x0 = (int) Math.floor(gx);
		double fx = gx - x0;
		x0 = Math.floorMod(x0, WIDTH);
		int x1 = x0 + 1 == WIDTH ? 0 : x0 + 1;

		int y0 = (int) Math.floor(gy);
		double fy = gy - y0;
		int y1 = y0 + 1 >= HEIGHT ? HEIGHT - 1 : y0 + 1;

		double v00 = (DATA[((y0 * WIDTH + x0) << 1) + channel] & 0xFF) / 255.0;
		double v10 = (DATA[((y0 * WIDTH + x1) << 1) + channel] & 0xFF) / 255.0;
		double v01 = (DATA[((y1 * WIDTH + x0) << 1) + channel] & 0xFF) / 255.0;
		double v11 = (DATA[((y1 * WIDTH + x1) << 1) + channel] & 0xFF) / 255.0;

		double top = v00 + (v10 - v00) * fx;
		double bot = v01 + (v11 - v01) * fx;
		return top + (bot - top) * fy;
	}
}
