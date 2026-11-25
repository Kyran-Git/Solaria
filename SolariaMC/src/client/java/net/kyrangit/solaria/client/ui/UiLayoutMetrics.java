package net.kyrangit.solaria.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, deterministic, font-free layout calculator for Step 12 screens.
 *
 * <p>Centres every panel inside {@code screenW × screenH}, wraps vertical space tightly around
 * actual content, and switches multi-choice layouts to a 2×2 grid on tight Scale-4 screens
 * ({@code screenH < 235}) so nothing clips or overlaps across GUI scales 1–4.
 */
public final class UiLayoutMetrics {
	private UiLayoutMetrics() {
	}

	public record Rect(int x, int y, int width, int height) {
		public int right() {
			return x + width;
		}

		public int bottom() {
			return y + height;
		}

		public boolean intersects(Rect other) {
			return x < other.right() && right() > other.x
				&& y < other.bottom() && bottom() > other.y;
		}

		public boolean contains(Rect inner) {
			return inner.x >= x && inner.right() <= right()
				&& inner.y >= y && inner.bottom() <= bottom();
		}
	}

	public record QuizLayout(
		Rect panel,
		Rect closeButton,
		Rect headerChip,
		Rect progressBar,
		Rect difficultyChip,
		Rect scoreChip,
		Rect questionWell,
		List<Rect> leftButtons,
		List<Rect> choiceButtons,
		Rect feedbackWell,
		List<Rect> footerButtons,
		boolean twoColumnChoices,
		boolean compact
	) {
		public static QuizLayout compute(
			int screenW,
			int screenH,
			int promptLines,
			int choiceCount,
			boolean isMatch,
			boolean hasFeedback,
			int footerCount,
			boolean isResult
		) {
			boolean tight = screenH < 220;
			boolean compact = screenH < 260;
			int margin = 6;
			int maxW = Math.max(200, screenW - margin * 2);
			int maxH = Math.max(150, screenH - margin * 2);
			int pw = Math.min(isResult ? 360 : 416, maxW);
			int padX = tight ? 10 : 14;
			int headerH = tight ? 22 : 26;
			int closeSize = tight ? 14 : 17;
			int innerW = pw - padX * 2;
			int footerH = footerCount > 0 ? (tight ? 16 : 20) : 0;
			int footerPad = footerCount > 0 ? (tight ? 6 : 9) : 0;

			if (isResult || choiceCount < 0) {
				int bodyH = isResult ? (tight ? 88 : 112) : (tight ? 44 : 56);
				int ph = Math.min(maxH, headerH + bodyH + footerH + footerPad * 2 + 8);
				int px = (screenW - pw) / 2;
				int py = (screenH - ph) / 2;
				Rect panel = new Rect(px, py, pw, ph);
				Rect closeBtn = new Rect(panel.right() - closeSize - 4, py + 4, closeSize, closeSize);
				Rect chip = new Rect(closeBtn.x - 62, py + (tight ? 4 : 6), 56, tight ? 13 : 14);
				List<Rect> footer = buildFooter(panel, padX, footerH, footerPad, footerCount);
				return new QuizLayout(panel, closeBtn, chip, null, null, null, null,
					List.of(), List.of(), null, footer, false, compact);
			}

			int metaH = tight ? 13 : 15;
			int lines = Math.max(1, Math.min(tight ? 3 : 4, promptLines));
			int qTextH = lines * (tight ? 9 : 11);
			int qWellH = tight ? Math.max(22, qTextH + 6) : Math.max(42, qTextH + 20);
			int chipW = Math.min(68, (innerW - 24) / 4);
			int scoreW = chipW;
			int diffW = chipW;

			boolean twoColChoices = !isMatch && choiceCount >= 3 && screenH < 235;
			int rows = isMatch ? Math.max(1, choiceCount) : twoColChoices ? (choiceCount + 1) / 2 : Math.max(1, choiceCount);
			int rowH = tight ? (hasFeedback && isMatch ? 12 : 15) : (compact ? 18 : 20);
			int rowGap = tight ? (hasFeedback && isMatch ? 1 : 2) : 4;
			int choicesH = rows * rowH + Math.max(0, rows - 1) * rowGap;

			int fbH = hasFeedback ? (tight ? (isMatch ? 21 : 28) : (compact ? 44 : 52)) : 0;
			int fbGap = hasFeedback ? (tight ? 3 : 6) : 0;
			int gap1 = tight ? 2 : 6;
			int gap2 = tight ? 2 : 6;
			int gap3 = tight ? 3 : 8;
			int preFooterGap = footerCount > 0 ? (tight ? 4 : 7) : (tight ? 6 : 10);

			int desiredPh = headerH + gap1 + metaH + gap2 + qWellH + gap3 + choicesH
				+ fbGap + fbH + preFooterGap + footerPad + footerH;
			int ph = Math.min(maxH, desiredPh);
			if (desiredPh > maxH) {
				int overflow = desiredPh - maxH;
				if (hasFeedback) {
					int cutFb = Math.min(overflow, Math.max(0, fbH - 20));
					fbH -= cutFb;
					overflow -= cutFb;
				}
				if (overflow > 0) {
					qWellH = Math.max(20, qWellH - overflow);
				}
			}

			int px = (screenW - pw) / 2;
			int py = (screenH - ph) / 2;
			Rect panel = new Rect(px, py, pw, ph);
			Rect closeBtn = new Rect(panel.right() - closeSize - 4, py + 4, closeSize, closeSize);
			Rect chip = new Rect(closeBtn.x - 56, py + (tight ? 4 : 6), 50, tight ? 13 : 14);

			int curY = py + headerH + gap1;
			int barW = Math.max(40, innerW - diffW - scoreW - 12);
			Rect bar = new Rect(px + padX, curY + (metaH - 6) / 2, barW, 6);
			Rect diffChip = new Rect(bar.right() + 6, curY, diffW, metaH);
			Rect scoreChip = new Rect(diffChip.right() + 6, curY, scoreW, metaH);
			curY += metaH + gap2;

			Rect qWell = new Rect(px + padX, curY, innerW, qWellH);
			curY += qWellH + gap3;

			List<Rect> lefts = new ArrayList<>();
			List<Rect> choices = new ArrayList<>();
			if (isMatch) {
				int colGap = 6;
				int colW = (innerW - colGap) / 2;
				for (int i = 0; i < choiceCount; i++) {
					int ry = curY + i * (rowH + rowGap);
					lefts.add(new Rect(px + padX, ry, colW, rowH));
					choices.add(new Rect(px + padX + colW + colGap, ry, colW, rowH));
				}
			} else if (twoColChoices) {
				int colGap = 6;
				int colW = (innerW - colGap) / 2;
				for (int i = 0; i < choiceCount; i++) {
					int col = i % 2;
					int row = i / 2;
					choices.add(new Rect(px + padX + col * (colW + colGap), curY + row * (rowH + rowGap), colW, rowH));
				}
			} else {
				int btnW = innerW - (tight ? 4 : 12);
				int bx = px + (pw - btnW) / 2;
				for (int i = 0; i < choiceCount; i++) {
					choices.add(new Rect(bx, curY + i * (rowH + rowGap), btnW, rowH));
				}
			}
			curY += choicesH;

			List<Rect> footer = buildFooter(panel, padX, footerH, footerPad, footerCount);
			if (hasFeedback && !footer.isEmpty()) {
				int maxFbBottom = footer.getFirst().y() - 3;
				fbH = Math.min(fbH, Math.max(18, maxFbBottom - (curY + fbGap)));
			}
			Rect fbWell = hasFeedback ? new Rect(px + padX, curY + fbGap, innerW, fbH) : null;
			return new QuizLayout(panel, closeBtn, chip, bar, diffChip, scoreChip, qWell,
				List.copyOf(lefts), List.copyOf(choices), fbWell, footer, twoColChoices, compact);
		}

		private static List<Rect> buildFooter(Rect panel, int padX, int footerH, int footerPad, int footerCount) {
			if (footerCount <= 0) {
				return List.of();
			}
			int fy = panel.bottom() - footerPad - footerH;
			int innerW = panel.width - padX * 2;
			if (footerCount == 1) {
				int bw = Math.min(132, innerW);
				return List.of(new Rect(panel.x + (panel.width - bw) / 2, fy, bw, footerH));
			}
			int bw = Math.min(126, (innerW - 8) / 2);
			return List.of(
				new Rect(panel.x + padX, fy, bw, footerH),
				new Rect(panel.right() - padX - bw, fy, bw, footerH)
			);
		}
	}

