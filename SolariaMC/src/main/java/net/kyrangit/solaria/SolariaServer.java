package net.kyrangit.solaria;

import net.fabricmc.api.DedicatedServerModInitializer;

/** Dedicated-server boundary. Commands also work on integrated servers via the common entrypoint. */
public final class SolariaServer implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		SolariaMod.config();
	}
}