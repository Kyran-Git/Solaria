package net.kyrangit.solaria;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.config.ConfigCodec;
import net.kyrangit.solaria.config.SolariaConfig;
import net.kyrangit.solaria.logging.ReasonCode;
import net.kyrangit.solaria.service.AiServiceClient;
import net.kyrangit.solaria.service.RuntimeResponses;
import net.kyrangit.solaria.service.ServiceException;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Dependency-free fixture and opt-in cross-process smoke tests. Never student accuracy evidence. */
public final class ServiceClientTests {
	private static final String TOKEN = "synthetic-private-token";
	private static final String SCAN = "0123456789abcdef0123456789abcdef";
	private static int assertions;
	private static final String HEALTH = """
		{"api_version":"api_v1","status":"ok","mock":true,"model_loaded":false,"threshold":0.6,
		"classes":["mercury","neptune","uranus","saturn","venus","jupiter","mars","moon","earth","sun"]}
		""";

	public static void main(String[] args) throws Exception {
		if (args.length > 0 && args[0].equals("--live")) {
			live();
		} else {
			fixtures();
		}
		System.out.println("Service client (synthetic-validated): " + assertions + " assertions passed.");
	}

	private static void tokenFiles() throws Exception {
		var file = java.nio.file.Files.createTempFile("solaria-synthetic-token-", ".txt");
		var key = "SOLARIA_STARTUP_TOKEN_FILE";
		try {
			java.nio.file.Files.writeString(file, TOKEN + "\n");
			var environment = java.util.Map.of(key, file.toString());
			check(net.kyrangit.solaria.service.HostServiceToken.load(environment).equals(TOKEN), "File token loaded");
			check(net.kyrangit.solaria.service.HostServiceToken.load(java.util.Map.of(key, file.toString(),
				"SOLARIA_STARTUP_TOKEN", "explicit")).equals("explicit"), "Explicit token wins");
			expect(ReasonCode.SERVICE_TOKEN_MISSING, () -> net.kyrangit.solaria.service.HostServiceToken.load(
				java.util.Map.of(key, file.toString(), "SOLARIA_STARTUP_TOKEN", "invalid\n")));
			for (String bad : new String[]{"", " \n", "x".repeat(257), "x".repeat(1025), "bad\nvalue", "bad\u00e9"}) {
				java.nio.file.Files.writeString(file, bad);
				expect(ReasonCode.SERVICE_TOKEN_MISSING, () -> net.kyrangit.solaria.service.HostServiceToken.load(environment));
			}
			java.nio.file.Files.delete(file);
			expect(ReasonCode.SERVICE_TOKEN_MISSING, () -> net.kyrangit.solaria.service.HostServiceToken.load(environment));
			check(!java.nio.file.Files.exists(file), "Missing token is not generated");
			expect(ReasonCode.SERVICE_TOKEN_MISSING, () -> net.kyrangit.solaria.service.HostServiceToken.load(java.util.Map.of()));
		} finally {
			java.nio.file.Files.deleteIfExists(file);
		}
	}

