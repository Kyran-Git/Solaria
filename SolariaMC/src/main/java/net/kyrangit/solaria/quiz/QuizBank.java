package net.kyrangit.solaria.quiz;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyrangit.solaria.blueprint.AstronomyClass;

/**
 * Loads and validates the per-body quiz banks. Built-in banks live at
 * {@code /data/solariamod/quiz/<body>.json} inside the mod jar; a teacher can drop an edited copy at
 * {@code <config>/solariamod/quiz/<body>.json} and it replaces the built-in bank for that body after
 * passing the same validation. Invalid override files are reported and ignored, never half-loaded.
 */
public final class QuizBank {
	public static final int MAX_CHOICE_LENGTH = 48;
	public static final int MAX_PROMPT_LENGTH = 220;
	public static final int MAX_EXPLANATION_LENGTH = 220;

	private final Map<AstronomyClass, List<QuizQuestion>> banks;
	private final List<String> warnings;

	private QuizBank(Map<AstronomyClass, List<QuizQuestion>> banks, List<String> warnings) {
		this.banks = banks;
		this.warnings = List.copyOf(warnings);
	}

	public List<QuizQuestion> questions(AstronomyClass body) {
		return banks.getOrDefault(body, List.of());
	}

	public List<String> warnings() {
		return warnings;
	}

