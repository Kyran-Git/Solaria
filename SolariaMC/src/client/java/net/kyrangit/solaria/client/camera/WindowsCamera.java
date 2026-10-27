package net.kyrangit.solaria.client.camera;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Client-only, process-isolated WinRT camera. All blocking I/O and decoding stay on its worker. */
public final class WindowsCamera implements AutoCloseable {
	public enum Failure { UNSUPPORTED, UNAVAILABLE, PERMISSION, TIMEOUT, BUSY }
	private static final Semaphore DEVICE = new Semaphore(1);
	private final AtomicReference<CameraFrame> latest = new AtomicReference<>();
	private volatile Failure failure;
	private volatile boolean closed;
	private volatile Process process;
	private volatile long deadline;
	private final boolean supported;
	private final ProcessStarter starter;
	private final long frameTimeoutNanos;
	private final long watchdogMillis;

	@FunctionalInterface
	interface ProcessStarter { Process start() throws IOException; }

	public WindowsCamera() {
		this(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"),
			WindowsCamera::launch, 20_000, 8_000, 1000);
	}

	/** Injectable process boundary for lifecycle tests that never touch camera hardware. */
	WindowsCamera(boolean supported, ProcessStarter starter, long startupMillis, long frameMillis, long watchdogMillis) {
		this.supported = supported;
		this.starter = starter;
		this.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(startupMillis);
		this.frameTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(frameMillis);
		this.watchdogMillis = watchdogMillis;
		Thread.ofPlatform().daemon().name("solaria-camera").start(this::run);
	}

	public CameraFrame poll() { return latest.getAndSet(null); }
	public Failure failure() { return failure; }

	private void run() {
		if (!supported) {
			failure = Failure.UNSUPPORTED;
			return;
		}
		if (!DEVICE.tryAcquire()) {
			failure = Failure.BUSY;
			return;
		}
		var watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread thread = new Thread(r, "solaria-camera-watchdog");
			thread.setDaemon(true);
			return thread;
		});
		Thread shutdown = new Thread(this::close, "solaria-camera-shutdown");
		boolean hookInstalled = false;
		try {
			if (closed) return;
			Runtime.getRuntime().addShutdownHook(shutdown);
			hookInstalled = true;
			process = starter.start();
			if (closed) return;
			watchdog.scheduleAtFixedRate(() -> {
				if (!closed && System.nanoTime() > deadline) {
					failure = Failure.TIMEOUT;
					close();
				}
			}, watchdogMillis, watchdogMillis, TimeUnit.MILLISECONDS);
			try (var input = new DataInputStream(process.getInputStream()); var output = process.getOutputStream()) {
				while (!closed) {
					output.write("FRAME\n".getBytes(StandardCharsets.US_ASCII));
					output.flush();
					CameraFrame frame = readFrame(input);
					if (!closed) latest.set(frame);
					deadline = System.nanoTime() + frameTimeoutNanos;
					Thread.sleep(250);
				}
			}
		} catch (PermissionException e) {
			if (!closed) failure = Failure.PERMISSION;
		} catch (IOException | RuntimeException e) {
			if (!closed) failure = Failure.UNAVAILABLE;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			if (!closed) failure = Failure.UNAVAILABLE;
		} finally {
			watchdog.shutdownNow();
			if (hookInstalled) {
				try { Runtime.getRuntime().removeShutdownHook(shutdown); }
				catch (IllegalStateException ignored) { /* JVM shutdown already running. */ }
			}
			Process child = process;
			if (child != null) {
				child.destroyForcibly();
				try { child.waitFor(3, TimeUnit.SECONDS); }
				catch (InterruptedException e) { Thread.currentThread().interrupt(); }
			}
			DEVICE.release();
			if (closed) latest.set(null);
		}
	}

	/** Bounded binary pipe protocol. Positive little-endian JPEG length, or a fixed negative failure code. */
	public static CameraFrame readFrame(InputStream stream) throws IOException {
		var input = new DataInputStream(stream);
		int length = Integer.reverseBytes(input.readInt());
		if (length == -2) throw new PermissionException();
		if (length < 1 || length > CameraFrame.MAX_BYTES) throw new IOException("Camera frame rejected");
		byte[] bytes = input.readNBytes(length);
		if (bytes.length != length) throw new IOException("Incomplete camera frame");
		return CameraFrame.decode(bytes);
	}

	private static Process launch() throws IOException {
		String script;
		try (var resource = WindowsCamera.class.getResourceAsStream("/solaria/windows-camera.ps1")) {
			if (resource == null) throw new IOException("Camera helper missing");
			script = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
		}
		String root = System.getenv("SystemRoot");
		if (root == null || root.isBlank()) throw new IOException("Windows unavailable");
		var builder = new ProcessBuilder(Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString(),
			"-NoLogo", "-NoProfile", "-NonInteractive", "-MTA", "-EncodedCommand",
			Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE)))
			.redirectError(ProcessBuilder.Redirect.DISCARD);
		// The camera needs no service credentials. Do not pass the host token to a helper process.
		builder.environment().remove("SOLARIA_STARTUP_TOKEN");
		return builder.start();
	}

	@Override
	public void close() {
		closed = true;
		latest.set(null);
		Process child = process;
		if (child != null) child.destroyForcibly();
	}

	private static final class PermissionException extends IOException { }
}