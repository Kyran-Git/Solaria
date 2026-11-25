package net.kyrangit.solaria.client;

import net.kyrangit.solaria.client.ui.UiLayoutMetrics;
import net.kyrangit.solaria.client.ui.UiLayoutMetrics.MenuLayout;
import net.kyrangit.solaria.client.ui.UiLayoutMetrics.QuizLayout;
import net.kyrangit.solaria.client.ui.UiLayoutMetrics.Rect;
import net.kyrangit.solaria.client.ui.UiLayoutMetrics.ScannerLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless responsive layout verification across 1280×720, 1366×768, and 1920×1080 at GUI
 * scales 1–4. Confirms panels stay centred, inside screen bounds, tightly fitted, and free
 * of clipped or overlapping buttons/wells.
 */
public final class UiLayoutTests {
	private static int assertions;

	public static void main(String[] args) {
		int[][] resolutions = {{1280, 720}, {1366, 768}, {1920, 1080}};
		for (int[] res : resolutions) {
			for (int scale = 1; scale <= 4; scale++) {
				int w = (int) Math.ceil(res[0] / (double) scale);
				int h = (int) Math.ceil(res[1] / (double) scale);
				String tag = res[0] + "x" + res[1] + "@scale" + scale + " (" + w + "x" + h + ")";
				verifyQuizModes(w, h, tag);
				verifyScanner(w, h, tag);
				verifyMenus(w, h, tag);
			}
		}

		// Explicit dead-space check on large 1920x1080 scale 2 (960x540).
		QuizLayout large = QuizLayout.compute(960, 540, 2, 4, false, false, 0, false);
		check(large.panel().height() < (int) (540 * 0.65),
			"large screen quiz panel avoids empty bottom void: height=" + large.panel().height());

		System.out.println("UI layout check (synthetic): " + assertions + " assertions passed.");
	}

	private static void verifyQuizModes(int w, int h, String tag) {
		verifyQuiz(w, h, 3, 4, false, false, 0, false, tag + " mcq");
		verifyQuiz(w, h, 2, 4, false, true, 1, false, tag + " mcq-fb");
		verifyQuiz(w, h, 2, 2, false, false, 0, false, tag + " tf");
		verifyQuiz(w, h, 2, 2, false, true, 1, false, tag + " tf-fb");
		verifyQuiz(w, h, 2, 4, false, false, 2, false, tag + " order");
		verifyQuiz(w, h, 2, 4, false, true, 1, false, tag + " order-fb");
		verifyQuiz(w, h, 2, 4, true, false, 2, false, tag + " match");
		verifyQuiz(w, h, 2, 4, true, true, 1, false, tag + " match-fb");
		verifyQuiz(w, h, 1, -1, false, false, 2, true, tag + " result");
		verifyQuiz(w, h, 1, -1, false, false, 1, false, tag + " waiting");
	}

	private static void verifyQuiz(
		int w, int h, int promptLines, int choices, boolean match, boolean feedback, int footerCount, boolean result, String label
	) {
		QuizLayout l = QuizLayout.compute(w, h, promptLines, choices, match, feedback, footerCount, result);
		checkCenteredBounds(l.panel(), w, h, label);
		checkInside(l.panel(), l.closeButton(), 12, 12, label + " close");
		checkInside(l.panel(), l.headerChip(), 24, 10, label + " headerChip");

		List<Rect> boxes = new ArrayList<>();
		boxes.add(l.closeButton());
		boxes.add(l.headerChip());
		if (l.progressBar() != null) {
			checkInside(l.panel(), l.progressBar(), 30, 4, label + " progressBar");
			checkInside(l.panel(), l.difficultyChip(), 24, 10, label + " diffChip");
			checkInside(l.panel(), l.scoreChip(), 24, 10, label + " scoreChip");
			boxes.add(l.progressBar());
			boxes.add(l.difficultyChip());
			boxes.add(l.scoreChip());
		}
		if (l.questionWell() != null) {
			checkInside(l.panel(), l.questionWell(), 40, 20, label + " questionWell");
			boxes.add(l.questionWell());
		}
		for (Rect r : l.leftButtons()) {
			checkInside(l.panel(), r, 16, 12, label + " leftBtn");
			boxes.add(r);
		}
		for (Rect r : l.choiceButtons()) {
			checkInside(l.panel(), r, 16, 12, label + " choiceBtn");
			boxes.add(r);
		}
		if (l.feedbackWell() != null) {
			checkInside(l.panel(), l.feedbackWell(), 40, 18, label + " feedbackWell");
			boxes.add(l.feedbackWell());
		}
		for (Rect r : l.footerButtons()) {
			checkInside(l.panel(), r, 32, 14, label + " footerBtn");
			boxes.add(r);
		}
		checkNoOverlaps(boxes, label);
	}

