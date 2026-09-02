package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/**
 * Tests {@link SoilValidationRunner#applyParsedOutcome} against real captured
 * {@code use-gui.jar -nogui} transcripts (from actually running these exact fixtures -- see
 * TRANSCRIPT_* constants below), not synthetic approximations, so a change to the parsing regexes
 * or the pass/fail decision is caught against ground truth already independently confirmed to be
 * correct by a live run.
 */
public class SoilValidationRunnerTest {

	// Real `info state' output from `java -jar use-gui.jar -nogui Library.use valid-instance.cmd'.
	// Both Library fixtures build the same 6-object/4-link state. Note the two Report tables: the
	// per-class one (total 6 objects) and then the per-association one (total 4 links) -- only the
	// FIRST is the object count, which is what parseObjectTotal has to get right.
	private static final String LIBRARY_INFO_STATE = "State: state#1\n"
			+ "class : #objects + #objects in subclasses\n"
			+ "-----------------------------------------\n"
			+ "Book  :        2                        2\n"
			+ "Copy  :        2                        2\n"
			+ "User  :        2                        2\n"
			+ "-----------------------------------------\n"
			+ "total :        6                         \n"
			+ "\n"
			+ "association : #links\n"
			+ "--------------------\n"
			+ "BelongsTo   :      2\n"
			+ "Borrows     :      2\n"
			+ "--------------------\n"
			+ "total       :      4\n";

	// Real output tail from `java -jar use-gui.jar -nogui Library.use valid-instance.cmd`.
	private static final String LIBRARY_VALID = LIBRARY_INFO_STATE
			+ "checking structure...\n"
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
	private static final String LIBRARY_INVALID = LIBRARY_INFO_STATE
			+ "checking invariant (1) `Book::authSeqFormatOk': OK.\n"
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
	private static final String LIBRARY_INVALID_RAW_WITH_ECHOED_COMMENTS = "invalid-instance.cmd> info state\n"
			+ LIBRARY_INFO_STATE
			+ "invalid-instance.cmd> -- Confirmed by actually running this: `check -v' reports\n"
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
	// exact transcript that surfaced the documented-out-of-scope-invariant case. Its `info state'
	// table has a single class, so the object total (4) and the link total (3) sit closer together
	// than in Library's -- another shape parseObjectTotal has to tell apart.
	private static final String GENEALOGY_VALID = "State: state#1\n"
			+ "class  : #objects + #objects in subclasses\n"
			+ "------------------------------------------\n"
			+ "Person :        4                        4\n"
			+ "------------------------------------------\n"
			+ "total  :        4                         \n"
			+ "\n"
			+ "association : #links\n"
			+ "--------------------\n"
			+ "Parenthood  :      3\n"
			+ "--------------------\n"
			+ "total       :      3\n"
			+ "checking invariant (1) `Person::acyclicParenthood': OK.\n"
			+ "checking invariant (2) `Person::balancedBinaryTree': FAILED.\n"
			+ "checking invariant (3) `Person::grandparentOlderGrandchild': OK.\n"
			+ "checking invariant (4) `Person::nameUnique': OK.\n"
			+ "checking invariant (5) `Person::parent_0_2_Set': OK.\n"
			+ "checking invariant (6) `Person::parentOlderChild': OK.\n"
			+ "checking invariant (7) `Person::parent_0_2_size': OK.\n"
			+ "checking invariant (8) `Person::parent_0_2_size_EQUIV_parent_0_2_Set': OK.\n"
			+ "checking invariant (9) `Person::parent_0_2_size_EQUIV_parent_0_2_Set_ONE': OK.\n"
			+ "checked 9 invariants in 0.058s, 1 failure.\n";

