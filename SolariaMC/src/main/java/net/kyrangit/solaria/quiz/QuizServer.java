package net.kyrangit.solaria.quiz;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kyrangit.solaria.SolariaMod;
import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Host-side quiz runner. Every LAN player gets their own {@link QuizSession}; the host grades answers and stores
 * best and last scores per body on the player (persistent attachment, survives death and relog).
 */
public final class QuizServer {
	private static final Logger LOG = LoggerFactory.getLogger("solariamod/quiz");
	private static final Map<UUID, Running> SESSIONS = new ConcurrentHashMap<>();
	private static volatile QuizBank bank;
	private static Path overrideDir;

	/** Lazy holder keeps pure quiz tests usable without Fabric's attachment mixins. */
	public static final class Data {
		/** body id -> best score (out of the session length at the time). */
		public static final AttachmentType<Map<String, Integer>> BEST = AttachmentRegistry.create(
			SolariaMod.id("quiz_best_v1"), b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());
		/** body id -> score of the most recently finished session. */
		public static final AttachmentType<Map<String, Integer>> LAST = AttachmentRegistry.create(
			SolariaMod.id("quiz_last_v1"), b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

		private Data() {
		}
	}

	private record Running(AstronomyClass body, QuizSession session, String language) {
	}

	private QuizServer() {
	}

	public static void install(Path configDir) {
		var unused = Data.BEST;
		overrideDir = configDir.resolve("solariamod").resolve("quiz");
		reloadBank();
		QuizPackets.register();
		ServerPlayNetworking.registerGlobalReceiver(QuizPackets.Start.TYPE, (packet, context) -> {
			AstronomyClass body = AstronomyClass.fromId(packet.body());
			if (body == null) return;
			start(context.player(), body);
		});
		ServerPlayNetworking.registerGlobalReceiver(QuizPackets.Answer.TYPE,
			(packet, context) -> answer(context.player(), packet));
		ServerPlayNetworking.registerGlobalReceiver(QuizPackets.Next.TYPE, (packet, context) -> next(context.player()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SESSIONS.clear());
	}

	/** Reloads built-in banks and teacher overrides; returns the number of rejected files. */
	public static int reloadBank() {
		QuizBank loaded = QuizBank.load(overrideDir);
		for (String warning : loaded.warnings()) LOG.warn(warning);
		bank = loaded;
		return loaded.warnings().size();
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("solaria").then(Commands.literal("quiz")
			.then(Commands.argument("body", StringArgumentType.word())
				.suggests((context, builder) -> {
					for (AstronomyClass c : AstronomyClass.values()) builder.suggest(c.id());
					return builder.buildFuture();
				})
				.executes(context -> command(context.getSource(), StringArgumentType.getString(context, "body"))))));
	}

	private static int command(CommandSourceStack source, String bodyId) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("[INPUT_INVALID] This command must be run by a player."));
			return 0;
		}
		AstronomyClass body = AstronomyClass.fromId(bodyId);
		if (body == null) {
			source.sendFailure(Component.literal("[INPUT_INVALID] Unknown body '" + bodyId + "'."));
			return 0;
		}
		if (!ServerPlayNetworking.canSend(player, QuizPackets.Question.TYPE)) {
			source.sendFailure(Component.literal("[CLIENT_MISSING] The quiz needs SolariaMC installed on this computer."));
			return 0;
		}
		return start(player, body) ? 1 : 0;
	}

	static boolean start(ServerPlayer player, AstronomyClass body) {
		var questions = bank.questions(body);
		if (questions.isEmpty()) {
			player.sendSystemMessage(Component.literal("[QUIZ_UNAVAILABLE] The " + body.id()
				+ " quiz could not be loaded. Ask your teacher to check the server log."));
			return false;
		}
		long seed = player.getRandom().nextLong() ^ System.nanoTime();
		QuizSession session = new QuizSession(questions, QuizSession.DEFAULT_LENGTH, seed);
		String language = player.clientInformation().language();
		SESSIONS.put(player.getUUID(), new Running(body, session, language));
		sendQuestion(player, SESSIONS.get(player.getUUID()));
		return true;
	}

	private static void sendQuestion(ServerPlayer player, Running running) {
		QuizSession.Presented p = running.session.current();
		QuizQuestion q = p.question();
		ServerPlayNetworking.send(player, new QuizPackets.Question(running.body.id(), running.session.index(),
			running.session.length(), running.session.score(), q.type().id(), q.difficulty().id(),
			q.prompt(running.language), p.displayChoices(), q.left()));
	}

	private static void answer(ServerPlayer player, QuizPackets.Answer packet) {
		Running running = SESSIONS.get(player.getUUID());
		if (running == null) return;
		QuizSession.Feedback feedback = running.session.answer(packet.answer());
		if (feedback == null) return;
		QuizQuestion q = running.session.current().question();
		ServerPlayNetworking.send(player, new QuizPackets.Feedback(feedback.correct(), feedback.correctDisplayAnswer(),
			q.explanation(running.language), q.source(), running.session.score()));
		play(player, feedback.correct() ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
			feedback.correct() ? 1.4f : 0.7f);
	}

	private static void next(ServerPlayer player) {
		Running running = SESSIONS.get(player.getUUID());
		if (running == null || !running.session.next()) return;
		if (!running.session.finished()) {
			sendQuestion(player, running);
			return;
		}
		SESSIONS.remove(player.getUUID());
		String id = running.body.id();
		int score = running.session.score();
		int total = running.session.length();
		Map<String, Integer> best = new HashMap<>(player.getAttachedOrElse(Data.BEST, Map.of()));
		Map<String, Integer> last = new HashMap<>(player.getAttachedOrElse(Data.LAST, Map.of()));
		int previous = best.getOrDefault(id, -1);
		boolean newBest = score > previous;
		if (newBest) best.put(id, score);
		last.put(id, score);
		player.setAttached(Data.BEST, Map.copyOf(best));
		player.setAttached(Data.LAST, Map.copyOf(last));
		ServerPlayNetworking.send(player, new QuizPackets.Result(id, score, total, Math.max(score, previous),
			newBest && previous >= 0, QuizScoring.message(score, total)));
		play(player, score * 2 >= total ? SoundEvents.PLAYER_LEVELUP : SoundEvents.UI_TOAST_IN, 1.0f);
	}

	private static void play(ServerPlayer player, SoundEvent sound, float pitch) {
		player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.7f, pitch);
	}

	/** Best scores for a player, for teacher commands and tests. */
	public static Map<String, Integer> bestScores(ServerPlayer player) {
		return player.getAttachedOrElse(Data.BEST, Map.of());
	}
}
