package net.kyrangit.solaria.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Custom ceramic-slate matte button styled to match {@link SolariaUiKit}, replacing Minecraft's
 * dark stone button texture so hover, CTA, keycap, disabled, and grading states look unified.
 */
public final class SolariaButton extends Button {

	public enum Style {
		DEFAULT,
		PRIMARY,
		CLOSE_ICON
	}

	public enum Highlight {
		NONE,
		SELECTED,
		CORRECT,
		WRONG
	}

	private final Style style;
	private String keycap;
	private Highlight highlight = Highlight.NONE;

	public SolariaButton(UiLayoutMetrics.Rect rect, Component message, Style style, OnPress onPress) {
		super(rect.x(), rect.y(), rect.width(), rect.height(), message, onPress, DEFAULT_NARRATION);
		this.style = style;
	}

	public SolariaButton withKeycap(String keycap) {
		this.keycap = keycap;
		return this;
	}

	public SolariaButton withHighlight(Highlight highlight) {
		this.highlight = highlight == null ? Highlight.NONE : highlight;
		return this;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		int x = this.getX();
		int y = this.getY();
		int w = this.getWidth();
		int h = this.getHeight();
		boolean hovered = this.active && (this.isHovered() || this.isFocused());

		int top, bot, hi, lip, border, ink;
		if (!this.active && highlight == Highlight.NONE) {
			top = 0xFFBFC5D4;
			bot = 0xFFB3BACB;
			hi = 0xFFD3D8E4;
			lip = 0xFF979EB2;
			border = 0xFF81899E;
			ink = 0xFF6E758A;
		} else if (highlight == Highlight.CORRECT) {
			top = 0xFF42A874;
			bot = 0xFF2E8B5B;
			hi = 0xFF86E0B1;
			lip = 0xFF1E6340;
			border = 0xFF174E32;
			ink = SolariaUiKit.TEXT_WHITE;
		} else if (highlight == Highlight.WRONG) {
			top = 0xFFC95A60;
			bot = 0xFFA94348;
			hi = 0xFFF09EA2;
			lip = 0xFF7D2E32;
			border = 0xFF5E2023;
			ink = SolariaUiKit.TEXT_WHITE;
		} else if (style == Style.PRIMARY && !hovered) {
			top = 0xFF3CA6BF;
			bot = 0xFF2E8DA5;
			hi = 0xFF83DDF0;
			lip = 0xFF20677B;
			border = 0xFF184E5E;
			ink = SolariaUiKit.TEXT_WHITE;
		} else if (hovered || highlight == Highlight.SELECTED) {
			top = 0xFF5EB6CA;
			bot = 0xFF479EB4;
			hi = 0xFF96E0F0;
			lip = 0xFF347A8D;
			border = 0xFF2A6475;
			ink = SolariaUiKit.TEXT_WHITE;
		} else {
			top = 0xFF989FB4;
			bot = 0xFF878EA4;
			hi = 0xFFB6BDD2;
			lip = 0xFF666D84;
			border = 0xFF4A5064;
			ink = SolariaUiKit.TEXT_WHITE;
		}

		SolariaUiKit.drawMatteFill(g, x, y, w, h, top, bot, hi, lip, border);

		if (style == Style.CLOSE_ICON) {
			int gx = x + (w - 7) / 2;
			int gy = y + (h - 7) / 2;
			SolariaUiKit.drawCloseGlyph(g, gx, gy, ink);
			return;
		}

		Font font = Minecraft.getInstance().font;
		boolean shadow = this.active || highlight != Highlight.NONE;
		String label = this.getMessage().getString();
		int textY = y + Math.max(2, (h - 8) / 2);

		if (keycap != null && !keycap.isEmpty() && w >= 40) {
			int capSize = Math.min(13, h - 4);
			int kx = x + 4;
			int ky = y + (h - capSize) / 2;
			int capBg = (hovered || highlight == Highlight.SELECTED) ? 0xFF206B7D
				: highlight == Highlight.CORRECT ? 0xFF1B5738
				: highlight == Highlight.WRONG ? 0xFF6D262A
				: 0xFF455068;
			SolariaUiKit.drawWell(g, kx, ky, capSize, capSize, capBg);
			int kw = font.width(keycap);
			g.text(font, keycap, kx + (capSize - kw) / 2, ky + Math.max(1, (capSize - 8) / 2), 0xFFE8F7FA, false);

			int textX = kx + capSize + 5;
			String clipped = SolariaUiKit.clipText(font, label, Math.max(8, x + w - textX - 5));
			g.text(font, clipped, textX, textY, ink, shadow);
		} else {
			String clipped = SolariaUiKit.clipText(font, label, Math.max(8, w - 10));
			int tw = font.width(clipped);
			g.text(font, clipped, x + (w - tw) / 2, textY, ink, shadow);
		}
	}
}
