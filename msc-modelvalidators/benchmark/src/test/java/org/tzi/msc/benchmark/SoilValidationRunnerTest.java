package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Tests {@link SoilValidationRunner#applyParsedOutcome} against real captured
 * {@code use-gui.jar -nogui} transcripts (from actually running these exact fixtures this session --
 * see TRANSCRIPT_* constants below), not synthetic approximations, so a change to the parsing regexes
 * or the pass/fail decision is caught against ground truth already independently confirmed to be
 * correct by a live run.
 */
public class SoilValidationRunnerTest {

	// Real output tail from `java -jar use-gui.jar -nogui Library.use valid-instance.cmd`.
	private static final String LIBRARY_VALID = "checking structure...\n"
			+ "checked structure in 1ms.\n"
			+ "checking invariants...\n"
			+ "checking invariant (1) `Book::authSeqFormatOk': OK.\n"
			+ "checking invariant (2) `Book::titleFormatOk': OK.\n"
			+ "checking invariant (3) `Book::titleIsKey': OK.\n"
			+ "checking invariant (4) `Book::yearPlausible': OK.\n"
			+ "checking invariant (5) `Copy::signatureFormatOk': OK.\n"
			+ "checking invariant (6) `Copy::signatureIsKey': OK.\n"
			+ "checking invariant (7) `User::nameAddressFormatOk': OK.\n"
			+ "checking invariant (8) `User::nameIsKey': OK.\n"
			+ "checking invariant (9) `User::noDoubleBorrowings': OK.\n"
			+ "checked 9 invariants in 0.015s, 0 failures.\n";

	// Real output tail from `java -jar use-gui.jar -nogui Library.use invalid-instance.cmd`.
	private static final String LIBRARY_INVALID = "checking invariant (1) `Book::authSeqFormatOk': OK.\n"
			+ "checking invariant (2) `Book::titleFormatOk': OK.\n"
			+ "checking invariant (3) `Book::titleIsKey': OK.\n"
			+ "checking invariant (4) `Book::yearPlausible': FAILED.\n"
			+ "checking invariant (5) `Copy::signatureFormatOk': OK.\n"
			+ "checking invariant (6) `Copy::signatureIsKey': OK.\n"
			+ "checking invariant (7) `User::nameAddressFormatOk': OK.\n"
			+ "checking invariant (8) `User::nameIsKey': OK.\n"
			+ "checking invariant (9) `User::noDoubleBorrowings': OK.\n"
			+ "checked 9 invariants in 0.022s, 1 failure.\n";

	// Real, UNTRIMMED raw transcript tail from `java -jar use-gui.jar -nogui Library.use
	// invalid-instance.cmd` -- unlike LIBRARY_INVALID above, this keeps -nogui's own echo of the
	// driving .cmd file's lines (prefixed "invalid-instance.cmd> "), including its header comment
	// that documents the expected check -v output for a human reader. That echoed comment line
	// itself matches INVARIANT_LINE/CHECK_SUMMARY (even the summary form with no "in Xs" suffix,
	// since that's optional) -- this is the exact shape that caused a real double-counting bug: a
	// single genuine failure was reported twice in failedInvariants because the echoed comment line
	// was scanned as if it were real engine output. See ECHOED_SCRIPT_LINE in SoilValidationRunner.
	private static final String LIBRARY_INVALID_RAW_WITH_ECHOED_COMMENTS = "invalid-instance.cmd> -- Confirmed by actually running this: `check -v' reports\n"
			+ "invalid-instance.cmd> -- \"checking invariant (4) `Book::yearPlausible': FAILED.\" with\n"
			+ "invalid-instance.cmd> -- DBforDummies as the sole object in Book.allInstances->select(not\n"
			+ "invalid-instance.cmd> -- yearPlausible) below, and finishes \"checked 9 invariants, 1 failure.\";\n"
			+ "invalid-instance.cmd> -- every one of the other 8 invariants reports OK/true in the same run.\n"
			+ "invalid-instance.cmd> check -v\n"
			+ "checking structure...\n"
			+ "checked structure in 1ms.\n"
			+ "checking invariants...\n"
			+ "checking invariant (1) `Book::authSeqFormatOk': OK.\n"
			+ "checking invariant (2) `Book::titleFormatOk': OK.\n"
			+ "checking invariant (3) `Book::titleIsKey': OK.\n"
			+ "checking invariant (4) `Book::yearPlausible': FAILED.\n"
			+ "checking invariant (5) `Copy::signatureFormatOk': OK.\n"
			+ "checking invariant (6) `Copy::signatureIsKey': OK.\n"
			+ "checking invariant (7) `User::nameAddressFormatOk': OK.\n"
			+ "checking invariant (8) `User::nameIsKey': OK.\n"
			+ "checking invariant (9) `User::noDoubleBorrowings': OK.\n"
			+ "checked 9 invariants in 0.038s, 1 failure.\n";

	// Real output tail from `java -jar use-gui.jar -nogui Genealogy.use valid-instance.cmd` -- the
	// exact transcript that surfaced the documented-out-of-scope-invariant case this session.
	private static final String GENEALOGY_VALID = "checking invariant (1) `Person::acyclicParenthood': OK.\n"
			+ "checking invariant (2) `Person::balancedBinaryTree': FAILED.\n"
			+ "checking invariant (3) `Person::grandparentOlderGrandchild': OK.\n"
			+ "checking invariant (4) `Person::nameUnique': OK.\n"
			+ "checking invariant (5) `Person::parentOlderChild': OK.\n"
			+ "checking invariant (6) `Person::parent_0_2_Set': OK.\n"
			+ "checking invariant (7) `Person::parent_0_2_size': OK.\n"
			+ "checking invariant (8) `Person::parent_0_2_size_EQUIV_parent_0_2_Set': OK.\n"
			+ "checking invariant (9) `Person::parent_0_2_size_EQUIV_parent_0_2_Set_ONE': OK.\n"
			+ "checked 9 invariants in 0.058s, 1 failure.\n";

	private SoilValidationResult apply(String output, String kind, List<String> outOfScope) {
		SoilValidationResult result = new SoilValidationResult();
		SoilValidationRunner.applyParsedOutcome(result, output, kind, outOfScope);
		return result;
	}

	@Test
	public void validFixtureWithNoFailuresPasses() {
		SoilValidationResult r = apply(LIBRARY_VALID, "valid", Collections.emptyList());
		assertTrue(r.passed);
		assertEquals(Integer.valueOf(9), r.numInvariantsChecked);
		assertEquals(Integer.valueOf(0), r.numFailures);
		assertTrue(r.failedInvariants.isEmpty());
	}

	@Test
	public void invalidFixtureWithOneFailurePasses() {
		// "passes" here means the fixture did its job: it proved the plugin detects the deliberately
		// broken invariant.
		SoilValidationResult r = apply(LIBRARY_INVALID, "invalid", Collections.emptyList());
		assertTrue(r.passed);
		assertEquals(Integer.valueOf(1), r.numFailures);
		assertEquals(List.of("Book::yearPlausible"), r.failedInvariants);
	}

	@Test
	public void echoedHeaderCommentIsNotDoubleCountedAsAFailure() {
		// Regression test for the real bug found in round 15's review: -nogui echoes the driving
		// .cmd file's own comment lines back to stdout, and Library's invalid-instance.cmd's header
		// comment documents the expected "checking invariant (4) `Book::yearPlausible': FAILED."
		// line verbatim for a human reader -- so scanning the unfiltered transcript found that
		// invariant name twice (once in the echoed comment, once in the real engine output) despite
		// only one genuine failure having occurred.
		SoilValidationResult r = apply(LIBRARY_INVALID_RAW_WITH_ECHOED_COMMENTS, "invalid", Collections.emptyList());
		assertTrue(r.passed);
		assertEquals(Integer.valueOf(1), r.numFailures);
		assertEquals(List.of("Book::yearPlausible"), r.failedInvariants);
	}

	@Test
	public void invalidFixtureWithZeroFailuresFails() {
		// Constructed edge case (not a real transcript): an invalid-instance.cmd that reports 0
		// failures means the plugin failed to detect the deliberately broken invariant -- must fail.
		String zeroFailureTranscript = "checking invariant (1) `Book::authSeqFormatOk': OK.\n"
				+ "checked 1 invariant in 0.01s, 0 failures.\n";
		SoilValidationResult r = apply(zeroFailureTranscript, "invalid", Collections.emptyList());
		assertFalse(r.passed);
	}

	@Test
	public void validFixtureWithUndocumentedFailureFails() {
		// This is the actual regression this test guards: without the out-of-scope allowlist,
		// Genealogy's valid-instance.cmd must be reported as NOT passed (its one failure is real
		// output, not fabricated -- see the class javadoc).
		SoilValidationResult r = apply(GENEALOGY_VALID, "valid", Collections.emptyList());
		assertFalse(r.passed);
		assertEquals(List.of("Person::balancedBinaryTree"), r.failedInvariants);
	}

	@Test
	public void validFixtureWithDocumentedOutOfScopeFailurePasses() {
		// Same transcript as above, but now with the documented allowlist applied (matching
		// manifest.json's Genealogy.soilKnownOutOfScopeInvariants) -- must pass.
		SoilValidationResult r = apply(GENEALOGY_VALID, "valid", List.of("Person::balancedBinaryTree"));
		assertTrue(r.passed);
		// failedInvariants still records the raw failure either way -- only the pass/fail judgment
		// changes, not the underlying data.
		assertEquals(List.of("Person::balancedBinaryTree"), r.failedInvariants);
	}

	@Test
	public void unparseableOutputFails() {
		SoilValidationResult r = apply("garbage output with no recognizable summary line\n", "valid",
				Collections.emptyList());
		assertFalse(r.passed);
		assertTrue(r.note.contains("could not find"));
	}
}
