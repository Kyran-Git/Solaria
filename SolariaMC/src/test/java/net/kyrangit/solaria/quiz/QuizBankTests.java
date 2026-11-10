package net.kyrangit.solaria.quiz;

import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import net.kyrangit.solaria.blueprint.AstronomyClass;

/** Pure quiz-bank, selection and grading tests. No Minecraft or Fabric runtime is touched. */
public final class QuizBankTests {
	private static int assertions;

	private QuizBankTests() {
	}

	public static void main(String[] args) throws Exception {
		builtInBanks();
		resourceDirectoryHoldsOnlyTheTenBodies();
		selectionIsSeededAndRampsUp();
		gradingUsesDisplayIndices();
		sessionStateMachine();
		puzzlesNeverStartSolved();
		malformedFilesAreRejected();
		overridesFallBackWhenInvalid();
		localizationFallsBackToEnglish();
		scoringTiers();
		System.out.println("QuizBankTests: " + assertions + " assertions passed");
	}

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}

	private static void builtInBanks() throws Exception {
		Set<String> ids = new HashSet<>();
		check(AstronomyClass.values().length == 10, "exactly ten supported bodies");
		for (AstronomyClass body : AstronomyClass.values()) {
			List<QuizQuestion> bank = QuizBank.loadBuiltIn(body);
			check(bank.size() >= 10, body.id() + " needs at least 10 questions, has " + bank.size());
			Set<QuizQuestion.Type> types = EnumSet.noneOf(QuizQuestion.Type.class);
			Set<QuizQuestion.Difficulty> difficulties = EnumSet.noneOf(QuizQuestion.Difficulty.class);
			for (QuizQuestion q : bank) {
				types.add(q.type());
				difficulties.add(q.difficulty());
				check(ids.add(q.id()), "question id is globally unique: " + q.id());
				check(q.id().startsWith(body.id() + "_"), "id is prefixed with the body: " + q.id());
				String host = URI.create(q.source()).getHost();
				check(host != null && (host.endsWith("nasa.gov") || host.endsWith("esa.int") || host.endsWith("moe.gov.my")),
					q.id() + " cites NASA, ESA or KSSM, got " + q.source());
				check(!q.prompt("en").toLowerCase().contains("pluto"), q.id() + " must not be about Pluto");
				check(!q.explanation("en").isBlank(), q.id() + " has an explanation");
				check(!q.kssm().isBlank(), q.id() + " carries a KSSM tag");
			}
			check(types.equals(EnumSet.allOf(QuizQuestion.Type.class)), body.id() + " covers all four question types");
			check(difficulties.size() >= 2, body.id() + " mixes difficulties");
		}
		QuizBank all = QuizBank.load(null);
		check(all.warnings().isEmpty(), "built-in banks load cleanly: " + all.warnings());
		for (AstronomyClass body : AstronomyClass.values()) check(!all.questions(body).isEmpty(), body.id() + " is in the bank");
	}

	private static void resourceDirectoryHoldsOnlyTheTenBodies() throws Exception {
		Path dir = Path.of("src", "main", "resources", "data", "solariamod", "quiz");
		check(Files.isDirectory(dir), "quiz resource directory exists at " + dir.toAbsolutePath());
		Set<String> expected = new HashSet<>();
		for (AstronomyClass body : AstronomyClass.values()) expected.add(body.id() + ".json");
		Set<String> actual = new HashSet<>();
		try (Stream<Path> files = Files.list(dir)) {
			files.forEach(p -> actual.add(p.getFileName().toString()));
		}
		check(actual.equals(expected), "quiz files are exactly the ten bodies, got " + actual);
	}

	private static void selectionIsSeededAndRampsUp() throws Exception {
		List<QuizQuestion> bank = QuizBank.loadBuiltIn(AstronomyClass.MARS);
		QuizSession a = new QuizSession(bank, QuizSession.DEFAULT_LENGTH, 42L);
		QuizSession b = new QuizSession(bank, QuizSession.DEFAULT_LENGTH, 42L);
		check(a.questions().equals(b.questions()), "same seed draws the same quiz");
		check(a.length() == Math.min(QuizSession.DEFAULT_LENGTH, bank.size()), "session uses the default length");

		Set<List<String>> distinct = new HashSet<>();
		for (long seed = 0; seed < 20; seed++) {
			QuizSession s = new QuizSession(bank, QuizSession.DEFAULT_LENGTH, seed);
			List<String> picked = new ArrayList<>();
			Set<QuizQuestion.Type> types = EnumSet.noneOf(QuizQuestion.Type.class);
			Set<String> unique = new HashSet<>();
			int lastDifficulty = -1;
			boolean ascending = true;
			for (QuizSession.Presented p : s.questions()) {
				picked.add(p.question().id());
				types.add(p.question().type());
				unique.add(p.question().id());
				int d = p.question().difficulty().ordinal();
				if (d < lastDifficulty) ascending = false;
				lastDifficulty = d;
			}
			check(unique.size() == picked.size(), "no question repeats within a session");
			check(types.equals(EnumSet.allOf(QuizQuestion.Type.class)), "each session covers all four types");
			check(ascending, "session is ordered easy to hard");
			distinct.add(picked);
		}
		check(distinct.size() > 10, "repeat plays differ: " + distinct.size() + " distinct quizzes from 20 seeds");

		QuizSession whole = new QuizSession(bank, 999, 1L);
		check(whole.length() == bank.size(), "length is capped at the bank size");
		boolean threw = false;
		try {
			new QuizSession(List.of(), 8, 1L);
		} catch (IllegalArgumentException expected) {
			threw = true;
		}
		check(threw, "empty bank is rejected");
	}

	private static String wrap(String q) {
		return "{\"body\":\"mars\",\"questions\":[" + q + "]}";
	}

	private static QuizQuestion question(String json) throws Exception {
		return QuizBank.parse(AstronomyClass.MARS, new StringReader(wrap(json))).getFirst();
	}

	private static final String MC = "{\"id\":\"mars_x1\",\"type\":\"multiple_choice\",\"difficulty\":\"easy\",\"kssm\":\"T\","
		+ "\"prompt\":{\"en\":\"Which?\"},\"choices\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":[2],"
		+ "\"explanation\":{\"en\":\"Because.\"},\"source\":\"https://science.nasa.gov/mars/\"}";
	private static final String TF = "{\"id\":\"mars_x2\",\"type\":\"true_false\",\"difficulty\":\"easy\",\"kssm\":\"T\","
		+ "\"prompt\":\"Red?\",\"choices\":[\"True\",\"False\"],\"answer\":[0],"
		+ "\"explanation\":\"Iron oxide.\",\"source\":\"https://science.nasa.gov/mars/\"}";
	private static final String ORDER = "{\"id\":\"mars_x3\",\"type\":\"order\",\"difficulty\":\"medium\",\"kssm\":\"T\","
		+ "\"prompt\":{\"en\":\"Order\"},\"choices\":[\"Third\",\"First\",\"Second\",\"Fourth\"],\"answer\":[1,2,0,3],"
		+ "\"explanation\":{\"en\":\"x\"},\"source\":\"https://science.nasa.gov/mars/\"}";
	private static final String MATCH = "{\"id\":\"mars_x4\",\"type\":\"match\",\"difficulty\":\"hard\",\"kssm\":\"T\","
		+ "\"prompt\":{\"en\":\"Match\"},\"left\":[\"L0\",\"L1\",\"L2\"],\"choices\":[\"R2\",\"R0\",\"R1\"],\"answer\":[1,2,0],"
		+ "\"explanation\":{\"en\":\"x\"},\"source\":\"https://science.nasa.gov/mars/\"}";

	private static void gradingUsesDisplayIndices() throws Exception {
		Random random = new Random(7);
		for (String json : List.of(MC, TF, ORDER, MATCH)) {
			QuizQuestion q = question(json);
			for (int trial = 0; trial < 25; trial++) {
				QuizSession.Presented p = QuizSession.present(q, random);
				check(QuizBank.isPermutation(p.permutation(), q.choices().size()), q.id() + " display is a permutation");
				List<Integer> right = p.correctDisplayAnswer();
				check(p.isCorrect(right), q.id() + " correct display answer is accepted");
				for (int k = 0; k < right.size(); k++) {
					check(p.displayChoices().get(right.get(k)).equals(q.choices().get(q.answer().get(k))),
						q.id() + " display answer maps back to the stored choice");
				}
				List<Integer> wrong = new ArrayList<>(right);
				if (wrong.size() == 1) wrong.set(0, (wrong.getFirst() + 1) % q.choices().size());
				else Collections.swap(wrong, 0, 1);
				check(!p.isCorrect(wrong), q.id() + " wrong answer is rejected");
			}
			QuizSession.Presented p = QuizSession.present(q, random);
			check(!p.isCorrect(null), "null answer is wrong");
			check(!p.isCorrect(List.of()), "empty answer is wrong");
			check(!p.isCorrect(List.of(99)), "out-of-range answer is wrong");
			check(!p.isCorrect(List.of(-1)), "negative answer is wrong");
		}
		QuizQuestion tf = question(TF);
		check(QuizSession.present(tf, new Random(3)).displayChoices().equals(List.of("True", "False")),
			"true/false is never shuffled");
		QuizQuestion order = question(ORDER);
		QuizSession.Presented p = QuizSession.present(order, new Random(5));
		List<Integer> dup = new ArrayList<>(p.correctDisplayAnswer());
		dup.set(1, dup.getFirst());
		check(!p.isCorrect(dup), "order answers with repeats are wrong");
		check(!p.isCorrect(p.correctDisplayAnswer().subList(0, 3)), "partial order answer is wrong");
	}

	private static void sessionStateMachine() throws Exception {
		List<QuizQuestion> bank = List.of(question(MC), question(TF), question(ORDER), question(MATCH));
		QuizSession s = new QuizSession(bank, 4, 11L);
		check(s.length() == 4 && s.index() == 0 && s.score() == 0, "fresh session");
		check(!s.next(), "cannot skip an unanswered question");
		QuizSession.Feedback f = s.answer(s.current().correctDisplayAnswer());
		check(f != null && f.correct() && s.score() == 1, "correct answer scores");
		check(s.answer(s.current().correctDisplayAnswer()) == null, "double answer is ignored");
		check(s.score() == 1, "double answer does not add score");
		check(s.next(), "next after answering");
		check(!s.next(), "cannot next twice");
		QuizSession.Feedback miss = s.answer(List.of(42));
		check(miss != null && !miss.correct() && s.score() == 1, "wrong answer does not score");
		check(miss.correctDisplayAnswer().equals(s.current().correctDisplayAnswer()), "feedback reveals the right answer");
		s.next();
		while (!s.finished()) {
			s.answer(s.current().correctDisplayAnswer());
			s.next();
		}
		check(s.score() == 3, "final score is 3 of 4");
		check(s.results().equals(List.of(true, false, true, true)), "results are recorded per question");
		check(s.answer(List.of(0)) == null, "answers after the end are ignored");
		check(!s.next(), "next after the end is ignored");
		boolean threw = false;
		try {
			s.current();
		} catch (IllegalStateException expected) {
			threw = true;
		}
		check(threw, "current() after the end throws");
	}

	private static void puzzlesNeverStartSolved() throws Exception {
		for (String json : List.of(ORDER, MATCH)) {
			QuizQuestion q = question(json);
			List<Integer> identity = new ArrayList<>();
			for (int i = 0; i < q.choices().size(); i++) identity.add(i);
			for (long seed = 0; seed < 500; seed++) {
				QuizSession.Presented p = QuizSession.present(q, new Random(seed));
				check(!p.isCorrect(identity), q.id() + " never starts solved (seed " + seed + ")");
			}
		}
	}

	private static void rejects(String json, String why) {
		boolean threw = false;
		try {
			QuizBank.parse(AstronomyClass.MARS, new StringReader(json));
		} catch (QuizBank.QuizFormatException expected) {
			threw = true;
		}
		check(threw, "rejects " + why);
	}

	private static void malformedFilesAreRejected() throws Exception {
		rejects("not json {", "invalid JSON");
		rejects("[]", "array root");
		rejects("{\"body\":\"venus\",\"questions\":[" + MC + "]}", "body mismatch");
		rejects("{\"body\":\"mars\",\"questions\":[]}", "empty bank");
		rejects(wrap(MC + "," + MC), "duplicate ids");
		rejects(wrap(MC.replace("https://", "http://")), "http source");
		rejects(wrap(MC.replace("\"answer\":[2]", "\"answer\":[4]")), "out-of-range answer");
		rejects(wrap(MC.replace("\"answer\":[2]", "\"answer\":[1,2]")), "two answers for multiple choice");
		rejects(wrap(MC.replace("\"answer\":[2]", "\"answer\":[1.5]")), "fractional answer");
		rejects(wrap(MC.replace("\"easy\"", "\"extreme\"")), "unknown difficulty");
		rejects(wrap(MC.replace("multiple_choice", "essay")), "unknown type");
		rejects(wrap(MC.replace("\"C\",\"D\"", "\"C\",\"C\"")), "duplicate choices");
		rejects(wrap(MC.replace("\"A\",\"B\",\"C\",\"D\"", "\"A\",\"B\",\"C\",\"D\",\"E\"")), "too many choices");
		rejects(wrap(MC.replace("\"A\",", "\"" + "x".repeat(QuizBank.MAX_CHOICE_LENGTH + 1) + "\",")), "long choice");
		rejects(wrap(MC.replace("Which?", "x".repeat(QuizBank.MAX_PROMPT_LENGTH + 1))), "long prompt");
		rejects(wrap(MC.replace("{\"en\":\"Which?\"}", "{\"ms\":\"Yang mana?\"}")), "missing English prompt");
		rejects(wrap(TF.replace("[\"True\",\"False\"]", "[\"Yes\",\"No\"]")), "bad true/false choices");
		rejects(wrap(ORDER.replace("[1,2,0,3]", "[1,1,0,3]")), "non-permutation order");
		rejects(wrap(ORDER.replace("[1,2,0,3]", "[1,2,0]")), "short order answer");
		rejects(wrap(MATCH.replace("\"L0\",\"L1\",\"L2\"", "\"L0\",\"L1\"")), "unbalanced match");
		rejects(wrap(MC.replace("\"choices\"", "\"left\":[\"a\",\"b\",\"c\",\"d\"],\"choices\"")), "left column on MC");
		rejects(wrap(MC.replace("\"source\":\"https://science.nasa.gov/mars/\"", "\"source\":\"\"")), "blank source");
		check(QuizBank.parse(AstronomyClass.MARS, new StringReader(wrap(MC + "," + TF + "," + ORDER + "," + MATCH))).size() == 4,
			"valid sample parses");
		check(QuizBank.isPermutation(List.of(2, 0, 1), 3), "isPermutation accepts permutations");
		check(!QuizBank.isPermutation(List.of(0, 0, 1), 3), "isPermutation rejects repeats");
		check(!QuizBank.isPermutation(List.of(0, 1), 3), "isPermutation rejects short lists");
	}

	private static void overridesFallBackWhenInvalid() throws Exception {
		Path dir = Files.createTempDirectory("solaria-quiz");
		try {
			Files.writeString(dir.resolve("mars.json"), wrap(MC + "," + TF));
			Files.writeString(dir.resolve("venus.json"), "{ broken");
			QuizBank bank = QuizBank.load(dir);
			check(bank.questions(AstronomyClass.MARS).size() == 2, "valid override replaces the built-in bank");
			check(bank.questions(AstronomyClass.VENUS).size() == QuizBank.loadBuiltIn(AstronomyClass.VENUS).size(),
				"invalid override falls back to the built-in bank");
			check(bank.warnings().size() == 1 && bank.warnings().getFirst().contains("venus.json"),
				"invalid override is reported: " + bank.warnings());
			check(bank.questions(AstronomyClass.EARTH).size() == QuizBank.loadBuiltIn(AstronomyClass.EARTH).size(),
				"bodies without an override use the built-in bank");
		} finally {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path p : files.toList()) Files.delete(p);
			}
			Files.delete(dir);
		}
		QuizBank missingDir = QuizBank.load(Path.of("does-not-exist-solaria"));
		check(missingDir.warnings().isEmpty(), "a missing override directory is fine");
	}

	private static void localizationFallsBackToEnglish() throws Exception {
		Map<String, String> both = Map.of("en", "Hello", "ms", "Helo");
		check(QuizQuestion.localized(both, "ms_my").equals("Helo"), "Malay client gets Malay");
		check(QuizQuestion.localized(both, "en_us").equals("Hello"), "English client gets English");
		check(QuizQuestion.localized(Map.of("en", "Hello"), "ms_my").equals("Hello"), "missing Malay falls back to English");
		check(QuizQuestion.localized(Map.of("en", "Hello", "ms", " "), "ms_my").equals("Hello"), "blank Malay falls back");
		check(QuizQuestion.localized(both, null).equals("Hello"), "unknown language falls back to English");
		check(question(MC).prompt("ms_my").equals("Which?"), "question prompt falls back to English");
	}

	private static void scoringTiers() {
		check(QuizScoring.percent(8, 8) == 100, "full marks");
		check(QuizScoring.percent(3, 8) == 38, "3 of 8 rounds to 38");
		check(QuizScoring.percent(0, 0) == 0, "zero total is safe");
		check(QuizScoring.message(8, 8).startsWith("Perfect"), "perfect tier");
		check(QuizScoring.message(6, 8).startsWith("Great"), "75% tier");
		check(QuizScoring.message(4, 8).startsWith("Good"), "50% tier");
		check(QuizScoring.message(3, 8).startsWith("Keep"), "low tier");
	}
}