	public record ScannerLayout(
		Rect panel,
		int headerHeight,
		Rect closeButton,
		Rect headerChip,
		List<Rect> stageChips,
		Rect mockBanner,
		Rect viewport,
		Rect infoWell,
		Rect studentChip,
		Rect teacherChip,
		Rect confidenceBar,
		Rect resultMockBanner,
		List<Rect> actionButtons,
		boolean compact
	) {
		public static ScannerLayout compute(int screenW, int screenH) {
			return compute(screenW, screenH, false, false);
		}

		public static ScannerLayout compute(int screenW, int screenH, boolean scannerMock, boolean resultMock) {
			boolean tight = screenH < 220;
			boolean compact = screenH < 270;
			boolean tightMock = tight && (scannerMock || resultMock);
			boolean compactMock = !tight && compact && (scannerMock || resultMock);
			int margin = 6;
			int maxW = Math.max(200, screenW - margin * 2);
			int maxH = Math.max(156, screenH - margin * 2);
			int pw = Math.min(440, maxW);
			int padX = tight ? 10 : 14;
			int innerW = pw - padX * 2;

			int headerH = tightMock ? 20 : (tight ? 21 : (compactMock ? 22 : 26));
			int closeSize = tight ? 14 : (compactMock ? 15 : 17);
			int stageH = tightMock ? 12 : (tight ? 13 : 14);
			int mockBannerH = scannerMock ? (tight ? 12 : (compactMock ? 13 : 14)) : 0;
			int gapMockBanner = scannerMock ? (tight ? 2 : (compactMock ? 3 : 5)) : 0;
			int infoH = tight ? (resultMock ? 35 : 32) : (resultMock ? 54 : 42);
			int btnH = tightMock ? 14 : (tight ? 15 : (compactMock ? 18 : 20));
			int btnGap = tightMock ? 2 : (tight ? 3 : (compactMock ? 3 : 4));
			int buttonsBlockH = btnH * 2 + btnGap;
			int gapStage = tightMock ? 2 : (tight ? 3 : (compactMock ? 4 : 6));
			int gapViewport = tightMock ? 2 : (tight ? 3 : (compactMock ? 4 : 6));
			int gapInfo = tightMock ? 2 : (tight ? 3 : (compactMock ? 4 : 7));
			int gapActions = tightMock ? 2 : (tight ? 3 : (compactMock ? 4 : 7));
			int bottomPad = tightMock ? 4 : (tight ? 5 : (compactMock ? 6 : 9));

			int fixedH = headerH + gapStage + stageH + gapMockBanner + mockBannerH
				+ gapViewport + gapInfo + infoH + gapActions + buttonsBlockH + bottomPad;
			int idealViewport = Math.min(148, Math.max(46, (innerW * 9) / 22));
			int ph = Math.min(maxH, fixedH + idealViewport);
			int vpH = Math.max(42, ph - fixedH);

			int px = (screenW - pw) / 2;
			int py = (screenH - ph) / 2;
			Rect panel = new Rect(px, py, pw, ph);
			int closeY = py + (tightMock || compactMock ? 3 : 4);
			Rect closeBtn = new Rect(panel.right() - closeSize - 4, closeY, closeSize, closeSize);
			int chipH = tightMock ? 12 : (tight || compactMock ? 13 : 14);
			int chipY = py + (tight || compactMock ? 4 : 6);
			Rect headerChip = new Rect(closeBtn.x - 68, chipY, 62, chipH);

			int curY = py + headerH + gapStage;
			int chipGap = 5;
			int chipW = (innerW - chipGap * 3) / 4;
			List<Rect> stageChips = new ArrayList<>(4);
			for (int i = 0; i < 4; i++) {
				stageChips.add(new Rect(px + padX + i * (chipW + chipGap), curY, chipW, stageH));
			}
			curY += stageH;

			Rect mockBanner = null;
			if (scannerMock) {
				curY += gapMockBanner;
				mockBanner = new Rect(px + padX, curY, innerW, mockBannerH);
				curY += mockBannerH;
			}
			curY += gapViewport;

			Rect viewport = new Rect(px + padX, curY, innerW, vpH);
			curY += vpH + gapInfo;

			Rect infoWell = new Rect(px + padX, curY, innerW, infoH);
			int scW = Math.min(64, (innerW - 24) / 4);
			int infoChipY = curY + (tight && resultMock ? 3 : 4);
			int infoChipH = tight ? (resultMock ? 11 : 12) : 14;
			Rect teacherChip = new Rect(infoWell.right() - 6 - scW, infoChipY, scW, infoChipH);
			Rect studentChip = new Rect(teacherChip.x - 4 - scW, infoChipY, scW, infoChipH);
			int barY = tight ? (resultMock ? curY + 16 : curY + 18) : curY + 21;
			int barH = tight && resultMock ? 4 : 6;
			Rect confBar = new Rect(infoWell.x + 8, barY, innerW - 16, barH);
			Rect resultMockBanner = resultMock
				? new Rect(infoWell.x + 8, barY + barH + (tight ? 2 : 3), innerW - 16, tight ? 11 : 12)
				: null;
			curY += infoH + gapActions;

			int halfW = (innerW - 6) / 2;
			List<Rect> actions = List.of(
				new Rect(px + padX, curY, halfW, btnH),
				new Rect(px + padX + halfW + 6, curY, halfW, btnH),
				new Rect(px + padX, curY + btnH + btnGap, halfW, btnH),
				new Rect(px + padX + halfW + 6, curY + btnH + btnGap, halfW, btnH)
			);
			return new ScannerLayout(panel, headerH, closeBtn, headerChip, List.copyOf(stageChips), mockBanner,
				viewport, infoWell, studentChip, teacherChip, confBar, resultMockBanner, actions, compact);
		}
	}

