package net.kyrangit.solaria.scan;

/**
 * User-driven inputs to {@link ScanState#transition(ScanAction)}.
 *
 * <p>These are the only transitions the student or teacher UI can trigger
 * directly. Classification results and build completions are injected via
 * {@link ScanState#withPrediction} / {@link ScanState#withFailure} /
 * {@link ScanState#withBuildComplete} because they arrive from the server,
 * not from a button press.
 */
public enum ScanAction {
	/** Student explicitly requested opening the camera on an IDLE scanner. */
	START_CAPTURE,
	/** Student accepted a prediction and wants the build to start. */
	ACCEPT,
	/** Student rejected the current prediction; return to IDLE. */
	REJECT,
	/** Student wants to try again after a UNAVAILABLE result. */
	RETRY,
	/** Student closed the screen; the next open starts from IDLE. */
	CLOSE
}
