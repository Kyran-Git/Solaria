package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.logging.ReasonCode;
import net.kyrangit.solaria.scan.ScanAction;
import net.kyrangit.solaria.scan.ScanState;

/**
 * Dependency-free unit tests for the scanner state machine. Plumbing check
 * (synthetic): no Minecraft runtime, no network, no files. Classroom
 * usability is validated separately in {@code MOD_MANUAL_TEST.md}.
 */
public final class ScanStateTests {
	private static int assertions;

	public static void main(String[] args) {
		testIdleIsClean();
		testUploadBounds();
		testLocalCaptureLifecycle();
		testRetryFromUnavailableGoesIdle();
		testCloseAlwaysReturnsIdle();
		testAcceptOnlyFromAwaitingConfirm();
		testRejectOnlyFromAwaitingConfirm();
		testPredictionInjectionAndAccept();
		testBuildCompleteTransitionsToDone();
		testFailureInjectionIsTerminal();
		testTransitionIsPure();
		testConfidenceBoundsRejected();
		testStateImmutable();
		System.out.println("Plumbing check (synthetic): " + assertions + " scanner assertions passed.");
	}

	private static void testUploadBounds() {
		var upload = new net.kyrangit.solaria.scan.ScanUpload(4, 4, 2, 100);
		check(!upload.append(0, new byte[]{1, 2}, 1), "partial upload");
		expectIllegalArg(() -> upload.append(0, new byte[]{3}, 2), "duplicate chunk");
		expectIllegalArg(() -> upload.append(1, new byte[]{3, 4, 5}, 2), "byte overflow");
		check(upload.append(1, new byte[]{3, 4}, 2), "exact completion");
		check(java.util.Arrays.equals(upload.finish(), new byte[]{1, 2, 3, 4}), "ordered bytes");
		expectIllegalArg(() -> upload.append(2, new byte[]{5}, 3), "post completion");
		expectIllegalArg(() -> new net.kyrangit.solaria.scan.ScanUpload(5, 4, 2, 100), "size policy");
		var expired = new net.kyrangit.solaria.scan.ScanUpload(1, 4, 2, 10);
		expectIllegalArg(() -> expired.append(0, new byte[]{1}, 10), "deadline");
		var chunkCount = new net.kyrangit.solaria.scan.ScanUpload(2, 4, 1, 100);
		chunkCount.append(0, new byte[]{1}, 1);
		expectIllegalArg(() -> chunkCount.append(1, new byte[]{2}, 2), "chunk count policy");
		ScanState captured = ScanState.idle().transition(ScanAction.START_CAPTURE).withCapturedFrame();
		check(captured.submit().status() == ScanState.Status.UPLOADING, "explicit submission");
		expectIllegal(() -> ScanState.idle().submit(), "no submission before capture");
	}

	private static void testIdleIsClean() {
		ScanState s = ScanState.idle();
		check(s.status() == ScanState.Status.IDLE, "idle starts in IDLE");
		check(s.predictedClass() == null, "idle has no prediction");
		check(s.confidence() == 0.0f, "idle has zero confidence");
		check(s.reasonCode() == null, "idle has no reason code");
		check(!s.isTerminal(), "idle is not terminal");
	}

	private static void testLocalCaptureLifecycle() {
		ScanState after = ScanState.idle().transition(ScanAction.START_CAPTURE);
		check(after.status() == ScanState.Status.CAPTURING, "explicit camera request enters CAPTURING");
		check(after.reasonCode() == null, "opening is not a fabricated failure");
		check(!after.isTerminal(), "preview is not terminal");
		ScanState captured = after.withCapturedFrame();
		check(captured.status() == ScanState.Status.CAPTURED, "real frame remains local");
		check(captured.predictedClass() == null, "capture does not invent a prediction");
		check(captured.reasonCode() == null, "capture does not invent upload failure");
		check(captured.transition(ScanAction.RETRY).status() == ScanState.Status.IDLE, "retake resets to idle");
		expectIllegal(() -> captured.transition(ScanAction.ACCEPT), "photo is not prediction consent");
		expectIllegal(() -> captured.withPrediction(AstronomyClass.EARTH, 0.9f), "photo is not an upload");
		expectIllegal(captured::withCapturedFrame, "duplicate capture rejected");
		expectIllegal(() -> ScanState.idle().withCapturedFrame(), "capture from idle rejected");
		expectIllegal(() -> after.transition(ScanAction.START_CAPTURE), "duplicate open rejected");
	}

	private static void testRetryFromUnavailableGoesIdle() {
		ScanState unavailable = ScanState.idle().withFailure(ReasonCode.CAPTURE_UNAVAILABLE);
		ScanState retried = unavailable.transition(ScanAction.RETRY);
		check(retried.status() == ScanState.Status.IDLE, "RETRY from UNAVAILABLE returns to IDLE");
		check(retried.reasonCode() == null, "RETRY clears the reason code");
	}

	private static void testCloseAlwaysReturnsIdle() {
		for (ScanState start : new ScanState[]{
			ScanState.idle(),
			ScanState.idle().transition(ScanAction.START_CAPTURE),
			ScanState.idle().transition(ScanAction.START_CAPTURE).withCapturedFrame()
		}) {
			check(start.transition(ScanAction.CLOSE).status() == ScanState.Status.IDLE,
				"CLOSE always returns to IDLE");
		}
	}

