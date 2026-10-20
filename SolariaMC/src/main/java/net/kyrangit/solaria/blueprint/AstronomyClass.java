package net.kyrangit.solaria.blueprint;

/**
 * The ten astronomy classes recognised by the Solaria classifier.
 * The live list comes from GET /v1/health; the mod holds one blueprint per value here.
 *
 * <p>Ids are lowercase and must match the dataset folder names, the model
 * metadata, and the service's {@code classes} list byte-for-byte. Mercury is
 * intentionally absent. Pluto is a dwarf planet; any text shown to the player
 * about it must say "dwarf planet".
 */
public enum AstronomyClass {
	SUN("sun"),
	EARTH("earth"),
	MOON("moon"),
	MARS("mars"),
	JUPITER("jupiter"),
	VENUS("venus"),
	SATURN("saturn"),
	NEPTUNE("neptune"),
	URANUS("uranus"),
	PLUTO("pluto");

	private final String id;

	AstronomyClass(String id) {
		this.id = id;
	}

	/** Wire-format identifier matching the classifier contract. */
	public String id() {
		return id;
	}

	/** Resolve from wire-format id, or null if unknown. */
	public static AstronomyClass fromId(String id) {
		for (AstronomyClass c : values()) {
			if (c.id.equals(id)) {
				return c;
			}
		}
		return null;
	}
}
