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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Opt-in classroom onboarding. All quiz/reward decisions are made by the server. */
public final class IntroHub {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, SolariaMod.id("intro_hub"));

    /** Lazy holder keeps pure quiz/layout tests usable without Fabric's attachment mixins. */
    public static final class Data {
        public static final AttachmentType<Boolean> ENABLED = AttachmentRegistry.create(SolariaMod.id("intro_enabled"),
            b -> b.persistent(Codec.BOOL));
        /** v5: flat void-glass floor with a starry pit below it, so existing hubs rebuild once. */
        public static final AttachmentType<Boolean> BUILT = AttachmentRegistry.create(SolariaMod.id("intro_built_v5"),
            b -> b.persistent(Codec.BOOL));
        /** Still registered only so older worlds load without "unknown attachment" warnings; never read. */
        public static final AttachmentType<Boolean> LEGACY_BUILT_V4 = AttachmentRegistry.create(SolariaMod.id("intro_built_v4"),
            b -> b.persistent(Codec.BOOL));
        /** Still registered only so older worlds load without "unknown attachment" warnings; never read. */
        public static final AttachmentType<Boolean> LEGACY_BUILT_V3 = AttachmentRegistry.create(SolariaMod.id("intro_built_v3"),
            b -> b.persistent(Codec.BOOL));
        /** Still registered only so older worlds load without "unknown attachment" warnings; never read. */
        public static final AttachmentType<Boolean> LEGACY_BUILT_V2 = AttachmentRegistry.create(SolariaMod.id("intro_built_v2"),
            b -> b.persistent(Codec.BOOL));
        /** Still registered only so older worlds load without "unknown attachment" warnings; never read. */
        public static final AttachmentType<Boolean> LEGACY_BUILT_V1 = AttachmentRegistry.create(SolariaMod.id("intro_built_v1"),
            b -> b.persistent(Codec.BOOL));
        public static final AttachmentType<Boolean> SEEN = AttachmentRegistry.create(SolariaMod.id("intro_seen"),
            b -> b.persistent(Codec.BOOL).copyOnDeath());
        public static final AttachmentType<Boolean> REQUIRED = AttachmentRegistry.create(SolariaMod.id("intro_required"),
            b -> b.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));
        /** Orbit Builder slots filled (see {@link OrbitPuzzle}); synced so the client knows when scanning unlocks. */
        public static final AttachmentType<Integer> ORBIT = AttachmentRegistry.create(SolariaMod.id("intro_orbit"),
            b -> b.persistent(OrbitPuzzle.CODEC).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
        /** Bodies this player has arrived at through Build or /solaria visit (see {@link StarChart}). */
        public static final AttachmentType<Integer> STAR_CHART = AttachmentRegistry.create(SolariaMod.id("star_chart"),
            b -> b.persistent(StarChart.CODEC).copyOnDeath());
        /** Legacy three-station quiz mask; read once to migrate finished players, then removed. */
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

    /** Selected supply core per player; transient, so a restart simply asks them to pick again. */
    private static final Map<UUID, Integer> HELD = new ConcurrentHashMap<>();
    /** Stage-3 round start (overworld game time) per player; transient, a restart just needs START again. */
    private static final Map<UUID, Long> ROUND = new ConcurrentHashMap<>();

    /** Orbit Builder slots filled. Server-side fallback treats an unmigrated finished quiz as complete. */
    public static int orbit(Player player) {
        Integer orbit = player.getAttached(Data.ORBIT);
        if (orbit != null) return orbit;
        return legacyFinished(player) ? OrbitPuzzle.ALL : 0;
    }

    /** Stage reached, 0..3 (3 = complete). */
    public static int stage(Player player) {
        return OrbitPuzzle.stage(orbit(player));
    }

    private static boolean legacyFinished(Player player) {
        Integer solved = player.getAttached(Data.SOLVED);
        Integer legacy = player.getAttached(Data.LEGACY_PROGRESS);
        return (solved != null && IntroQuiz.complete(solved)) || (legacy != null && IntroQuiz.fromLegacyCount(legacy) == IntroQuiz.ALL);
    }

    /** Students who already finished the old quiz keep their scanner; anyone partway restarts with the new mission. */
    private static void migrateLegacyProgress(ServerPlayer player) {
        if (!player.hasAttached(Data.SOLVED) && !player.hasAttached(Data.LEGACY_PROGRESS)) return;
        if (!player.hasAttached(Data.ORBIT)) player.setAttached(Data.ORBIT, legacyFinished(player) ? OrbitPuzzle.ALL : 0);
        player.removeAttached(Data.SOLVED);
        player.removeAttached(Data.LEGACY_PROGRESS);
    }

    public static boolean enrolled(Player player) {
        return player.getAttachedOrElse(Data.REQUIRED, false);
    }

    public static boolean canScan(Player player) {
        return !enrolled(player) || OrbitPuzzle.complete(orbit(player));
    }

    /** Records an arrival on the student's star chart and tells them what is left; called after a successful planet teleport. */
    public static void chartArrival(ServerPlayer player, net.kyrangit.solaria.blueprint.AstronomyClass body) {
        int before = player.getAttachedOrElse(Data.STAR_CHART, 0);
        int after = StarChart.visit(before, body);
        if (after == before) return;
        player.setAttached(Data.STAR_CHART, after);
        tell(player, "New entry on your star chart! " + StarChart.describe(after), ChatFormatting.LIGHT_PURPLE);
    }

    private static String progressText(int mask) {
        return "Stage " + Math.min(OrbitPuzzle.stage(mask) + 1, 3) + "/3, " + OrbitPuzzle.count(mask) + "/" + OrbitPuzzle.SLOTS + " cores placed";
    }

    public static void install() {
        var unused = Data.ORBIT;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            // Defer teleport until the join packet sequence has completed.
            server.execute(() -> {
                if (player.hasDisconnected()) return;
                migrateLegacyProgress(player);
                // The timed round never survives a reconnect: clear half-placed giants so START restarts it fairly.
                int mask = orbit(player);
                if (OrbitPuzzle.stage(mask) == 2 && OrbitPuzzle.expire(mask) != mask) player.setAttached(Data.ORBIT, OrbitPuzzle.expire(mask));
                boolean seen = player.getAttachedOrElse(Data.SEEN, false);
                player.setAttached(Data.SEEN, true);
                if (!seen && server.overworld().getAttachedOrElse(Data.ENABLED, false)) enter(player, server, true);
                else if (enrolled(player) && !canScan(player)) enter(player, server, false);
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HELD.remove(handler.getPlayer().getUUID());
            ROUND.remove(handler.getPlayer().getUUID());
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
            long now = server.overworld().getGameTime();
            if (now % 20 != 0) return;
            for (ServerPlayer player : java.util.List.copyOf(server.getPlayerList().getPlayers())) {
                boolean inHub = player.level().dimension().equals(DIMENSION);
                if (!inHub) ROUND.remove(player.getUUID());
                else {
                    tickRound(player, now);
                    // Chunk reloads drop the client-only core blocks, so refresh them every few seconds.
                    if (now % 60 == 0) showCores(player);
                }
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
            for (int core = 0; core < OrbitPuzzle.CORES.size(); core++) {
                if (button.equals(HubLayout.supply(core))) {
                    pick(serverPlayer, core);
                    return InteractionResult.SUCCESS;
                }
            }
            for (int slot = 0; slot < OrbitPuzzle.SLOTS; slot++) {
                if (button.equals(HubLayout.pedestal(slot))) {
                    place(serverPlayer, slot);
                    return InteractionResult.SUCCESS;
                }
            }
            if (button.equals(HubLayout.START)) startRound(serverPlayer);
            if (button.equals(HubLayout.TERMINAL)) reward(serverPlayer);
            if (button.equals(HubLayout.EXIT)) leave(serverPlayer, serverPlayer.level().getServer());
            return InteractionResult.SUCCESS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, face) ->
            level.dimension().equals(DIMENSION) && !player.isCreative() ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) ->
            !level.dimension().equals(DIMENSION) || player.isCreative());
    }

    private static void pick(ServerPlayer player, int core) {
        if (OrbitPuzzle.complete(orbit(player))) {
            tell(player, "Every orbit is filled! Collect your scanner at the green depot.", ChatFormatting.AQUA);
            return;
        }
        HELD.put(player.getUUID(), core);
        player.sendOverlayMessage(Component.literal("Holding the " + OrbitPuzzle.name(core) + " core. Press an orbit slot button to place it.")
            .withStyle(ChatFormatting.AQUA));
        player.playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.6f, 1.4f);
    }

    private static void place(ServerPlayer player, int slot) {
        int before = orbit(player);
        if (OrbitPuzzle.placed(before, slot)) {
            player.sendOverlayMessage(Component.literal("That orbit already has its " + OrbitPuzzle.name(OrbitPuzzle.expected(slot)) + " core.")
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        int stage = OrbitPuzzle.stage(before);
        if (OrbitPuzzle.slotStage(slot) != stage) {
            tell(player, OrbitPuzzle.slotStage(slot) > stage ? "That slot is locked for now. " + OrbitPuzzle.objective(before)
                : "That stage is done. " + OrbitPuzzle.objective(before), ChatFormatting.YELLOW);
            return;
        }
        Integer core = HELD.get(player.getUUID());
        if (core == null) {
            tell(player, "Your hands are empty. Press a core button on the supply wall first.", ChatFormatting.YELLOW);
            return;
        }
        long now = player.level().getServer().overworld().getGameTime();
        if (stage == 2 && !ROUND.containsKey(player.getUUID())) {
            tell(player, "Press the START button to begin the timed round before placing a giant.", ChatFormatting.YELLOW);
            return;
        }
        int after = OrbitPuzzle.place(before, slot, core);
        ServerLevel level = (ServerLevel) player.level();
        BlockPos marker = HubLayout.marker(slot);
        if (after == before) {
            player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
            level.sendParticles(player, ParticleTypes.SMOKE, true, false, marker.getX() + 0.5, marker.getY() + 0.5, marker.getZ() + 0.5,
                8, 0.2, 0.2, 0.2, 0.01);
            String penalty = "";
            if (stage == 2) {
                // Move the round start back so the remaining time drops, without letting it go below zero.
                ROUND.computeIfPresent(player.getUUID(), (id, start) -> start - OrbitPuzzle.PENALTY_TICKS);
                penalty = " (-10 s, " + OrbitPuzzle.secondsLeft(ROUND.getOrDefault(player.getUUID(), now), now) + " s left)";
            }
            tell(player, "Not quite: the " + OrbitPuzzle.name(core) + " core does not belong there" + penalty + ". " + OrbitPuzzle.hint(slot),
                ChatFormatting.YELLOW);
            return;
        }
        player.setAttached(Data.ORBIT, after);
        HELD.remove(player.getUUID());
        showCore(player, slot);
        level.sendParticles(player, ParticleTypes.END_ROD, true, false, marker.getX() + 0.5, marker.getY() + 0.5, marker.getZ() + 0.5,
            16, 0.3, 0.3, 0.3, 0.03);
        player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 0.7f + 0.1f * (OrbitPuzzle.count(after) % 9));
        player.sendOverlayMessage(Component.literal("Correct! " + OrbitPuzzle.name(core) + " is in place. " + progressText(after) + ".")
            .withStyle(ChatFormatting.GREEN));
        int newStage = OrbitPuzzle.stage(after);
        if (newStage == stage) return;
        ROUND.remove(player.getUUID());
        if (newStage == 3) {
            title(player, "Orbit Builder complete!", "Your scanner is unlocked");
            player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            level.sendParticles(player, ParticleTypes.FIREWORK, true, false, HubLayout.TERMINAL.getX() + 0.5, HubLayout.TERMINAL.getY() + 1.0,
                HubLayout.TERMINAL.getZ() + 0.5, 40, 0.6, 0.6, 0.6, 0.08);
            tell(player, "Explorer mission complete! You rebuilt the Solar System. Visit the green depot, then the cyan lesson exit.", ChatFormatting.GOLD);
            reward(player);
        } else {
            title(player, "Stage " + (newStage + 1) + " unlocked", newStage == 1 ? "Outer planets and the Moon" : "Timed: giant planet clues");
            player.playSound(SoundEvents.PLAYER_LEVELUP, 0.7f, 1.0f);
            tell(player, OrbitPuzzle.objective(after), ChatFormatting.AQUA);
        }
    }

    private static void startRound(ServerPlayer player) {
        int mask = orbit(player);
        int stage = OrbitPuzzle.stage(mask);
        if (stage != 2) {
            tell(player, stage < 2 ? "The timed round unlocks at stage 3. " + OrbitPuzzle.objective(mask)
                : "You already finished the timed round!", ChatFormatting.YELLOW);
            return;
        }
        long now = player.level().getServer().overworld().getGameTime();
        Long running = ROUND.get(player.getUUID());
        if (running != null && !OrbitPuzzle.expired(running, now)) {
            player.sendOverlayMessage(Component.literal(OrbitPuzzle.secondsLeft(running, now) + " seconds left. Read the clue signs!")
                .withStyle(ChatFormatting.GOLD));
            return;
        }
        ROUND.put(player.getUUID(), now);
        title(player, "GO!", "Match each giant to its clue: 120 seconds");
        player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.0f);
    }

    private static void tickRound(ServerPlayer player, long now) {
        Long start = ROUND.get(player.getUUID());
        if (start == null) return;
        int mask = orbit(player);
        if (OrbitPuzzle.stage(mask) != 2) {
            ROUND.remove(player.getUUID());
            return;
        }
        if (OrbitPuzzle.expired(start, now)) {
            ROUND.remove(player.getUUID());
            int reset = OrbitPuzzle.expire(mask);
            player.setAttached(Data.ORBIT, reset);
            for (int slot = 9; slot < OrbitPuzzle.SLOTS; slot++) showCore(player, slot);
            player.playSound(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.8f, 0.8f);
            title(player, "Time's up!", "Press START to try the giants again");
            tell(player, "Time's up! The giant slots reset, but stages 1 and 2 are saved. Press START when you are ready.", ChatFormatting.YELLOW);
            return;
        }
        int left = OrbitPuzzle.secondsLeft(start, now);
        if (left <= 10) player.playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.5f, 2.0f);
        player.sendOverlayMessage(Component.literal("Giant clues: " + left + " s left").withStyle(left <= 10 ? ChatFormatting.RED : ChatFormatting.GOLD));
    }

    /** Lights a placed core on this player's client only, so every student sees their own progress on a shared hub. */
    private static void showCore(ServerPlayer player, int slot) {
        int mask = orbit(player);
        BlockState state = OrbitPuzzle.placed(mask, slot) ? coreBlock(OrbitPuzzle.expected(slot)) : Blocks.AIR.defaultBlockState();
        player.connection.send(new ClientboundBlockUpdatePacket(HubLayout.marker(slot), state));
    }

    private static void showCores(ServerPlayer player) {
        if (orbit(player) == 0) return;
        for (int slot = 0; slot < OrbitPuzzle.SLOTS; slot++) showCore(player, slot);
    }

    /** Colour hints only; real blocks are never placed at the markers. */
    public static BlockState coreBlock(int core) {
        Block block = switch (OrbitPuzzle.CORES.get(core)) {
            case "mercury" -> Blocks.TUFF;
            case "venus" -> Blocks.YELLOW_TERRACOTTA;
            case "earth" -> Blocks.LIGHT_BLUE_CONCRETE;
            case "mars" -> Blocks.RED_TERRACOTTA;
            case "jupiter" -> Blocks.ORANGE_TERRACOTTA;
            case "saturn" -> Blocks.YELLOW_CONCRETE;
            case "uranus" -> Blocks.LIGHT_BLUE_TERRACOTTA;
            case "neptune" -> Blocks.BLUE_CONCRETE;
            default -> Blocks.SMOOTH_STONE;
        };
        return block.defaultBlockState();
    }

    private static void title(ServerPlayer player, String title, String subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle).withStyle(ChatFormatting.AQUA)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(ChatFormatting.GOLD)));
    }

    private static boolean reward(ServerPlayer player) {
        if (!OrbitPuzzle.complete(orbit(player))) {
            tell(player, "Finish the Orbit Builder to earn your scanner. " + progressText(orbit(player)) + ". " + OrbitPuzzle.objective(orbit(player)),
                ChatFormatting.YELLOW);
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
        HELD.remove(arrived.getUUID());
        ROUND.remove(arrived.getUUID());
        int mask = orbit(arrived);
        title(arrived, "Solaria Academy", OrbitPuzzle.complete(mask) ? "Mission complete" : "Orbit Builder: rebuild the Solar System");
        tell(arrived, "Welcome to Solaria Academy! The Sun's orbit slots have lost their planets. Press a core on the supply wall, then press the "
            + "orbit slot where it belongs. " + progressText(mask) + ". " + OrbitPuzzle.objective(mask), ChatFormatting.AQUA);
        // Wait a moment so the hub chunks reach the client before the per-player cores are drawn.
        arrived.level().getServer().execute(() -> showCores(arrived));
        return true;
    }

    private static ServerPlayer teleportToHub(ServerPlayer player, MinecraftServer server) {
        ServerLevel hub = server.getLevel(DIMENSION);
        if (hub == null) return null;
        return player.teleport(new TeleportTransition(hub,
            new Vec3(HubLayout.SPAWN.getX() + 0.5, HubLayout.SPAWN.getY(), HubLayout.SPAWN.getZ() + 0.5),
            Vec3.ZERO, 180, 0, TeleportTransition.DO_NOTHING));
    }

    /** Also backs {@code /solaria return} while inside the academy, which saves its own return point. */
    public static boolean leave(ServerPlayer player, MinecraftServer server) {
        if (!player.level().dimension().equals(DIMENSION)) {
            tell(player, "You are not in the intro hub.", ChatFormatting.YELLOW);
            return false;
        }
        if (!canScan(player)) {
            tell(player, "Finish the Orbit Builder first. You can do it! " + progressText(orbit(player)) + ". " + OrbitPuzzle.objective(orbit(player)),
                ChatFormatting.YELLOW);
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
                    player.setAttached(Data.ORBIT, OrbitPuzzle.ALL);
                    player.removeAttached(Data.SOLVED);
                    player.removeAttached(Data.LEGACY_PROGRESS);
                    ROUND.remove(player.getUUID());
                    if (player.level().dimension().equals(DIMENSION)) showCores(player);
                    reward(player);
                    count++;
                }
                return count;
            })))
            .then(Commands.literal("chart").executes(c -> {
                ServerPlayer player = c.getSource().getPlayerOrException();
                c.getSource().sendSuccess(() -> Component.literal(StarChart.describe(player.getAttachedOrElse(Data.STAR_CHART, 0)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                return 1;
            }))));
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        source.sendSuccess(() -> Component.literal("Solaria Academy: automatic intro "
            + (source.getServer().overworld().getAttachedOrElse(Data.ENABLED, false) ? "enabled" : "disabled")
            + (player == null ? "." : "; Orbit Builder " + progressText(orbit(player)) + "; scanner " + (canScan(player) ? "available" : "locked")
                + ". " + OrbitPuzzle.objective(orbit(player)) + " " + StarChart.describe(player.getAttachedOrElse(Data.STAR_CHART, 0)))
            + " Use /solaria intro join, leave, scanner, or chart."), false);
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