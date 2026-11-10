package net.kyrangit.solaria.quiz;

/** Pure end-of-quiz wording, kept separate so it is testable without Minecraft. */
public final class QuizScoring {
	private QuizScoring() {
	}

	public static int percent(int score, int total) {
		if (total <= 0) return 0;
		return Math.round(score * 100f / total);
	}

	public static String message(int score, int total) {
		int percent = percent(score, total);
		if (percent >= 100) return "Perfect score! Mission control is impressed.";
		if (percent >= 75) return "Great work, explorer. You know this world well.";
		if (percent >= 50) return "Good effort. Fly around the body again and retry to beat your best.";
		return "Keep exploring. Each retry draws different questions.";
	}
}
