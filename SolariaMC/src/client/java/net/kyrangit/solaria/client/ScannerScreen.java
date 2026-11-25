package net.kyrangit.solaria.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.kyrangit.solaria.client.camera.CameraFrame;
import net.kyrangit.solaria.client.camera.WindowsCamera;
import net.kyrangit.solaria.client.ui.SolariaButton;
import net.kyrangit.solaria.client.ui.SolariaUiKit;
import net.kyrangit.solaria.client.ui.UiLayoutMetrics;
import net.kyrangit.solaria.logging.ReasonCode;
import net.kyrangit.solaria.scan.ScanAction;
import net.kyrangit.solaria.scan.ScanState;
import net.kyrangit.solaria.scan.WindowsCameraAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Explicit opt-in local webcam preview and still capture restyled with {@link SolariaUiKit}.
 * Camera I/O is isolated from the client thread; only explicit Submit sends the frozen JPEG
 * through the LAN host.
 */
public final class ScannerScreen extends Screen {
	private static final int LINE_HEIGHT = 10;

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

	private boolean studentAgreed;
	private boolean teacherApproved;
	private boolean qualityAccepted;
	private boolean serviceMock;
	private boolean healthRequested;
	private UiLayoutMetrics.ScannerLayout layout;

	public ScannerScreen() {
		super(Component.translatable("solaria.scan.title"));
	}

