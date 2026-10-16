package net.kyrangit.solaria;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Structural checks for the Oracle Index book shipped in this mod.
 * Plumbing check (synthetic), not classroom evidence.
 *
 * <p>Oracle Index 2.0.0 ({@code rearth.oracle.ui.OracleScreen#parseJson}) decides
 * whether a {@code _meta.json} entry is a folder purely from its key:
 * {@code directory = !id.endsWith(".mdx")}. A page listed as {@code "introduction"}
 * is therefore treated as a folder, Oracle looks for
 * {@code books/solariamod/docs/introduction/_meta.json}, logs
 * {@code "No _meta.json found for ..."} and drops that whole branch from the
 * sidebar. These checks encode that contract so the bug cannot regress.
 */
public final class WikiStructureTests {
	private static final Path WIKI = Path.of("wiki");
	private static final Path BOOK = Path.of("src", "main", "resources", "assets", "oracle_index", "books", "solariamod");
	private static int assertions;

	public static void main(String[] args) throws IOException {
		check(Files.isDirectory(WIKI), "wiki/ source directory exists");

		Map<String, Object> wikiMeta = json(read(WIKI.resolve("sinytra-wiki.json")));
		check("1".equals(wikiMeta.get("schema")), "sinytra-wiki.json declares schema 1 (selects V1DocsFormat)");
		check("solariamod".equals(wikiMeta.get("id")), "sinytra-wiki.json id matches the book folder name");
		check("solariamod".equals(wikiMeta.get("modid")), "sinytra-wiki.json modid matches the mod id");
		check(Files.isRegularFile(BOOK.resolve("sinytra-wiki.json")),
			"book folder is named after the mod id and contains sinytra-wiki.json");

		// V1DocsFormat.getDocsRoot(DOCS) is "/docs", so navigation starts at docs/.
		check(Files.isRegularFile(WIKI.resolve("_meta.json")), "book root has _meta.json");
		check(Files.isRegularFile(WIKI.resolve("docs").resolve("_meta.json")), "docs/ has _meta.json");
		check(Files.isRegularFile(BOOK.resolve("docs").resolve("_meta.json")),
			"synced book root has docs/_meta.json");

		Set<String> referenced = new HashSet<>();
		for (Path folder : folders()) {
			Path metaFile = folder.resolve("_meta.json");
			check(Files.isRegularFile(metaFile), "every wiki folder has _meta.json: " + normalize(metaFile));
			for (Map.Entry<String, Object> entry : json(read(metaFile)).entrySet()) {
				String key = entry.getKey();
				check(!name(entry.getValue()).isBlank(),
					"entry has a display name: " + normalize(metaFile) + " -> " + key);
				if (key.endsWith(".mdx")) {
					Path page = folder.resolve(key);
					check(Files.isRegularFile(page),
						"_meta.json lists a page that exists (key needs the .mdx suffix): " + normalize(page));
					referenced.add(normalize(WIKI.relativize(page)));
				} else {
					Path child = folder.resolve(key);
					check(Files.isDirectory(child),
						"key without .mdx is treated as a folder by Oracle Index and must exist: " + normalize(child));
					check(Files.isRegularFile(child.resolve("_meta.json")),
						"listed folder has its own _meta.json: " + normalize(child));
				}
			}
		}

		// A page that no _meta.json mentions is indexed but unreachable in the sidebar.
		for (Path page : pages()) {
			check(referenced.contains(normalize(WIKI.relativize(page))),
				"every page is listed in a _meta.json: " + normalize(page));
		}

		// The in-game wiki is the synced copy, so drift would ship a stale book.
		for (Path source : allFiles()) {
			Path relative = WIKI.relativize(source);
			Path synced = BOOK.resolve(relative);
			check(Files.isRegularFile(synced), "synced book contains: " + normalize(relative));
			check(read(source).equals(read(synced)), "synced book matches wiki/: " + normalize(relative));
		}

		System.out.println("Wiki structure check (synthetic): " + assertions + " assertions passed.");
	}

	private static List<Path> folders() throws IOException {
		List<Path> found = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(WIKI)) {
			for (Path path : walk.toList()) {
				if (Files.isDirectory(path) && !isAsset(path)) {
					found.add(path);
				}
			}
		}
		return found;
	}

	private static List<Path> pages() throws IOException {
		List<Path> found = new ArrayList<>();
		for (Path path : allFiles()) {
			String fileName = path.getFileName().toString();
			if (fileName.endsWith(".mdx") && !fileName.startsWith("_")) {
				found.add(path);
			}
		}
		return found;
	}

	private static List<Path> allFiles() throws IOException {
		List<Path> found = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(WIKI)) {
			for (Path path : walk.toList()) {
				if (Files.isRegularFile(path) && !isAsset(path)) {
					found.add(path);
				}
			}
		}
		return found;
	}

	/** Schema-1 wikis keep images in assets/&lt;namespace&gt;, not in the legacy .assets/. */
	private static boolean isAsset(Path path) {
		for (Path part : WIKI.relativize(path)) {
			String name = part.toString();
			if (name.equals(".assets") || name.equals("assets")) {
				return true;
			}
		}
		return false;
	}

	private static String name(Object value) {
		if (value instanceof String text) {
			return text;
		}
		if (value instanceof Map<?, ?> map && map.get("name") instanceof String text) {
			return text;
		}
		return "";
	}

	private static String normalize(Path path) {
		return path.toString().replace('\\', '/');
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	// Minimal JSON reader: the wiki files only use objects, strings, arrays and booleans.
	private static Map<String, Object> json(String text) {
		Object parsed = new Json(text).value();
		if (parsed instanceof Map<?, ?> map) {
			Map<String, Object> result = new LinkedHashMap<>();
			map.forEach((key, value) -> result.put(String.valueOf(key), value));
			return result;
		}
		throw new AssertionError("Expected a JSON object, got: " + parsed);
	}

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	/** Tiny recursive-descent reader for the JSON subset used by wiki metadata. */
	private static final class Json {
		private final String text;
		private int index;

		Json(String text) {
			this.text = text;
		}

		Object value() {
			skip();
			return switch (text.charAt(index)) {
				case '{' -> object();
				case '[' -> array();
				case '"' -> string();
				case 't' -> literal("true", Boolean.TRUE);
				case 'f' -> literal("false", Boolean.FALSE);
				case 'n' -> literal("null", null);
				default -> number();
			};
		}

		private Map<String, Object> object() {
			Map<String, Object> result = new LinkedHashMap<>();
			index++;
			skip();
			if (text.charAt(index) == '}') {
				index++;
				return result;
			}
			while (true) {
				skip();
				String key = string();
				skip();
				index++; // ':'
				result.put(key, value());
				skip();
				if (text.charAt(index++) == '}') {
					return result;
				}
			}
		}

		private List<Object> array() {
			List<Object> result = new ArrayList<>();
			index++;
			skip();
			if (text.charAt(index) == ']') {
				index++;
				return result;
			}
			while (true) {
				result.add(value());
				skip();
				if (text.charAt(index++) == ']') {
					return result;
				}
			}
		}

		private String string() {
			skip();
			index++; // opening quote
			StringBuilder builder = new StringBuilder();
			while (true) {
				char current = text.charAt(index++);
				if (current == '"') {
					return builder.toString();
				}
				if (current != '\\') {
					builder.append(current);
					continue;
				}
				char escaped = text.charAt(index++);
				switch (escaped) {
					case 'n' -> builder.append('\n');
					case 't' -> builder.append('\t');
					case 'r' -> builder.append('\r');
					case 'b' -> builder.append('\b');
					case 'f' -> builder.append('\f');
					case 'u' -> {
						builder.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
						index += 4;
					}
					default -> builder.append(escaped);
				}
			}
		}

		private Object literal(String expected, Object result) {
			index += expected.length();
			return result;
		}

		private Object number() {
			int start = index;
			while (index < text.length() && "-+.eE0123456789".indexOf(text.charAt(index)) >= 0) {
				index++;
			}
			return Double.parseDouble(text.substring(start, index));
		}

		private void skip() {
			while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
				index++;
			}
		}
	}
}
