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
 * would have caught the two real manifest bugs found this session (see {@link BenchmarkRunnerTest}
 * for the sibling bug in outcome classification):
 * <ul>
 * <li>{@code mode} claiming "validating" for 5 examples that had no genuine standalone SOIL fixture
 * (ZebraPuzzle, RecursiveTree, GraphColoring, NQueens, Subsets) -- caught here as
 * "mode.contains('validating') must equal hasValidationTests".</li>
 * <li>{@code hasValidationTests} independently wrong for RecursiveTree.</li>
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
	public void idsAreUnique() {
		Set<String> seen = new HashSet<>();
		for (ExampleEntry ex : manifest.examples) {
			assertTrue("duplicate id: " + ex.id, seen.add(ex.id));
		}
	}

	@Test
	public void modeValidatingClaimMatchesHasValidationTestsFlag() {
		// The exact regression this session found: these two fields must never disagree. If they
		// diverge, at least one of them is describing a fixture that doesn't actually exist.
		for (ExampleEntry ex : manifest.examples) {
			boolean claimsValidating = ex.mode.contains("validating");
			assertEquals(ex.id + ": mode=" + ex.mode + " but hasValidationTests=" + ex.hasValidationTests,
					ex.hasValidationTests, claimsValidating);
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
			boolean classificationIsSat = "sat".equals(ex.expected.classification);
			boolean outcomeSaysSat = ex.expected.outcome.contains("SATISFIABLE")
					&& !ex.expected.outcome.contains("UNSATISFIABLE");
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
