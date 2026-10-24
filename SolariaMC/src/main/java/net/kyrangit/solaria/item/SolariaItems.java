package net.kyrangit.solaria.item;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.kyrangit.solaria.SolariaMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/**
 * Common registry for Solaria items. Phase 4 adds the scanner (camera) item;
 * future phases may add diagrams or chalk.
 *
 * <p>Items are created in the common source set, not the client, so the server
 * knows about them for inventory and command handling. The actual GUI opened
 * by right-click is client-only; see
 * {@code net.kyrangit.solaria.client.ScannerClientBridge}.
 */
public final class SolariaItems {

	/** Resource key for the camera/scanner item. */
	public static final ResourceKey<Item> SCANNER_KEY =
		ResourceKey.create(Registries.ITEM, SolariaMod.id("scanner"));

	/** The registered scanner item instance. Non-null after {@link #register()}. */
	public static final Item SCANNER = Registry.register(
		BuiltInRegistries.ITEM,
		SCANNER_KEY,
		new ScannerItem(new Item.Properties().setId(SCANNER_KEY).stacksTo(1))
	);

	private SolariaItems() {
	}

	/**
	 * Called from the common mod entrypoint so classloading registers {@link #SCANNER}
	 * and wires it into the Tools and Utilities creative tab.
	 */
	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(SCANNER));
	}
}