	private static void testAcceptOnlyFromAwaitingConfirm() {
		expectIllegal(() -> ScanState.idle().transition(ScanAction.ACCEPT), "ACCEPT rejected from IDLE");
		expectIllegal(
			() -> ScanState.idle().transition(ScanAction.START_CAPTURE).transition(ScanAction.ACCEPT),
			"ACCEPT rejected from CAPTURING");
	}

	private static void testRejectOnlyFromAwaitingConfirm() {
		expectIllegal(() -> ScanState.idle().transition(ScanAction.REJECT), "REJECT rejected from IDLE");
	}

	private static void testPredictionInjectionAndAccept() {
		// We cannot reach UPLOADING through user actions yet; the injection
		// paths exist for Phase 4 wiring and are tested here independently.
		ScanState uploading = reflectivelyEnter(ScanState.Status.UPLOADING);
		ScanState awaiting = uploading.withPrediction(AstronomyClass.EARTH, 0.75f);
		check(awaiting.status() == ScanState.Status.AWAITING_STUDENT_CONFIRM,
			"prediction moves to AWAITING_STUDENT_CONFIRM");
		check(awaiting.predictedClass() == AstronomyClass.EARTH, "prediction carries class");
		check(awaiting.confidence() == 0.75f, "prediction carries confidence");

		ScanState building = awaiting.transition(ScanAction.ACCEPT);
		check(building.status() == ScanState.Status.BUILDING, "ACCEPT moves to BUILDING");
		check(building.predictedClass() == AstronomyClass.EARTH, "BUILDING keeps class");

		ScanState rejected = awaiting.transition(ScanAction.REJECT);
		check(rejected.status() == ScanState.Status.IDLE, "REJECT returns to IDLE");

		ScanState retried = awaiting.transition(ScanAction.RETRY);
		check(retried.status() == ScanState.Status.IDLE, "RETRY from AWAITING returns to IDLE");
	}

	private static void testBuildCompleteTransitionsToDone() {
		ScanState building = reflectivelyEnter(ScanState.Status.UPLOADING)
			.withPrediction(AstronomyClass.MARS, 0.8f)
			.transition(ScanAction.ACCEPT);
		ScanState done = building.withBuildComplete();
		check(done.status() == ScanState.Status.DONE, "withBuildComplete moves to DONE");
		check(done.predictedClass() == AstronomyClass.MARS, "DONE remembers class");
		check(done.isTerminal(), "DONE is terminal");
	}

	private static void testFailureInjectionIsTerminal() {
		ScanState failed = reflectivelyEnter(ScanState.Status.CLASSIFYING)
			.withFailure(ReasonCode.CLASSIFIER_UNAVAILABLE);
		check(failed.status() == ScanState.Status.UNAVAILABLE, "withFailure moves to UNAVAILABLE");
		check(failed.reasonCode() == ReasonCode.CLASSIFIER_UNAVAILABLE, "withFailure carries reason");
		check(failed.isTerminal(), "failure is terminal");
	}

	private static void testTransitionIsPure() {
		ScanState before = ScanState.idle();
		ScanState after = before.transition(ScanAction.START_CAPTURE);
		check(before.status() == ScanState.Status.IDLE, "transition does not mutate caller status");
		check(before.reasonCode() == null, "transition does not mutate caller reason");
		check(after != before, "transition returns new instance");
	}

	private static void testConfidenceBoundsRejected() {
		ScanState uploading = reflectivelyEnter(ScanState.Status.UPLOADING);
		expectIllegalArg(() -> uploading.withPrediction(AstronomyClass.SUN, -0.01f), "negative confidence");
		expectIllegalArg(() -> uploading.withPrediction(AstronomyClass.SUN, 1.01f), "confidence > 1");
		expectIllegalArg(() -> uploading.withPrediction(AstronomyClass.SUN, Float.NaN), "NaN confidence");
	}

	private static void testStateImmutable() {
		// All ScanState fields are private final; the record-free design still
		// guards invariants through its API. Any attempt to add setters later
		// should break this test by introducing a public mutator.
		for (var method : ScanState.class.getDeclaredMethods()) {
			if (java.lang.reflect.Modifier.isPublic(method.getModifiers()) && method.getName().startsWith("set")) {
				throw new AssertionError("ScanState must not expose public setters: " + method);
			}
		}
		assertions++;
	}

	/**
	 * Jumps the state machine into a non-user-reachable {@link ScanState.Status}
	 * for test purposes. In production these statuses are entered by the
	 * server-side scan pipeline (Phase 4 future work), not by button presses,
	 * so there is no public API to reach them from IDLE yet.
	 */
	private static ScanState reflectivelyEnter(ScanState.Status status) {
		try {
			var ctor = ScanState.class.getDeclaredConstructor(
				ScanState.Status.class, AstronomyClass.class, float.class, ReasonCode.class);
			ctor.setAccessible(true);
			return ctor.newInstance(status, null, 0.0f, null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Cannot construct ScanState for test; API changed", e);
		}
	}

	private static void expectIllegal(Runnable action, String label) {
		try {
			action.run();
			throw new AssertionError("Expected IllegalStateException: " + label);
		} catch (IllegalStateException expected) {
			assertions++;
		}
	}

	private static void expectIllegalArg(Runnable action, String label) {
		try {
			action.run();
			throw new AssertionError("Expected IllegalArgumentException: " + label);
		} catch (IllegalArgumentException expected) {
			assertions++;
		}
	}

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
