package net.kyrangit.solaria.service;

import com.google.gson.JsonObject;
import net.kyrangit.solaria.config.ConfigCodec;
import net.kyrangit.solaria.config.ConfigException;
import net.kyrangit.solaria.config.SolariaConfig;
import net.kyrangit.solaria.logging.ReasonCode;

import java.io.ByteArrayOutputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Host-only HTTP boundary. Blocking methods must run off the game thread. No retries or redirects. */
public final class AiServiceClient implements AutoCloseable {
	public static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
	public static final int MAX_RESPONSE_BYTES = 512 * 1024;
	private static final Set<ReasonCode> WIRE_ERRORS = Set.of(ReasonCode.UNAUTHORIZED, ReasonCode.NOT_FOUND,
		ReasonCode.INPUT_INVALID, ReasonCode.INPUT_TOO_LARGE, ReasonCode.LABEL_NOT_IN_CLASSES,
		ReasonCode.QUEUE_FULL, ReasonCode.SERVICE_UNAVAILABLE, ReasonCode.MODEL_UNAVAILABLE,
		ReasonCode.SCAN_TIMEOUT, ReasonCode.INTERNAL_ERROR, ReasonCode.REVIEW_CONFLICT, ReasonCode.QUALITY_REJECTED);
	private static final ProxySelector DIRECT = new ProxySelector() {
		@Override
		public List<Proxy> select(URI uri) {
			return List.of(Proxy.NO_PROXY);
		}

		@Override
		public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) {
			// No diagnostics containing URLs or credentials.
		}
	};
	private final SolariaConfig config;
	private final String token;
	private final HttpClient http;
	private final Semaphore slots = new Semaphore(4);

	public AiServiceClient(SolariaConfig config, String token) throws ServiceException {
		// Records can be constructed directly; revalidate before any request can carry a token.
		try {
			this.config = ConfigCodec.parse(ConfigCodec.encode(config));
		} catch (ConfigException | RuntimeException e) {
			throw new ServiceException(ReasonCode.INPUT_INVALID);
		}
		if (token == null || token.isBlank() || token.length() > 256
			|| token.chars().anyMatch(c -> c < 32 || c > 126) || !token.equals(token.strip())) {
			throw new ServiceException(ReasonCode.SERVICE_TOKEN_MISSING);
		}
		this.token = token;
		http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
			.followRedirects(HttpClient.Redirect.NEVER).proxy(DIRECT).version(HttpClient.Version.HTTP_1_1).build();
	}

	/** Environment read only on host-side command/session creation, never from a client payload. */
	public static AiServiceClient fromEnvironment(SolariaConfig config) throws ServiceException {
		return new AiServiceClient(config, System.getenv("SOLARIA_STARTUP_TOKEN"));
	}

	public RuntimeResponses.Health health() throws ServiceException {
		return RuntimeResponses.health(send(request("/v1/health").GET().build()));
	}

	/** Student id must be a host-generated pseudonym, not a player name or client-provided field. */
	public RuntimeResponses.Prediction classify(byte[] image, String contentType, String requestId,
		String studentId) throws ServiceException {
		if (image == null || image.length == 0) throw new ServiceException(ReasonCode.INPUT_INVALID);
		if (image.length > config.maxScanBytes()) throw new ServiceException(ReasonCode.INPUT_TOO_LARGE);
		if (!"image/png".equals(contentType) && !"image/jpeg".equals(contentType)) {
			throw new ServiceException(ReasonCode.INPUT_INVALID);
		}
		validateId(requestId);
		validateId(studentId);
		// Fail closed on class-set drift or model loss, including after a service restart.
		health();
		HttpRequest request = request("/v1/classify").header("Content-Type", contentType)
			.header("X-Request-ID", requestId).header("X-Solaria-Student-ID", studentId)
			.POST(HttpRequest.BodyPublishers.ofByteArray(image.clone())).build();
		return RuntimeResponses.prediction(send(request), requestId);
	}

	/** Read-only: teacher writes happen in the AI console. No build or teleport side effects. */
	public RuntimeResponses.Review review(String scanId, String requestId, String studentId,
		int minimumRevision) throws ServiceException {
		if (scanId == null || !scanId.matches("[a-f0-9]{32}") || minimumRevision < 0 || minimumRevision > 20) {
			throw new ServiceException(ReasonCode.INPUT_INVALID);
		}
		validateId(requestId);
		validateId(studentId);
		return RuntimeResponses.review(send(request("/v1/reviews/" + scanId).GET().build()),
			scanId, requestId, studentId, minimumRevision);
	}

	public RuntimeResponses.Review acknowledge(RuntimeResponses.Review review, String status) throws ServiceException {
		if (review == null || review.scanId() == null || !review.scanId().matches("[a-f0-9]{32}")
			|| review.revision() < 1 || review.revision() > 20 || status == null
			|| !Set.of("waiting_student", "ready", "rejected").contains(status))
			throw new ServiceException(ReasonCode.INPUT_INVALID);
		validateId(review.prediction().requestId());
		validateId(review.studentId());
		JsonObject body = new JsonObject();
		body.addProperty("revision", review.revision());
		body.addProperty("request_id", review.prediction().requestId());
		body.addProperty("student_id", review.studentId());
		body.addProperty("status", status);
		return RuntimeResponses.review(send(request("/v1/reviews/" + review.scanId() + "/ack")
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()),
			review.scanId(), review.prediction().requestId(), review.studentId(), review.revision());
	}

	private static void validateId(String id) throws ServiceException {
		if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) throw new ServiceException(ReasonCode.INPUT_INVALID);
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(config.serviceUrl().resolve(path))
			.timeout(Duration.ofMillis(config.requestTimeoutMs()))
			.header("Authorization", "Bearer " + token).header("Accept", "application/json");
	}

	private JsonObject send(HttpRequest request) throws ServiceException {
		if (!slots.tryAcquire()) throw new ServiceException(ReasonCode.QUEUE_FULL, 1000);
		CompletableFuture<HttpResponse<byte[]>> future = null;
		try {
			future = http.sendAsync(request, _ -> new LimitedBody());
			// Includes the entire body, not just response headers; a stalled stream cannot occupy a slot forever.
			HttpResponse<byte[]> response = future.get(config.requestTimeoutMs(), TimeUnit.MILLISECONDS);
			String type = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip();
			if (!type.equalsIgnoreCase("application/json")) throw RuntimeResponses.protocol();
			JsonObject object = RuntimeResponses.parse(new String(response.body(), StandardCharsets.UTF_8));
			RuntimeResponses.version(object);
			if (response.statusCode() != 200) {
				ReasonCode code;
				try {
					code = ReasonCode.valueOf(RuntimeResponses.text(object, "reason_code"));
				} catch (IllegalArgumentException e) {
					throw RuntimeResponses.protocol();
				}
				if (!WIRE_ERRORS.contains(code) || response.statusCode() < 400 || response.statusCode() > 599) {
					throw RuntimeResponses.protocol();
				}
				int retry = object.has("retry_after_ms")
					? RuntimeResponses.integer(object, "retry_after_ms", 1, 300000) : 0;
				throw new ServiceException(code, retry);
			}
			return object;
		} catch (TimeoutException e) {
			throw new ServiceException(ReasonCode.SCAN_TIMEOUT);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ServiceException(ReasonCode.SERVICE_UNAVAILABLE);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof HttpTimeoutException) throw new ServiceException(ReasonCode.SCAN_TIMEOUT);
			if (cause instanceof ServiceException safe) throw safe;
			throw new ServiceException(ReasonCode.SERVICE_UNAVAILABLE);
		} catch (RuntimeException e) {
			throw new ServiceException(ReasonCode.SERVICE_UNAVAILABLE);
		} finally {
			if (future != null && !future.isDone()) future.cancel(true);
			slots.release();
		}
	}

	@Override
	public void close() {
		http.shutdownNow();
	}

	private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
		private final CompletableFuture<byte[]> result = new CompletableFuture<>();
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private Flow.Subscription subscription;

		@Override
		public CompletionStage<byte[]> getBody() {
			return result;
		}

		@Override
		public void onSubscribe(Flow.Subscription subscription) {
			this.subscription = subscription;
			subscription.request(1);
		}

		@Override
		public void onNext(List<ByteBuffer> buffers) {
			for (ByteBuffer buffer : buffers) {
				if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
					result.completeExceptionally(RuntimeResponses.protocol());
					subscription.cancel();
					return;
				}
				byte[] chunk = new byte[buffer.remaining()];
				buffer.get(chunk);
				bytes.writeBytes(chunk);
			}
			subscription.request(1);
		}

		@Override
		public void onError(Throwable error) {
			result.completeExceptionally(new ServiceException(ReasonCode.SERVICE_UNAVAILABLE));
		}

		@Override
		public void onComplete() {
			result.complete(bytes.toByteArray());
		}
	}
}