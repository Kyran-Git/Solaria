package net.kyrangit.solaria.intro;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kyrangit.solaria.SolariaMod;
import net.kyrangit.solaria.config.ConfigManager;
import net.kyrangit.solaria.dimension.LessonReturn;
import net.kyrangit.solaria.item.SolariaItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Opt-in classroom onboarding. All quiz/reward decisions are made by the server. */
public final class IntroHub {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, SolariaMod.id("intro_hub"));

    /** Lazy holder keeps pure quiz/layout tests usable without Fabric's attachment mixins. */
    public static final class Data {
        public static final AttachmentType<Boolean> ENABLED = AttachmentRegistry.create(SolariaMod.id("intro_enabled"),
            b -> b.persistent(Codec.BOOL));
        /** v2: mission board text changed for any-order stations, so existing hubs rebuild once. */
        public static final AttachmentType<Boolean> BUILT = AttachmentRegistry.create(SolariaMod.id("intro_built_v2"),
            b -> b.persistent(Codec.BOOL));
        /** Still registered only so older worlds load without "unknown attachment" warnings; never read. */
        public static final AttachmentType<Boolean> LEGACY_BUILT_V1 = AttachmentRegistry.create(SolariaMod.id("intro_built_v1"),
            b -> b.persistent(Codec.BOOL));
        public static final AttachmentType<Boolean> SEEN = AttachmentRegistry.create(SolariaMod.id("intro_seen"),
            b -> b.persistent(Codec.BOOL).copyOnDeath());
        public static final AttachmentType<Boolean> REQUIRED = AttachmentRegistry.create(SolariaMod.id("intro_required"),
            b -> b.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));
        /** Bitmask of solved stations (see {@link IntroQuiz}); any order. */
        public static final AttachmentType<Integer> SOLVED = AttachmentRegistry.create(SolariaMod.id("intro_solved"),
            b -> b.persistent(IntroQuiz.MASK_CODEC).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
        /** Old in-order count from before any-order stations; converted to {@link #SOLVED} on join. */
        public static final AttachmentType<Integer> LEGACY_PROGRESS = AttachmentRegistry.create(SolariaMod.id("intro_progress"),
            b -> b.persistent(IntroQuiz.LEGACY_COUNT_CODEC).copyOnDeath());
        public static final AttachmentType<LessonReturn> RETURN = AttachmentRegistry.create(SolariaMod.id("intro_return"),
            b -> b.persistent(LessonReturn.CODEC).copyOnDeath());

        private Data() {
        }
    }

    private IntroHub() {
    }

    /** Bitmask of solved stations. */
    public static int solved(Player player) {
        Integer solved = player.getAttached(Data.SOLVED);
        if (solved != null) return solved;
        // Server-side fallback until the join migration has run; the client only ever sees SOLVED.
        Integer legacy = player.getAttached(Data.LEGACY_PROGRESS);
        return legacy == null ? 0 : IntroQuiz.fromLegacyCount(legacy);
    }

    /** Number of solved stations, 0..3. */
    public static int progress(Player player) {
        return IntroQuiz.count(solved(player));
    }

    private static void migrateLegacyProgress(ServerPlayer player) {
        Integer legacy = player.getAttached(Data.LEGACY_PROGRESS);
        if (legacy == null) return;
        if (!player.hasAttached(Data.SOLVED)) player.setAttached(Data.SOLVED, IntroQuiz.fromLegacyCount(legacy));
        player.removeAttached(Data.LEGACY_PROGRESS);
    }

    public static boolean enrolled(Player player) {
        return player.getAttachedOrElse(Data.REQUIRED, false);
    }

    public static boolean canScan(Player player) {
        return !enrolled(player) || IntroQuiz.complete(solved(player));
    }

    public static void install() {
        var unused = Data.SOLVED;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            // Defer teleport until the join packet sequence has completed.
            server.execute(() -> {
                if (player.hasDisconnected()) return;
                migrateLegacyProgress(player);
                boolean seen = player.getAttachedOrElse(Data.SEEN, false);
                player.setAttached(Data.SEEN, true);
                if (!seen && server.overworld().getAttachedOrElse(Data.ENABLED, false)) enter(player, server, true);
                else if (enrolled(player) && !canScan(player)) enter(player, server, false);
            });
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            // Only an unfinished mission is mandatory; a finished explorer respawns normally.
            if (enrolled(player) && !canScan(player)) {
                // Run after the respawn packet/position has been sent, not in the middle of vanilla respawn.
                player.level().getServer().execute(() -> enter(player, player.level().getServer(), false));
            } else if (!player.level().dimension().equals(DIMENSION)) {
                // A finished explorer who respawned elsewhere no longer needs the old hub exit point.
                player.removeAttached(Data.RETURN);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.overworld().getGameTime() % 20 != 0) return;
            for (ServerPlayer player : java.util.List.copyOf(server.getPlayerList().getPlayers())) {
                boolean inHub = player.level().dimension().equals(DIMENSION);
                if (enrolled(player) && !canScan(player) && !inHub) enter(player, server, false);
                // Teachers in creative/spectator may inspect freely; everyone else stays on the platform.
                if (inHub && !player.isCreative() && !player.isSpectator()
                    && (player.getY() < HubLayout.FLOOR_Y - 4 || Math.abs(player.getX()) > HubLayout.RADIUS
                    || Math.abs(player.getZ()) > HubLayout.RADIUS)) teleportToHub(player, server);
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.dimension().equals(DIMENSION)) return InteractionResult.PASS;
            // Teachers inspecting or repairing the hub in creative keep vanilla behaviour.
            if (player.isCreative()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.FAIL;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) return InteractionResult.FAIL;
            BlockPos pos = hit.getBlockPos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 36) return InteractionResult.FAIL;
            // Signs above buttons work too: a large, forgiving interaction target.
            BlockPos button = level.getBlockState(pos).is(Blocks.BIRCH_WALL_SIGN) ? pos.below() : pos;
            if (!level.getBlockState(button).is(Blocks.STONE_BUTTON)) return InteractionResult.FAIL;
            if (!enrolled(player)) {
                tell(player, "Use /solaria intro join to start your explorer mission.", ChatFormatting.AQUA);
                return InteractionResult.SUCCESS;
            }
            for (int station = 0; station < IntroQuiz.COUNT; station++) {
                for (int answer = 0; answer < 3; answer++) {
                    if (button.equals(HubLayout.choice(station, answer))) {
                        answer(serverPlayer, station, answer);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            if (button.equals(HubLayout.TERMINAL)) reward(serverPlayer);
            if (button.equals(HubLayout.EXIT)) leave(serverPlayer, serverPlayer.level().getServer());
            return InteractionResult.SUCCESS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, face) ->
            level.dimension().equals(DIMENSION) && !player.isCreative() ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) ->
            !level.dimension().equals(DIMENSION) || player.isCreative());
    }

    private static void answer(ServerPlayer player, int station, int choice) {
        int before = solved(player);
        if (IntroQuiz.solved(before, station)) {
            tell(player, IntroQuiz.complete(before) ? "Mission complete! Collect your scanner at the green depot behind you."
                : "You already solved this station. Still to explore: " + IntroQuiz.remaining(before) + ".", ChatFormatting.AQUA);
            return;
        }
        var question = IntroQuiz.QUESTIONS.get(station);
        int after = IntroQuiz.answer(before, station, choice);
        boolean correct = after != before;
        tell(player, (correct ? "Correct! " : "Not quite - try again! ") + question.explanation(),
            correct ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        if (!correct) {
            tell(player, question.prompt(), ChatFormatting.AQUA);
            return;
        }
        player.setAttached(Data.SOLVED, after);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        if (IntroQuiz.complete(after)) {
            tell(player, "Explorer mission complete! Your scanner is unlocked. Visit the green depot, then the cyan lesson exit.", ChatFormatting.GOLD);
            reward(player);
        } else tell(player, IntroQuiz.count(after) + "/3 complete! Still to explore: " + IntroQuiz.remaining(after) + ".", ChatFormatting.AQUA);
    }

    private static boolean reward(ServerPlayer player) {
        if (!IntroQuiz.complete(solved(player))) {
            tell(player, "Complete the three stations to earn your scanner. Progress: " + progress(player) + "/3.", ChatFormatting.YELLOW);
            return false;
        }
        if (player.getInventory().contains(stack -> stack.is(SolariaItems.SCANNER))) return true;
        if (player.getInventory().getFreeSlot() < 0) {
            tell(player, "Scanner unlocked! Make one inventory slot free, then press the green depot button to collect it.", ChatFormatting.YELLOW);
            return false;
        }
        if (!player.getInventory().add(new ItemStack(SolariaItems.SCANNER))) return false;
        player.containerMenu.broadcastChanges();
        tell(player, "You earned a Solaria Scanner! Right-click it when you are ready to explore your drawing.", ChatFormatting.GOLD);
        return true;
    }

    private static boolean ensureBuilt(MinecraftServer server) {
        ServerLevel hub = server.getLevel(DIMENSION);
        if (hub == null) return false;
        if (!hub.getAttachedOrElse(Data.BUILT, false)) {
            HubLayout.build(hub);
            hub.setAttached(Data.BUILT, true);
        }
        return true;
    }

    private static boolean enter(ServerPlayer player, MinecraftServer server, boolean enroll) {
        if (!ensureBuilt(server)) {
            tell(player, "[PLACEMENT_BLOCKED] Intro dimension is unavailable. Ask your teacher to check the world data.", ChatFormatting.RED);
            return false;
        }
        LessonReturn oldReturn = player.getAttached(Data.RETURN);
        boolean oldRequired = enrolled(player);
        // Keep the first lesson return across repeated entries until the player actually leaves.
        if (!player.level().dimension().equals(DIMENSION) && oldReturn == null) {
            // From a planet, return to the saved lesson spot rather than into space.
            LessonReturn planetAnchor = player.getAttached(LessonReturn.Attachment.ANCHOR);
            boolean onPlanet = net.kyrangit.solaria.dimension.SolariaDimensions
                .classFromDimensionId(player.level().dimension().identifier().toString()) != null;
            player.setAttached(Data.RETURN, onPlanet && planetAnchor != null ? planetAnchor
                : new LessonReturn(player.level().dimension(), player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer().getName()));
        }
        if (enroll) player.setAttached(Data.REQUIRED, true);
        ServerPlayer arrived = teleportToHub(player, server);
        if (arrived == null) {
            if (oldReturn == null) player.removeAttached(Data.RETURN); else player.setAttached(Data.RETURN, oldReturn);
            player.setAttached(Data.REQUIRED, oldRequired);
            return false;
        }
        arrived.setGameMode(GameType.ADVENTURE);
        // The planet trip is now superseded by the academy return point.
        arrived.removeAttached(LessonReturn.Attachment.ANCHOR);
        arrived.getAbilities().flying = false;
        arrived.onUpdateAbilities();
        tell(arrived, "Welcome to Solaria Academy! Explore the three stations ahead (Planets, Sun, Moon) in any order. Right-click an answer button. Progress: "
            + progress(arrived) + "/3. /solaria intro status shows your mission.", ChatFormatting.AQUA);
        return true;
    }

    private static ServerPlayer teleportToHub(ServerPlayer player, MinecraftServer server) {
        ServerLevel hub = server.getLevel(DIMENSION);
        if (hub == null) return null;
        return player.teleport(new TeleportTransition(hub,
            new Vec3(HubLayout.SPAWN.getX() + 0.5, HubLayout.SPAWN.getY(), HubLayout.SPAWN.getZ() + 0.5),
            Vec3.ZERO, 180, 0, TeleportTransition.DO_NOTHING));
    }

    private static boolean leave(ServerPlayer player, MinecraftServer server) {
        if (!player.level().dimension().equals(DIMENSION)) {
            tell(player, "You are not in the intro hub.", ChatFormatting.YELLOW);
            return false;
        }
        if (!canScan(player)) {
            tell(player, "Finish the three stations first. You can do it! Progress: " + progress(player) + "/3.", ChatFormatting.YELLOW);
            return false;
        }
        if (!reward(player)) return false;
        LessonReturn anchor = player.getAttached(Data.RETURN);
        if (anchor == null || server.getLevel(anchor.dimension()) == null || anchor.dimension().equals(DIMENSION)) {
            tell(player, "[PLACEMENT_BLOCKED] No safe lesson return is available. Ask your teacher to teleport you to a safe lesson location.", ChatFormatting.RED);
            return false;
        }
        ServerPlayer arrived = player.teleport(new TeleportTransition(server.getLevel(anchor.dimension()),
            new Vec3(anchor.x(), anchor.y(), anchor.z()), Vec3.ZERO, anchor.yaw(), anchor.pitch(), TeleportTransition.DO_NOTHING));
        if (arrived == null) return false;
        arrived.setGameMode(GameType.byName(anchor.gameMode(), GameType.SURVIVAL));
        arrived.removeAttached(Data.RETURN);
        tell(arrived, "Welcome back, explorer! Your scanner is ready. Ask your teacher to start the main lesson.", ChatFormatting.GREEN);
        return true;
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, ConfigManager config) {
        dispatcher.register(Commands.literal("solaria").then(Commands.literal("intro")
            .executes(c -> status(c.getSource()))
            .then(Commands.literal("status").executes(c -> status(c.getSource())))
            .then(Commands.literal("join").executes(c -> {
                ServerPlayer player = c.getSource().getPlayerOrException();
                return enter(player, c.getSource().getServer(), true) ? 1 : 0;
            }))
            .then(Commands.literal("leave").executes(c -> leave(c.getSource().getPlayerOrException(), c.getSource().getServer()) ? 1 : 0))
            .then(Commands.literal("scanner").executes(c -> reward(c.getSource().getPlayerOrException()) ? 1 : 0))
            .then(Commands.literal("enable").executes(c -> {
                if (!teacher(c.getSource(), config)) return 0;
                if (!ensureBuilt(c.getSource().getServer())) {
                    c.getSource().sendFailure(Component.literal("[PLACEMENT_BLOCKED] Intro dimension is unavailable."));
                    return 0;
                }
                c.getSource().getServer().overworld().setAttached(Data.ENABLED, true);
                c.getSource().sendSuccess(() -> Component.literal("Intro enabled for first-time joiners. Existing players are unchanged; use /solaria intro enroll <players>."), false);
                return 1;
            }))
            .then(Commands.literal("disable").executes(c -> {
                if (!teacher(c.getSource(), config)) return 0;
                c.getSource().getServer().overworld().setAttached(Data.ENABLED, false);
                c.getSource().sendSuccess(() -> Component.literal("Automatic intro disabled. Enrolled players retain their mission and progress."), false);
                return 1;
            }))
            .then(Commands.literal("rebuild").executes(c -> {
                if (!teacher(c.getSource(), config)) return 0;
                ServerLevel hub = c.getSource().getServer().getLevel(DIMENSION);
                if (hub == null) {
                    c.getSource().sendFailure(Component.literal("[PLACEMENT_BLOCKED] Intro dimension is unavailable."));
                    return 0;
                }
                // Rewrites only the hub's own bounded footprint; it never touches lesson dimensions.
                HubLayout.build(hub);
                hub.setAttached(Data.BUILT, true);
                c.getSource().sendSuccess(() -> Component.literal("Solaria Academy hub repaired."), false);
                return 1;
            }))
            .then(Commands.literal("enroll").then(Commands.argument("players", EntityArgument.players()).executes(c -> {
                if (!teacher(c.getSource(), config)) return 0;
                int count = 0;
                for (ServerPlayer player : EntityArgument.getPlayers(c, "players")) if (enter(player, c.getSource().getServer(), true)) count++;
                return count;
            })))
            .then(Commands.literal("unlock").then(Commands.argument("players", EntityArgument.players()).executes(c -> {
                if (!teacher(c.getSource(), config)) return 0;
                int count = 0;
                for (ServerPlayer player : EntityArgument.getPlayers(c, "players")) {
                    player.setAttached(Data.SOLVED, IntroQuiz.ALL);
                    player.removeAttached(Data.LEGACY_PROGRESS);
                    reward(player);
                    count++;
                }
                return count;
            })))));
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        source.sendSuccess(() -> Component.literal("Solaria Academy: automatic intro "
            + (source.getServer().overworld().getAttachedOrElse(Data.ENABLED, false) ? "enabled" : "disabled")
            + (player == null ? "" : "; your progress " + progress(player) + "/3; scanner " + (canScan(player) ? "available" : "locked"))
            + ". Use /solaria intro join, leave, or scanner."), false);
        return 1;
    }

    private static boolean teacher(CommandSourceStack source, ConfigManager config) {
        if (source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(config.current().teacherPermissionLevel())))) return true;
        source.sendFailure(Component.literal("[PERMISSION_DENIED] Ask your teacher to manage the introduction."));
        return false;
    }

    private static void tell(Player player, String text, ChatFormatting colour) {
        player.sendSystemMessage(Component.literal(text).withStyle(colour));
    }
}