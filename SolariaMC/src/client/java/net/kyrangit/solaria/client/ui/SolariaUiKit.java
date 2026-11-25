package net.kyrangit.solaria.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Shared ceramic silver-slate and futuristic cyan UI kit used across all Solaria client screens.
 * Draws purely procedural 9-slice-style bevels, recessed wells, status pills, progress tracks,
 * and icon glyphs in Minecraft's native font without any external texture dependencies.
 */
public final class SolariaUiKit {

	public static final int BACKDROP = 0xD209101D;

	// Outer ceramic silver-lavender chrome
	public static final int PANEL_BG = 0xFFD6DAE6;
	public static final int PANEL_OUTLINE = 0xFF5A6275;
	public static final int PANEL_BEVEL_HI = 0xFFF5F7FC;
	public static final int PANEL_BEVEL_HI2 = 0xFFEAEFF9;
	public static final int PANEL_BEVEL_SH = 0xFF9FA6BA;
	public static final int PANEL_BEVEL_SH2 = 0xFFB5BCCF;
	public static final int DIVIDER_DARK = 0xFFB7BECE;
	public static final int DIVIDER_LIGHT = 0xFFE9ECF5;

	// Cyan accents & recessed wells
	public static final int CYAN_ACCENT = 0xFF4CC9DF;
	public static final int CYAN_BRIGHT = 0xFF8CE8F8;
	public static final int WELL_LIGHT = 0xFFC4CAD8;
	public static final int WELL_MID = 0xFFAEB5C8;
	public static final int WELL_SH = 0xFF8B92A8;
	public static final int WELL_HI = 0xFFEFF2F8;
	public static final int VIEWPORT_BG = 0xFF0E1726;
	public static final int TRACK_BG = 0xFFA6ADC1;

	// Ink & semantic dots
	public static final int TEXT_DARK = 0xFF2E3345;
	public static final int TEXT_MUTED = 0xFF5A6178;
	public static final int TEXT_WHITE = 0xFFFFFFFF;
	public static final int CHIP_BG = 0xFFC5CCDC;
	public static final int CHIP_BORDER = 0xFF9DA5BA;
	public static final int DOT_CYAN = 0xFF26B5CE;
	public static final int DOT_GREEN = 0xFF2EAA6B;
	public static final int DOT_AMBER = 0xFFD68A28;
	public static final int DOT_ROSE = 0xFFD2555A;
	public static final int DOT_IDLE = 0xFF8E95AA;

	private SolariaUiKit() {
	}

	public static void drawBackdrop(GuiGraphicsExtractor g, int width, int height) {
		g.fill(0, 0, width, height, BACKDROP);
		long now = System.currentTimeMillis();
		int maxW = Math.max(1, width);
		int maxH = Math.max(1, height);
		for (int i = 0; i < 55; i++) {
			int sx = Math.floorMod(i * 7919 + (int) (now / (110 + (i % 3) * 70)), maxW);
			int sy = Math.floorMod(i * 104729, maxH);
			int size = 1 + (i % 2);
			g.fill(sx, sy, sx + size, sy + size, i % 5 == 0 ? 0xC0FFF2BA : 0x80D7E8FF);
		}
	}