	private static void fixtures() throws Exception {
		tokenFiles();
		var health = RuntimeResponses.health(RuntimeResponses.parse(HEALTH));
		check(health.mock() && health.classes().getFirst() == AstronomyClass.MERCURY, "Metadata order preserved");
		for (String bad : new String[]{HEALTH.replace("mercury", "earth"), HEALTH.replace("mercury", "Mercury"),
			HEALTH.replace("\"mercury\",", "")}) {
			expect(ReasonCode.CLASS_SET_MISMATCH, () -> RuntimeResponses.health(RuntimeResponses.parse(bad)));
		}
		expect(ReasonCode.MODEL_UNAVAILABLE, () -> RuntimeResponses.health(RuntimeResponses.parse(HEALTH.replace("\"mock\":true", "\"mock\":false"))));
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.health(RuntimeResponses.parse(HEALTH.replace("api_v1", "api_v2"))));
		for (String bad : new String[]{"{} {}", "{\"x\":1,\"x\":2}", "{\"x\":NaN}", "/* hi */{}", "[]", "{\"x\":1,}",
			"{\"nested\":{\"x\":1,\"x\":2}}", "[".repeat(40) + "0" + "]".repeat(40)}) {
			expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.parse(bad));
		}
		for (AstronomyClass cls : AstronomyClass.values()) {
			var result = RuntimeResponses.prediction(prediction(cls.id()), "request");
			check(result.label() == cls && result.accepted(), "All ten labels accepted");
		}
		JsonObject low = prediction("earth");
		low.addProperty("accepted", false);
		low.addProperty("label", "unknown");
		low.addProperty("threshold", 0.99);
		low.addProperty("reason_code", "CONF_BELOW_THRESHOLD");
		check(!RuntimeResponses.prediction(low, "request").accepted(), "Low confidence stays rejected");
		JsonObject quality = low.deepCopy();
		quality.addProperty("confidence", 0);
		quality.addProperty("reason_code", "too_blurry");
		quality.getAsJsonObject("quality").addProperty("status", "fail");
		quality.getAsJsonObject("quality").addProperty("reason_code", "too_blurry");
		quality.add("top3", new com.google.gson.JsonArray());
		check(!RuntimeResponses.prediction(quality, "request").qualityPassed(), "Quality rejection retained");
		for (String change : new String[]{"\"confidence\":\"0.9\"", "\"confidence\":1.1", "\"confidence\":1e999"}) {
			String json = prediction("earth").toString().replace("\"confidence\":0.9", change);
			expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.prediction(RuntimeResponses.parse(json), "request"));
		}
		JsonObject invalid = prediction("earth");
		invalid.addProperty("threshold", 0.95);
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.prediction(invalid, "request"));
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.prediction(prediction("earth"), "other"));
		JsonObject extra = prediction("earth");
		extra.addProperty("future_field", "ignored");
		check(RuntimeResponses.prediction(extra, "request").accepted(), "Additive fields accepted");
		JsonObject legacy = prediction("earth");
		legacy.remove("scan_id");
		check(RuntimeResponses.prediction(legacy, "request").scanId() == null, "Older stateless response has no review id");
		JsonObject malformedThumbnail = prediction("earth");
		malformedThumbnail.add("thumbnail", RuntimeResponses.parse("{\"content_type\":\"image/png\",\"base64\":\"not-base64\"}"));
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.prediction(malformedThumbnail, "request"));
		JsonObject failedButAccepted = quality.deepCopy();
		failedButAccepted.addProperty("accepted", true);
		failedButAccepted.addProperty("label", "earth");
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.prediction(failedButAccepted, "request"));
		JsonObject review = review(low, 1);
		check(RuntimeResponses.review(review, SCAN, "request", "participant", 0).finalLabel() == AstronomyClass.MARS,
			"Teacher can correct low confidence");
		expect(ReasonCode.REVIEW_CONFLICT, () -> RuntimeResponses.review(review, SCAN, "request", "participant", 2));
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.review(review, SCAN, "request", "other", 0));
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.review(review, "f".repeat(32), "request", "participant", 0));
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.review(review, SCAN, "other", "participant", 0));
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.review(review(quality, 1), SCAN, "request", "participant", 0));
		JsonObject rejected = review(low, 1);
		rejected.getAsJsonObject("review").addProperty("accepted", false);
		rejected.getAsJsonObject("review").getAsJsonArray("overrides").get(0).getAsJsonObject()
			.addProperty("accepted", false);
		check(!RuntimeResponses.review(rejected, SCAN, "request", "participant", 0).accepted(),
			"Explicit teacher rejection is valid");
		rejected.getAsJsonObject("review").getAsJsonArray("overrides").get(0).getAsJsonObject()
			.addProperty("accepted", true);
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.review(rejected, SCAN, "request", "participant", 0));
		JsonObject inconsistent = review(low, 1);
		inconsistent.getAsJsonObject("review").addProperty("final_label", "sun");
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.review(inconsistent, SCAN, "request", "participant", 0));
		JsonObject fractionalRevision = RuntimeResponses.parse(review(low, 1).toString()
			.replace("\"revision\":1", "\"revision\":1.00000000000000001"));
		expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> RuntimeResponses.review(fractionalRevision, SCAN, "request", "participant", 0));
		JsonObject original = review(prediction("earth"), 0);
		original.getAsJsonObject("review").addProperty("decider", "ai");
		original.getAsJsonObject("review").addProperty("final_label", "earth");
		original.getAsJsonObject("review").add("overrides", new com.google.gson.JsonArray());
		check(!RuntimeResponses.review(original, SCAN, "request", "participant", 0).teacherDecision(), "Unreviewed AI record retained");

		AtomicReference<String> body = new AtomicReference<>(HEALTH);
		AtomicInteger status = new AtomicInteger(200), requests = new AtomicInteger(), delay = new AtomicInteger();
		AtomicReference<String> contentType = new AtomicReference<>("application/json");
		AtomicReference<String> failure = new AtomicReference<>();
		String classificationBody = prediction("earth").toString();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var workers = Executors.newVirtualThreadPerTaskExecutor();
		server.setExecutor(workers);
		server.createContext("/", exchange -> {
			try (exchange) {
				requests.incrementAndGet();
				if (!exchange.getRequestHeaders().getFirst("Authorization").equals("Bearer " + TOKEN)) failure.set("Token header");
				if (exchange.getRequestURI().getPath().equals("/v1/classify")) {
					if (!"participant".equals(exchange.getRequestHeaders().getFirst("X-Solaria-Student-ID"))
					|| !"request".equals(exchange.getRequestHeaders().getFirst("X-Request-ID"))
					|| exchange.getRequestHeaders().getFirst("Content-Length") == null
					|| exchange.getRequestHeaders().getFirst("Transfer-Encoding") != null
					|| exchange.getRequestBody().readAllBytes().length != 3) failure.set("Upload framing");
				}
				String response = exchange.getRequestURI().getPath().equals("/v1/classify") ? classificationBody : body.get();
				exchange.getResponseHeaders().set("Content-Type", contentType.get());
				exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/redirected");
				byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(status.get(), bytes.length);
				if (delay.get() > 0) Thread.sleep(delay.get());
				exchange.getResponseBody().write(bytes);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (java.io.IOException ignored) {
				// Expected when the bounded client cancels a stalled or oversized response.
			}
		});
		server.start();
		SolariaConfig config = config(server.getAddress().getPort(), 1000);
		try (AiServiceClient client = new AiServiceClient(config, TOKEN)) {
			check(client.health().mock(), "Authenticated HTTP health");
			check(client.classify(new byte[]{1, 2, 3}, "image/png", "request", "participant").accepted(), "HTTP classify");
			check(failure.get() == null, "Token, ids and fixed length framing correct");
			int before = requests.get();
			expect(ReasonCode.INPUT_INVALID, () -> client.classify(new byte[]{1}, "image/gif", "request", "participant"));
			expect(ReasonCode.INPUT_INVALID, () -> client.classify(new byte[]{1}, "image/png", "bad\r\n", "participant"));
			expect(ReasonCode.INPUT_TOO_LARGE, () -> client.classify(new byte[AiServiceClient.MAX_IMAGE_BYTES + 1], "image/png", "request", "participant"));
			expect(ReasonCode.INPUT_INVALID, () -> client.review("../health", "request", "participant", 0));
			check(requests.get() == before, "Invalid input sends no requests");
			body.set(HEALTH.replace("mercury", "bad"));
			expect(ReasonCode.CLASS_SET_MISMATCH, () -> client.classify(new byte[]{1}, "image/png", "request", "participant"));
			check(requests.get() == before + 1, "Failed health prevents upload");
			body.set(review(prediction("earth"), 1).toString());
			check(client.review(SCAN, "request", "participant", 0).teacherDecision(), "HTTP teacher review lookup");
			for (String code : new String[]{"UNAUTHORIZED", "NOT_FOUND", "INPUT_INVALID", "INPUT_TOO_LARGE", "LABEL_NOT_IN_CLASSES",
				"QUEUE_FULL", "SERVICE_UNAVAILABLE", "MODEL_UNAVAILABLE", "SCAN_TIMEOUT", "INTERNAL_ERROR", "REVIEW_CONFLICT", "QUALITY_REJECTED"}) {
				status.set(503);
				body.set("{\"api_version\":\"api_v1\",\"reason_code\":\"" + code + "\",\"message\":\"" + TOKEN + "\",\"retry_after_ms\":1000}");
				expect(ReasonCode.valueOf(code), client::health);
			}
			try {
				client.health();
				throw new AssertionError("Expected service rejection");
			} catch (ServiceException error) {
				check(error.retryAfterMs() == 1000, "Retry hint retained without automatic retry");
			}
			status.set(302);
			before = requests.get();
			expect(ReasonCode.SERVICE_PROTOCOL_ERROR, client::health);
			check(requests.get() == before + 1, "Redirect never followed; no retry");
			status.set(200);
			body.set(" ".repeat(AiServiceClient.MAX_RESPONSE_BYTES + 1));
			expect(ReasonCode.SERVICE_PROTOCOL_ERROR, client::health);
			body.set(HEALTH);
			contentType.set("text/html");
			expect(ReasonCode.SERVICE_PROTOCOL_ERROR, client::health);
			contentType.set("application/json");
			delay.set(1500);
			expect(ReasonCode.SCAN_TIMEOUT, client::health);
			// Occupy all four slots with responses whose headers arrive but bodies stall.
			before = requests.get();
			try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
				var calls = new java.util.ArrayList<java.util.concurrent.Future<ReasonCode>>();
				for (int i = 0; i < 4; i++) calls.add(callers.submit(() -> {
					try {
						client.health();
						throw new AssertionError("Stalled request unexpectedly succeeded");
					} catch (ServiceException error) {
						return error.reasonCode();
					}
				}));
				long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(800);
				while (requests.get() < before + 4 && System.nanoTime() < deadline) Thread.sleep(5);
				check(requests.get() == before + 4, "Four bounded requests in flight");
				expect(ReasonCode.QUEUE_FULL, client::health);
				for (var call : calls) check(call.get() == ReasonCode.SCAN_TIMEOUT, "Stalled requests expire");
			}
			delay.set(0);
			check(client.health().mock(), "Timeouts release concurrency slots");
		} finally {
			server.stop(0);
			workers.shutdownNow();
		}
		for (String token : new String[]{"", " ", "bad\r\n", "x".repeat(257), "secret "}) {
			expect(ReasonCode.SERVICE_TOKEN_MISSING, () -> new AiServiceClient(config, token));
		}
		SolariaConfig unsafe = new SolariaConfig(URI.create("http://example.org"), 100, 1000, 2, true, 256,
			new SolariaConfig.AnchorOffset(0, 0, 5), 1536, 1, false);
		expect(ReasonCode.INPUT_INVALID, () -> new AiServiceClient(unsafe, TOKEN));
		try (AiServiceClient disconnected = new AiServiceClient(config, TOKEN)) {
			expect(ReasonCode.SERVICE_UNAVAILABLE, disconnected::health);
		}
	}

	private static JsonObject prediction(String label) throws ServiceException {
		return RuntimeResponses.parse("""
			{"api_version":"api_v1","request_id":"request","scan_id":"%s","mock":true,
			"quality":{"status":"pass","reason_code":null,"hint":"Ready"},"label":"%s",
			"confidence":0.9,"accepted":true,"threshold":0.6,"reason_code":null,
			"top3":[{"label":"%s","confidence":0.9}],"thumbnail":null,"elapsed_ms":1}
			""".formatted(SCAN, label, label));
	}

	private static JsonObject review(JsonObject prediction, int revision) throws ServiceException {
		JsonObject envelope = RuntimeResponses.parse("""
			{"api_version":"api_v1","review":{"scan_id":"%s","student_id":"participant",
			"revision":%d,"final_label":"mars","accepted":true,"decider":"teacher",
			"overrides":[{"revision":1,"label":"mars","decider":"teacher"}]}}
			""".formatted(SCAN, revision));
		envelope.getAsJsonObject("review").add("prediction", prediction);
		return envelope;
	}

	private static SolariaConfig config(int port, int timeout) throws Exception {
		return ConfigCodec.parse("{\"service_url\":\"http://127.0.0.1:" + port
			+ "\",\"connect_timeout_ms\":100,\"request_timeout_ms\":" + timeout + "}");
	}

	private static void live() throws Exception {
		int port = Integer.parseInt(System.getenv().getOrDefault("SOLARIA_SMOKE_PORT", "8000"));
		String token = System.getenv("SOLARIA_STARTUP_TOKEN");
		try (AiServiceClient client = AiServiceClient.fromEnvironment(config(port, 30000))) {
			check(client.health().mock(), "Live smoke only permits mock service");
			BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
			var graphics = image.createGraphics();
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, 640, 480);
			graphics.setColor(Color.BLACK);
			graphics.setStroke(new BasicStroke(8));
			graphics.drawOval(200, 120, 240, 240);
			graphics.dispose();
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageIO.write(image, "PNG", output);
			var prediction = client.classify(output.toByteArray(), "image/png", "java-smoke", "synthetic-java");
			check(prediction.accepted() && prediction.label() == AstronomyClass.EARTH && prediction.scanId() != null,
				"Java upload to Python mock");
			check(prediction.thumbnailBase64() != null, "Cleaned thumbnail received");
			check(client.review(prediction.scanId(), "java-smoke", "synthetic-java", 0).revision() == 0, "Host maps review");
			// Test-only teacher action. Production client deliberately exposes no override writer.
			try (HttpClient teacher = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
				HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/reviews/" + prediction.scanId() + "/override"))
					.timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString("{\"label\":\"mars\",\"revision\":0,\"note\":\"Synthetic Java smoke\"}")).build();
				check(teacher.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200, "Python teacher override");
			}
			var reviewed = client.review(prediction.scanId(), "java-smoke", "synthetic-java", 1);
			check(reviewed.teacherDecision() && reviewed.finalLabel() == AstronomyClass.MARS
				&& reviewed.prediction().label() == AstronomyClass.EARTH, "Override retrieved; original prediction preserved");
			expect(ReasonCode.SERVICE_PROTOCOL_ERROR, () -> client.review(prediction.scanId(), "java-smoke", "wrong-session", 0));
		}
	}

	private static void check(boolean value, String message) {
		assertions++;
		if (!value) throw new AssertionError(message);
	}

	private static void expect(ReasonCode reason, Checked action) throws Exception {
		try {
			action.run();
			throw new AssertionError("Expected " + reason);
		} catch (ServiceException error) {
			check(error.reasonCode() == reason, "Expected " + reason + ", got " + error.reasonCode());
			check(error.getCause() == null && !error.toString().contains(TOKEN), "Error sanitised");
		}
	}

	@FunctionalInterface
	private interface Checked {
		void run() throws Exception;
	}
}