package net.kyrangit.solaria.quiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * One student's run through a randomized subset of a body's bank. Pure logic with no Minecraft types, so it
 * is fully testable. The client only ever sees "display" indices; the session maps them back to bank indices,
 * so the shuffled layout never leaks the stored answer order.
 */
public final class QuizSession {
	public static final int DEFAULT_LENGTH = 8;

	private final List<Presented> questions;
	private int index;
	private int score;
	private boolean answered;
	private final List<Boolean> results = new ArrayList<>();

	public QuizSession(List<QuizQuestion> bank, int length, long seed) {
		if (bank.isEmpty()) throw new IllegalArgumentException("empty quiz bank");
		Random random = new Random(seed);
		List<QuizQuestion> picked = pick(bank, Math.min(length, bank.size()), random);
		List<Presented> presented = new ArrayList<>();
		for (QuizQuestion q : picked) presented.add(present(q, random));
		this.questions = List.copyOf(presented);
	}

	/**
	 * Draws a random subset that covers as many question types as possible, then orders it easy to hard so a
	 * session ramps up. Ties keep the random draw order.
	 */
	static List<QuizQuestion> pick(List<QuizQuestion> bank, int length, Random random) {
		List<QuizQuestion> pool = new ArrayList<>(bank);
		Collections.shuffle(pool, random);
		List<QuizQuestion> chosen = new ArrayList<>();
		Set<QuizQuestion.Type> covered = EnumSet.noneOf(QuizQuestion.Type.class);
		for (QuizQuestion q : pool) {
			if (chosen.size() < length && covered.add(q.type())) chosen.add(q);
		}
		for (QuizQuestion q : pool) {
			if (chosen.size() >= length) break;
			if (!chosen.contains(q)) chosen.add(q);
		}
		List<QuizQuestion> ordered = new ArrayList<>(chosen);
		ordered.sort(Comparator.comparingInt(q -> q.difficulty().ordinal()));
		return ordered;
	}

	static Presented present(QuizQuestion q, Random random) {
		int n = q.choices().size();
		List<Integer> perm = new ArrayList<>();
		for (int i = 0; i < n; i++) perm.add(i);
		if (q.type() != QuizQuestion.Type.TRUE_FALSE) {
			Collections.shuffle(perm, random);
			// Order and match must never start out already solved.
			if ((q.type() == QuizQuestion.Type.ORDER || q.type() == QuizQuestion.Type.MATCH) && perm.equals(q.answer())) {
				Collections.swap(perm, 0, 1);
			}
		}
		return new Presented(q, List.copyOf(perm));
	}

	public int length() {
		return questions.size();
	}

	public int index() {
		return index;
	}

	public int score() {
		return score;
	}

	public boolean answered() {
		return answered;
	}

	public boolean finished() {
		return index >= questions.size();
	}

	public List<Boolean> results() {
		return List.copyOf(results);
	}

	public Presented current() {
		if (finished()) throw new IllegalStateException("quiz finished");
		return questions.get(index);
	}

	public List<Presented> questions() {
		return questions;
	}

	/** Grades the current question once. Further answers before {@link #next()} are rejected with null. */
	public Feedback answer(List<Integer> displayAnswer) {
		if (finished() || answered) return null;
		Presented p = current();
		boolean correct = p.isCorrect(displayAnswer);
		answered = true;
		results.add(correct);
		if (correct) score++;
		return new Feedback(correct, p.correctDisplayAnswer());
	}

	/** Moves on after feedback. Returns false (and does nothing) if the current question is unanswered. */
	public boolean next() {
		if (finished() || !answered) return false;
		index++;
		answered = false;
		return true;
	}

	public record Feedback(boolean correct, List<Integer> correctDisplayAnswer) {
	}

	/** A question as the student sees it: {@code display[i] = choices[permutation[i]]}. */
	public record Presented(QuizQuestion question, List<Integer> permutation) {
		public List<String> displayChoices() {
			List<String> out = new ArrayList<>();
			for (int bankIndex : permutation) out.add(question.choices().get(bankIndex));
			return out;
		}

		private int displayOf(int bankIndex) {
			return permutation.indexOf(bankIndex);
		}

		/** The expected answer expressed in display indices, sent back to the client for feedback. */
		public List<Integer> correctDisplayAnswer() {
			List<Integer> out = new ArrayList<>();
			for (int bankIndex : question.answer()) out.add(displayOf(bankIndex));
			return out;
		}

		public boolean isCorrect(List<Integer> displayAnswer) {
			if (displayAnswer == null) return false;
			int n = permutation.size();
			for (Integer d : displayAnswer) {
				if (d == null || d < 0 || d >= n) return false;
			}
			return switch (question.type()) {
				case MULTIPLE_CHOICE, TRUE_FALSE -> displayAnswer.size() == 1
					&& permutation.get(displayAnswer.getFirst()).equals(question.answer().getFirst());
				case ORDER, MATCH -> {
					if (!QuizBank.isPermutation(displayAnswer, n)) yield false;
					for (int k = 0; k < n; k++) {
						if (!permutation.get(displayAnswer.get(k)).equals(question.answer().get(k))) yield false;
					}
					yield true;
				}
			};
		}
	}
}