	public static void drawPanel(
		GuiGraphicsExtractor g,
		Font font,
		UiLayoutMetrics.Rect panel,
		String title,
		int headerH,
		UiLayoutMetrics.Rect chipRect,
		String chipText,
		int chipDot
	) {
		int x = panel.x();
		int y = panel.y();
		int w = panel.width();
		int h = panel.height();
		int x2 = panel.right();
		int y2 = panel.bottom();

		// Soft ambient shadow behind modal
		g.fill(x + 2, y + 2, x2 + 2, y2 + 2, 0x40050912);
		g.fill(x + 1, y + 1, x2 + 1, y2 + 1, 0x55050912);

		// Chamfered outer hairline + ceramic silver-slate fill
		g.fill(x + 1, y, x2 - 1, y2, PANEL_OUTLINE);
		g.fill(x, y + 1, x2, y2 - 1, PANEL_OUTLINE);
		g.fill(x + 1, y + 1, x2 - 1, y2 - 1, PANEL_BG);

		// Soft 2px ceramic bevel
		g.fill(x + 1, y + 1, x2 - 2, y + 2, PANEL_BEVEL_HI);
		g.fill(x + 1, y + 1, x + 2, y2 - 2, PANEL_BEVEL_HI);
		g.fill(x + 2, y + 2, x2 - 3, y + 3, PANEL_BEVEL_HI2);
		g.fill(x + 2, y + 2, x + 3, y2 - 3, PANEL_BEVEL_HI2);
		g.fill(x + 2, y2 - 2, x2 - 1, y2 - 1, PANEL_BEVEL_SH);
		g.fill(x2 - 2, y + 2, x2 - 1, y2 - 1, PANEL_BEVEL_SH);
		g.fill(x + 3, y2 - 3, x2 - 2, y2 - 2, PANEL_BEVEL_SH2);
		g.fill(x2 - 3, y + 3, x2 - 2, y2 - 2, PANEL_BEVEL_SH2);

		// Title + subtle cyan hairline marker directly on #D6DAE6
		int titleY = y + Math.max(6, (headerH - 9) / 2);
		g.fill(x + 10, titleY, x + 12, titleY + 8, CYAN_ACCENT);
		g.text(font, title, x + 17, titleY, TEXT_DARK, false);
		drawDivider(g, x + 10, y + headerH - 2, w - 20);

		if (chipRect != null && chipText != null && !chipText.isBlank()) {
			drawChip(g, font, chipRect, chipText, chipDot);
		}
	}

	public static void drawDivider(GuiGraphicsExtractor g, int x, int y, int width) {
		if (width <= 4) {
			return;
		}
		g.fill(x, y, x + width, y + 1, DIVIDER_DARK);
		g.fill(x, y + 1, x + width, y + 2, DIVIDER_LIGHT);
	}

	public static void drawWell(GuiGraphicsExtractor g, UiLayoutMetrics.Rect r, int fill) {
		drawWell(g, r.x(), r.y(), r.width(), r.height(), fill);
	}

	public static void drawWell(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
		if (w <= 2 || h <= 2) {
			return;
		}
		int x2 = x + w;
		int y2 = y + h;
		g.fill(x, y, x2, y2, fill);
		g.fill(x, y, x2 - 1, y + 1, WELL_SH);
		g.fill(x, y, x + 1, y2 - 1, WELL_SH);
		g.fill(x + 1, y2 - 1, x2, y2, WELL_HI);
		g.fill(x2 - 1, y + 1, x2, y2, WELL_HI);
	}

	public static void drawAccentWell(GuiGraphicsExtractor g, UiLayoutMetrics.Rect r, int fill, int accentColor) {
		drawWell(g, r, fill);
		if (r.height() > 4) {
			g.fill(r.x() + 1, r.y() + 1, r.x() + 3, r.bottom() - 1, accentColor);
		}
	}

	public static void drawProgressBar(GuiGraphicsExtractor g, UiLayoutMetrics.Rect r, float fraction) {
		drawWell(g, r, TRACK_BG);
		int clampedW = Math.max(0, Math.round((r.width() - 2) * Math.max(0f, Math.min(1f, fraction))));
		if (clampedW > 0 && r.height() >= 4) {
			g.fill(r.x() + 1, r.y() + 1, r.x() + 1 + clampedW, r.bottom() - 1, CYAN_ACCENT);
			g.fill(r.x() + 1, r.y() + 1, r.x() + 1 + clampedW, r.y() + 2, CYAN_BRIGHT);
		}
	}

	public static void drawChip(GuiGraphicsExtractor g, Font font, UiLayoutMetrics.Rect r, String text, int dotColor) {
		if (r == null || r.width() < 12 || r.height() < 10) {
			return;
		}
		int x = r.x();
		int y = r.y();
		int x2 = r.right();
		int y2 = r.bottom();
		g.fill(x, y, x2, y2, CHIP_BORDER);
		g.fill(x + 1, y + 1, x2 - 1, y2 - 1, CHIP_BG);
		g.fill(x + 1, y + 1, x2 - 1, y + 2, WELL_HI);

		int dotSize = r.height() >= 14 ? 4 : 3;
		int dy = y + (r.height() - dotSize) / 2;
		g.fill(x + 4, dy, x + 4 + dotSize, dy + dotSize, dotColor);

		int textX = x + 6 + dotSize;
		int maxTextW = Math.max(0, x2 - textX - 3);
		String clipped = clipText(font, text, maxTextW);
		int ty = y + (r.height() - 8) / 2;
		g.text(font, clipped, textX, ty, TEXT_DARK, false);
	}

