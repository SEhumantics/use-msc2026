package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.Gson;

/**
 * Validates the real, committed {@code manifest.json} against structural invariants -- this is what
 * "manifest validation" means for this benchmark: not a JSON-Schema file, but executable checks that
 * checks the manifest's declared files as well as its metadata, so a valid-looking JSON row cannot
 * refer to a missing model, configuration, or declared SOIL fixture. It also guards the mode/fixture
 * consistency regressions found during this benchmark's curation (see {@link BenchmarkRunnerTest}
 * for the sibling bug in outcome classification):
 * <ul>
 * <li>{@code mode} claiming validating behavior while {@code hasValidationTests} disagreed.</li>
 * <li>{@code hasValidationTests} independently disagreed with the available SOIL fixtures.</li>
 * </ul>
 * Reads the real file directly (not a fixture copy) so this test fails the moment the committed
 * manifest regresses, not just when a fixture happens to be kept in sync.
 */
public class ManifestSchemaTest {

	private static ExampleManifest manifest;

	@BeforeClass
	public static void loadRealManifest() throws Exception {
		try (FileReader r = new FileReader(new File("src/main/resources/manifest.json"))) {
			manifest = new Gson().fromJson(r, ExampleManifest.class);
		}
	}

	@Test
	public void everyExampleHasCoreIdentifyingFields() {
		for (ExampleEntry ex : manifest.examples) {
			assertNotNull("id", ex.id);
			assertNotNull(ex.id + ": directory", ex.directory);
			assertNotNull(ex.id + ": useFile", ex.useFile);
			assertNotNull(ex.id + ": category", ex.category);
			assertNotNull(ex.id + ": mode", ex.mode);
		}
	}

	@Test
	public void categoryAndModeUseTheDocumentedVocabulary() {
		Set<String> categories = Set.of("expressiveness", "performance");
		Set<String> modes = Set.of("finding", "validating", "finding+validating");
		for (ExampleEntry ex : manifest.examples) {
			assertTrue(ex.id + ": unsupported category " + ex.category, categories.contains(ex.category));
			assertTrue(ex.id + ": unsupported mode " + ex.mode, modes.contains(ex.mode));
		}
	}

	@Test
	public void idsAreUnique() {
		Set<String> seen = new HashSet<>();
		for (ExampleEntry ex : manifest.examples) {
			assertTrue("duplicate id: " + ex.id, seen.add(ex.id));
		}
	}

	@Test
	public void modeValidatingClaimMatchesHasValidationTestsFlag() {
		// The exact metadata regression this session found: these two fields must never disagree.
		for (ExampleEntry ex : manifest.examples) {
			boolean claimsValidating = ex.mode.contains("validating");
			assertEquals(ex.id + ": mode=" + ex.mode + " but hasValidationTests=" + ex.hasValidationTests,
					ex.hasValidationTests, claimsValidating);
		}
	}

	@Test
	public void declaredExampleFilesExist() {
		File examplesDir = new File("examples");
		assertTrue("benchmark examples directory missing: " + examplesDir.getAbsolutePath(), examplesDir.isDirectory());
		for (ExampleEntry ex : manifest.examples) {
			File exDir = new File(examplesDir, ex.directory);
			assertTrue(ex.id + ": example directory missing: " + exDir, exDir.isDirectory());
			assertTrue(ex.id + ": model file missing: " + ex.useFile, new File(exDir, ex.useFile).isFile());
			if (ex.mode.contains("finding")) {
				assertNotNull(ex.id + ": finding mode needs propertiesFile", ex.propertiesFile);
				assertTrue(ex.id + ": properties file missing: " + ex.propertiesFile,
						new File(exDir, ex.propertiesFile).isFile());
			}
			if (ex.hasValidationTests) {
				assertTrue(ex.id + ": valid SOIL fixture missing", new File(exDir, "valid-instance.cmd").isFile());
				assertTrue(ex.id + ": invalid SOIL fixture missing", new File(exDir, "invalid-instance.cmd").isFile());
			}
		}
	}

	@Test
	public void everyFindingModeExampleHasAWellFormedExpectedOracle() {
		for (ExampleEntry ex : manifest.examples) {
			if (!ex.mode.contains("finding")) {
				continue;
			}
			assertNotNull(ex.id + ": missing expected oracle", ex.expected);
			assertNotNull(ex.id + ": expected.classification", ex.expected.classification);
			assertNotNull(ex.id + ": expected.outcome", ex.expected.outcome);
			assertTrue(ex.id + ": unsupported expected.classification " + ex.expected.classification,
					Set.of("sat", "unsat").contains(ex.expected.classification));
			assertTrue(ex.id + ": unsupported expected.outcome " + ex.expected.outcome,
					Set.of("SATISFIABLE", "TRIVIALLY_SATISFIABLE", "UNSATISFIABLE", "TRIVIALLY_UNSATISFIABLE")
							.contains(ex.expected.outcome));
			boolean classificationIsSat = "sat".equals(ex.expected.classification);
			boolean outcomeSaysSat = "SATISFIABLE".equals(ex.expected.outcome)
					|| "TRIVIALLY_SATISFIABLE".equals(ex.expected.outcome);
			assertEquals(ex.id + ": classification/outcome disagree (" + ex.expected.classification + " / "
					+ ex.expected.outcome + ")", classificationIsSat, outcomeSaysSat);
		}
	}

	@Test
	public void soilKnownOutOfScopeInvariantsOnlyDeclaredWhenValidationTestsExist() {
		for (ExampleEntry ex : manifest.examples) {
			if (ex.soilKnownOutOfScopeInvariants != null) {
				assertTrue(ex.id + ": declares soilKnownOutOfScopeInvariants but hasValidationTests=false",
						ex.hasValidationTests);
				assertFalse(ex.id + ": soilKnownOutOfScopeInvariants is present but empty (omit it instead)",
						ex.soilKnownOutOfScopeInvariants.isEmpty());
			}
		}
	}
}
