package net.kyrangit.solaria.quiz;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One validated question from a body's JSON bank. All four types share {@code choices} and {@code answer}:
 * <ul>
 *   <li>multiple_choice / true_false: {@code answer} holds the single correct choice index.</li>
 *   <li>order: {@code answer[k]} is the choice index that belongs at position {@code k}.</li>
 *   <li>match: {@code answer[i]} is the choice index that pairs with {@code left[i]}.</li>
 * </ul>
 * Text maps are keyed by language ({@code en} required, {@code ms} optional and marked for human review).
 */
public record QuizQuestion(String id, Type type, Difficulty difficulty, String kssm,
	Map<String, String> prompt, List<String> choices, List<String> left, List<Integer> answer,
	Map<String, String> explanation, String source) {

	public QuizQuestion {
		prompt = Map.copyOf(prompt);
		choices = List.copyOf(choices);
		left = List.copyOf(left);
		answer = List.copyOf(answer);
		explanation = Map.copyOf(explanation);
	}

	public enum Type {
		MULTIPLE_CHOICE("multiple_choice"), TRUE_FALSE("true_false"), ORDER("order"), MATCH("match");

		private final String id;

		Type(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public static Type fromId(String id) {
			for (Type t : values()) if (t.id.equals(id)) return t;
			return null;
		}
	}

	public enum Difficulty {
		EASY("easy"), MEDIUM("medium"), HARD("hard");

		private final String id;

		Difficulty(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public static Difficulty fromId(String id) {
			for (Difficulty d : values()) if (d.id.equals(id)) return d;
			return null;
		}
	}

	public String prompt(String language) {
		return localized(prompt, language);
	}

	public String explanation(String language) {
		return localized(explanation, language);
	}

	/** Malay is used only when the client runs in Malay and the bank provides it; English otherwise. */
	static String localized(Map<String, String> text, String language) {
		if (language != null && language.toLowerCase(Locale.ROOT).startsWith("ms")) {
			String ms = text.get("ms");
			if (ms != null && !ms.isBlank()) return ms;
		}
		return text.get("en");
	}
}
