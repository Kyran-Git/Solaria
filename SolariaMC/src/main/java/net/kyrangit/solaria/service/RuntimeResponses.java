package net.kyrangit.solaria.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.logging.ReasonCode;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict, immutable runtime DTOs. Unknown fields are additive; duplicate fields are not. */
public final class RuntimeResponses {
	private static final Set<String> QUALITY_CODES = Set.of("no_drawing_found", "too_blurry", "too_dark",
		"too_bright", "low_contrast", "drawing_too_small", "drawing_off_centre");

	private RuntimeResponses() {
	}

	public record Health(boolean mock, boolean modelLoaded, double threshold, List<AstronomyClass> classes) {
		public Health {
			classes = List.copyOf(classes);
		}
	}

	public record Candidate(AstronomyClass label, double confidence) {
	}

	public record Prediction(String requestId, String scanId, boolean mock, boolean qualityPassed,
		String qualityReason, AstronomyClass label, double confidence, boolean accepted, double threshold,
		String reasonCode, List<Candidate> top3, String thumbnailBase64) {
		public Prediction {
			top3 = List.copyOf(top3);
		}
	}

	/** A teacher decision is not student consent or permission to generate/teleport. */
	public record Review(String scanId, String studentId, Prediction prediction, int revision,
		AstronomyClass finalLabel, boolean accepted, boolean teacherDecision) {
	}

	public static Health health(JsonObject object) throws ServiceException {
		version(object);
		String status = text(object, "status");
		require(status.equals("ok") || status.equals("degraded"));
		List<AstronomyClass> classes = new ArrayList<>();
		for (JsonElement element : array(object, "classes")) {
			AstronomyClass cls = AstronomyClass.fromId(string(element));
			if (cls == null || classes.contains(cls)) throw new ServiceException(ReasonCode.CLASS_SET_MISMATCH);
			classes.add(cls);
		}
		if (classes.size() != AstronomyClass.values().length) throw new ServiceException(ReasonCode.CLASS_SET_MISMATCH);
		boolean mock = bool(object, "mock"), loaded = bool(object, "model_loaded");
		if (!status.equals("ok") || !mock && !loaded) throw new ServiceException(ReasonCode.MODEL_UNAVAILABLE);
		return new Health(mock, loaded, probability(object, "threshold"), classes);
	}

	public static Prediction prediction(JsonObject object, String requestId) throws ServiceException {
		Prediction prediction = prediction(object);
		require(prediction.requestId().equals(requestId));
		return prediction;
	}

	private static Prediction prediction(JsonObject object) throws ServiceException {
		version(object);
		String requestId = text(object, "request_id");
		require(requestId.matches("[A-Za-z0-9_-]{1,64}"));
		// scan_id is additive in runtime_v1. Older servers still classify, but cannot supply reviews.
		String scanId = object.has("scan_id") ? text(object, "scan_id") : null;
		require(scanId == null || scanId.matches("[a-f0-9]{32}"));
		JsonObject quality = object(object, "quality");
		String status = text(quality, "status"), qualityReason = nullableText(quality, "reason_code");
		require(status.equals("pass") || status.equals("fail"));
		boolean passed = status.equals("pass");
		require(!text(quality, "hint").isBlank());
		require(passed ? qualityReason == null : QUALITY_CODES.contains(qualityReason == null ? "" : qualityReason));
		AstronomyClass label = label(object, "label");
		double confidence = probability(object, "confidence"), threshold = probability(object, "threshold");
		boolean accepted = bool(object, "accepted");
		String reason = nullableText(object, "reason_code");
		if (accepted) {
			require(passed && label != null && reason == null && confidence >= threshold);
		} else {
			require(label == null);
			require(passed ? "CONF_BELOW_THRESHOLD".equals(reason) && confidence < threshold
				: Objects.equals(reason, qualityReason));
		}
		List<Candidate> top3 = new ArrayList<>();
		Set<AstronomyClass> seen = new HashSet<>();
		double previous = 1.0;
		for (JsonElement element : array(object, "top3")) {
			require(element.isJsonObject());
			JsonObject candidate = element.getAsJsonObject();
			AstronomyClass cls = label(candidate, "label");
			double score = probability(candidate, "confidence");
			require(cls != null && seen.add(cls) && score <= previous);
			top3.add(new Candidate(cls, score));
			previous = score;
		}
		require(top3.size() <= 3);
		if (passed) {
			require(!top3.isEmpty() && Double.compare(top3.getFirst().confidence(), confidence) == 0);
			require(!accepted || top3.getFirst().label() == label);
		} else {
			require(top3.isEmpty() && confidence == 0);
		}
		String thumbnail = null;
		JsonElement image = field(object, "thumbnail");
		if (!image.isJsonNull()) {
			require(image.isJsonObject());
			JsonObject data = image.getAsJsonObject();
			require(text(data, "content_type").equals("image/png"));
			thumbnail = text(data, "base64");
			require(!thumbnail.isEmpty() && thumbnail.length() <= 262144);
			try {
				byte[] bytes = Base64.getDecoder().decode(thumbnail);
				byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
				require(bytes.length >= signature.length);
				for (int i = 0; i < signature.length; i++) require(bytes[i] == signature[i]);
			} catch (IllegalArgumentException e) {
				throw protocol();
			}
		}
		require(passed || thumbnail == null);
		number(object, "elapsed_ms", 0, Double.MAX_VALUE);
		return new Prediction(requestId, scanId, bool(object, "mock"), passed, qualityReason, label,
			confidence, accepted, threshold, reason, top3, thumbnail);
	}

