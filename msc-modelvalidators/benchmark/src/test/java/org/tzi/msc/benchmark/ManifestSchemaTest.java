package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	/**
	 * Study B supersession rows are the corpus's strongest and most falsifiable claims -- "the
	 * incumbent gives a WRONG answer here and we give the right one" -- so a half-filled one must not
	 * be possible. Every column is required, the incumbent's outcome must be a real Kodkod outcome
	 * name, the SMT column must agree with the row's own expected oracle, and the divergence class
	 * must come from the fixed vocabulary in which "false-unsat"/"false-sat" are the STRONG claims
	 * and "silent-drop"/"cannot-configure"/"error" are the weaker ones. Four rows carry this block
	 * today -- one per spec S9 Study B case; the count is pinned so a fifth cannot appear without a
	 * deliberate update here.
	 */
	@Test
	public void everySupersessionRowFillsAllFiveStudyBColumnsConsistently() {
		Set<String> kodkodOutcomes = Set.of("SATISFIABLE", "TRIVIALLY_SATISFIABLE", "UNSATISFIABLE",
				"TRIVIALLY_UNSATISFIABLE");
		Set<String> divergenceClasses = Set.of("false-unsat", "false-sat", "silent-drop", "cannot-configure", "error");
		int rows = 0;
		for (ExampleEntry ex : manifest.examples) {
			if (ex.supersession == null) {
				continue;
			}
			rows++;
			assertEquals(ex.id + ": supersession columns are Study B's", "B", ex.supersession.study);
			assertTrue(ex.id + ": unsupported kodkodOutcome " + ex.supersession.kodkodOutcome,
					kodkodOutcomes.contains(ex.supersession.kodkodOutcome));
			assertTrue(ex.id + ": unsupported divergenceClass " + ex.supersession.divergenceClass,
					divergenceClasses.contains(ex.supersession.divergenceClass));
			assertNotNull(ex.id + ": kodkodReason", ex.supersession.kodkodReason);
			assertFalse(ex.id + ": kodkodReason must say WHY, not just restate the outcome",
					ex.supersession.kodkodReason.isBlank());
			assertNotNull(ex.id + ": groundTruth", ex.supersession.groundTruth);
			assertFalse(ex.id + ": groundTruth must be independently establishable, so it must be stated",
					ex.supersession.groundTruth.isBlank());
			assertNotNull(ex.id + ": a supersession row still needs its own expected oracle", ex.expected);
			assertEquals(ex.id + ": smtOutcome must agree with this row's expected oracle", ex.expected.outcome,
					ex.supersession.smtOutcome);
			assertFalse(ex.id + ": a supersession row must actually diverge from the incumbent",
					ex.supersession.kodkodOutcome.equals(ex.supersession.smtOutcome));
		}
		assertEquals("Study B supersession rows in the corpus", 4, rows);
	}

	/**
	 * Spec S9 names FOUR Study B cases, and the corpus is where "covering all four" is either true or
	 * not. Pinning the exact four ids stops the table quietly shrinking back to two, and pinning each
	 * row's divergence class stops the two DIRECTIONS collapsing into one: the bitwidth and off-grid
	 * rows are {@code false-unsat} (the incumbent wrongly REFUTES a model that has a witness), the two
	 * UReal rows are {@code false-sat} (it wrongly ACCEPTS a model that has none). For a verification
	 * tool those are not interchangeable -- a false accept reports the model is fine when it is not --
	 * so a corpus that labelled all four identically would be recording a weaker finding than the
	 * evidence supports.
	 */
	@Test
	public void theFourStudyBRowsCoverBothDivergenceDirections() {
		Map<String, String> byId = new LinkedHashMap<>();
		for (ExampleEntry ex : manifest.examples) {
			if (ex.supersession != null) {
				byId.put(ex.id, ex.supersession.divergenceClass);
			}
		}

		assertEquals("spec S9 lists four Study B cases; the corpus must carry one row for each",
				List.of("IntegerBitwidth-DailyCap", "RealGrid-UnitInterval", "URealThreshold-Below",
						"URealThreshold-NominalErasure"),
				List.copyOf(byId.keySet()));
		assertEquals("integer bitwidth: the incumbent wrongly refutes", "false-unsat",
				byId.get("IntegerBitwidth-DailyCap"));
		assertEquals("off-grid real: the incumbent wrongly refutes", "false-unsat",
				byId.get("RealGrid-UnitInterval"));
		assertEquals("dropped UReal invariant: the incumbent wrongly ACCEPTS", "false-sat",
				byId.get("URealThreshold-Below"));
		assertEquals("nominal erasure: the incumbent wrongly ACCEPTS", "false-sat",
				byId.get("URealThreshold-NominalErasure"));
	}

	/**
	 * The two false-accept rows are the corpus's most dangerous claim, so their ground truth must be a
	 * refutation and their recorded incumbent outcome must be one of Kodkod's two SATISFIABLE names.
	 * Without this, "false-sat" could be pinned onto a row whose oracle is SAT, which would make the
	 * label decorative.
	 */
	@Test
	public void everyFalseSatRowHasAnUnsatisfiableOracleAndASatisfiableIncumbentOutcome() {
		int rows = 0;
		for (ExampleEntry ex : manifest.examples) {
			if (ex.supersession == null || !"false-sat".equals(ex.supersession.divergenceClass)) {
				continue;
			}
			rows++;
			assertEquals(ex.id + ": a false ACCEPT requires an UNSAT oracle", "unsat", ex.expected.classification);
			assertEquals(ex.id + ": a false ACCEPT requires an UNSAT oracle", "UNSATISFIABLE",
					ex.expected.outcome);
			assertTrue(ex.id + ": the incumbent must have ACCEPTED, was " + ex.supersession.kodkodOutcome,
					Set.of("SATISFIABLE", "TRIVIALLY_SATISFIABLE").contains(ex.supersession.kodkodOutcome));
		}
		assertEquals("false-sat rows in the corpus", 2, rows);
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
