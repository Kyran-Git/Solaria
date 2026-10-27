package net.kyrangit.solaria.client.camera;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** One bounded, immutable JPEG and its decoded preview. No file-backed ImageIO cache. */
public final class CameraFrame {
	public static final int MAX_BYTES = 2 * 1024 * 1024;
	private final byte[] jpeg;
	private final BufferedImage image;

	private CameraFrame(byte[] jpeg, BufferedImage image) {
		this.jpeg = jpeg;
		this.image = image;
	}

	public static CameraFrame decode(byte[] bytes) throws IOException {
		if (bytes.length < 4 || bytes.length > MAX_BYTES || bytes[0] != (byte) 0xff || bytes[1] != (byte) 0xd8) {
			throw new IOException("Invalid camera frame");
		}
		BufferedImage decoded;
		try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			var readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) throw new IOException("Invalid camera JPEG");
			var reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0), height = reader.getHeight(0);
				if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 8_000_000) {
					throw new IOException("Camera dimensions out of bounds");
				}
				decoded = reader.read(0);
			} finally {
				reader.dispose();
			}
		}
		double scale = Math.min(1.0, Math.min(640.0 / decoded.getWidth(), 480.0 / decoded.getHeight()));
		BufferedImage image = new BufferedImage(Math.max(1, (int) (decoded.getWidth() * scale)),
			Math.max(1, (int) (decoded.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.drawImage(decoded, 0, 0, image.getWidth(), image.getHeight(), null);
		} finally {
			graphics.dispose();
			decoded.flush();
		}
		ByteArrayOutputStream encoded = new ByteArrayOutputStream();
		try (var output = new javax.imageio.stream.MemoryCacheImageOutputStream(encoded)) {
			var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
			try {
				writer.setOutput(output);
				writer.write(image);
			} finally {
				writer.dispose();
			}
		}
		if (encoded.size() > MAX_BYTES) throw new IOException("Encoded frame too large");
		return new CameraFrame(encoded.toByteArray(), image);
	}

	public byte[] jpeg() { return jpeg.clone(); }
	public int width() { return image.getWidth(); }
	public int height() { return image.getHeight(); }
	public int pixel(int x, int y) { return image.getRGB(x, y); }
}