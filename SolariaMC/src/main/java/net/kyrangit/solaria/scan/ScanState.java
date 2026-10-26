package net.kyrangit.solaria.scan;

import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.logging.ReasonCode;

import java.util.Objects;

/**
 * Pure state machine for the scanner screen.
 *
 * <p>Lives in the common source set so it can be unit-tested without a
 * Minecraft runtime. Rendering code (see {@code client.ScannerScreen}) only
 * reads these states and dispatches {@link ScanAction}s. Camera resources
 * remain client-owned and never enter this common state object.
 *
 * <p>Capture stays local. CAPTURED does not imply upload, student agreement
 * with a prediction, teacher approval, generation, or teleport permission.
 */
public final class ScanState {

	/** Lifecycle of a single scan session. */
	public enum Status {
		/** Student just opened the camera; nothing has been captured. */
		IDLE,
		/** Client is opening the camera or showing a live, local preview. */
		CAPTURING,
		/** A still image is held locally. Upload/classification is not connected yet. */
		CAPTURED,
		/** A frame has been captured and is being uploaded to the host. Not reachable yet (Phase 4). */
		UPLOADING,
		/** The AI service is deciding. Not reachable yet (Phase 4). */
		CLASSIFYING,
		/** A result above the confidence threshold is awaiting student accept/reject. */
		AWAITING_STUDENT_CONFIRM,
		/** The student accepted; the server is building the structure. */
		BUILDING,
		/** Terminal: this session cannot continue; show {@link #reasonCode} and allow close/retry. */
		UNAVAILABLE,
		/** Terminal: build finished successfully for {@link #predictedClass}. */
		DONE
	}

	private final Status status;
	private final AstronomyClass predictedClass;
	private final float confidence;
	private final ReasonCode reasonCode;

	private ScanState(Status status, AstronomyClass predictedClass, float confidence, ReasonCode reasonCode) {
		this.status = Objects.requireNonNull(status, "status");
		this.predictedClass = predictedClass;
		this.confidence = confidence;
		this.reasonCode = reasonCode;
	}

	public static ScanState idle() {
		return new ScanState(Status.IDLE, null, 0.0f, null);
	}

	public Status status() {
		return status;
	}

	/** Non-null only when {@link #status} is AWAITING_STUDENT_CONFIRM, BUILDING or DONE. */
	public AstronomyClass predictedClass() {
		return predictedClass;
	}

	/** Confidence in [0,1]; meaningful only when a prediction is present. */
	public float confidence() {
		return confidence;
	}

	/** Non-null only when {@link #status} is UNAVAILABLE. */
	public ReasonCode reasonCode() {
		return reasonCode;
	}

	public boolean isTerminal() {
		return status == Status.UNAVAILABLE || status == Status.DONE;
	}

	/**
	 * Advance this state in response to {@code action}. Pure: never mutates
	 * {@code this}; always returns a new {@link ScanState}.
	 *
	 * @throws IllegalStateException if the action is not valid for the current status.
	 */
	public ScanState transition(ScanAction action) {
		Objects.requireNonNull(action, "action");
		return switch (action) {
			case START_CAPTURE -> {
				if (status != Status.IDLE) {
					throw new IllegalStateException("START_CAPTURE only valid in IDLE");
				}
				yield new ScanState(Status.CAPTURING, null, 0.0f, null);
			}
			case ACCEPT -> {
				if (status != Status.AWAITING_STUDENT_CONFIRM) {
					throw new IllegalStateException("ACCEPT only valid when awaiting confirm");
				}
				yield new ScanState(Status.BUILDING, predictedClass, confidence, null);
			}
			case RETRY -> {
				if (status != Status.UNAVAILABLE && status != Status.AWAITING_STUDENT_CONFIRM && status != Status.CAPTURED) {
					throw new IllegalStateException("Cannot RETRY from " + status);
				}
				yield idle();
			}
			case REJECT -> {
				if (status != Status.AWAITING_STUDENT_CONFIRM) {
					throw new IllegalStateException("Cannot REJECT from " + status);
				}
				yield idle();
			}
			case CLOSE -> idle();
		};
	}

	/** A real camera frame was frozen locally; never pretends that upload occurred. */
	public ScanState withCapturedFrame() {
		if (status != Status.CAPTURING) throw new IllegalStateException("Capture only valid while camera is open");
		return new ScanState(Status.CAPTURED, null, 0.0f, null);
	}

	/**
	 * Server-side injection: a real classification result arrived above the
	 * confidence threshold. Only valid from UPLOADING or CLASSIFYING (which
	 * are not reachable yet in Phase 4; this method exists so the hook point
	 * is already covered by tests).
	 */
	public ScanState withPrediction(AstronomyClass predicted, float conf) {
		Objects.requireNonNull(predicted, "predicted");
		if (conf < 0.0f || conf > 1.0f || Float.isNaN(conf)) {
			throw new IllegalArgumentException("confidence must be in [0,1]");
		}
		if (status != Status.UPLOADING && status != Status.CLASSIFYING) {
			throw new IllegalStateException("withPrediction not valid from " + status);
		}
		return new ScanState(Status.AWAITING_STUDENT_CONFIRM, predicted, conf, null);
	}

	/** Server-side injection: a classifier/transport error happened. */
	public ScanState withFailure(ReasonCode code) {
		Objects.requireNonNull(code, "code");
		return new ScanState(Status.UNAVAILABLE, null, 0.0f, code);
	}

	/** Server-side injection: the build completed. */
	public ScanState withBuildComplete() {
		if (status != Status.BUILDING) {
			throw new IllegalStateException("withBuildComplete only valid from BUILDING");
		}
		return new ScanState(Status.DONE, predictedClass, confidence, null);
	}
}
