package net.kyrangit.solaria.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.intro.IntroHub;
import net.kyrangit.solaria.quiz.QuizPackets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Hub opened by right-clicking the scanner: students pick between scanning a drawing and taking a quiz,
 * so neither needs a typed command. Picking a quiz only sends {@link QuizPackets.Start}; the host's first
 * question opens {@link QuizScreen} in place of this menu.
 */
public final class ScannerMenuScreen extends Screen {
	private static final int ACCENT = 0xFF80E8D7;
	private static final int MUTED = 0xFF8CA0B4;
	private static final int BODY = 0xFFCFCFCF;
	private static final int BUTTON_W = 200;
	private static final int ROW = 24;

	private boolean picking;

	public ScannerMenuScreen() {
		super(Component.translatable("solaria.menu.title"));
	}

	@Override
	protected void init() {
		if (picking) {
			initPicker();
		} else {
			initMain();
		}
	}

	private void initMain() {
		int x = this.width / 2 - BUTTON_W / 2;
		int y = this.height / 2 - 30;
		boolean canScan = this.minecraft != null && this.minecraft.player != null && IntroHub.canScan(this.minecraft.player);
		Button scan = Button.builder(Component.translatable("solaria.menu.scan"), b -> {
			if (this.minecraft != null) this.minecraft.setScreen(new ScannerScreen());
		}).bounds(x, y, BUTTON_W, 20).build();
		scan.active = canScan;
		if (!canScan) scan.setTooltip(Tooltip.create(Component.translatable("solaria.intro.scanner_locked")));
		addRenderableWidget(scan);

		Button quiz = Button.builder(Component.translatable("solaria.menu.quiz"), b -> {
			picking = true;
			rebuildWidgets();
		}).bounds(x, y + ROW, BUTTON_W, 20).build();
		quiz.active = quizAvailable();
		if (!quiz.active) quiz.setTooltip(Tooltip.create(Component.translatable("solaria.menu.quiz.unavailable")));
		addRenderableWidget(quiz);

		addRenderableWidget(Button.builder(Component.translatable("solaria.menu.close"), b -> onClose())
			.bounds(x, y + ROW * 2 + 8, BUTTON_W, 20).build());
	}

	private void initPicker() {
		AstronomyClass[] bodies = AstronomyClass.values();
		int colW = 120;
		int gap = 6;
		int rows = (bodies.length + 1) / 2;
		int left = this.width / 2 - colW - gap / 2;
		int top = Math.max(44, this.height / 2 - (rows * ROW) / 2 - 10);
		boolean available = quizAvailable();
		for (int i = 0; i < bodies.length; i++) {
			String id = bodies[i].id();
			int col = i % 2;
			int row = i / 2;
			Button b = Button.builder(Component.literal(capitalize(id)), x -> {
				ClientPlayNetworking.send(new QuizPackets.Start(id));
			}).bounds(left + col * (colW + gap), top + row * ROW, colW, 20).build();
			b.active = available;
			addRenderableWidget(b);
		}
		addRenderableWidget(Button.builder(Component.translatable("solaria.menu.back"), b -> {
			picking = false;
			rebuildWidgets();
		}).bounds(this.width / 2 - 60, top + rows * ROW + 8, 120, 20).build());
	}

	private static boolean quizAvailable() {
		try {
			return ClientPlayNetworking.canSend(QuizPackets.Start.TYPE);
		} catch (IllegalStateException notConnected) {
			return false;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, this.width, this.height, 0xD509101D);
		int pw = Math.min(300, this.width - 16);
		int left = (this.width - pw) / 2;
		roundedPanel(g, left, 8, pw, this.height - 16, 8, 0xF018263B);
		frameBorder(g, left + 2, 10, pw - 4, this.height - 20, 0xFF2D6E78);
		g.centeredText(this.font, Component.translatable(picking ? "solaria.menu.quiz.title" : "solaria.menu.title"),
			this.width / 2, 16, ACCENT);
		if (picking) {
			g.centeredText(this.font, Component.translatable("solaria.menu.quiz.pick"), this.width / 2, 30, BODY);
		} else {
			g.centeredText(this.font, Component.translatable("solaria.menu.hint"), this.width / 2, this.height / 2 - 52, MUTED);
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static void frameBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		if (width <= 2 || height <= 2) return;
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y, x + 1, y + height, color);
		graphics.fill(x + width - 1, y, x + width, y + height, color);
	}

	private static void roundedPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
		int radius, int color) {
		if (width <= 0 || height <= 0) return;
		int r = Math.min(radius, Math.min(width, height) / 2);
		graphics.fill(x, y + r, x + width, y + height - r, color);
		for (int row = 0; row < r; row++) {
			double dy = r - row - 0.5;
			int inset = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
			graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
			graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