	public static void drawViewfinder(GuiGraphicsExtractor g, UiLayoutMetrics.Rect viewport, boolean liveSweep) {
		drawWell(g, viewport.x() - 1, viewport.y() - 1, viewport.width() + 2, viewport.height() + 2, WELL_MID);
		g.fill(viewport.x(), viewport.y(), viewport.right(), viewport.bottom(), VIEWPORT_BG);
		drawBrackets(g, viewport.x() + 5, viewport.y() + 5, Math.max(10, viewport.width() - 10),
			Math.max(10, viewport.height() - 10), liveSweep);
	}

	public static void drawViewfinderOverlay(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean liveSweep) {
		drawBrackets(g, x - 2, y - 2, w + 4, h + 4, liveSweep);
	}

	private static void drawBrackets(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean liveSweep) {
		int arm = Math.max(5, Math.min(w, h) / 6);
		int x2 = x + w;
		int y2 = y + h;
		g.fill(x, y, x + arm, y + 1, CYAN_ACCENT);
		g.fill(x, y, x + 1, y + arm, CYAN_ACCENT);
		g.fill(x2 - arm, y, x2, y + 1, CYAN_ACCENT);
		g.fill(x2 - 1, y, x2, y + arm, CYAN_ACCENT);
		g.fill(x, y2 - 1, x + arm, y2, CYAN_ACCENT);
		g.fill(x, y2 - arm, x + 1, y2, CYAN_ACCENT);
		g.fill(x2 - arm, y2 - 1, x2, y2, CYAN_ACCENT);
		g.fill(x2 - 1, y2 - arm, x2, y2, CYAN_ACCENT);
		if (liveSweep && h > 6) {
			int sweepY = y + (int) ((System.currentTimeMillis() / 16L) % Math.max(1, h - 2));
			g.fill(x + 2, sweepY, x2 - 2, sweepY + 1, 0x904CC9DF);
		}
	}

	public static void drawMatteFill(
		GuiGraphicsExtractor g,
		int x,
		int y,
		int w,
		int h,
		int top,
		int bottom,
		int highlight,
		int lip,
		int border
	) {
		if (w <= 2 || h <= 2) {
			return;
		}
		int x2 = x + w;
		int y2 = y + h;
		g.fill(x, y, x2, y2, border);
		g.fillGradient(x + 1, y + 1, x2 - 1, y2 - 1, top, bottom);
		g.fill(x + 1, y + 1, x2 - 1, y + 2, highlight);
		g.fill(x + 1, y + 1, x + 2, y2 - 2, highlight);
		g.fill(x + 1, y2 - 2, x2 - 1, y2 - 1, lip);
		if (h >= 18) {
			g.fill(x + 1, y2 - 3, x2 - 1, y2 - 2, lip);
		}
	}

	public static void drawCloseGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
		g.fill(x + 1, y + 1, x + 6, y + 2, color);
		g.fill(x + 5, y + 1, x + 6, y + 6, color);
		g.fill(x + 1, y + 5, x + 6, y + 6, color);
		g.fill(x + 1, y + 4, x + 3, y + 7, color);
	}

	public static int drawCenteredLines(
		GuiGraphicsExtractor g,
		Font font,
		Component text,
		int centerX,
		int startY,
		int maxWidth,
		int lineHeight,
		int maxBottomY,
		int color,
		boolean shadow
	) {
		int y = startY;
		for (FormattedCharSequence line : font.split(text, Math.max(20, maxWidth))) {
			if (y + 8 > maxBottomY) {
				break;
			}
			int lw = font.width(line);
			g.text(font, line, centerX - lw / 2, y, color, shadow);
			y += lineHeight;
		}
		return y;
	}

	public static String clipText(Font font, String text, int maxWidth) {
		if (text == null || text.isEmpty() || maxWidth <= 8) {
			return "";
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		int ellipsisW = font.width("...");
		if (maxWidth <= ellipsisW) {
			return font.plainSubstrByWidth(text, maxWidth);
		}
		return font.plainSubstrByWidth(text, maxWidth - ellipsisW) + "...";
	}
}
