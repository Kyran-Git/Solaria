package net.kyrangit.solaria.scan;

import java.io.ByteArrayOutputStream;

/** Ordered bounded reassembly. All calls belong to the server thread. No image decoding here. */
public final class ScanUpload {
	public static final int CHUNK_BYTES = 12000;
	private final int total, maxChunks;
	private final long deadline;
	private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	private int next;

	public ScanUpload(int total, int maxBytes, int maxChunks, long deadline) {
		if (total < 1 || total > maxBytes || maxChunks < 1
			|| (total + (long) CHUNK_BYTES - 1) / CHUNK_BYTES > maxChunks) {
			throw new IllegalArgumentException("Upload exceeds policy");
		}
		this.total = total;
		this.maxChunks = maxChunks;
		this.deadline = deadline;
	}

	public boolean append(int sequence, byte[] chunk, long now) {
		if (now >= deadline || sequence != next || next >= maxChunks || chunk.length == 0
			|| chunk.length > CHUNK_BYTES || chunk.length > total - bytes.size()
			|| bytes.size() == total) throw new IllegalArgumentException("Invalid upload chunk");
		bytes.writeBytes(chunk);
		next++;
		return bytes.size() == total;
	}

	public byte[] finish() {
		if (bytes.size() != total) throw new IllegalStateException("Incomplete upload");
		return bytes.toByteArray();
	}
}
