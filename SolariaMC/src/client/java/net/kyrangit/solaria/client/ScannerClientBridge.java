package net.kyrangit.solaria.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side bridge from the common {@code ScannerItem} (and the
 * {@code /solaria scan} command) to the Minecraft client thread.
 *
 * <p>Right-clicking the scanner can happen from a packet dispatcher thread,
 * which is not allowed to call {@link Minecraft#setScreen(net.minecraft.client.gui.screens.Screen)}
 * directly. We instead set a one-shot flag and read it from a client tick
 * callback, which always runs on the client thread. The item's use handler
 * invokes {@link #requestOpen()} reflectively so the common source set
 * never imports client-only types.
 */
public final class ScannerClientBridge {
	private static final AtomicBoolean OPEN_REQUESTED = new AtomicBoolean(false);

	private ScannerClientBridge() {
	}

	/** Called by {@code ScannerItem} and {@code /solaria scan}. Thread-safe. */
	public static void requestOpen() {
		OPEN_REQUESTED.set(true);
	}

	/**
	 * Installs the client tick hook that opens {@link ScannerScreen} when a
	 * request has been posted. Call once from the client entrypoint.
	 */
	public static void install() {
		ClientTickEvents.END_CLIENT_TICK.register(ScannerClientBridge::onClientTick);
	}

	private static void onClientTick(Minecraft client) {
		if (!OPEN_REQUESTED.compareAndSet(true, false)) {
			return;
		}
		// Only open the screen if the player is actually in a world and no
		// other screen is already visible (so we never cover a pause menu or
		// another mod's GUI without the student knowing).
		if (client.player == null || client.screen != null) {
			return;
		}
		client.setScreen(new ScannerScreen());
	}
}
