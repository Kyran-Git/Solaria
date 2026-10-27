package net.kyrangit.solaria.scan;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Best-effort, read-only Windows desktop camera privacy check; never grants access or opens a camera. */
public final class WindowsCameraAccess {
	public static final String DEVICE_KEY = "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\webcam";
	public static final String DESKTOP_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\webcam\\NonPackaged";
	private static final Pattern VALUE = Pattern.compile("(?im)^\\s*Value\\s+REG_SZ\\s+(Allow|Deny)\\s*$");

	public enum Access { ENABLED, BLOCKED, UNKNOWN, NOT_WINDOWS }

	@FunctionalInterface
	public interface RegistryReader {
		String read(String key) throws IOException, InterruptedException;
	}

	private WindowsCameraAccess() {
	}

	public static Access check() {
		return check(System.getProperty("os.name", ""), WindowsCameraAccess::readRegistry);
	}

	/** Missing/unreadable settings are unknown, not an invented denial or grant. */
	public static Access check(String osName, RegistryReader registry) {
		if (!osName.toLowerCase(Locale.ROOT).startsWith("windows")) return Access.NOT_WINDOWS;
		String device = readValue(registry, DEVICE_KEY);
		String desktop = readValue(registry, DESKTOP_KEY);
		if ("Deny".equalsIgnoreCase(device) || "Deny".equalsIgnoreCase(desktop)) return Access.BLOCKED;
		if ("Allow".equalsIgnoreCase(device) && "Allow".equalsIgnoreCase(desktop)) return Access.ENABLED;
		return Access.UNKNOWN;
	}

	private static String readValue(RegistryReader reader, String key) {
		try {
			String output = reader.read(key);
			if (output == null) return null;
			var match = VALUE.matcher(output);
			return match.find() ? match.group(1) : null;
		} catch (IOException | SecurityException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private static String readRegistry(String key) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(windowsExecutable("reg.exe"), "query", key, "/v", "Value")
			.redirectError(ProcessBuilder.Redirect.DISCARD).start();
		try {
			if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0) return null;
			byte[] output = process.getInputStream().readNBytes(8193);
			return output.length <= 8192 ? new String(output, Charset.defaultCharset()) : null;
		} finally {
			process.destroyForcibly();
			process.getInputStream().close();
		}
	}

	/** Called only following an explicit button click. No registry writes or elevation. */
	public static boolean openSettings() {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) return false;
		try {
			new ProcessBuilder(windowsExecutable("explorer.exe"), "ms-settings:privacy-webcam")
				.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			return true;
		} catch (IOException | SecurityException e) {
			return false;
		}
	}

	private static String windowsExecutable(String name) throws IOException {
		String root = System.getenv("SystemRoot");
		if (root == null || root.isBlank()) throw new IOException("Windows installation unavailable");
		return Path.of(root, name.equals("reg.exe") ? "System32" : "", name).toString();
	}
}