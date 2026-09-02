package org.tzi.use.kodkod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Guards {@code use-gui/lib/plugins/KK-ModelValidator-1.0.jar}, the ONE build artifact this repo
 * deliberately tracks in git: it is what the dev tree's USE actually loads as a plugin, so unlike
 * everything else under {@code target/} it does not get rebuilt by running the build.
 *
 * <p>It went stale exactly the way an unguarded tracked binary does. Commit 28b64986 changed
 * {@link org.tzi.kodkod.KodkodModelValidator} to surface swallowed Kodkod {@code solve()} exceptions through
 * {@code validationError()}; the tracked jar, last refreshed 2026-08-25, still contained the older
 * class with no {@code validationError} symbol at all. Nothing in the build, the tests or CI noticed
 * -- a developer running the dev tree's USE was silently exercising superseded plugin code while
 * every test passed against the current source. This test is that missing guard.
 *
 * <p><b>What is compared, and why not the raw bytes.</b> For every {@code org/tzi/**} class the
 * module currently compiles, the tracked jar must contain the same entry, and that entry's constant
 * pool must contain the same set of UTF-8 constants -- every class, method, field and descriptor
 * name, plus every string literal. That is the class's symbol table: it changes whenever the source
 * gains, loses or renames a member or a literal (the {@code validationError} case above), and it does
 * NOT change with bytecode layout, line-number tables or stack map frames, which differ harmlessly
 * between javac versions. A byte-for-byte comparison would catch the same regression but would also
 * fail for anyone building with a different JDK than whoever last refreshed the jar.
 *
 * <p>Shaded third-party classes are skipped: they come from dependency jars, not from this module's
 * source, so they cannot go stale relative to it.
 */
public class TrackedPluginJarFreshnessTest {

	private static final String TRACKED_JAR = "use-gui/lib/plugins/KK-ModelValidator-1.0.jar";
	private static final String REMEDY = "\n\nThe tracked plugin jar no longer matches this checkout's source. Refresh it with:\n"
			+ "  mvn -f <repo root>/pom.xml -pl msc-modelvalidators/kk-modelvalidator -am package -DskipTests\n"
			+ "  cp msc-modelvalidators/kk-modelvalidator/target/KK-ModelValidator-1.0.jar " + TRACKED_JAR + "\n"
			+ "and commit the result.";

	@Test
	public void theTrackedDevTreePluginJarMatchesTheCurrentlyCompiledClasses() throws Exception {
		Path compiledClasses = Paths.get("target", "classes").toAbsolutePath();
		assertTrue("this test needs the module's own compiled classes at " + compiledClasses
				+ "; run it through Maven (mvn test), not against a bare source tree",
				Files.isDirectory(compiledClasses));
		Path trackedJar = locateFromRepoRoot(TRACKED_JAR);

		List<String> mismatches = new ArrayList<>();
		try (JarFile jar = new JarFile(trackedJar.toFile())) {
			for (Path classFile : ownCompiledClasses(compiledClasses)) {
				String entryName = compiledClasses.relativize(classFile).toString().replace('\\', '/');
				JarEntry entry = jar.getJarEntry(entryName);
				if (entry == null) {
					mismatches.add(entryName + ": missing from the tracked jar entirely");
					continue;
				}
				Set<String> fresh = utf8Constants(Files.readAllBytes(classFile));
				Set<String> tracked;
				try (InputStream in = jar.getInputStream(entry)) {
					tracked = utf8Constants(in.readAllBytes());
				}
				if (!fresh.equals(tracked)) {
					mismatches.add(entryName + ": only in this checkout's build " + difference(fresh, tracked)
							+ "; only in the tracked jar " + difference(tracked, fresh));
				}
			}
		}

		if (!mismatches.isEmpty()) {
			fail(mismatches.size() + " class(es) in " + TRACKED_JAR + " differ from this checkout's build:\n  "
					+ String.join("\n  ", mismatches) + REMEDY);
		}
	}

	/** Guards the guard: a symbol-set comparison is only useful if it can actually tell classes apart. */
	@Test
	public void theSymbolComparisonDetectsAChangedMemberName() throws Exception {
		Path compiledClasses = Paths.get("target", "classes").toAbsolutePath();
		byte[] real = Files.readAllBytes(
				compiledClasses.resolve("org/tzi/kodkod/KodkodModelValidator.class"));
		Set<String> symbols = utf8Constants(real);

		assertTrue("the class this guard exists for must declare validationError (commit 28b64986)",
				symbols.contains("validationError"));
		// Rename that one symbol in a copy, leaving every byte count identical, and confirm the
		// comparison notices -- i.e. it is reading the constant pool, not just the file length.
		byte[] renamed = new String(real, StandardCharsets.ISO_8859_1)
				.replace("validationError", "validationErroR").getBytes(StandardCharsets.ISO_8859_1);
		assertEquals("the mutation must not change the file size", real.length, renamed.length);
		Set<String> mutated = utf8Constants(renamed);
		assertTrue("a renamed member must show up as a symbol difference", !symbols.equals(mutated));
	}

	private static String difference(Set<String> a, Set<String> b) {
		Set<String> only = new TreeSet<>(a);
		only.removeAll(b);
		if (only.isEmpty()) {
			return "(none)";
		}
		List<String> shown = only.stream().limit(8).collect(Collectors.toList());
		return shown + (only.size() > shown.size() ? " (+" + (only.size() - shown.size()) + " more)" : "");
	}

	private static List<Path> ownCompiledClasses(Path compiledClasses) throws IOException {
		Path owned = compiledClasses.resolve("org").resolve("tzi");
		if (!Files.isDirectory(owned)) {
			return List.of();
		}
		try (Stream<Path> files = Files.walk(owned)) {
			return files.filter(p -> p.getFileName().toString().endsWith(".class")).sorted()
					.collect(Collectors.toList());
		}
	}

	/**
	 * Every {@code CONSTANT_Utf8} entry in a class file's constant pool -- the class's whole symbol
	 * table (member and type names, descriptors, string literals, attribute names). Parsed directly
	 * from the JVM spec's fixed-size tag layout rather than through a bytecode library, so this test
	 * adds no dependency to a plugin module that is deliberately self-contained.
	 */
	static Set<String> utf8Constants(byte[] classFile) throws IOException {
		Set<String> constants = new LinkedHashSet<>();
		try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(classFile))) {
			int magic = in.readInt();
			if (magic != 0xCAFEBABE) {
				throw new IOException("not a class file (magic=" + Integer.toHexString(magic) + ")");
			}
			in.readUnsignedShort(); // minor version -- deliberately ignored
			in.readUnsignedShort(); // major version -- deliberately ignored
			int poolCount = in.readUnsignedShort();
			for (int index = 1; index < poolCount; index++) {
				int tag = in.readUnsignedByte();
				switch (tag) {
					case 1: // Utf8
						constants.add(in.readUTF());
						break;
					case 7: // Class
					case 8: // String
					case 16: // MethodType
					case 19: // Module
					case 20: // Package
						in.skipBytes(2);
						break;
					case 15: // MethodHandle
						in.skipBytes(3);
						break;
					case 3: // Integer
					case 4: // Float
					case 9: // Fieldref
					case 10: // Methodref
					case 11: // InterfaceMethodref
					case 12: // NameAndType
					case 17: // Dynamic
					case 18: // InvokeDynamic
						in.skipBytes(4);
						break;
					case 5: // Long
					case 6: // Double
						in.skipBytes(8);
						index++; // an 8-byte constant occupies two pool slots (JVMS 4.4.5)
						break;
					default:
						throw new IOException("unknown constant pool tag " + tag + " at index " + index);
				}
			}
		}
		return constants;
	}

	/** Resolves a repo-relative path by walking up from the module directory Surefire runs in. */
	private static Path locateFromRepoRoot(String repoRelative) {
		TreeMap<Integer, Path> tried = new TreeMap<>();
		Path base = Paths.get("").toAbsolutePath();
		for (int depth = 0; depth <= 8 && base != null; depth++) {
			Path candidate = base.resolve(repoRelative);
			tried.put(depth, candidate);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
			base = base.getParent();
		}
		throw new AssertionError("could not find " + repoRelative + "; looked at " + tried.values());
	}
}
