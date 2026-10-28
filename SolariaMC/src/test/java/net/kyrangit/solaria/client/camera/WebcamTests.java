package net.kyrangit.solaria.client.camera;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.InputStream;
import java.io.OutputStream;

/** Default runner uses generated pixels only. --live is explicitly opt-in webcam use. */
public final class WebcamTests {
	private static int assertions;

	public static void main(String[] args) throws Exception {
		byte[] jpeg = jpeg(1280, 720);
		CameraFrame frame = CameraFrame.decode(jpeg);
		check(frame.width() == 640 && frame.height() == 360, "bounded aspect-preserving preview");
		check(frame.jpeg().length < CameraFrame.MAX_BYTES, "bounded JPEG for future relay");
		byte[] copy = frame.jpeg();
		copy[0] = 0;
		check(frame.jpeg()[0] == (byte) 0xff, "captured JPEG is immutable");
		check(CameraFrame.decode(frame.jpeg()).width() == 640, "reencoded JPEG valid");
		check(CameraFrame.decode(jpeg(32, 24)).width() == 32, "small frames not upscaled");
		check(WindowsCamera.readFrame(new ByteArrayInputStream(packet(jpeg))).height() == 360, "little endian frame protocol");
		for (int value : new int[]{0, -1, -2, Integer.MAX_VALUE, CameraFrame.MAX_BYTES + 1}) {
			bad(() -> WindowsCamera.readFrame(new ByteArrayInputStream(header(value))), "invalid or failure length " + value);
		}
		bad(() -> WindowsCamera.readFrame(new ByteArrayInputStream(new byte[]{1, 2})), "truncated header");
		bad(() -> WindowsCamera.readFrame(new ByteArrayInputStream(header(32))), "truncated body");
		bad(() -> CameraFrame.decode(new byte[0]), "empty frame");
		bad(() -> CameraFrame.decode(new byte[]{(byte) 0xff, (byte) 0xd8, 0, 0}), "malformed JPEG");
		bad(() -> CameraFrame.decode(new byte[CameraFrame.MAX_BYTES + 1]), "oversized frame before decode");
		bad(() -> CameraFrame.decode(jpeg(4097, 1)), "oversized dimensions before decode");
		check(frame.pixel(0, 0) >>> 24 == 255, "preview pixels opaque ARGB");
		testLifecycle(jpeg);
		System.out.println("Webcam synthetic fixtures: " + assertions + " assertions passed (no camera used).");
		if (args.length == 1 && args[0].equals("--live")) {
			for (int attempt = 0; attempt < 2; attempt++) {
				try (WindowsCamera camera = new WindowsCamera()) {
					long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
					int frames = 0;
					while (frames < 2 && System.nanoTime() < end) {
						if (camera.failure() != null) throw new AssertionError("Live camera failure: " + camera.failure());
						if (camera.poll() != null) frames++;
						Thread.sleep(50);
					}
					check(frames == 2, "live frames received");
					System.out.println("Live webcam pass " + (attempt + 1) + ": two frames received and discarded; no files or upload.");
				}
				Thread.sleep(1500);
			}
		}
	}