	public record MenuLayout(
		Rect panel,
		Rect closeButton,
		Rect headerChip,
		Rect subtitleWell,
		List<Rect> buttons,
		boolean compact
	) {
		public static MenuLayout computeMain(int screenW, int screenH) {
			boolean tight = screenH < 200;
			int pw = Math.min(256, Math.max(200, screenW - 12));
			int padX = 14;
			int innerW = pw - padX * 2;
			int headerH = tight ? 22 : 26;
			int closeSize = tight ? 14 : 17;
			int wellH = tight ? 24 : 30;
			int btnH = tight ? 16 : 20;
			int gap = tight ? 4 : 5;
			int ph = headerH + 6 + wellH + 8 + btnH * 3 + gap * 2 + 12;
			ph = Math.min(screenH - 8, ph);

			int px = (screenW - pw) / 2;
			int py = (screenH - ph) / 2;
			Rect panel = new Rect(px, py, pw, ph);
			Rect closeBtn = new Rect(panel.right() - closeSize - 4, py + 4, closeSize, closeSize);
			Rect chip = new Rect(closeBtn.x - 58, py + (tight ? 4 : 6), 52, tight ? 13 : 14);
			Rect well = new Rect(px + padX, py + headerH + 6, innerW, wellH);

			int by = well.bottom() + 8;
			int bw = innerW - 4;
			int bx = px + (pw - bw) / 2;
			List<Rect> btns = List.of(
				new Rect(bx, by, bw, btnH),
				new Rect(bx, by + btnH + gap, bw, btnH),
				new Rect(bx, by + (btnH + gap) * 2 + (tight ? 2 : 5), bw, btnH)
			);
			return new MenuLayout(panel, closeBtn, chip, well, btns, tight);
		}

		public static MenuLayout computePicker(int screenW, int screenH, int bodyCount) {
			boolean tight = screenH < 215;
			int pw = Math.min(286, Math.max(220, screenW - 12));
			int padX = 14;
			int innerW = pw - padX * 2;
			int headerH = tight ? 22 : 26;
			int closeSize = tight ? 14 : 17;
			int subtitleH = tight ? 12 : 14;
			int rows = (bodyCount + 1) / 2;
			int btnH = tight ? 15 : 20;
			int rowGap = tight ? 3 : 5;
			int backH = tight ? 16 : 20;
			int ph = Math.min(screenH - 8,
				headerH + 5 + subtitleH + 5 + rows * btnH + (rows - 1) * rowGap + 8 + backH + (tight ? 7 : 10));

			int px = (screenW - pw) / 2;
			int py = (screenH - ph) / 2;
			Rect panel = new Rect(px, py, pw, ph);
			Rect closeBtn = new Rect(panel.right() - closeSize - 4, py + 4, closeSize, closeSize);
			Rect chip = new Rect(closeBtn.x - 66, py + (tight ? 4 : 6), 60, tight ? 13 : 14);
			Rect subWell = new Rect(px + padX, py + headerH + 4, innerW, subtitleH);

			int colGap = 6;
			int colW = (innerW - colGap) / 2;
			int gridTop = subWell.bottom() + (tight ? 4 : 6);
			List<Rect> btns = new ArrayList<>(bodyCount + 1);
			for (int i = 0; i < bodyCount; i++) {
				int col = i % 2;
				int row = i / 2;
				btns.add(new Rect(px + padX + col * (colW + colGap), gridTop + row * (btnH + rowGap), colW, btnH));
			}
			int backW = Math.min(116, innerW);
			btns.add(new Rect(px + (pw - backW) / 2, panel.bottom() - (tight ? 7 : 10) - backH, backW, backH));
			return new MenuLayout(panel, closeBtn, chip, subWell, List.copyOf(btns), tight);
		}
	}
}
