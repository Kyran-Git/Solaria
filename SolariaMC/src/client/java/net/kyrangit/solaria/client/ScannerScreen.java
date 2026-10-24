package net.kyrangit.solaria.client;

import net.kyrangit.solaria.logging.ReasonCode;
import net.kyrangit.solaria.scan.ScanAction;
import net.kyrangit.solaria.scan.ScanState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Scanner (camera) UI. Phase 4 scope: show the student an actionable screen
 * and route their input through {@link ScanState}.
 *
 * <p>The screen is deliberately simple: title, status line, a few bullet
 * points of guidance, and up to two buttons. Visual polish and the real
 * capture/upload wiring are deferred to the next Phase 4 iteration. What
 * matters here is that the item + command + GUI chain is live, and that the
 * student sees an honest, specific message when a capability (webcam
 * capture, AI service) is missing instead of a fake "scanned!" success.
 */
public final class ScannerScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 8;
	private static final int LINE_HEIGHT = 12;

	private ScanState state = ScanState.idle();

	public ScannerScreen() {
		super(Component.translatable("solaria.scan.title"));
	}

	@Override
	protected void init() {
		int centreX = this.width / 2;
		int buttonsY = this.height - 40;
		switch (state.status()) {
			case IDLE -> {
				addRenderableWidget(primary(centreX - BUTTON_WIDTH - BUTTON_GAP / 2, buttonsY,
					Component.translatable("solaria.scan.button.capture"),
					b -> advance(ScanAction.START_CAPTURE)));
				addRenderableWidget(closeBtn(centreX + BUTTON_GAP / 2, buttonsY));
			}
			case AWAITING_STUDENT_CONFIRM -> {
				addRenderableWidget(primary(centreX - BUTTON_WIDTH - BUTTON_GAP / 2, buttonsY,
					Component.translatable("solaria.scan.button.build"),
					b -> advance(ScanAction.ACCEPT)));
				addRenderableWidget(primary(centreX + BUTTON_GAP / 2, buttonsY,
					Component.translatable("solaria.scan.button.retry"),
					b -> advance(ScanAction.REJECT)));
			}
			case UNAVAILABLE -> {
				addRenderableWidget(primary(centreX - BUTTON_WIDTH - BUTTON_GAP / 2, buttonsY,
					Component.translatable("solaria.scan.button.retry"),
					b -> advance(ScanAction.RETRY)));
				addRenderableWidget(closeBtn(centreX + BUTTON_GAP / 2, buttonsY));
			}
			case DONE -> addRenderableWidget(closeBtn(centreX - BUTTON_WIDTH / 2, buttonsY));
			case UPLOADING, CLASSIFYING, BUILDING ->
				addRenderableWidget(closeBtn(centreX - BUTTON_WIDTH / 2, buttonsY));
		}
	}

	private Button primary(int x, int y, Component label, Button.OnPress onPress) {
		return Button.builder(label, onPress).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
	}

	private Button closeBtn(int x, int y) {
		return Button.builder(Component.translatable("solaria.scan.button.close"), b -> onClose())
			.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
	}

	private void advance(ScanAction action) {
		try {
			state = state.transition(action);
		} catch (IllegalStateException ignored) {
			// A disabled-but-rendered button could theoretically fire; keep
			// the UI steady instead of crashing the client.
		}
		rebuildWidgets();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int centreX = this.width / 2;
		int y = 36;
		graphics.centeredText(this.font, this.getTitle(), centreX, y, 0xFFFFFFFF);
		y += LINE_HEIGHT * 2;
		graphics.centeredText(this.font, statusLine(), centreX, y, 0xFFFFC640);
		y += LINE_HEIGHT * 2;
		for (Component bullet : bulletLines()) {
			graphics.centeredText(this.font, bullet, centreX, y, 0xFFCFCFCF);
			y += LINE_HEIGHT;
		}
	}

	private Component statusLine() {
		return switch (state.status()) {
			case IDLE -> Component.translatable("solaria.scan.status.idle");
			case UPLOADING -> Component.translatable("solaria.scan.status.uploading");
			case CLASSIFYING -> Component.translatable("solaria.scan.status.classifying");
			case AWAITING_STUDENT_CONFIRM -> Component.translatable(
				"solaria.scan.status.confirm", planetName(),
				String.format("%.0f", state.confidence() * 100.0f));
			case BUILDING -> Component.translatable("solaria.scan.status.building", planetName());
			case DONE -> Component.translatable("solaria.scan.status.done", planetName());
			case UNAVAILABLE -> Component.translatable(
				"solaria.scan.status.unavailable", reasonLabel(state.reasonCode()));
		};
	}

	private Component[] bulletLines() {
		return switch (state.status()) {
			case IDLE -> new Component[]{
				Component.translatable("solaria.scan.help.hold_drawing"),
				Component.translatable("solaria.scan.help.good_light"),
				Component.translatable("solaria.scan.help.press_capture")
			};
			case UNAVAILABLE -> new Component[]{
				Component.translatable("solaria.scan.help.unavailable_detail").withStyle(ChatFormatting.GRAY),
				Component.translatable("solaria.scan.help.ask_teacher")
			};
			case AWAITING_STUDENT_CONFIRM -> new Component[]{
				Component.translatable("solaria.scan.help.confirm_detail")
			};
			default -> new Component[0];
		};
	}

	private String planetName() {
		if (state.predictedClass() == null) return "";
		String id = state.predictedClass().id();
		return Character.toUpperCase(id.charAt(0)) + id.substring(1);
	}

	private static String reasonLabel(ReasonCode code) {
		return code == null ? "" : code.name();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