	private static void testLifecycle(byte[] jpeg) throws Exception {
		try (var unsupported = new WindowsCamera(false, () -> { throw new AssertionError("unsupported launched process"); }, 500, 500, 10)) {
			await(() -> unsupported.failure() != null, "unsupported failure delivered");
			check(unsupported.failure() == WindowsCamera.Failure.UNSUPPORTED, "unsupported platform explicit");
		}
		CountDownLatch launching = new CountDownLatch(1), allowLaunch = new CountDownLatch(1);
		FakeProcess late = new FakeProcess(new byte[0], true);
		var cancelled = new WindowsCamera(true, () -> {
			launching.countDown();
			try { allowLaunch.await(); } catch (InterruptedException e) { throw new IOException(e); }
			return late;
		}, 5000, 500, 10);
		check(launching.await(2, TimeUnit.SECONDS), "launch started asynchronously");
		cancelled.close();
		allowLaunch.countDown();
		await(() -> !late.isAlive(), "close during launch kills late child");
		check(cancelled.poll() == null, "closed session publishes no frame");
		Thread.sleep(100);

		FakeProcess preview = new FakeProcess(packet(jpeg), true);
		try (var camera = new WindowsCamera(true, () -> preview, 5000, 5000, 10)) {
			await(() -> camera.poll() != null, "preview frame delivered");
			try (var busy = new WindowsCamera(true, () -> { throw new AssertionError("second camera launched"); }, 1000, 1000, 10)) {
				await(() -> busy.failure() != null, "second session rejected");
				check(busy.failure() == WindowsCamera.Failure.BUSY, "one camera owner only");
			}
		}
		await(() -> !preview.isAlive(), "closing preview terminates helper");
		Thread.sleep(350);

		FakeProcess stalled = new FakeProcess(new byte[0], true);
		try (var timeout = new WindowsCamera(true, () -> stalled, 100, 100, 10)) {
			await(() -> timeout.failure() != null, "stalled pipe times out");
			check(timeout.failure() == WindowsCamera.Failure.TIMEOUT, "timeout is distinguished");
			await(() -> !stalled.isAlive(), "timeout kills helper");
		}
		Thread.sleep(100);
		FakeProcess denied = new FakeProcess(header(-2), false);
		try (var camera = new WindowsCamera(true, () -> denied, 1000, 1000, 10)) {
			await(() -> camera.failure() != null, "permission denial delivered");
			check(camera.failure() == WindowsCamera.Failure.PERMISSION, "native access denial is explicit");
		}
		Thread.sleep(100);
		FakeProcess malformed = new FakeProcess(header(Integer.MAX_VALUE), false);
		try (var camera = new WindowsCamera(true, () -> malformed, 1000, 1000, 10)) {
			await(() -> camera.failure() != null, "invalid helper response delivered");
			check(camera.failure() == WindowsCamera.Failure.UNAVAILABLE, "bad protocol fails safely");
		}
	}

	private static void await(java.util.function.BooleanSupplier condition, String label) throws Exception {
		long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > end) throw new AssertionError("Timed out: " + label);
			Thread.sleep(10);
		}
		assertions++;
	}

	/** In-memory child boundary; can simulate native reads that stall until process termination. */
	private static final class FakeProcess extends Process {
		private final AtomicBoolean alive = new AtomicBoolean(true);
		private final CountDownLatch killed = new CountDownLatch(1);
		private final InputStream input;
		FakeProcess(byte[] bytes, boolean stall) {
			input = new InputStream() {
				int offset;
				@Override public int read() throws IOException {
					if (offset < bytes.length) return bytes[offset++] & 255;
					if (stall) {
						try { killed.await(); } catch (InterruptedException e) { throw new IOException(e); }
					}
					return -1;
				}
			};
		}
		@Override public InputStream getInputStream() { return input; }
		@Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
		@Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
		@Override public int waitFor() throws InterruptedException { killed.await(); return 0; }
		@Override public int exitValue() { if (alive.get()) throw new IllegalThreadStateException(); return 0; }
		@Override public void destroy() { alive.set(false); killed.countDown(); }
		@Override public Process destroyForcibly() { destroy(); return this; }
		@Override public boolean isAlive() { return alive.get(); }
	}

	private static byte[] jpeg(int width, int height) throws IOException {
		var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, 0xff996633);
		var bytes = new ByteArrayOutputStream();
		try (var output = new MemoryCacheImageOutputStream(bytes)) {
			var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
			try { writer.setOutput(output); writer.write(image); }
			finally { writer.dispose(); }
		}
		return bytes.toByteArray();
	}

	private static byte[] header(int length) throws IOException {
		var bytes = new ByteArrayOutputStream();
		new DataOutputStream(bytes).writeInt(Integer.reverseBytes(length));
		return bytes.toByteArray();
	}

	private static byte[] packet(byte[] bytes) throws IOException {
		var output = new ByteArrayOutputStream();
		output.write(header(bytes.length));
		output.write(bytes);
		return output.toByteArray();
	}

	private interface IoAction { void run() throws IOException; }
	private static void bad(IoAction action, String label) throws IOException {
		try { action.run(); throw new AssertionError("Expected rejection: " + label); }
		catch (IOException expected) { assertions++; }
	}
	private static void check(boolean condition, String label) {
		assertions++;
		if (!condition) throw new AssertionError(label);
	}
}