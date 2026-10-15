package net.kyrangit.solaria.command;

import com.mojang.brigadier.CommandDispatcher;
import net.kyrangit.solaria.config.ConfigException;
import net.kyrangit.solaria.config.ConfigManager;
import net.kyrangit.solaria.logging.PrivacyLog;
import net.kyrangit.solaria.logging.ReasonCode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public final class SolariaCommands {
	private SolariaCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ConfigManager config) {
		dispatcher.register(Commands.literal("solaria")
			.executes(context -> status(context.getSource(), config))
			.then(Commands.literal("status").executes(context -> status(context.getSource(), config)))
			.then(Commands.literal("config").then(Commands.literal("reload")
				.executes(context -> reload(context.getSource(), config)))));
	}

	private static boolean authorized(CommandSourceStack source, ConfigManager config) {
		int level = config.current().teacherPermissionLevel();
		if (source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)))) {
			return true;
		}
		source.sendFailure(Component.literal("[PERMISSION_DENIED] Ask an operator for teacher permission level " + level + "."));
		PrivacyLog.failure(ReasonCode.PERMISSION_DENIED);
		return false;
	}

	private static int status(CommandSourceStack source, ConfigManager config) {
		if (!authorized(source, config)) {
			return 0;
		}
		source.sendSuccess(() -> Component.literal("SolariaMC foundation loaded. Service health is not checked yet (M3); scanning and building are not available yet."), false);
		ConfigException error = config.lastError();
		if (error != null) {
			source.sendFailure(Component.literal("[INPUT_INVALID] " + error.getMessage()
				+ " Keeping the last valid configuration (safe defaults if no successful load)."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Configuration valid. Use /solaria config reload after editing config/solariamod.json."), false);
		return 1;
	}

	private static int reload(CommandSourceStack source, ConfigManager config) {
		if (!authorized(source, config)) {
			return 0;
		}
		ConfigManager.ReloadResult result = config.reload();
		if (!result.success()) {
			PrivacyLog.failure(result.error().reasonCode());
			source.sendFailure(Component.literal("[INPUT_INVALID] " + result.error().getMessage() + " Previous configuration retained."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Solaria configuration reloaded. On multiplayer this reloads the server configuration only."), false);
		return 1;
	}
}