	/** Load every body from the classpath, preferring a valid override in {@code overrideDir} when present. */
	public static QuizBank load(Path overrideDir) {
		Map<AstronomyClass, List<QuizQuestion>> banks = new EnumMap<>(AstronomyClass.class);
		List<String> warnings = new ArrayList<>();
		for (AstronomyClass body : AstronomyClass.values()) {
			List<QuizQuestion> loaded = null;
			if (overrideDir != null) {
				Path file = overrideDir.resolve(body.id() + ".json");
				if (Files.isRegularFile(file)) {
					try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
						loaded = parse(body, reader);
					} catch (IOException | QuizFormatException e) {
						warnings.add("Ignoring quiz override " + file + ": " + e.getMessage());
					}
				}
			}
			if (loaded == null) {
				try {
					loaded = loadBuiltIn(body);
				} catch (IOException | QuizFormatException e) {
					warnings.add("Built-in quiz for " + body.id() + " is invalid: " + e.getMessage());
					loaded = List.of();
				}
			}
			banks.put(body, loaded);
		}
		return new QuizBank(banks, warnings);
	}

	public static List<QuizQuestion> loadBuiltIn(AstronomyClass body) throws IOException, QuizFormatException {
		String resource = "/data/solariamod/quiz/" + body.id() + ".json";
		try (InputStream in = QuizBank.class.getResourceAsStream(resource)) {
			if (in == null) throw new QuizFormatException("missing resource " + resource);
			return parse(body, new StringReader(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
		}
	}

	public static List<QuizQuestion> parse(AstronomyClass body, Reader reader) throws QuizFormatException {
		JsonElement root;
		try {
			root = JsonParser.parseReader(reader);
		} catch (JsonParseException e) {
			throw new QuizFormatException("not valid JSON: " + e.getMessage());
		}
		if (!root.isJsonObject()) throw new QuizFormatException("root must be an object");
		JsonObject object = root.getAsJsonObject();
		String declared = string(object, "body", "root");
		if (!body.id().equals(declared)) {
			throw new QuizFormatException("file declares body '" + declared + "' but was loaded for '" + body.id() + "'");
		}
		JsonArray array = array(object, "questions", "root");
		List<QuizQuestion> questions = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < array.size(); i++) {
			if (!array.get(i).isJsonObject()) throw new QuizFormatException("question " + i + " must be an object");
			QuizQuestion q = parseQuestion(array.get(i).getAsJsonObject(), "question " + i);
			if (!ids.add(q.id())) throw new QuizFormatException("duplicate question id " + q.id());
			questions.add(q);
		}
		if (questions.isEmpty()) throw new QuizFormatException("no questions");
		return List.copyOf(questions);
	}

	static QuizQuestion parseQuestion(JsonObject o, String where) throws QuizFormatException {
		String id = string(o, "id", where);
		where = id;
		QuizQuestion.Type type = QuizQuestion.Type.fromId(string(o, "type", where));
		if (type == null) throw new QuizFormatException(where + ": unknown type");
		QuizQuestion.Difficulty difficulty = QuizQuestion.Difficulty.fromId(string(o, "difficulty", where));
		if (difficulty == null) throw new QuizFormatException(where + ": difficulty must be easy, medium or hard");
		String kssm = o.has("kssm") && o.get("kssm").isJsonPrimitive() ? o.get("kssm").getAsString() : "";
		Map<String, String> prompt = text(o, "prompt", where, MAX_PROMPT_LENGTH);
		Map<String, String> explanation = text(o, "explanation", where, MAX_EXPLANATION_LENGTH);
		List<String> choices = strings(o, "choices", where);
		List<String> left = o.has("left") ? strings(o, "left", where) : List.of();
		List<Integer> answer = ints(o, "answer", where);
		String source = string(o, "source", where);
		if (!source.startsWith("https://")) throw new QuizFormatException(where + ": source must be an https URL");

		for (String c : choices) {
			if (c.isBlank() || c.length() > MAX_CHOICE_LENGTH) {
				throw new QuizFormatException(where + ": choice text must be 1-" + MAX_CHOICE_LENGTH + " characters");
			}
		}
		for (String l : left) {
			if (l.isBlank() || l.length() > MAX_CHOICE_LENGTH) {
				throw new QuizFormatException(where + ": left text must be 1-" + MAX_CHOICE_LENGTH + " characters");
			}
		}
		switch (type) {
			case MULTIPLE_CHOICE -> {
				requireSize(choices, 2, 4, where);
				requireSingleAnswer(answer, choices.size(), where);
			}
			case TRUE_FALSE -> {
				if (!choices.equals(List.of("True", "False"))) {
					throw new QuizFormatException(where + ": true_false choices must be exactly [\"True\",\"False\"]");
				}
				requireSingleAnswer(answer, 2, where);
			}
			case ORDER -> {
				requireSize(choices, 3, 5, where);
				requirePermutation(answer, choices.size(), where);
			}
			case MATCH -> {
				requireSize(choices, 3, 4, where);
				if (left.size() != choices.size()) {
					throw new QuizFormatException(where + ": match needs as many left items as choices");
				}
				requirePermutation(answer, choices.size(), where);
			}
		}
		if (type != QuizQuestion.Type.MATCH && !left.isEmpty()) {
			throw new QuizFormatException(where + ": only match questions may have a left column");
		}
		return new QuizQuestion(id, type, difficulty, kssm, prompt, choices, left, answer, explanation, source);
	}

	private static void requireSize(List<String> list, int min, int max, String where) throws QuizFormatException {
		if (list.size() < min || list.size() > max) {
			throw new QuizFormatException(where + ": needs " + min + "-" + max + " choices");
		}
		if (new HashSet<>(list).size() != list.size()) throw new QuizFormatException(where + ": duplicate choices");
	}

	private static void requireSingleAnswer(List<Integer> answer, int size, String where) throws QuizFormatException {
		if (answer.size() != 1 || answer.getFirst() < 0 || answer.getFirst() >= size) {
			throw new QuizFormatException(where + ": answer must hold one valid choice index");
		}
	}

	private static void requirePermutation(List<Integer> answer, int size, String where) throws QuizFormatException {
		if (answer.size() != size || !isPermutation(answer, size)) {
			throw new QuizFormatException(where + ": answer must use every choice index exactly once");
		}
	}

	static boolean isPermutation(List<Integer> values, int size) {
		if (values.size() != size) return false;
		boolean[] seen = new boolean[size];
		for (Integer v : values) {
			if (v == null || v < 0 || v >= size || seen[v]) return false;
			seen[v] = true;
		}
		return true;
	}

	private static String string(JsonObject o, String key, String where) throws QuizFormatException {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank()) {
			throw new QuizFormatException(where + ": '" + key + "' must be a non-empty string");
		}
		return e.getAsString().strip();
	}

	private static JsonArray array(JsonObject o, String key, String where) throws QuizFormatException {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonArray()) throw new QuizFormatException(where + ": '" + key + "' must be an array");
		return e.getAsJsonArray();
	}

	private static List<String> strings(JsonObject o, String key, String where) throws QuizFormatException {
		List<String> out = new ArrayList<>();
		for (JsonElement e : array(o, key, where)) {
			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
				throw new QuizFormatException(where + ": '" + key + "' must only contain strings");
			}
			out.add(e.getAsString().strip());
		}
		return out;
	}

	private static List<Integer> ints(JsonObject o, String key, String where) throws QuizFormatException {
		List<Integer> out = new ArrayList<>();
		for (JsonElement e : array(o, key, where)) {
			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
				throw new QuizFormatException(where + ": '" + key + "' must only contain integers");
			}
			double d = e.getAsDouble();
			if (d != Math.rint(d)) throw new QuizFormatException(where + ": '" + key + "' must only contain integers");
			out.add((int) d);
		}
		return out;
	}

	/** Accepts {"en": "...", "ms": "..."} objects, or a bare string treated as English. */
	private static Map<String, String> text(JsonObject o, String key, String where, int max) throws QuizFormatException {
		JsonElement e = o.get(key);
		Map<String, String> out = new LinkedHashMap<>();
		if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			out.put("en", e.getAsString().strip());
		} else if (e != null && e.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) {
				if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
					out.put(entry.getKey(), entry.getValue().getAsString().strip());
				}
			}
		}
		String en = out.get("en");
		if (en == null || en.isBlank()) throw new QuizFormatException(where + ": '" + key + "' needs English text");
		for (String value : out.values()) {
			if (value.length() > max) throw new QuizFormatException(where + ": '" + key + "' is longer than " + max);
		}
		return out;
	}

	public static final class QuizFormatException extends Exception {
		public QuizFormatException(String message) {
			super(message);
		}
	}
}
