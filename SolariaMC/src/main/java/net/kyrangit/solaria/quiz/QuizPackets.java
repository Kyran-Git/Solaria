package net.kyrangit.solaria.quiz;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.kyrangit.solaria.SolariaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Bounded quiz packets. The host owns the session and grading; the client only receives display text and
 * sends display indices, so a modified client cannot read the answer before submitting.
 */
public final class QuizPackets {
	public static final int MAX_ITEMS = 6;
	private static final int TEXT = 512;

	private QuizPackets() {
	}

	/** Client asks to (re)start the quiz for one body. */
	public record Start(String body) implements CustomPacketPayload {
		public static final Type<Start> TYPE = new Type<>(SolariaMod.id("quiz_start_v1"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = CustomPacketPayload.codec(
			(p, b) -> b.writeUtf(p.body, 32), b -> new Start(b.readUtf(32)));

		@Override
		public Type<Start> type() {
			return TYPE;
		}
	}

	public record Question(String body, int index, int total, int score, String kind, String difficulty,
		String prompt, List<String> choices, List<String> left) implements CustomPacketPayload {
		public static final Type<Question> TYPE = new Type<>(SolariaMod.id("quiz_question_v1"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Question> CODEC = CustomPacketPayload.codec(
			(p, b) -> {
				b.writeUtf(p.body, 32);
				b.writeVarInt(p.index);
				b.writeVarInt(p.total);
				b.writeVarInt(p.score);
				b.writeUtf(p.kind, 32);
				b.writeUtf(p.difficulty, 16);
				b.writeUtf(p.prompt, TEXT);
				writeStrings(b, p.choices);
				writeStrings(b, p.left);
			},
			b -> new Question(b.readUtf(32), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(32),
				b.readUtf(16), b.readUtf(TEXT), readStrings(b), readStrings(b)));

		@Override
		public Type<Question> type() {
			return TYPE;
		}
	}

	public record Answer(List<Integer> answer) implements CustomPacketPayload {
		public static final Type<Answer> TYPE = new Type<>(SolariaMod.id("quiz_answer_v1"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = CustomPacketPayload.codec(
			(p, b) -> writeInts(b, p.answer), b -> new Answer(readInts(b)));

		@Override
		public Type<Answer> type() {
			return TYPE;
		}
	}

	public record Feedback(boolean correct, List<Integer> correctAnswer, String explanation, String source,
		int score) implements CustomPacketPayload {
		public static final Type<Feedback> TYPE = new Type<>(SolariaMod.id("quiz_feedback_v1"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Feedback> CODEC = CustomPacketPayload.codec(
			(p, b) -> {
				b.writeBoolean(p.correct);
				writeInts(b, p.correctAnswer);
				b.writeUtf(p.explanation, TEXT);
				b.writeUtf(p.source, TEXT);
				b.writeVarInt(p.score);
			},
			b -> new Feedback(b.readBoolean(), readInts(b), b.readUtf(TEXT), b.readUtf(TEXT), b.readVarInt()));

		@Override
		public Type<Feedback> type() {
			return TYPE;
		}
	}

	/** Client acknowledges feedback and asks for the next question (or the result). */
	public record Next() implements CustomPacketPayload {
		public static final Type<Next> TYPE = new Type<>(SolariaMod.id("quiz_next_v1"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Next> CODEC = CustomPacketPayload.codec(
			(p, b) -> {
			}, b -> new Next());

		@Override
		public Type<Next> type() {
			return TYPE;
		}
	}

	public record Result(String body, int score, int total, int best, boolean newBest, String message)
		implements CustomPacketPayload {
		public static final Type<Result> TYPE = new Type<>(SolariaMod.id("quiz_result_v1"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = CustomPacketPayload.codec(
			(p, b) -> {
				b.writeUtf(p.body, 32);
				b.writeVarInt(p.score);
				b.writeVarInt(p.total);
				b.writeVarInt(p.best);
				b.writeBoolean(p.newBest);
				b.writeUtf(p.message, TEXT);
			},
			b -> new Result(b.readUtf(32), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(),
				b.readUtf(TEXT)));

		@Override
		public Type<Result> type() {
			return TYPE;
		}
	}

	private static void writeStrings(RegistryFriendlyByteBuf b, List<String> values) {
		b.writeVarInt(Math.min(values.size(), MAX_ITEMS));
		for (int i = 0; i < Math.min(values.size(), MAX_ITEMS); i++) b.writeUtf(values.get(i), 128);
	}

	private static List<String> readStrings(RegistryFriendlyByteBuf b) {
		int n = b.readVarInt();
		if (n < 0 || n > MAX_ITEMS) throw new IllegalArgumentException("too many quiz items");
		List<String> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) out.add(b.readUtf(128));
		return out;
	}

	private static void writeInts(RegistryFriendlyByteBuf b, List<Integer> values) {
		b.writeVarInt(Math.min(values.size(), MAX_ITEMS));
		for (int i = 0; i < Math.min(values.size(), MAX_ITEMS); i++) b.writeVarInt(values.get(i));
	}

	private static List<Integer> readInts(RegistryFriendlyByteBuf b) {
		int n = b.readVarInt();
		if (n < 0 || n > MAX_ITEMS) throw new IllegalArgumentException("too many quiz answers");
		List<Integer> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) out.add(b.readVarInt());
		return out;
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Start.TYPE, Start.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Answer.TYPE, Answer.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Next.TYPE, Next.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Question.TYPE, Question.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Feedback.TYPE, Feedback.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Result.TYPE, Result.CODEC);
	}
}