	public static Review review(JsonObject envelope, String scanId, String requestId, String studentId,
		int minimumRevision) throws ServiceException {
		version(envelope);
		JsonObject record = object(envelope, "review");
		require(text(record, "scan_id").equals(scanId));
		require(Objects.equals(nullableText(record, "student_id"), studentId));
		Prediction prediction = prediction(object(record, "prediction"), requestId);
		require(prediction.scanId() == null || prediction.scanId().equals(scanId));
		int revision = integer(record, "revision", 0, 20);
		if (revision < minimumRevision) throw new ServiceException(ReasonCode.REVIEW_CONFLICT);
		AstronomyClass finalLabel = label(record, "final_label");
		boolean accepted = bool(record, "accepted");
		String decider = text(record, "decider");
		JsonArray overrides = array(record, "overrides");
		require(overrides.size() == revision);
		for (int i = 0; i < overrides.size(); i++) {
			require(overrides.get(i).isJsonObject());
			JsonObject override = overrides.get(i).getAsJsonObject();
			require(integer(override, "revision", 1, 20) == i + 1);
			require(text(override, "decider").equals("teacher") && label(override, "label") != null);
		}
		if (revision == 0) {
			require(decider.equals("ai") && finalLabel == prediction.label() && accepted == prediction.accepted());
		} else {
			require(decider.equals("teacher") && prediction.qualityPassed() && finalLabel != null);
			JsonObject last = overrides.get(revision - 1).getAsJsonObject();
			require(last.has("accepted") ? bool(last, "accepted") == accepted : accepted);
			require(label(overrides.get(revision - 1).getAsJsonObject(), "label") == finalLabel);
		}
		return new Review(scanId, studentId, prediction, revision, finalLabel, accepted, revision > 0);
	}

	public static JsonObject parse(String json) throws ServiceException {
		try (JsonReader reader = new JsonReader(new StringReader(json))) {
			reader.setStrictness(Strictness.STRICT);
			JsonElement root = read(reader, 0);
			require(root.isJsonObject() && reader.peek() == JsonToken.END_DOCUMENT);
			return root.getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			throw protocol();
		}
	}

	private static JsonElement read(JsonReader reader, int depth) throws IOException, ServiceException {
		require(depth <= 32);
		return switch (reader.peek()) {
			case BEGIN_OBJECT -> {
				JsonObject result = new JsonObject();
				reader.beginObject();
				while (reader.hasNext()) {
					String name = reader.nextName();
					require(!result.has(name));
					result.add(name, read(reader, depth + 1));
				}
				reader.endObject();
				yield result;
			}
			case BEGIN_ARRAY -> {
				JsonArray result = new JsonArray();
				reader.beginArray();
				while (reader.hasNext()) result.add(read(reader, depth + 1));
				reader.endArray();
				yield result;
			}
			case STRING -> new JsonPrimitive(reader.nextString());
			case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
			case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
			case NULL -> {
				reader.nextNull();
				yield JsonNull.INSTANCE;
			}
			default -> throw protocol();
		};
	}

	static void version(JsonObject object) throws ServiceException {
		require(text(object, "api_version").equals("api_v1"));
	}

	static String text(JsonObject object, String key) throws ServiceException {
		return string(field(object, key));
	}

	private static String string(JsonElement value) throws ServiceException {
		require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString());
		return value.getAsString();
	}

	private static String nullableText(JsonObject object, String key) throws ServiceException {
		return field(object, key).isJsonNull() ? null : text(object, key);
	}

	private static JsonElement field(JsonObject object, String key) throws ServiceException {
		require(object.has(key));
		return object.get(key);
	}

	private static JsonObject object(JsonObject object, String key) throws ServiceException {
		JsonElement value = field(object, key);
		require(value.isJsonObject());
		return value.getAsJsonObject();
	}

	private static JsonArray array(JsonObject object, String key) throws ServiceException {
		JsonElement value = field(object, key);
		require(value.isJsonArray());
		return value.getAsJsonArray();
	}

	private static boolean bool(JsonObject object, String key) throws ServiceException {
		JsonElement value = field(object, key);
		require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean());
		return value.getAsBoolean();
	}

	private static AstronomyClass label(JsonObject object, String key) throws ServiceException {
		String id = text(object, key);
		AstronomyClass cls = AstronomyClass.fromId(id);
		require(cls != null || id.equals("unknown"));
		return cls;
	}

	static int integer(JsonObject object, String key, int min, int max) throws ServiceException {
		JsonElement value = field(object, key);
		require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber());
		try {
			int result = value.getAsBigDecimal().intValueExact();
			require(result >= min && result <= max);
			return result;
		} catch (ArithmeticException | NumberFormatException e) {
			throw protocol();
		}
	}

	private static double probability(JsonObject object, String key) throws ServiceException {
		return number(object, key, 0, 1);
	}

	private static double number(JsonObject object, String key, double min, double max) throws ServiceException {
		JsonElement value = field(object, key);
		require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber());
		double number = value.getAsDouble();
		require(Double.isFinite(number) && number >= min && number <= max);
		return number;
	}

	private static void require(boolean condition) throws ServiceException {
		if (!condition) throw protocol();
	}

	static ServiceException protocol() {
		return new ServiceException(ReasonCode.SERVICE_PROTOCOL_ERROR);
	}
}