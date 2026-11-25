package net.kyrangit.solaria.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kyrangit.solaria.blueprint.AstronomyClass;
import net.kyrangit.solaria.client.ui.SolariaButton;
import net.kyrangit.solaria.client.ui.SolariaUiKit;
import net.kyrangit.solaria.client.ui.UiLayoutMetrics;
import net.kyrangit.solaria.intro.IntroHub;
import net.kyrangit.solaria.quiz.QuizPackets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Hub opened by right-clicking the scanner, restyled with {@link SolariaUiKit}: students choose
 * between scanning a drawing and taking a quiz without typing commands.
 */
public final class ScannerMenuScreen extends Screen {
	private boolean picking;
	private UiLayoutMetrics.MenuLayout layout;

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
		layout = UiLayoutMetrics.MenuLayout.computeMain(this.width, this.height);
		addRenderableWidget(new SolariaButton(layout.closeButton(), Component.literal("Close"),
			SolariaButton.Style.CLOSE_ICON, b -> onClose()));

		boolean canScan = this.minecraft != null && this.minecraft.player != null && IntroHub.canScan(this.minecraft.player);
		SolariaButton scan = new SolariaButton(layout.buttons().get(0), Component.translatable("solaria.menu.scan"),
			SolariaButton.Style.PRIMARY, b -> {
				if (this.minecraft != null) this.minecraft.setScreen(new ScannerScreen());
			});
		scan.active = canScan;
		if (!canScan) scan.setTooltip(Tooltip.create(Component.translatable("solaria.intro.scanner_locked")));
		addRenderableWidget(scan);

		SolariaButton quiz = new SolariaButton(layout.buttons().get(1), Component.translatable("solaria.menu.quiz"),
			SolariaButton.Style.PRIMARY, b -> {
				picking = true;
				rebuildWidgets();
			});
		quiz.active = quizAvailable();
		if (!quiz.active) quiz.setTooltip(Tooltip.create(Component.translatable("solaria.menu.quiz.unavailable")));
		addRenderableWidget(quiz);

		addRenderableWidget(new SolariaButton(layout.buttons().get(2), Component.translatable("solaria.menu.close"),
			SolariaButton.Style.DEFAULT, b -> onClose()));
	}

	private void initPicker() {
		AstronomyClass[] bodies = AstronomyClass.values();
		layout = UiLayoutMetrics.MenuLayout.computePicker(this.width, this.height, bodies.length);
		addRenderableWidget(new SolariaButton(layout.closeButton(), Component.literal("Close"),
			SolariaButton.Style.CLOSE_ICON, b -> onClose()));

		boolean available = quizAvailable();
		for (int i = 0; i < bodies.length; i++) {
			String id = bodies[i].id();
			SolariaButton b = new SolariaButton(layout.buttons().get(i), Component.literal(capitalize(id)),
				SolariaButton.Style.DEFAULT, x -> ClientPlayNetworking.send(new QuizPackets.Start(id)));
			b.active = available;
			addRenderableWidget(b);
		}
		addRenderableWidget(new SolariaButton(layout.buttons().get(bodies.length), Component.translatable("solaria.menu.back"),
			SolariaButton.Style.DEFAULT, b -> {
				picking = false;
				rebuildWidgets();
			}));
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
		if (layout == null) {
			layout = picking
				? UiLayoutMetrics.MenuLayout.computePicker(this.width, this.height, AstronomyClass.values().length)
				: UiLayoutMetrics.MenuLayout.computeMain(this.width, this.height);
		}
		SolariaUiKit.drawBackdrop(g, this.width, this.height);
		int headerH = layout.compact() ? 22 : 26;
		boolean canScan = this.minecraft != null && this.minecraft.player != null && IntroHub.canScan(this.minecraft.player);

		String title = Component.translatable(picking ? "solaria.menu.quiz.title" : "solaria.menu.title").getString();
		String chipText = picking ? "10 Bodies" : (canScan ? "Ready" : "Locked");
		int chipDot = picking || canScan ? SolariaUiKit.DOT_CYAN : SolariaUiKit.DOT_AMBER;

		SolariaUiKit.drawPanel(g, this.font, layout.panel(), title, headerH, layout.headerChip(), chipText, chipDot);

		UiLayoutMetrics.Rect sw = layout.subtitleWell();
		if (picking) {
			SolariaUiKit.drawCenteredLines(g, this.font, Component.translatable("solaria.menu.quiz.pick"),
				sw.x() + sw.width() / 2, sw.y() + Math.max(1, (sw.height() - 8) / 2),
				sw.width() - 8, 9, sw.bottom() + 2, SolariaUiKit.TEXT_MUTED, false);
		} else {
			SolariaUiKit.drawWell(g, sw, SolariaUiKit.WELL_LIGHT);
			SolariaUiKit.drawCenteredLines(g, this.font, Component.translatable("solaria.menu.hint"),
				sw.x() + sw.width() / 2, sw.y() + Math.max(4, (sw.height() - 16) / 2),
				sw.width() - 12, 10, sw.bottom() - 2, SolariaUiKit.TEXT_DARK, false);
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