	// REAL transcript of a deliberately broken fixture: Library/valid-instance.cmd with its
	// `open valid-instance.soil' changed to a file that does not exist, run through the real
	// use-gui.jar -nogui invocation. This is the vacuous pass that F2 is about, verbatim -- USE
	// prints ONE "Error:" line, does NOT abort, carries on with the empty state it already had, and
	// then reports all 9 invariants OK / 0 failures and exits 0. Judging this transcript on the
	// invariant lines alone says "as expected"; it proves nothing whatsoever about the instance the
	// fixture claims to validate. Kept untrimmed (echoes and the JVM readline warning included) so
	// the test exercises the real input the runner sees.
	private static final String LIBRARY_VALID_WITH_MISSING_SOIL = "USE version 7.5.0, Copyright (C) 1999-2025"
			+ " University of Bremen & University of Applied Sciences Hamburg\n"
			+ "Invalid extension directory '/home/u/use-msc2026/use-gui/oclextensions'\n"
			+ "java.lang.UnsatisfiedLinkError: no natGNUReadline in java.library.path: /usr/lib\n"
			+ "Apparently, the GNU readline library is not available on your system.\n"
			+ "valid-instance.cmd> -- Model VALIDATION of a hand-built instance -- a distinct concern from\n"
			+ "valid-instance.cmd> open no-such-file.soil\n"
			+ "Error: File `/home/u/use-msc2026/examples/Library/no-such-file.soil' could not be found!\n"
			+ "valid-instance.cmd> \n"
			+ "valid-instance.cmd> info state\n"
			+ "State: state#1\n"
			+ "class : #objects + #objects in subclasses\n"
			+ "-----------------------------------------\n"
			+ "Book  :        0                        0\n"
			+ "Copy  :        0                        0\n"
			+ "User  :        0                        0\n"
			+ "-----------------------------------------\n"
			+ "total :        0                         \n"
			+ "\n"
			+ "association : #links\n"
			+ "--------------------\n"
			+ "BelongsTo   :      0\n"
			+ "Borrows     :      0\n"
			+ "--------------------\n"
			+ "total       :      0\n"
			+ "valid-instance.cmd> check -v\n"
			+ "checking structure...\n"
			+ "checked structure in 0ms.\n"
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
			+ "checked 9 invariants in 0.009s, 0 failures.\n"
			+ "valid-instance.cmd> quit\n";

	private SoilValidationResult apply(String output, String kind, List<String> outOfScope) {
		return apply(output, kind, outOfScope, 0);
	}

	private SoilValidationResult apply(String output, String kind, List<String> outOfScope, int exitCode) {
		SoilValidationResult result = new SoilValidationResult();
		result.exitCode = exitCode;
		SoilValidationRunner.applyParsedOutcome(result, output, kind, outOfScope);
		return result;
	}

	@Test
	public void validFixtureWithNoFailuresPasses() {
		SoilValidationResult r = apply(LIBRARY_VALID, "valid", Collections.emptyList());
		assertTrue(r.note, r.passed);
		assertEquals(Integer.valueOf(9), r.numInvariantsChecked);
		assertEquals(Integer.valueOf(0), r.numFailures);
		assertTrue(r.failedInvariants.isEmpty());
		assertEquals(Integer.valueOf(6), r.numObjectsInState);
		assertTrue(r.errorLines.isEmpty());
	}

	@Test
	public void invalidFixtureWithOneFailurePasses() {
		// "passes" here means the fixture did its job: it proved the plugin detects the deliberately
		// broken invariant.
		SoilValidationResult r = apply(LIBRARY_INVALID, "invalid", Collections.emptyList());
		assertTrue(r.note, r.passed);
		assertEquals(Integer.valueOf(1), r.numFailures);
		assertEquals(List.of("Book::yearPlausible"), r.failedInvariants);
		assertEquals(Integer.valueOf(6), r.numObjectsInState);
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
		assertTrue(r.note, r.passed);
		assertEquals(Integer.valueOf(1), r.numFailures);
		assertEquals(List.of("Book::yearPlausible"), r.failedInvariants);
	}

