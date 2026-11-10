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

	private boolean studentAgreed;
	private boolean teacherApproved;
	private boolean qualityAccepted;

	@Override
	protected void init() {
		int width = Math.max(1, Math.min(BUTTON_WIDTH, (this.width - 46) / 2));
		int left = this.width / 2 - width - 3;
		int right = this.width / 2 + 3;
		int y = this.height - 60;
		Component label = Component.literal("Open camera");
		Button.OnPress action = b -> checkCameraAccess(true);
		boolean enabled = !checkingCamera;
		if (cameraPermissionRequired) {
			label = Component.literal("Camera settings"); action = b -> openCameraSettings();
		} else switch (state.status()) {
			case CAPTURING -> { label = Component.literal("Capture"); action = b -> captureFrame(); enabled = frame != null; }
			case CAPTURED -> { label = Component.literal("Submit drawing"); action = b -> submitDrawing(); }
			case AWAITING_STUDENT_CONFIRM -> {
				label = Component.literal(studentAgreed ? "Agreement recorded" : "Yes, this is correct");
				action = b -> advance(ScanAction.ACCEPT); enabled = !studentAgreed;
			}
			case UNAVAILABLE -> { label = Component.literal("Retry scan"); action = b -> retryScan(); }
			case UPLOADING, CLASSIFYING, BUILDING, DONE -> { label = Component.literal("Please wait"); enabled = false; }
			default -> { }
		}
		Button primary = Button.builder(label, action).bounds(left, y, width, BUTTON_HEIGHT).build();
		primary.active = enabled;
		addRenderableWidget(primary);
		Button retry = Button.builder(Component.literal("Retry / new scan"), b -> retryScan())
			.bounds(right, y, width, BUTTON_HEIGHT).build();
		retry.active = !checkingCamera && state.status() != ScanState.Status.IDLE && state.status() != ScanState.Status.BUILDING;
		addRenderableWidget(retry);
		Button build = Button.builder(Component.literal("Build"), b -> {
			if (buildBlockReason() == null) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					new net.kyrangit.solaria.scan.ScanPackets.Build(submissionId, reviewRevision));
				state = state.transition(ScanAction.ACCEPT);
				serviceStatus = Component.literal("Build requested from host...");
				rebuildWidgets();
			}
		}).bounds(left, y + 24, width, BUTTON_HEIGHT).build();
		String reason = buildBlockReason();
		build.active = reason == null;
		build.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
			reason == null ? "Build the approved observation" : reason)));
		addRenderableWidget(build);
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
			.bounds(right, y + 24, width, BUTTON_HEIGHT).build());
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
		studentAgreed = teacherApproved = qualityAccepted = false;
	}

	public void receiveResult(net.kyrangit.solaria.scan.ScanPackets.Result packet) {
		if (!packet.id().equals(submissionId)) return;
		try {
			var data = com.google.gson.JsonParser.parseString(packet.json()).getAsJsonObject();
			String status = data.get("status").getAsString();
			if (status.equals("classifying")) serviceStatus = Component.literal("Cleaning and classifying drawing...");
			else if (status.equals("waiting_teacher")) serviceStatus = Component.literal("Student agreement recorded; waiting for teacher approval.");
			else if (status.equals("building")) {
				serviceStatus = Component.literal("Building your approved observation...");
			} else if (status.equals("built")) {
				if (state.status() == ScanState.Status.BUILDING) state = state.withBuildComplete();
				submissionId = null;
				onClose();
				return;
			} else if (status.equals("build_failed")) {
				// Keep an approved result buildable so a transient host failure can be retried.
				state = state.predictedClass() != null
					? state.withTeacherLabel(state.predictedClass(), state.confidence())
					: state.withFailure(ReasonCode.SERVICE_PROTOCOL_ERROR);
				serviceStatus = Component.literal("Build failed: " + (data.has("reason") ? data.get("reason").getAsString() : "Ask your teacher"));
			}
			else if (status.equals("correction")) {
				reviewRevision = data.get("revision").getAsInt();
				awaitingTeacher = false;
				studentAgreed = teacherApproved = false;
				state = state.withTeacherLabel(net.kyrangit.solaria.blueprint.AstronomyClass.fromId(data.get("label").getAsString()),
					data.get("confidence").getAsFloat());
				serviceStatus = Component.literal((data.get("mock").getAsBoolean() ? "MOCK (synthetic): " : "")
					+ "Teacher selected " + planetName() + ". Do you agree?");
			} else if (status.equals("ready")) {
				reviewRevision = data.get("revision").getAsInt();
				awaitingTeacher = true;
				teacherApproved = studentAgreed = true;
				serviceStatus = Component.literal("Both approvals recorded. Select Build to continue.");
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
				qualityAccepted = !data.has("quality") || data.get("quality").isJsonNull()
					|| data.get("quality").getAsString().isBlank();
				if (data.get("accepted").getAsBoolean()) {
					state = state.withPrediction(net.kyrangit.solaria.blueprint.AstronomyClass.fromId(data.get("label").getAsString()),
						data.get("confidence").getAsFloat());
					serviceStatus = mock ? Component.literal("MOCK (synthetic): " + planetName() + "  -  is this correct?") : null;
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
		int panelWidth = Math.max(1, Math.min(760, this.width - 16));
		int left = (this.width - panelWidth) / 2;
		int bottom = this.height - 68;
		graphics.fill(0, 0, this.width, this.height, 0xD509101D);
		long now = System.currentTimeMillis();
		for (int i = 0; i < 70; i++) {
			int sx = Math.floorMod(i * 7919 + (int) (now / (90 + i % 3 * 70)), Math.max(1, this.width));
			int sy = Math.floorMod(i * 104729, Math.max(1, this.height));
			graphics.fill(sx, sy, sx + 1 + i % 2, sy + 1 + i % 2, i % 5 == 0 ? 0xFFFFF4C0 : 0xA0DDEBFF);
		}
		roundedPanel(graphics, left, 8, panelWidth, this.height - 16, 8, 0xF018263B);
		frameBorder(graphics, left + 2, 10, panelWidth - 4, this.height - 20, 0xFF2D6E78);
		graphics.centeredText(this.font, Component.literal("SOLARIA / OBSERVATORY"), this.width / 2, 16, 0xFF80E8D7);
		int textWidth = Math.max(1, panelWidth - 24);
		int y = 32;
		String[] stages = {"Captured", "Classified", "Approved", "Built"};
		int reached = stageReached();
		int chipGap = 4;
		int chipsWidth = -chipGap;
		for (String stage : stages) chipsWidth += this.font.width(stage) + 8 + chipGap;
		if (chipsWidth <= textWidth && y + 12 <= bottom) {
			int cx = (this.width - chipsWidth) / 2;
			for (int i = 0; i < stages.length; i++) {
				int cw = this.font.width(stages[i]) + 8;
				int fill = i < reached ? 0xFF1F6B45 : i == reached ? 0xFF6B5A1F : 0xFF22303F;
				graphics.fill(cx, y, cx + cw, y + 11, fill);
				frameBorder(graphics, cx, y, cw, 11, i < reached ? 0xFF55D17A : 0xFF3F5468);
				graphics.text(this.font, stages[i], cx + 4, y + 2, i <= reached ? 0xFFFFFFFF : 0xFF8CA0B4);
				cx += cw + chipGap;
			}
			y += 14;
		} else {
			for (var line : this.font.split(Component.literal(String.join(" > ", stages)), textWidth)) {
				if (y + LINE_HEIGHT > bottom) break;
				graphics.centeredText(this.font, line, this.width / 2, y, 0xFFCFCFCF); y += LINE_HEIGHT;
			}
		}
		String confidence = state.predictedClass() == null ? "Confidence: pending" :
			planetName() + " / Confidence: " + Math.round(state.confidence() * 100) + "%";
		String approvals = " / Student: " + (studentAgreed ? "yes" : "pending") + " / Teacher: " + (teacherApproved ? "yes" : "pending");
		for (var line : this.font.split(Component.literal(confidence + approvals), textWidth)) {
			if (y + LINE_HEIGHT > bottom) break;
			graphics.centeredText(this.font, line, this.width / 2, y, 0xFF80E8D7); y += LINE_HEIGHT;
		}
		int statusBudget = Math.min(3, Math.max(0, (bottom - y - 20) / LINE_HEIGHT));
		for (var line : this.font.split(statusLine(), textWidth)) {
			if (statusBudget-- <= 0) break;
			graphics.centeredText(this.font, line, this.width / 2, y, 0xFFFFFFFF); y += LINE_HEIGHT;
		}
		if (state.predictedClass() != null && y + 8 < bottom) {
			int meterWidth = Math.min(220, textWidth);
			int mx = (this.width - meterWidth) / 2;
			graphics.fill(mx, y + 1, mx + meterWidth, y + 6, 0xFF0C1420);
			int filled = (int) (meterWidth * Math.max(0f, Math.min(1f, state.confidence())));
			graphics.fill(mx, y + 1, mx + filled, y + 6, qualityAccepted ? 0xFF55D17A : 0xFFE3A33A);
			y += 8;
		}
		y += 6;
		if (preview != null && frame != null && bottom - y > 20) {
			double scale = Math.min((panelWidth - 24.0) / frame.width(), (bottom - y - 6.0) / frame.height());
			int w = Math.max(1, (int) (frame.width() * scale));
			int h = Math.max(1, (int) (frame.height() * scale));
			int x = (this.width - w) / 2;
			graphics.blit(PREVIEW_ID, x, y, x + w, y + h, 0, 1, 0, 1);
			int corner = Math.max(6, Math.min(w, h) / 8);
			int bracket = 0xFF80E8D7;
			graphics.fill(x - 2, y - 2, x + corner, y, bracket); graphics.fill(x - 2, y - 2, x, y + corner, bracket);
			graphics.fill(x + w - corner, y - 2, x + w + 2, y, bracket); graphics.fill(x + w, y - 2, x + w + 2, y + corner, bracket);
			graphics.fill(x - 2, y + h, x + corner, y + h + 2, bracket); graphics.fill(x - 2, y + h - corner, x, y + h + 2, bracket);
			graphics.fill(x + w - corner, y + h, x + w + 2, y + h + 2, bracket); graphics.fill(x + w, y + h - corner, x + w + 2, y + h + 2, bracket);
			if (state.status() == ScanState.Status.CAPTURING || state.status() == ScanState.Status.CLASSIFYING) {
				int sweep = y + (int) ((now / 12) % Math.max(1, h));
				graphics.fill(x, sweep, x + w, Math.min(y + h, sweep + 2), 0x9080E8D7);
			}
		} else {
			for (Component bullet : bulletLines()) {
				for (var line : this.font.split(bullet, textWidth)) {
					if (y + LINE_HEIGHT > bottom) break;
					graphics.centeredText(this.font, line, this.width / 2, y, 0xFFCFCFCF); y += LINE_HEIGHT;
				}
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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

	private static void frameBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		if (width <= 2 || height <= 2) return;
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y, x + 1, y + height, color);
		graphics.fill(x + width - 1, y, x + width, y + height, color);
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
			case UNAVAILABLE -> cameraFailure == null && state.reasonCode() == ReasonCode.INTRO_REQUIRED ? new Component[]{
				// The host refused the scan: the explorer mission is unfinished (e.g. a handed-over scanner).
				Component.translatable("solaria.intro.scanner_locked"),
				Component.translatable("solaria.scan.help.intro_required")
			} : cameraFailure == null ? new Component[]{
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
