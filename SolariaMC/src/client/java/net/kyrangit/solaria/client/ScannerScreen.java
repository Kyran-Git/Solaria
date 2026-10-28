package net.kyrangit.solaria.client;

import net.kyrangit.solaria.logging.ReasonCode;
import net.kyrangit.solaria.scan.ScanAction;
import net.kyrangit.solaria.scan.ScanState;
import net.kyrangit.solaria.scan.WindowsCameraAccess;
import net.kyrangit.solaria.client.camera.CameraFrame;
import net.kyrangit.solaria.client.camera.WindowsCamera;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Explicit opt-in local webcam preview and still capture. Camera I/O is isolated
 * from the client thread; only texture updates and UI changes happen here.
 * Preview is local. Only explicit Submit sends the frozen JPEG through the LAN host.
 * No frames are saved. Student agreement does not bypass teacher approval.
 */
public final class ScannerScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 8;
	private static final int LINE_HEIGHT = 12;

	private ScanState state = ScanState.idle();
	private String submissionId;
	private int reviewRevision;
	private Component serviceStatus;
	private boolean awaitingTeacher;
	private byte[] pendingUpload;
	private int uploadOffset, uploadSequence;
	private boolean checkingCamera;
	private boolean cameraPermissionRequired;
	private boolean settingsLaunchFailed;
	private int permissionCheckTicks;
	private int checkGeneration;
	private WindowsCamera camera;
	private CameraFrame frame;
	private DynamicTexture preview;
	private WindowsCamera.Failure cameraFailure;
	private static final Identifier PREVIEW_ID = Identifier.fromNamespaceAndPath("solariamod", "camera_preview");

	public ScannerScreen() {
		super(Component.translatable("solaria.scan.title"));
	}

	@Override
	protected void init() {
		int centreX = this.width / 2;
		int buttonsY = this.height - 40;
		if (awaitingTeacher) {
			addRenderableWidget(closeBtn(centreX - BUTTON_WIDTH / 2, buttonsY));
			return;
		}
		if (checkingCamera && !cameraPermissionRequired) {
			addRenderableWidget(closeBtn(centreX - BUTTON_WIDTH / 2, buttonsY));
			return;
		}
		if (cameraPermissionRequired) {
			addRenderableWidget(primary(centreX - BUTTON_WIDTH - BUTTON_GAP / 2, buttonsY,
				Component.translatable("solaria.scan.button.camera_settings"), b -> openCameraSettings()));
			addRenderableWidget(closeBtn(centreX + BUTTON_GAP / 2, buttonsY));
			return;
		}
		switch (state.status()) {
			case IDLE -> {
				addRenderableWidget(primary(centreX - BUTTON_WIDTH - BUTTON_GAP / 2, buttonsY,
					Component.translatable("solaria.scan.button.open_camera"),
					b -> checkCameraAccess(true)));
				addRenderableWidget(closeBtn(centreX + BUTTON_GAP / 2, buttonsY));
			}
			case CAPTURING -> {
				Button capture = primary(centreX - BUTTON_WIDTH - BUTTON_GAP / 2, buttonsY,
					Component.translatable("solaria.scan.button.capture"), b -> captureFrame());
				capture.active = frame != null;
				addRenderableWidget(capture);
				addRenderableWidget(closeBtn(centreX + BUTTON_GAP / 2, buttonsY));
			}
			case CAPTURED -> {
				addRenderableWidget(primary(centreX - BUTTON_WIDTH - BUTTON_GAP / 2, buttonsY - 28,
					Component.literal("Submit drawing"), b -> submitDrawing()));
				addRenderableWidget(primary(centreX - BUTTON_WIDTH - BUTTON_GAP / 2, buttonsY,
					Component.translatable("solaria.scan.button.retake"), b -> advance(ScanAction.RETRY)));
				addRenderableWidget(closeBtn(centreX + BUTTON_GAP / 2, buttonsY));
			}
			case AWAITING_STUDENT_CONFIRM -> {
				addRenderableWidget(primary(centreX - BUTTON_WIDTH - BUTTON_GAP / 2, buttonsY,
					Component.literal("Yes, this is correct"),
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
		if (action == ScanAction.ACCEPT && submissionId != null) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new net.kyrangit.solaria.scan.ScanPackets.Decision(submissionId, reviewRevision, true));
			awaitingTeacher = true;
			serviceStatus = Component.literal("Waiting for teacher approval. Closing cancels this scan.");
			rebuildWidgets();
			return;
		}
		if (action == ScanAction.RETRY || action == ScanAction.REJECT || action == ScanAction.CLOSE) {
			cancelSubmission();
			serviceStatus = null;
		}
		if (action == ScanAction.RETRY || action == ScanAction.REJECT) {
			stopCamera();
			clearFrame();
			cameraFailure = null;
		}
		try {
			state = state.transition(action);
		} catch (IllegalStateException ignored) {
			// A disabled-but-rendered button could theoretically fire; keep
			// the UI steady instead of crashing the client.
		}
		rebuildWidgets();
	}

	private void checkCameraAccess(boolean captureAfterCheck) {
		if (checkingCamera) return;
		checkingCamera = true;
		int generation = ++checkGeneration;
		var client = this.minecraft;
		rebuildWidgets();
		Thread.ofVirtual().name("solaria-camera-access").start(() -> {
			WindowsCameraAccess.Access access = WindowsCameraAccess.check();
			client.execute(() -> {
				if (client.screen != this || generation != checkGeneration) return;
				checkingCamera = false;
				cameraPermissionRequired = access == WindowsCameraAccess.Access.BLOCKED;
				permissionCheckTicks = 0;
				if (!cameraPermissionRequired) {
					settingsLaunchFailed = false;
					if (captureAfterCheck) {
						advance(ScanAction.START_CAPTURE);
						camera = new WindowsCamera();
						return;
					}
				}
				rebuildWidgets();
			});
		});
	}

	private void openCameraSettings() {
		// Recheck first: do not launch Settings if access was enabled since the last check.
		if (checkingCamera) return;
		checkingCamera = true;
		int generation = ++checkGeneration;
		var client = this.minecraft;
		Thread.ofVirtual().name("solaria-camera-settings").start(() -> {
			WindowsCameraAccess.Access access = WindowsCameraAccess.check();
			client.execute(() -> {
				if (client.screen != this || generation != checkGeneration) return;
				checkingCamera = false;
				cameraPermissionRequired = access == WindowsCameraAccess.Access.BLOCKED;
				settingsLaunchFailed = cameraPermissionRequired && !WindowsCameraAccess.openSettings();
				permissionCheckTicks = 0;
				rebuildWidgets();
			});
		});
	}

	@Override
	public void tick() {
		super.tick();
		if (this.minecraft.player == null || this.minecraft.level == null) {
			this.onClose();
			return;
		}
		if (pendingUpload != null) {
			int end = Math.min(pendingUpload.length, uploadOffset + net.kyrangit.solaria.scan.ScanUpload.CHUNK_BYTES);
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new net.kyrangit.solaria.scan.ScanPackets.Upload(submissionId, pendingUpload.length, uploadSequence++,
					java.util.Arrays.copyOfRange(pendingUpload, uploadOffset, end)));
			uploadOffset = end;
			if (end == pendingUpload.length) pendingUpload = null;
		}
		if (camera != null) {
			WindowsCamera.Failure failure = camera.failure();
			if (failure != null) {
				stopCamera();
				clearFrame();
				cameraFailure = failure;
				if (failure == WindowsCamera.Failure.PERMISSION) {
					state = ScanState.idle();
					cameraPermissionRequired = true;
				} else {
					state = state.withFailure(failure == WindowsCamera.Failure.TIMEOUT
						? ReasonCode.SCAN_TIMEOUT : ReasonCode.CAPTURE_UNAVAILABLE);
				}
				rebuildWidgets();
			} else {
				CameraFrame incoming = camera.poll();
				if (incoming != null) {
					boolean first = frame == null;
					frame = incoming;
					updatePreview();
					if (first) rebuildWidgets();
				}
			}
		}
		if (cameraPermissionRequired && !checkingCamera && ++permissionCheckTicks >= 40) checkCameraAccess(false);
	}

	private void captureFrame() {
		if (frame == null || state.status() != ScanState.Status.CAPTURING) return;
		stopCamera();
		state = state.withCapturedFrame();
		rebuildWidgets();
	}

	private void submitDrawing() {
		if (frame == null || state.status() != ScanState.Status.CAPTURED) return;
		if (!net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
			net.kyrangit.solaria.scan.ScanPackets.Upload.TYPE)) {
			state = state.withFailure(ReasonCode.SCAN_UPLOAD_UNAVAILABLE);
			rebuildWidgets(); return;
		}
		submissionId = java.util.UUID.randomUUID().toString().replace("-", "");
		pendingUpload = frame.jpeg(); uploadOffset = uploadSequence = 0;
		state = state.submit();
		serviceStatus = Component.literal("Uploading drawing to the LAN host...");
		rebuildWidgets();
	}

	private void cancelSubmission() {
		pendingUpload = null;
		if (submissionId != null && this.minecraft.getConnection() != null
			&& net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
				net.kyrangit.solaria.scan.ScanPackets.Decision.TYPE)) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new net.kyrangit.solaria.scan.ScanPackets.Decision(submissionId, reviewRevision, false));
		}
		submissionId = null; reviewRevision = 0; awaitingTeacher = false;
	}

	public void receiveResult(net.kyrangit.solaria.scan.ScanPackets.Result packet) {
		if (!packet.id().equals(submissionId)) return;
		try {
			var data = com.google.gson.JsonParser.parseString(packet.json()).getAsJsonObject();
			String status = data.get("status").getAsString();
			if (status.equals("classifying")) serviceStatus = Component.literal("Cleaning and classifying drawing...");
			else if (status.equals("waiting_teacher")) serviceStatus = Component.literal("Student agreement recorded; waiting for teacher approval.");
			else if (status.equals("correction")) {
				reviewRevision = data.get("revision").getAsInt();
				awaitingTeacher = false;
				state = state.withTeacherLabel(net.kyrangit.solaria.blueprint.AstronomyClass.fromId(data.get("label").getAsString()),
					data.get("confidence").getAsFloat());
				serviceStatus = Component.literal((data.get("mock").getAsBoolean() ? "MOCK (synthetic): " : "")
					+ "Teacher selected " + planetName() + ". Do you agree?");
			} else if (status.equals("ready")) {
				reviewRevision = data.get("revision").getAsInt();
				awaitingTeacher = true;
				serviceStatus = Component.literal("Both approvals recorded. Generation/travel not enabled yet.");
			} else if (status.equals("rejected")) {
				reviewRevision = data.get("revision").getAsInt();
				awaitingTeacher = false;
				state = state.withFailure(ReasonCode.QUALITY_REJECTED);
				serviceStatus = Component.literal("Teacher requested a new scan.");
			}
			else if (status.equals("error")) {
				pendingUpload = null; awaitingTeacher = false;
				state = state.withFailure(ReasonCode.valueOf(data.get("reason").getAsString()));
				serviceStatus = Component.literal("Scan failed: " + data.get("reason").getAsString());
			} else if (status.equals("prediction")) {
				boolean mock = data.get("mock").getAsBoolean();
				if (data.get("accepted").getAsBoolean()) {
					state = state.withPrediction(net.kyrangit.solaria.blueprint.AstronomyClass.fromId(data.get("label").getAsString()),
						data.get("confidence").getAsFloat());
					serviceStatus = mock ? Component.literal("MOCK (synthetic): " + planetName() + " — is this correct?") : null;
				} else {
					state = state.withFailure(ReasonCode.QUALITY_REJECTED);
					serviceStatus = Component.literal((mock ? "MOCK (synthetic): " : "") + "Please retake: "
						+ (data.get("quality").isJsonNull() ? "confidence below threshold" : data.get("quality").getAsString()));
				}
			}
			rebuildWidgets();
		} catch (RuntimeException e) {
			pendingUpload = null; awaitingTeacher = false;
			state = state.withFailure(ReasonCode.SERVICE_PROTOCOL_ERROR);
			serviceStatus = null; rebuildWidgets();
		}
	}

	private void stopCamera() {
		if (camera != null) {
			camera.close();
			camera = null;
		}
	}

	private void clearFrame() {
		frame = null;
		if (preview != null) {
			this.minecraft.getTextureManager().release(PREVIEW_ID);
			preview = null;
		}
	}

	private void updatePreview() {
		if (preview != null && (preview.getPixels().getWidth() != frame.width()
			|| preview.getPixels().getHeight() != frame.height())) {
			this.minecraft.getTextureManager().release(PREVIEW_ID);
			preview = null;
		}
		if (preview == null) {
			preview = new DynamicTexture("Solaria local camera", frame.width(), frame.height(), false);
			this.minecraft.getTextureManager().register(PREVIEW_ID, preview);
		}
		NativeImage pixels = preview.getPixels();
		for (int y = 0; y < frame.height(); y++) {
			for (int x = 0; x < frame.width(); x++) pixels.setPixel(x, y, frame.pixel(x, y));
		}
		preview.upload();
	}

	@Override
	public void removed() {
		cancelSubmission();
		++checkGeneration;
		stopCamera();
		clearFrame();
		super.removed();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int centreX = this.width / 2;
		int panelWidth = Math.min(660, this.width - 16);
		int left = centreX - panelWidth / 2;
		graphics.fill(0, 0, this.width, this.height, 0xB509101D);
		roundedPanel(graphics, left + 2, 14, panelWidth, this.height - 24, 12, 0x70000000);
		roundedPanel(graphics, left, 10, panelWidth, this.height - 24, 12, 0xF018263B);
		roundedPanel(graphics, left + 1, 11, panelWidth - 2, 55, 11, 0xFF203650);
		graphics.fill(left + 20, 64, left + panelWidth - 20, 65, 0xFF50D6C2);
		roundedPanel(graphics, left + 8, this.height - 78, panelWidth - 16, 56, 8, 0xFF101C2F);
		int y = 25;
		graphics.centeredText(this.font, this.getTitle(), centreX, y, 0xFFFFFFFF);
		y += LINE_HEIGHT * 2;
		for (var line : this.font.split(statusLine(), Math.max(1, panelWidth - 40))) {
			graphics.centeredText(this.font, line, centreX, y, 0xFF80E8D7);
			y += LINE_HEIGHT;
		}
		y += LINE_HEIGHT * 2;
		for (Component bullet : bulletLines()) {
			for (var line : this.font.split(bullet, Math.max(1, panelWidth - 40))) {
				graphics.centeredText(this.font, line, centreX, y, 0xFFCFCFCF);
				y += LINE_HEIGHT;
			}
		}
		if (preview != null && frame != null) {
			int availableHeight = this.height - 88 - y;
			if (availableHeight > 16) {
				double scale = Math.min((panelWidth - 48.0) / frame.width(), (double) availableHeight / frame.height());
				int w = Math.max(1, (int) (frame.width() * scale));
				int h = Math.max(1, (int) (frame.height() * scale));
				roundedPanel(graphics, centreX - w / 2 - 5, y - 5, w + 10, h + 10, 7, 0xFF50D6C2);
				graphics.blit(PREVIEW_ID, centreX - w / 2, y, centreX - w / 2 + w, y + h, 0, 1, 0, 1);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/** Scanline-rounded panels, using the game's GUI primitives without extra dependencies. */
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

	private Component statusLine() {
		if (serviceStatus != null) return serviceStatus;
		if (cameraPermissionRequired) return Component.translatable("solaria.scan.status.camera_permission");
		if (checkingCamera) return Component.translatable("solaria.scan.status.camera_check");
		return switch (state.status()) {
			case IDLE -> Component.translatable("solaria.scan.status.idle");
			case CAPTURING -> Component.translatable(frame == null ? "solaria.scan.status.camera_opening" : "solaria.scan.status.camera_live");
			case CAPTURED -> Component.translatable("solaria.scan.status.captured");
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
		if (cameraPermissionRequired) return new Component[]{
			Component.translatable("solaria.scan.help.camera_permission"),
			Component.translatable("solaria.scan.help.camera_device"),
			Component.translatable("solaria.scan.help.camera_desktop"),
			Component.translatable(settingsLaunchFailed ? "solaria.scan.help.camera_settings_failed" : "solaria.scan.help.camera_return")
		};
		if (checkingCamera) return new Component[0];
		return switch (state.status()) {
			case CAPTURING -> new Component[]{Component.translatable("solaria.scan.help.local_preview")};
			case CAPTURED -> new Component[]{Component.translatable("solaria.scan.help.captured")};
			case IDLE -> new Component[]{
				Component.translatable("solaria.scan.help.hold_drawing"),
				Component.translatable("solaria.scan.help.good_light"),
				Component.translatable("solaria.scan.help.press_capture")
			};
			case UNAVAILABLE -> cameraFailure == null ? new Component[]{
				Component.translatable(state.reasonCode() == ReasonCode.SERVICE_TOKEN_MISSING
					|| state.reasonCode() == ReasonCode.UNAUTHORIZED
					? "solaria.scan.help.service_token" : "solaria.scan.help.service_failed"),
				Component.translatable("solaria.scan.help.ask_teacher")
			} : new Component[]{
				Component.translatable(cameraFailure == WindowsCamera.Failure.UNSUPPORTED
					? "solaria.scan.help.camera_unsupported" : "solaria.scan.help.camera_failed").withStyle(ChatFormatting.GRAY),
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
