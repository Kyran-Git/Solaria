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
						StringArgumentType.getString(context, "planet"), config))))
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
	private static int visit(CommandSourceStack source, String planetId, ConfigManager config) {
		if (!authorized(source, config)) return 0;
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
        return travelToPlanet(player, cls);
    }

    /** Shared server-only path for operator previews and approved scanner builds. */
    public static int travelToPlanet(ServerPlayer player, AstronomyClass cls) {
        CommandSourceStack source = player.createCommandSourceStack();
        ResourceKey<Level> dimKey = SolariaDimensions.keyFor(cls);
		ServerLevel targetLevel = source.getServer().getLevel(dimKey);
		if (targetLevel == null) {
			source.sendFailure(Component.literal("[PLACEMENT_BLOCKED] Dimension solariamod:"
				+ cls.id() + " is not loaded. Check mod installation."));
			return 0;
		}
		if (!(targetLevel.getChunkSource().getGenerator() instanceof net.kyrangit.solaria.dimension.PlanetChunkGenerator)) {
			source.sendFailure(Component.literal("[PLACEMENT_BLOCKED] This world still uses the old void generator. Use a new test world; saved chunks are not migrated."));
			return 0;
		}
		// Never record the academy as a lesson return point, and never let a trip skip an unfinished mission.
		if (player.level().dimension().equals(net.kyrangit.solaria.intro.IntroHub.DIMENSION)
			|| !net.kyrangit.solaria.intro.IntroHub.canScan(player)) {
			source.sendFailure(Component.literal("[INTRO_REQUIRED] Finish Solaria Academy and use its lesson exit before visiting a planet."));
			return 0;
		}
		var previousAnchor = player.getAttached(net.kyrangit.solaria.dimension.LessonReturn.Attachment.ANCHOR);
		boolean inPlanet = SolariaDimensions.classFromDimensionId(player.level().dimension().identifier().toString()) != null;
		if (!inPlanet && previousAnchor == null) {
			player.setAttached(net.kyrangit.solaria.dimension.LessonReturn.Attachment.ANCHOR,
				new net.kyrangit.solaria.dimension.LessonReturn(player.level().dimension(),
					player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer().getName()));
		}
		// Stand clear of the body (including rings) and look back toward the centre.
		var generator = (net.kyrangit.solaria.dimension.PlanetChunkGenerator) targetLevel.getChunkSource().getGenerator();
		if (generator.blueprint().astronomyClass() != cls) {
            player.setAttached(net.kyrangit.solaria.dimension.LessonReturn.Attachment.ANCHOR, previousAnchor);
            source.sendFailure(Component.literal("[PLACEMENT_BLOCKED] This dimension has the wrong body generator. Check the host dimension data."));
            return 0;
        }
        Vec3 spawnPos = new Vec3(generator.viewingDistance() + 0.5, 0.5, 0.5);
		TeleportTransition transition = new TeleportTransition(
			targetLevel, spawnPos, Vec3.ZERO,
			90.0f, 0.0f, TeleportTransition.DO_NOTHING);
		ServerPlayer arrived = player.teleport(transition);
		if (arrived == null) {
			player.setAttached(net.kyrangit.solaria.dimension.LessonReturn.Attachment.ANCHOR, previousAnchor);
			source.sendFailure(Component.literal("[PLACEMENT_BLOCKED] Teleport to "
				+ cls.id() + " dimension failed."));
			return 0;
		}
		arrived.setGameMode(GameType.CREATIVE);
		arrived.getAbilities().flying = true;
		arrived.onUpdateAbilities();
		final String display = capitalize(cls.id());
        arrived.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(display)));
        arrived.level().playSound(null, arrived.blockPosition(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
            net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
		arrived.sendSystemMessage(Component.literal(
			"Entering the " + display + " dimension. Bodies are not to scale. "
			+ "Use /solaria return to go back."));
		return 1;
	}

	/**
	 * Returns the player to the exact lesson location saved when they first left for a planet.
	 * The anchor is cleared only after the teleport succeeds, so a failure keeps the way back.
	 */
	private static int returnToOverworld(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("[INPUT_INVALID] This command must be run by a player."));
			return 0;
		}
		var anchor = player.getAttached(net.kyrangit.solaria.dimension.LessonReturn.Attachment.ANCHOR);
		if (anchor == null) {
			source.sendFailure(Component.literal(
				"No saved lesson location. Use /solaria visit <planet> first; this command only undoes that trip."));
			return 0;
		}
		ServerLevel lessonLevel = source.getServer().getLevel(anchor.dimension());
		if (lessonLevel == null) {
			source.sendFailure(Component.literal("[PLACEMENT_BLOCKED] The saved lesson world "
				+ anchor.dimension().identifier() + " is not loaded. The saved location is kept."));
			return 0;
		}
		TeleportTransition transition = new TeleportTransition(
			lessonLevel, new Vec3(anchor.x(), anchor.y(), anchor.z()), Vec3.ZERO,
			anchor.yaw(), anchor.pitch(), TeleportTransition.DO_NOTHING);
		ServerPlayer arrived = player.teleport(transition);
		if (arrived == null) {
			source.sendFailure(Component.literal(
				"[PLACEMENT_BLOCKED] Return failed. The saved lesson location is kept; try again."));
			return 0;
		}
		arrived.setGameMode(GameType.byName(anchor.gameMode(), GameType.SURVIVAL));
		arrived.removeAttached(net.kyrangit.solaria.dimension.LessonReturn.Attachment.ANCHOR);
		arrived.sendSystemMessage(Component.literal("Returned to where you started the lesson."));
		return 1;
	}

	private static String capitalize(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}