	@Override
	protected void init() {
		if (!healthRequested) {
			healthRequested = true;
			if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
				net.kyrangit.solaria.scan.ScanPackets.HealthCheck.TYPE)) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					new net.kyrangit.solaria.scan.ScanPackets.HealthCheck());
			}
		}
		layout = UiLayoutMetrics.ScannerLayout.compute(this.width, this.height,
			showScannerMockWarning(), showResultMockWarning());
		addRenderableWidget(new SolariaButton(layout.closeButton(), Component.literal("Close"),
			SolariaButton.Style.CLOSE_ICON, b -> onClose()));

		Component label = Component.literal("Open camera");
		net.minecraft.client.gui.components.Button.OnPress action = b -> checkCameraAccess(true);
		boolean enabled = !checkingCamera;
		if (cameraPermissionRequired) {
			label = Component.literal("Camera settings");
			action = b -> openCameraSettings();
		} else switch (state.status()) {
			case CAPTURING -> { label = Component.literal("Capture"); action = b -> captureFrame(); enabled = frame != null; }
			case CAPTURED -> { label = Component.literal("Submit drawing"); action = b -> submitDrawing(); }
			case AWAITING_STUDENT_CONFIRM -> {
				label = Component.literal(studentAgreed ? "Agreement recorded" : "Yes, this is correct");
				action = b -> advance(ScanAction.ACCEPT);
				enabled = !studentAgreed;
			}
			case UNAVAILABLE -> { label = Component.literal("Retry scan"); action = b -> retryScan(); }
			case UPLOADING, CLASSIFYING, BUILDING, DONE -> { label = Component.literal("Please wait"); enabled = false; }
			default -> { }
		}

		String reason = buildBlockReason();
		SolariaButton primary = new SolariaButton(layout.actionButtons().get(0), label,
			reason == null ? SolariaButton.Style.DEFAULT : SolariaButton.Style.PRIMARY, action);
		primary.active = enabled;
		addRenderableWidget(primary);

		SolariaButton retry = new SolariaButton(layout.actionButtons().get(1), Component.literal("Retry / new scan"),
			SolariaButton.Style.DEFAULT, b -> retryScan());
		retry.active = !checkingCamera && state.status() != ScanState.Status.IDLE && state.status() != ScanState.Status.BUILDING;
		addRenderableWidget(retry);

		SolariaButton build = new SolariaButton(layout.actionButtons().get(2), Component.literal("Build"),
			reason == null ? SolariaButton.Style.PRIMARY : SolariaButton.Style.DEFAULT, b -> {
				if (buildBlockReason() == null) {
					net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new net.kyrangit.solaria.scan.ScanPackets.Build(submissionId, reviewRevision));
					state = state.transition(ScanAction.ACCEPT);
					serviceStatus = Component.literal("Build requested from host...");
					rebuildWidgets();
				}
			});
		build.active = reason == null;
		build.setTooltip(Tooltip.create(Component.literal(reason == null ? "Build the approved observation" : reason)));
		addRenderableWidget(build);

		addRenderableWidget(new SolariaButton(layout.actionButtons().get(3), Component.literal("Close"),
			SolariaButton.Style.DEFAULT, b -> onClose()));
	}

	private String buildBlockReason() {
		if (!qualityAccepted || state.predictedClass() == null) return "A scan must pass host confidence and quality checks.";
		if (!studentAgreed) return "Confirm that the identified object is correct first.";
		if (!teacherApproved) return "Waiting for teacher approval.";
		if (state.status() != ScanState.Status.AWAITING_STUDENT_CONFIRM) return "Build is not available in this stage.";
		if (!net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
			net.kyrangit.solaria.scan.ScanPackets.Build.TYPE)) return "Host does not support building.";
		return null;
	}

	private void retryScan() {
		cancelSubmission();
		stopCamera();
		clearFrame();
		cameraFailure = null;
		serviceStatus = null;
		state = ScanState.idle();
		rebuildWidgets();
	}

	private void advance(ScanAction action) {
		if (action == ScanAction.ACCEPT && submissionId != null) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new net.kyrangit.solaria.scan.ScanPackets.Decision(submissionId, reviewRevision, true));
			awaitingTeacher = true;
			studentAgreed = true;
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
			rebuildWidgets();
			return;
		}
		submissionId = java.util.UUID.randomUUID().toString().replace("-", "");
		pendingUpload = frame.jpeg();
		uploadOffset = uploadSequence = 0;
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
		submissionId = null;
		reviewRevision = 0;
		awaitingTeacher = false;
		studentAgreed = teacherApproved = qualityAccepted = false;
	}

	public void receiveResult(net.kyrangit.solaria.scan.ScanPackets.Result packet) {
		if (!packet.id().isEmpty() && !packet.id().equals(submissionId)) return;
		try {
			var data = com.google.gson.JsonParser.parseString(packet.json()).getAsJsonObject();
			String status = data.get("status").getAsString();
			if (packet.id().isEmpty()) {
				if (status.equals("health") && data.has("mock") && !data.get("mock").isJsonNull()) {
					boolean mock = data.get("mock").getAsBoolean();
					if (serviceMock != mock) {
						serviceMock = mock;
						rebuildWidgets();
					}
				}
				return;
			}
			boolean hasMock = data.has("mock") && !data.get("mock").isJsonNull();
			boolean mock = hasMock ? data.get("mock").getAsBoolean() : state.mock();
			if (hasMock) serviceMock = mock;
			if (status.equals("classifying")) serviceStatus = Component.literal("Cleaning and classifying drawing...");
			else if (status.equals("waiting_teacher")) serviceStatus = Component.literal("Student agreement recorded; waiting for teacher approval.");
			else if (status.equals("building")) serviceStatus = Component.literal("Building your approved observation...");
			else if (status.equals("built")) {
				if (state.status() == ScanState.Status.BUILDING) state = state.withBuildComplete();
				submissionId = null;
				onClose();
				return;
			} else if (status.equals("build_failed")) {
				state = state.predictedClass() != null
					? state.withTeacherLabel(state.predictedClass(), state.confidence(), mock)
					: state.withFailure(ReasonCode.SERVICE_PROTOCOL_ERROR, mock);
				serviceStatus = Component.literal("Build failed: " + (data.has("reason") ? data.get("reason").getAsString() : "Ask your teacher"));
			} else if (status.equals("correction")) {
				reviewRevision = data.get("revision").getAsInt();
				awaitingTeacher = false;
				studentAgreed = teacherApproved = false;
				state = state.withTeacherLabel(net.kyrangit.solaria.blueprint.AstronomyClass.fromId(data.get("label").getAsString()),
					data.get("confidence").getAsFloat(), mock);
				serviceStatus = Component.literal("Teacher selected " + planetName() + ". Do you agree?");
			} else if (status.equals("ready")) {
				reviewRevision = data.get("revision").getAsInt();
				awaitingTeacher = true;
				teacherApproved = studentAgreed = true;
				serviceStatus = Component.literal("Both approvals recorded. Select Build to continue.");
			} else if (status.equals("rejected")) {
				reviewRevision = data.get("revision").getAsInt();
				awaitingTeacher = false;
				state = state.withFailure(ReasonCode.QUALITY_REJECTED, mock);
				serviceStatus = Component.literal("Teacher requested a new scan.");
			} else if (status.equals("error")) {
				pendingUpload = null;
				awaitingTeacher = false;
				state = state.withFailure(ReasonCode.valueOf(data.get("reason").getAsString()));
				serviceStatus = Component.literal("Scan failed: " + data.get("reason").getAsString());
			} else if (status.equals("prediction")) {
				qualityAccepted = !data.has("quality") || data.get("quality").isJsonNull()
					|| data.get("quality").getAsString().isBlank();
				if (data.get("accepted").getAsBoolean()) {
					state = state.withPrediction(net.kyrangit.solaria.blueprint.AstronomyClass.fromId(data.get("label").getAsString()),
						data.get("confidence").getAsFloat(), mock);
					serviceStatus = null;
				} else {
					state = state.withFailure(ReasonCode.QUALITY_REJECTED, mock);
					serviceStatus = Component.literal("Please retake: "
						+ (data.get("quality").isJsonNull() ? "confidence below threshold" : data.get("quality").getAsString()));
				}
			}
			rebuildWidgets();
		} catch (RuntimeException e) {
			if (packet.id().isEmpty()) return;
			pendingUpload = null;
			awaitingTeacher = false;
			state = state.withFailure(ReasonCode.SERVICE_PROTOCOL_ERROR);
			serviceStatus = null;
			rebuildWidgets();
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
		boolean scannerMock = showScannerMockWarning();
		boolean resultMock = showResultMockWarning();
		if (layout == null || (layout.mockBanner() != null) != scannerMock
			|| (layout.resultMockBanner() != null) != resultMock) {
			layout = UiLayoutMetrics.ScannerLayout.compute(this.width, this.height, scannerMock, resultMock);
		}
		SolariaUiKit.drawBackdrop(graphics, this.width, this.height);
		int headerH = layout.headerHeight();
		int reached = stageReached();
		String headerLabel = scannerMock ? "Mock"
			: state.status() == ScanState.Status.UNAVAILABLE ? "Retry"
			: reached >= 4 ? "Built" : reached >= 3 ? "Approved" : state.status() == ScanState.Status.CAPTURING ? "Live" : "Ready";
		int headerDot = scannerMock ? SolariaUiKit.DOT_AMBER
			: state.status() == ScanState.Status.UNAVAILABLE ? SolariaUiKit.DOT_ROSE
			: reached >= 3 ? SolariaUiKit.DOT_GREEN : SolariaUiKit.DOT_CYAN;

		SolariaUiKit.drawPanel(graphics, this.font, layout.panel(), "Observatory Scanner", headerH,
			layout.headerChip(), headerLabel, headerDot);

		String[] stages = {"Captured", "Classified", "Approved", "Built"};
		for (int i = 0; i < stages.length && i < layout.stageChips().size(); i++) {
			int dot = i < reached ? SolariaUiKit.DOT_GREEN : i == reached ? SolariaUiKit.DOT_CYAN : SolariaUiKit.DOT_IDLE;
			SolariaUiKit.drawChip(graphics, this.font, layout.stageChips().get(i), stages[i], dot);
		}

		String mockWarningText = scannerMock || resultMock
			? Component.translatable("solaria.scan.mock_warning").getString()
			: null;
		if (layout.mockBanner() != null && mockWarningText != null) {
			SolariaUiKit.drawWarningBanner(graphics, this.font, layout.mockBanner(), mockWarningText);
		}

		UiLayoutMetrics.Rect vp = layout.viewport();
		boolean sweep = state.status() == ScanState.Status.CAPTURING || state.status() == ScanState.Status.CLASSIFYING;
		SolariaUiKit.drawViewfinder(graphics, vp, sweep);
		if (preview != null && frame != null && vp.height() > 14) {
			double scale = Math.min((vp.width() - 8.0) / frame.width(), (vp.height() - 8.0) / frame.height());
			int w = Math.max(1, (int) (frame.width() * scale));
			int h = Math.max(1, (int) (frame.height() * scale));
			int x = vp.x() + (vp.width() - w) / 2;
			int y = vp.y() + (vp.height() - h) / 2;
			graphics.blit(PREVIEW_ID, x, y, x + w, y + h, 0, 1, 0, 1);
			SolariaUiKit.drawViewfinderOverlay(graphics, x, y, w, h, sweep);
		} else {
			int ty = vp.y() + 10;
			for (Component bullet : bulletLines()) {
				for (FormattedCharSequence line : this.font.split(bullet, Math.max(40, vp.width() - 24))) {
					if (ty + 8 > vp.bottom() - 8) break;
					int lw = this.font.width(line);
					graphics.text(this.font, line, vp.x() + (vp.width() - lw) / 2, ty, 0xFFD7E8FF, false);
					ty += LINE_HEIGHT;
				}
			}
		}

		UiLayoutMetrics.Rect info = layout.infoWell();
		SolariaUiKit.drawAccentWell(graphics, info, SolariaUiKit.WELL_LIGHT,
			resultMock ? SolariaUiKit.DOT_AMBER : SolariaUiKit.CYAN_ACCENT);
		String pred = state.predictedClass() == null ? "Pending scan"
			: planetName() + " (" + Math.round(state.confidence() * 100f) + "%)";
		int maxPredW = Math.max(30, layout.studentChip().x() - info.x() - 12);
		int predY = info.y() + (layout.studentChip().y() - info.y()) + Math.max(0, (layout.studentChip().height() - 8) / 2);
		graphics.text(this.font, SolariaUiKit.clipText(this.font, pred, maxPredW),
			info.x() + 8, predY, SolariaUiKit.TEXT_DARK, false);
		SolariaUiKit.drawChip(graphics, this.font, layout.studentChip(), "Student",
			studentAgreed ? SolariaUiKit.DOT_GREEN : SolariaUiKit.DOT_IDLE);
		SolariaUiKit.drawChip(graphics, this.font, layout.teacherChip(), "Teacher",
			teacherApproved ? SolariaUiKit.DOT_GREEN : SolariaUiKit.DOT_IDLE);

		float barFrac = state.predictedClass() == null ? 0f : state.confidence();
		SolariaUiKit.drawProgressBar(graphics, layout.confidenceBar(), barFrac);
		if (layout.resultMockBanner() != null && mockWarningText != null) {
			SolariaUiKit.drawWarningBanner(graphics, this.font, layout.resultMockBanner(), mockWarningText);
		}
		int statusY = layout.resultMockBanner() != null
			? layout.resultMockBanner().bottom() + 2
			: layout.confidenceBar().bottom() + 4;
		if (statusY + 8 <= info.bottom() - 2) {
			String statusStr = SolariaUiKit.clipText(this.font, statusLine().getString(), info.width() - 16);
			int statusInk = state.status() == ScanState.Status.UNAVAILABLE ? SolariaUiKit.DOT_ROSE : SolariaUiKit.TEXT_MUTED;
			graphics.text(this.font, statusStr, info.x() + 8, statusY, statusInk, false);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private boolean showScannerMockWarning() {
		return ScanState.showScannerMockWarning(state, serviceMock);
	}

	private boolean showResultMockWarning() {
		return ScanState.showResultMockWarning(state);
	}

	private int stageReached() {
		return switch (state.status()) {
			case IDLE, CAPTURING, UNAVAILABLE -> 0;
			case CAPTURED, UPLOADING, CLASSIFYING -> 1;
			case AWAITING_STUDENT_CONFIRM -> studentAgreed && teacherApproved ? 3 : 2;
			case BUILDING -> 3;
			case DONE -> 4;
		};
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
			case UNAVAILABLE -> Component.translatable("solaria.scan.status.unavailable", reasonLabel(state.reasonCode()));
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
			case UNAVAILABLE -> cameraFailure == null && state.reasonCode() == ReasonCode.INTRO_REQUIRED ? new Component[]{
				Component.translatable("solaria.intro.scanner_locked"),
				Component.translatable("solaria.scan.help.intro_required")
			} : cameraFailure == null ? new Component[]{
				Component.translatable(state.reasonCode() == ReasonCode.SERVICE_TOKEN_MISSING
					|| state.reasonCode() == ReasonCode.UNAUTHORIZED
					? "solaria.scan.help.service_token" : "solaria.scan.help.service_failed"),
				Component.translatable("solaria.scan.help.ask_teacher")
			} : new Component[]{
				Component.translatable(cameraFailure == WindowsCamera.Failure.UNSUPPORTED
					? "solaria.scan.help.camera_unsupported" : "solaria.scan.help.camera_failed"),
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
