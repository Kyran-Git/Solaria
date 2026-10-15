package net.kyrangit.solaria;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.Identifier;
import net.kyrangit.solaria.command.SolariaCommands;
import net.kyrangit.solaria.config.ConfigManager;
import net.kyrangit.solaria.logging.PrivacyLog;

public class SolariaMod implements ModInitializer {
	public static final String MOD_ID = "solariamod";

	private static ConfigManager config;

	@Override
	public void onInitialize() {
		config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve("solariamod.json"));
		ConfigManager.ReloadResult result = config.reload();
		if (!result.success()) {
			PrivacyLog.failure(result.error().reasonCode());
		}
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			SolariaCommands.register(dispatcher, config));
	}

	public static ConfigManager config() {
		if (config == null) {
			throw new IllegalStateException("Solaria common initialization has not completed.");
		}
		return config;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
