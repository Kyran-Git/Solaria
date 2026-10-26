package net.kyrangit.solaria.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.config.ConfigException;
import net.kyrangit.solaria.config.ConfigManager;
import net.kyrangit.solaria.dimension.SolariaDimensions;
import net.kyrangit.solaria.logging.PrivacyLog;
import net.kyrangit.solaria.logging.ReasonCode;
import net.kyrangit.solaria.service.AiServiceClient;
import net.kyrangit.solaria.service.ServiceException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

public final class SolariaCommands {
	private static final Semaphore HEALTH_CHECK = new Semaphore(1);
	private SolariaCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ConfigManager config) {
		String[] planetIds = Arrays.stream(AstronomyClass.values())
				.map(AstronomyClass::id).toArray(String[]::new);
		dispatcher.register(Commands.literal("solaria")
			.executes(context -> status(context.getSource(), config))
			.then(Commands.literal("status").executes(context -> status(context.getSource(), config)))
			.then(Commands.literal("service").then(Commands.literal("health")
				.executes(context -> serviceHealth(context.getSource(), config))))
			.then(Commands.literal("config").then(Commands.literal("reload")
				.executes(context -> reload(context.getSource(), config))))
			.then(Commands.literal("visit")
				.then(Commands.argument("planet", StringArgumentType.word())
					.suggests((context, builder) -> {
						for (String id : planetIds) builder.suggest(id);
						return builder.buildFuture();
					})
					.executes(context -> visit(context.getSource(),
						StringArgumentType.getString(context, "planet")))))
			.then(Commands.literal("return")
				.executes(context -> returnToOverworld(context.getSource()))));
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
		source.sendSuccess(() -> Component.literal("SolariaMC loaded. Use /solaria service health to check the host AI service."), false);
		ConfigException error = config.lastError();
		if (error != null) {
			source.sendFailure(Component.literal("[INPUT_INVALID] " + error.getMessage()
				+ " Keeping the last valid configuration (safe defaults if no successful load)."));
			return 0;
		}
		String planets = Arrays.stream(AstronomyClass.values())
				.map(AstronomyClass::id).collect(Collectors.joining(", "));
		source.sendSuccess(() -> Component.literal("Config valid. Planet dimensions: " + planets
			+ ". Use /solaria visit <planet> to explore. /solaria return to go back."), false);
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
		source.sendSuccess(() -> Component.literal("Solaria configuration reloaded."), false);
		return 1;
	}

	private static int serviceHealth(CommandSourceStack source, ConfigManager config) {
		if (!authorized(source, config)) return 0;
		if (!HEALTH_CHECK.tryAcquire()) {
			source.sendFailure(Component.literal("[QUEUE_FULL] A service health check is already running."));
			return 0;
		}
		var snapshot = config.current();
		source.sendSuccess(() -> Component.literal("Checking host AI service..."), false);
		Thread.ofVirtual().name("solaria-health").start(() -> {
			try (AiServiceClient client = AiServiceClient.fromEnvironment(snapshot)) {
				var health = client.health();
				source.getServer().execute(() -> {
					if (authorized(source, config)) source.sendSuccess(() -> Component.literal(
						"AI service ready: api_v1, ten matching classes, threshold=" + health.threshold()
							+ (health.mock() ? ". MOCK: synthetic predictions, not drawing recognition." : ". Model loaded.")
							+ " Scanner capture/relay and planet generation are not wired yet."), false);
				});
			} catch (ServiceException error) {
				PrivacyLog.failure(error.reasonCode());
				source.getServer().execute(() -> {
					if (authorized(source, config)) source.sendFailure(Component.literal("[" + error.reasonCode()
						+ "] Check host service, startup token and class contract. No scan was submitted."));
				});
			} finally {
				HEALTH_CHECK.release();
			}
		});
		return 1;
	}

	/**
	 * Teleports the player into the named planet's individual dimension.
	 * The player is placed in creative mode so they can fly and explore the sphere.
	 * Note: bodies are not to scale with one another or with the real solar system.
	 */
	private static int visit(CommandSourceStack source, String planetId) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("[INPUT_INVALID] This command must be run by a player."));
			return 0;
		}
		AstronomyClass cls = AstronomyClass.fromId(planetId.toLowerCase());
		if (cls == null) {
			String valid = Arrays.stream(AstronomyClass.values())
					.map(AstronomyClass::id).collect(Collectors.joining(", "));
			source.sendFailure(Component.literal("[INPUT_INVALID] Unknown planet '" + planetId
				+ "'. Valid: " + valid + "."));
			return 0;
		}
		ResourceKey<Level> dimKey = SolariaDimensions.keyFor(cls);
		ServerLevel targetLevel = source.getServer().getLevel(dimKey);
		if (targetLevel == null) {
			source.sendFailure(Component.literal("[PLACEMENT_BLOCKED] Dimension solariamod:"
				+ cls.id() + " is not loaded. Check mod installation."));
			return 0;
		}
		// Spawn 800 blocks out from origin on the +X axis so the player has a clear
		// view of the sphere centred at origin before flying in.
		Vec3 spawnPos = new Vec3(800.0, 0.0, 0.0);
		TeleportTransition transition = new TeleportTransition(
			targetLevel, spawnPos, Vec3.ZERO,
			-90.0f, 0.0f, TeleportTransition.DO_NOTHING);
		ServerPlayer arrived = player.teleport(transition);
		if (arrived == null) {
			source.sendFailure(Component.literal("[PLACEMENT_BLOCKED] Teleport to "
				+ cls.id() + " dimension failed."));
			return 0;
		}
		arrived.setGameMode(GameType.CREATIVE);
		final String display = capitalize(cls.id());
		arrived.sendSystemMessage(Component.literal(
			"Entering the " + display + " dimension. Bodies are not to scale. "
			+ "Use /solaria return to go back."));
		return 1;
	}

	/**
	 * Returns the player to the overworld spawn. Safe to call from any Solaria planet dimension.
	 */
	private static int returnToOverworld(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("[INPUT_INVALID] This command must be run by a player."));
			return 0;
		}
		if (player.level().dimension().equals(Level.OVERWORLD)) {
			source.sendFailure(Component.literal("You are already in the overworld."));
			return 0;
		}
		ServerLevel overworld = source.getServer().overworld();
		net.minecraft.world.level.storage.LevelData.RespawnData respawn =
			overworld.getLevelData().getRespawnData();
		Vec3 spawnPos = Vec3.atCenterOf(respawn.pos());
		TeleportTransition transition = new TeleportTransition(
			overworld, spawnPos, Vec3.ZERO,
			respawn.yaw(), 0.0f, TeleportTransition.DO_NOTHING);
		ServerPlayer arrived = player.teleport(transition);
		if (arrived == null) {
			source.sendFailure(Component.literal("[PLACEMENT_BLOCKED] Return to overworld failed."));
			return 0;
		}
		arrived.sendSystemMessage(Component.literal("Returned to the overworld."));
		return 1;
	}

	private static String capitalize(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}