	private static void verifyScanner(int w, int h, String tag) {
		ScannerLayout l = ScannerLayout.compute(w, h);
		checkCenteredBounds(l.panel(), w, h, tag + " scanner");
		checkInside(l.panel(), l.closeButton(), 12, 12, tag + " scanner close");
		checkInside(l.panel(), l.headerChip(), 24, 10, tag + " scanner chip");

		List<Rect> outer = new ArrayList<>();
		outer.add(l.closeButton());
		outer.add(l.headerChip());
		for (Rect r : l.stageChips()) {
			checkInside(l.panel(), r, 24, 10, tag + " scanner stageChip");
			outer.add(r);
		}
		checkInside(l.panel(), l.viewport(), 60, 40, tag + " scanner viewport");
		checkInside(l.panel(), l.infoWell(), 60, 28, tag + " scanner infoWell");
		checkInside(l.infoWell(), l.studentChip(), 24, 10, tag + " scanner studentChip");
		checkInside(l.infoWell(), l.teacherChip(), 24, 10, tag + " scanner teacherChip");
		checkInside(l.infoWell(), l.confidenceBar(), 40, 4, tag + " scanner confBar");
		checkNoOverlaps(List.of(l.studentChip(), l.teacherChip(), l.confidenceBar()), tag + " scanner infoWell inner");
		outer.add(l.viewport());
		outer.add(l.infoWell());
		for (Rect r : l.actionButtons()) {
			checkInside(l.panel(), r, 40, 14, tag + " scanner actionBtn");
			outer.add(r);
		}
		checkNoOverlaps(outer, tag + " scanner");
	}

	private static void verifyMenus(int w, int h, String tag) {
		MenuLayout main = MenuLayout.computeMain(w, h);
		checkCenteredBounds(main.panel(), w, h, tag + " menuMain");
		List<Rect> mainBoxes = new ArrayList<>();
		mainBoxes.add(main.closeButton());
		mainBoxes.add(main.headerChip());
		mainBoxes.add(main.subtitleWell());
		for (Rect r : main.buttons()) {
			checkInside(main.panel(), r, 40, 14, tag + " menuMain btn");
			mainBoxes.add(r);
		}
		checkNoOverlaps(mainBoxes, tag + " menuMain");

		MenuLayout picker = MenuLayout.computePicker(w, h, 10);
		checkCenteredBounds(picker.panel(), w, h, tag + " menuPicker");
		List<Rect> pickerBoxes = new ArrayList<>();
		pickerBoxes.add(picker.closeButton());
		pickerBoxes.add(picker.headerChip());
		pickerBoxes.add(picker.subtitleWell());
		for (Rect r : picker.buttons()) {
			checkInside(picker.panel(), r, 40, 14, tag + " menuPicker btn");
			pickerBoxes.add(r);
		}
		checkNoOverlaps(pickerBoxes, tag + " menuPicker");
	}

	private static void checkCenteredBounds(Rect panel, int w, int h, String label) {
		check(panel.x() >= 4 && panel.right() <= w - 4, label + " horizontal screen bounds");
		check(panel.y() >= 4 && panel.bottom() <= h - 4, label + " vertical screen bounds");
		check(Math.abs((panel.x() + panel.right()) - w) <= 2, label + " horizontally centred");
		check(Math.abs((panel.y() + panel.bottom()) - h) <= 2, label + " vertically centred");
	}

	private static void checkInside(Rect parent, Rect child, int minW, int minH, String label) {
		check(parent.contains(child), label + " contained inside parent " + parent + " vs " + child);
		check(child.width() >= minW && child.height() >= minH, label + " usable size " + child);
	}

	private static void checkNoOverlaps(List<Rect> rects, String label) {
		for (int i = 0; i < rects.size(); i++) {
			for (int j = i + 1; j < rects.size(); j++) {
				check(!rects.get(i).intersects(rects.get(j)),
					label + " overlap between " + rects.get(i) + " and " + rects.get(j));
			}
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("UI layout check failed: " + message);
		}
		assertions++;
	}
}
