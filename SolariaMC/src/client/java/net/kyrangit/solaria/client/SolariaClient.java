package net.kyrangit.solaria.client;

import net.fabricmc.api.ClientModInitializer;
import net.kyrangit.solaria.SolariaMod;

/** Client-only entrypoint; future HTTP and UI code must stay in this source set. */
public final class SolariaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SolariaMod.config();
	}
}