package net.kyrangit.solaria;

import net.kyrangit.solaria.scan.WindowsCameraAccess;
import net.kyrangit.solaria.scan.WindowsCameraAccess.Access;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Synthetic permission fixtures; no camera use, registry edits or Settings launches. */
public final class WindowsCameraAccessTests {
	private static int assertions;

	public static void main(String[] args) {
		String[] values = {"Allow", "Deny", ""};
		for (String device : values) {
			for (String desktop : values) {
				Access expected = device.equals("Deny") || desktop.equals("Deny") ? Access.BLOCKED
					: device.equals("Allow") && desktop.equals("Allow") ? Access.ENABLED : Access.UNKNOWN;
				check(probe(device, desktop) == expected, "device=" + device + ", desktop=" + desktop);
			}
		}
		check(probe("allow", "ALLOW") == Access.ENABLED, "case insensitive allow");
		check(probe("deny", "Allow") == Access.BLOCKED, "case insensitive deny");
		check(WindowsCameraAccess.check("Linux", key -> { throw new AssertionError("registry on Linux"); })
			== Access.NOT_WINDOWS, "non-Windows does not run commands");
		check(WindowsCameraAccess.check("Darwin", key -> { throw new AssertionError("registry on macOS"); })
			== Access.NOT_WINDOWS, "Darwin is not Windows");
		check(WindowsCameraAccess.check("Windows 11", key -> null) == Access.UNKNOWN, "missing values unknown");
		check(WindowsCameraAccess.check("Windows 11", key -> { throw new IOException("unavailable"); })
			== Access.UNKNOWN, "command failure unknown");
		check(WindowsCameraAccess.check("Windows 11", key -> { throw new SecurityException("blocked"); })
			== Access.UNKNOWN, "restricted process unknown");
		for (String malformed : List.of("Value REG_DWORD 0x0", "Other REG_SZ Deny", "Value REG_SZ AllowExtra",
			"Some path Deny", "Value REG_SZ", "Value REG_SZ Deny trailing")) {
			check(WindowsCameraAccess.check("Windows 11", key -> malformed) == Access.UNKNOWN,
				"malformed registry response ignored: " + malformed);
		}
		check(WindowsCameraAccess.check("Windows 11", key -> key.equals(WindowsCameraAccess.DEVICE_KEY)
			? "Value REG_SZ Deny" : null) == Access.BLOCKED, "explicit denial overrides unreadable desktop value");
		check(WindowsCameraAccess.check("Windows 11", key -> {
			if (key.equals(WindowsCameraAccess.DEVICE_KEY)) throw new IOException("device unreadable");
			return "Value REG_SZ Deny";
		}) == Access.BLOCKED, "desktop denial overrides unreadable device value");
		check(WindowsCameraAccess.check("Windows 11", key -> { throw new InterruptedException(); })
			== Access.UNKNOWN, "interruption unknown");
		check(Thread.currentThread().isInterrupted(), "interruption flag preserved");
		Thread.interrupted();
		check(probe("Deny", "Deny") == Access.BLOCKED, "initial denial");
		check(probe("Allow", "Allow") == Access.ENABLED, "permission recheck has no stale cache");
		check(probe("Allow", "Deny") == Access.BLOCKED, "revocation detected");
		System.out.println("Plumbing check (synthetic): " + assertions + " Windows camera access assertions passed.");
		if (args.length == 1 && args[0].equals("--live")) {
			System.out.println("Read-only local Windows camera privacy probe: " + WindowsCameraAccess.check());
		}
	}

	private static Access probe(String device, String desktop) {
		List<String> keys = new ArrayList<>();
		Access result = WindowsCameraAccess.check("Windows 11", key -> {
			keys.add(key);
			String value = key.equals(WindowsCameraAccess.DEVICE_KEY) ? device : desktop;
			return key + "\r\n    Value    REG_SZ    " + value + "\r\n\r\n";
		});
		check(keys.equals(List.of(WindowsCameraAccess.DEVICE_KEY, WindowsCameraAccess.DESKTOP_KEY)),
			"only device and desktop settings queried, not Store per-app permissions");
		return result;
	}

	private static void check(boolean condition, String label) {
		assertions++;
		if (!condition) throw new AssertionError(label);
	}
}