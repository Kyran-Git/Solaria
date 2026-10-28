package net.kyrangit.solaria;

import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.scan.ReviewGate;

/** Synthetic consent/revision regression runner; no Minecraft instance required. */
public final class ReviewGateTests {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var gate = new ReviewGate(AstronomyClass.EARTH, true);
        check(!gate.ready(), "AI accepted is not teacher approval");
        check(gate.agree(0) && !gate.ready(), "Student alone cannot authorize");
        check(gate.apply(1, AstronomyClass.EARTH, true) && gate.ready(), "Same-label approval keeps consent");
        check(!gate.apply(1, AstronomyClass.MARS, true), "Duplicate revision ignored");
        check(gate.label() == AstronomyClass.EARTH, "Duplicate cannot mutate label");
        check(!gate.apply(0, AstronomyClass.MARS, true), "Older revision ignored");
        check(gate.apply(2, AstronomyClass.MARS, true) && !gate.ready(), "Correction revokes consent");
        check(!gate.agree(1) && !gate.ready(), "Delayed consent cannot approve correction");
        check(gate.agree(2) && gate.ready(), "Fresh consent authorizes corrected label");
        check(gate.apply(3, AstronomyClass.MARS, false) && !gate.ready(), "Rejection revokes consent");
        check(!gate.agree(3), "Rejected result cannot be accepted");
        check(gate.application().equals("rejected"), "Rejection acknowledged explicitly");
        check(gate.apply(4, AstronomyClass.MARS, true) && !gate.ready(), "Approval after rejection needs consent");
        check(gate.application().equals("waiting_student"), "Waiting student acknowledgement");
        check(gate.agree(4) && gate.application().equals("ready"), "Ready acknowledgement");
        check(!gate.apply(21, AstronomyClass.MOON, true), "Revision bounded");
        var low = new ReviewGate(null, false);
        check(!low.agree(0), "Unknown label cannot be accepted");
        check(low.apply(1, AstronomyClass.MOON, true), "Teacher resolves low confidence");
        check(!low.ready() && low.agree(1) && low.ready(), "Low confidence needs explicit consent");
        var separate = new ReviewGate(AstronomyClass.MARS, true);
        check(!separate.ready() && !separate.consent(), "Replacement sessions never inherit consent");
        var screen = net.kyrangit.solaria.scan.ScanState.idle()
            .withFailure(net.kyrangit.solaria.logging.ReasonCode.QUALITY_REJECTED)
            .withTeacherLabel(AstronomyClass.MARS, 0.2f);
        check(screen.status() == net.kyrangit.solaria.scan.ScanState.Status.AWAITING_STUDENT_CONFIRM,
            "Teacher resolves prior unavailable screen");
        check(screen.predictedClass() == AstronomyClass.MARS, "Corrected screen shows effective label");
        System.out.println("ReviewGateTests: " + assertions + " assertions passed (synthetic).");
    }
}