	@Test
	public void invalidFixtureWithZeroFailuresFails() {
		// Constructed edge case (not a real transcript): an invalid-instance.cmd that reports 0
		// failures means the plugin failed to detect the deliberately broken invariant -- must fail.
		String zeroFailureTranscript = LIBRARY_INFO_STATE
				+ "checking invariant (1) `Book::authSeqFormatOk': OK.\n"
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
	public void validFixtureWithAnUnattributedSummaryFailureFails() {
		// A failed-invariant line is the evidence that lets the runner apply the narrowly scoped
		// documented-exception allowlist. If USE reports a failure in its summary but the detailed
		// line is absent or has a changed format, treating the empty parsed list as a clean run would
		// hide a real regression.
		String summaryOnlyFailure = LIBRARY_INFO_STATE
				+ "checking invariants...\n"
				+ "checked 1 invariant in 0.01s, 1 failure.\n";
		SoilValidationResult r = apply(summaryOnlyFailure, "valid", Collections.emptyList());
		assertFalse(r.passed);
		assertEquals(Integer.valueOf(1), r.numFailures);
		assertTrue(r.failedInvariants.isEmpty());
		assertTrue(r.note, r.note.contains("only 0 failed invariant detail"));
	}

	@Test
	public void validFixtureWithDocumentedOutOfScopeFailurePasses() {
		// Same transcript as above, but now with the documented allowlist applied (matching
		// manifest.json's Genealogy.soilKnownOutOfScopeInvariants) -- must pass.
		SoilValidationResult r = apply(GENEALOGY_VALID, "valid", List.of("Person::balancedBinaryTree"));
		assertTrue(r.note, r.passed);
		// failedInvariants still records the raw failure either way -- only the pass/fail judgment
		// changes, not the underlying data.
		assertEquals(List.of("Person::balancedBinaryTree"), r.failedInvariants);
		assertEquals(Integer.valueOf(4), r.numObjectsInState);
	}

	@Test
	public void unparseableOutputFails() {
		SoilValidationResult r = apply("garbage output with no recognizable summary line\n", "valid",
				Collections.emptyList());
		assertFalse(r.passed);
		assertTrue(r.note, r.note.contains("could not find"));
	}

	// ------------------------------------------------------------------ F2: vacuous-pass gates

	@Test
	public void aValidFixtureWhoseSoilFailedToLoadFailsInsteadOfPassingVacuously() {
		// THE F2 regression. Every invariant line in this real transcript says OK, the summary says
		// "0 failures", and USE exited 0 -- so on the invariant evidence alone this fixture "passed".
		// It must not: the `open' failed, the state is empty, and all nine verdicts are vacuous.
		SoilValidationResult r = apply(LIBRARY_VALID_WITH_MISSING_SOIL, "valid", Collections.emptyList());
		assertFalse("a fixture that never loaded its instance must not pass", r.passed);
		assertEquals(Integer.valueOf(9), r.numInvariantsChecked);
		assertEquals(Integer.valueOf(0), r.numFailures);
		assertEquals(Integer.valueOf(0), r.numObjectsInState);
		assertEquals(List.of("Error: File `/home/u/use-msc2026/examples/Library/no-such-file.soil'"
				+ " could not be found!"), r.errorLines);
		assertTrue(r.note, r.note.contains("could not be found"));
		assertTrue(r.note, r.note.contains("0 objects"));
	}

	@Test
	public void theSameBrokenTranscriptAlsoFailsAsAnInvalidFixture() {
		// The gates are deliberately kind-independent: an invalid-instance fixture whose .soil never
		// loaded is exactly as uninformative as a valid one, and must not be reported on either.
		SoilValidationResult r = apply(LIBRARY_VALID_WITH_MISSING_SOIL, "invalid", Collections.emptyList());
		assertFalse(r.passed);
	}

	@Test
	public void aUseErrorLineFailsAnOtherwiseCleanRun() {
		// A populated state and a clean check -v are still not enough if USE reported an error along
		// the way -- e.g. a SOIL statement that failed to compile, leaving a partially built instance.
		String withSoilCompileError = "Error: line 3:12 mismatched input ':=' expecting '.'\n" + LIBRARY_VALID;
		SoilValidationResult r = apply(withSoilCompileError, "valid", Collections.emptyList());
		assertFalse(r.passed);
		assertEquals(1, r.errorLines.size());
		assertTrue(r.note, r.note.contains("1 error line"));
	}

	@Test
	public void useLogsOtherTwoErrorFormatsAlsoFail() {
		// org.tzi.use.util.Log has three error shapes, not one (Log.java:192-227). All three must be
		// caught, or an error reported through the exception-carrying overloads slips past silently.
		assertFalse(apply("error in org.tzi.use.main.shell.Shell: broken\n" + LIBRARY_VALID, "valid",
				Collections.emptyList()).passed);
		assertFalse(apply("exception java.lang.IllegalStateException: broken\n" + LIBRARY_VALID, "valid",
				Collections.emptyList()).passed);
	}

	@Test
	public void wordsMerelyContainingErrorAreNotMistakenForUseErrors() {
		// Every one of these runs emits the JVM's own readline warning, whose first line ends in
		// "UnsatisfiedLinkError: ...". An unanchored scan for "Error:" would fail all 32 fixtures on
		// a machine without GNU readline -- so the patterns are line-anchored on Log's exact
		// prefixes. Likewise an echoed .cmd comment that merely quotes an error message is input
		// text, not engine output.
		String noise = "java.lang.UnsatisfiedLinkError: no natGNUReadline in java.library.path: /usr/lib\n"
				+ "valid-instance.cmd> -- Error: this line is an echoed comment, not a USE error\n"
				+ "  (some.expr) : Boolean = false\n"
				+ LIBRARY_VALID;
		SoilValidationResult r = apply(noise, "valid", Collections.emptyList());
		assertTrue(r.note, r.errorLines.isEmpty());
		assertTrue(r.note, r.passed);
	}

	@Test
	public void aNonZeroExitCodeFailsTheFixture() {
		// exitCode was recorded but never consulted before F2.
		SoilValidationResult r = apply(LIBRARY_VALID, "valid", Collections.emptyList(), 1);
		assertFalse(r.passed);
		assertTrue(r.note, r.note.contains("exited with code 1"));
	}

	@Test
	public void aTranscriptWithNoInfoStateReportFails() {
		// Absence of evidence is not evidence: without an `info state' report there is no way to
		// tell a real instance from an empty one, so the fixture cannot be certified either way.
		String noInfoState = "checking invariant (1) `Book::titleIsKey': OK.\n"
				+ "checked 1 invariant in 0.01s, 0 failures.\n";
		SoilValidationResult r = apply(noInfoState, "valid", Collections.emptyList());
		assertFalse(r.passed);
		assertNull(r.numObjectsInState);
		assertTrue(r.note, r.note.contains("no `info state' object report"));
	}

	@Test
	public void objectTotalIsReadFromTheObjectTableNotTheLinkTable() {
		// `info state' prints two Report tables, each ending in a "total : n" row. Library's state
		// has 6 objects and 4 links; reading the wrong table would silently make the gate check the
		// link count instead -- and would report a non-empty state for a link-free instance.
		assertEquals(Integer.valueOf(6), SoilValidationRunner.parseObjectTotal(LIBRARY_INFO_STATE));
		assertEquals(Integer.valueOf(4), SoilValidationRunner.parseObjectTotal(GENEALOGY_VALID));
	}

	@Test
	public void objectTotalSurvivesLocaleDependentDigitGrouping() {
		// cmdInfoState formats through NumberFormat.getInstance(), so a four-digit total is grouped
		// according to the JVM's default locale. Comma, dot and the no-break space Java uses for
		// locales such as fr must all read back as the same count.
		String template = "State: state#1\n"
				+ "class : #objects + #objects in subclasses\n"
				+ "-----------------------------------------\n"
				+ "Cell  :    %s                    %s\n"
				+ "-----------------------------------------\n"
				+ "total :    %s                         \n";
		assertEquals(Integer.valueOf(1024), SoilValidationRunner.parseObjectTotal(
				String.format(template, "1,024", "1,024", "1,024")));
		assertEquals(Integer.valueOf(1024), SoilValidationRunner.parseObjectTotal(
				String.format(template, "1.024", "1.024", "1.024")));
		assertEquals(Integer.valueOf(1024), SoilValidationRunner.parseObjectTotal(
				String.format(template, "1 024", "1 024", "1 024")));
	}

	@Test
	public void aSpaceGroupedTotalFailsClosedRatherThanReadingAWrongCount() {
		// A plain ASCII space as grouping separator is indistinguishable from this Report's own column
		// padding. Reading "1 024" as 1 would be worse than not reading it at all, so the row comes back
		// unparsed and the gate reports the honest "no `info state' object report" failure instead of
		// certifying a fixture on a wrong count. (CHECK_SUMMARY already assumes a '.' decimal separator,
		// so such a locale was never supported by this runner in the first place.)
		String asciiSpaceGrouped = "State: state#1\n"
				+ "class : #objects + #objects in subclasses\n"
				+ "-----------------------------------------\n"
				+ "Cell  :    1 024                    1 024\n"
				+ "-----------------------------------------\n"
				+ "total :    1 024                         \n";
		assertNull(SoilValidationRunner.parseObjectTotal(asciiSpaceGrouped));
	}

	@Test
	public void aClassNamedTotalIsNotMistakenForTheSummaryRow() {
		// The summary row is identified by its preceding ruler AND by the number being the last thing
		// on the line -- a class row always carries a second number ("+ #objects in subclasses"), so
		// a model with a class actually named "total" still reports the real object count.
		String withTotalClass = "State: state#1\n"
				+ "class : #objects + #objects in subclasses\n"
				+ "-----------------------------------------\n"
				+ "total :        7                        7\n"
				+ "-----------------------------------------\n"
				+ "total :        7                         \n";
		assertEquals(Integer.valueOf(7), SoilValidationRunner.parseObjectTotal(withTotalClass));
	}

	// ------------------------------------------------------------------ subprocess plumbing

	@Test
	public void timeoutStartsBeforeAQuietChildClosesStdout() throws Exception {
		// A quiet, sleeping child leaves its stdout pipe open. Before the concurrent-drain fix,
		// SoilValidationRunner read that pipe to EOF *before* starting waitFor(timeout), so this
		// returned only after the full sleep instead of respecting the supplied deadline.
		Process process = new ProcessBuilder("sh", "-c", "exec sleep 2").start();
		StringBuilder output = new StringBuilder();
		long started = System.nanoTime();
		boolean finished = SoilValidationRunner.waitForProcessAndCollectOutput(process, output, 1);
		long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

		assertFalse(finished);
		assertTrue("timeout should not wait for the child sleep, elapsed=" + elapsedMillis + "ms",
				elapsedMillis < 1800);
		assertFalse(process.isAlive());
	}

	@Test
	public void interruptionReapsTheChildProcess() throws Exception {
		Process process = new ProcessBuilder("sh", "-c", "exec sleep 10").start();
		AtomicBoolean sawInterrupt = new AtomicBoolean();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread waiter = new Thread(() -> {
			try {
				SoilValidationRunner.waitForProcessAndCollectOutput(process, new StringBuilder(), 30);
			} catch (InterruptedException expected) {
				sawInterrupt.set(true);
			} catch (Throwable unexpected) {
				failure.set(unexpected);
			}
		});
		waiter.start();
		Thread.sleep(100);
		waiter.interrupt();
		waiter.join(2_000);

		assertFalse("interrupted waiter must finish", waiter.isAlive());
		assertTrue(sawInterrupt.get());
		assertEquals(null, failure.get());
		assertFalse("interrupted run must not leave use-gui alive", process.isAlive());
	}
}
