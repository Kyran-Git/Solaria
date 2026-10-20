package net.kyrangit.solaria.blueprint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ordered mapping from palette index (1-based) to Minecraft block ID strings.
 * Index 0 is always air (implicit). The palette avoids gravity blocks, fluids,
 * harmful or support-dependent blocks.
 */
public final class MaterialPalette {
	/** Block IDs that must never appear in any palette. */
	public static final Set<String> FORBIDDEN_BLOCKS = Set.of(
		"minecraft:sand", "minecraft:red_sand", "minecraft:gravel",
		"minecraft:white_concrete_powder", "minecraft:orange_concrete_powder",
		"minecraft:magenta_concrete_powder", "minecraft:light_blue_concrete_powder",
		"minecraft:yellow_concrete_powder", "minecraft:lime_concrete_powder",
		"minecraft:pink_concrete_powder", "minecraft:gray_concrete_powder",
		"minecraft:light_gray_concrete_powder", "minecraft:cyan_concrete_powder",
		"minecraft:purple_concrete_powder", "minecraft:blue_concrete_powder",
		"minecraft:brown_concrete_powder", "minecraft:green_concrete_powder",
		"minecraft:red_concrete_powder", "minecraft:black_concrete_powder",
		"minecraft:water", "minecraft:lava",
		"minecraft:fire", "minecraft:soul_fire",
		"minecraft:tnt",
		"minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch",
		"minecraft:redstone_torch", "minecraft:redstone_wall_torch",
		"minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil",
		"minecraft:pointed_dripstone", "minecraft:scaffolding",
		"minecraft:dragon_egg"
	);

	private final String fallbackBlock;
	private final List<String> entries; // 1-based: entries.get(0) is palette index 1
	private final Map<String, Integer> nameToIndex;

	private MaterialPalette(String fallbackBlock, List<String> entries) {
		this.fallbackBlock = fallbackBlock;
		this.entries = List.copyOf(entries);
		Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < entries.size(); i++) {
			map.put(entries.get(i), i + 1);
		}
		this.nameToIndex = Collections.unmodifiableMap(map);
	}

	/** Palette index (1-based) for the given abstract material name. */
	public int indexOf(String name) {
		Integer index = nameToIndex.get(name);
		if (index == null) {
			throw new IllegalArgumentException("Unknown material: " + name);
		}
		return index;
	}

	/** Block ID string for the given palette index (1-based). Returns fallback for 0 or out-of-range. */
	public String blockId(int paletteIndex) {
		if (paletteIndex < 1 || paletteIndex > entries.size()) {
			return fallbackBlock;
		}
		return entries.get(paletteIndex - 1);
	}

	public String fallbackBlock() {
		return fallbackBlock;
	}

	public int size() {
		return entries.size();
	}

	/** All block IDs used (including fallback). */
	public List<String> allBlockIds() {
		var list = new java.util.ArrayList<>(entries);
		if (!list.contains(fallbackBlock)) {
			list.add(fallbackBlock);
		}
		return Collections.unmodifiableList(list);
	}

	public static Builder builder(String fallbackBlock) {
		return new Builder(fallbackBlock);
	}

	public static final class Builder {
		private final String fallbackBlock;
		private final java.util.ArrayList<String> entries = new java.util.ArrayList<>();

		private Builder(String fallbackBlock) {
			validate(fallbackBlock);
			this.fallbackBlock = fallbackBlock;
		}

		/** Add a material entry; palette index will be entries.size() (1-based). */
		public Builder add(String blockId) {
			validate(blockId);
			entries.add(blockId);
			return this;
		}

		public MaterialPalette build() {
			if (entries.isEmpty()) {
				throw new IllegalStateException("Palette must have at least one entry.");
			}
			return new MaterialPalette(fallbackBlock, entries);
		}

		private static void validate(String blockId) {
			if (FORBIDDEN_BLOCKS.contains(blockId)) {
				throw new IllegalArgumentException("Forbidden block in palette: " + blockId);
			}
		}
	}
}
