package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

/**
 * Regression test for the exact P0 bug this session found and fixed: a substring check
 * ({@code outcome.contains("SATISFIABLE")}) that matched all 4 of Kodkod's Outcome values,
 * including UNSATISFIABLE and TRIVIALLY_UNSATISFIABLE, causing a witness digest to be captured from
 * an empty/default system state on UNSAT results. See BenchmarkRunner.isWitnessCapturingOutcome's own
 * javadoc for the full story; this test exists so that bug cannot silently come back.
 */
public class BenchmarkRunnerTest {

	@Test
	public void satisfiableCapturesWitness() {
		assertTrue(BenchmarkRunner.isWitnessCapturingOutcome("SATISFIABLE"));
	}

	@Test
	public void triviallySatisfiableCapturesWitness() {
		assertTrue(BenchmarkRunner.isWitnessCapturingOutcome("TRIVIALLY_SATISFIABLE"));
	}

	@Test
	public void unsatisfiableDoesNotCaptureWitness() {
		// The exact regression case: "UNSATISFIABLE".contains("SATISFIABLE") is true, which is what
		// made the old substring-based check wrong.
		assertFalse(BenchmarkRunner.isWitnessCapturingOutcome("UNSATISFIABLE"));
	}

	@Test
	public void triviallyUnsatisfiableDoesNotCaptureWitness() {
		assertFalse(BenchmarkRunner.isWitnessCapturingOutcome("TRIVIALLY_UNSATISFIABLE"));
	}

	@Test
	public void errorDoesNotCaptureWitness() {
		assertFalse(BenchmarkRunner.isWitnessCapturingOutcome("ERROR"));
	}

	@Test
	public void nullDoesNotCaptureWitness() {
		assertFalse(BenchmarkRunner.isWitnessCapturingOutcome(null));
	}

	/**
	 * Regression test for round 17's finding: a repeat that fails AFTER earlier repeats in the same
	 * cell already succeeded and captured witness digests must not leak those digests into the final
	 * ERROR result -- that would contradict SolverResult.witnessDigest's own "null on
	 * UNSATISFIABLE/ERROR" contract and render misleadingly (an ERROR badge next to a real witness
	 * count) since the report template has no ERROR guard on the witness column specifically.
	 */
	@Test
	public void errorAfterPartialSuccessClearsWitnessDigests() {
		SolverResult result = new SolverResult();
		result.outcome = "ERROR"; // set by runOne()'s catch block before finalizeResult() is reached
		List<Double> wallMs = Arrays.asList(120.0, 130.0); // 2 repeats succeeded before the 3rd threw
		List<Long> kodkodSolveMs = Arrays.asList(80L, 85L);
		List<Long> kodkodTranslateMs = Arrays.asList(10L, 12L);
		List<String> digests = Arrays.asList("Foo#count=1;Foo.objects=[Foo{x=1}]");

		BenchmarkRunner.finalizeResult(result, "SATISFIABLE", wallMs, kodkodSolveMs, kodkodTranslateMs, digests);

		assertEquals("ERROR", result.outcome);
		assertNull("witnessDigest must be null on ERROR, even with partial pre-failure data", result.witnessDigest);
		assertTrue("allWitnessDigests must be empty on ERROR, even with partial pre-failure data",
				result.allWitnessDigests.isEmpty());
	}

	@Test
	public void satisfiableKeepsWitnessDigests() {
		SolverResult result = new SolverResult();
		List<Double> wallMs = Arrays.asList(120.0);
		List<Long> kodkodSolveMs = Arrays.asList(80L);
		List<Long> kodkodTranslateMs = Arrays.asList(10L);
		List<String> digests = Arrays.asList("Foo#count=1;Foo.objects=[Foo{x=1}]");

		BenchmarkRunner.finalizeResult(result, "SATISFIABLE", wallMs, kodkodSolveMs, kodkodTranslateMs, digests);

		assertEquals("SATISFIABLE", result.outcome);
		assertEquals("Foo#count=1;Foo.objects=[Foo{x=1}]", result.witnessDigest);
		assertEquals(digests, result.allWitnessDigests);
	}

	@Test
	public void errorWithNoPriorSuccessHasEmptyWitnessDigests() {
		SolverResult result = new SolverResult();
		result.outcome = "ERROR"; // first repeat threw before any wall time was even recorded
		BenchmarkRunner.finalizeResult(result, null, Collections.emptyList(), Collections.emptyList(),
				Collections.emptyList(), Collections.emptyList());

		assertEquals("ERROR", result.outcome);
		assertNull(result.witnessDigest);
		assertTrue(result.allWitnessDigests.isEmpty());
	}

	@Test
	public void positiveRepeatsAndNonNegativeWarmupsAreAccepted() {
		BenchmarkRunner.validateIterationCounts(1, 0, "test");
		BenchmarkRunner.validateIterationCounts(3, 2, "test");
	}

	@Test(expected = IllegalArgumentException.class)
	public void zeroRepeatsAreRejectedInsteadOfProducingEmptyErrorRows() {
		BenchmarkRunner.validateIterationCounts(0, 0, "test");
	}

	@Test(expected = IllegalArgumentException.class)
	public void negativeWarmupsAreRejected() {
		BenchmarkRunner.validateIterationCounts(1, -1, "test");
	}

	/**
	 * Study A (spec S9) needs "snapshot reconstructed" and "USE re-evaluation passed" as two SEPARATE
	 * columns, so they must be two separate recorded facts. On the SMT side they genuinely differ: a
	 * scenario can be witnessed (a snapshot exists) and still fail independent re-evaluation, which is
	 * precisely the case the parity table must not be able to hide.
	 */
	@Test
	public void reconstructionAndUseCheckAreRecordedIndependently() {
		SolverResult result = new SolverResult();
		result.outcome = "SATISFIABLE";

		BenchmarkRunner.recordReconstruction(result, true, false);

		assertEquals(Boolean.TRUE, result.reconstructed);
		assertEquals(Boolean.FALSE, result.useChecked);
	}

	/**
	 * The incumbent performs no independent USE re-evaluation of its own witnesses at all. Recording
	 * that as {@code false} would read as "re-evaluated and failed"; it must stay null, i.e. "no such
	 * claim is made", so the parity table can print it as not-applicable rather than as a failure.
	 */
	@Test
	public void anUnperformedUseCheckIsNullNotFalse() {
		SolverResult result = new SolverResult();
		result.outcome = "SATISFIABLE";

		BenchmarkRunner.recordReconstruction(result, true, null);

		assertEquals(Boolean.TRUE, result.reconstructed);
		assertNull("an unperformed check must not be recorded as a failed one", result.useChecked);
	}

	/**
	 * Same discipline as {@link #errorAfterPartialSuccessClearsWitnessDigests}: an ERROR cell reached
	 * no verdict, so it makes no reconstruction claim either, whatever a partially-completed repeat
	 * managed to build before it threw.
	 */
	@Test
	public void errorMakesNoReconstructionClaim() {
		SolverResult result = new SolverResult();
		result.outcome = "ERROR";

		BenchmarkRunner.recordReconstruction(result, true, true);

		assertNull("an ERROR cell must claim no reconstruction", result.reconstructed);
		assertNull("an ERROR cell must claim no USE re-evaluation", result.useChecked);
	}

	/**
	 * Regression test for the exact bug documented in
	 * docs/experiments/rq4-cost/raw-results-repeats5.json: 10 rows (RealOps/RealOps-UNSAT x 5 Kodkod
	 * solvers) all had {@code outcome=ERROR, error=None} because {@code KodkodModelValidator.validate}
	 * swallowed the {@code kodkodSolver.solve} exception with no channel for a caller to recover it. See
	 * {@code KodkodModelValidatorErrorReportingTest} (kk-modelvalidator module) for the actual solve()
	 * exception this reproduces ({@code IllegalArgumentException: No such atom in the universe:
	 * Real_3.5}); this test only exercises this method's own string formatting, pure and without a real
	 * Kodkod solve.
	 */
	@Test
	public void errorMessageFromValidationErrorFormatsClassAndMessage() {
		assertEquals("IllegalArgumentException: No such atom in the universe: Real_3.5",
				BenchmarkRunner.errorMessageFromValidationError(
						new IllegalArgumentException("No such atom in the universe: Real_3.5")));
	}

	@Test
	public void errorMessageFromValidationErrorIsNullWhenValidateDidNotThrow() {
		assertNull(BenchmarkRunner.errorMessageFromValidationError(null));
	}

	// ------------------------------------------------------- results.json is written atomically

	/**
	 * BenchmarkRunner rewrites results.json after EVERY completed cell so that the outer
	 * {@code timeout -k} kill in run-benchmark.sh loses at most the cell in progress. Truncating the
	 * destination and writing into it turns that safety net into its own hazard: a kill landing inside
	 * the rewrite leaves a half-written file that is not valid JSON at all, losing every cell rather
	 * than one. A rename cannot be observed half-done, so the destination must be a NEW file each
	 * time -- which is exactly what this asserts, via the file's identity rather than its contents
	 * (identical content would prove nothing about how it got there).
	 */
	@Test
	public void writeResultsReplacesTheDestinationByRenameNotByTruncatingItInPlace() throws Exception {
		Path dir = Files.createTempDirectory("benchmark-write-results");
		File out = dir.resolve("results.json").toFile();

		BenchmarkRunner.writeResults(cells(40), out);
		Object identityAfterFirstWrite = fileKeyOf(out.toPath());
		long sizeAfterFirstWrite = Files.size(out.toPath());

		BenchmarkRunner.writeResults(cells(1), out);

		Assume.assumeNotNull("this filesystem does not expose a file identity", identityAfterFirstWrite);
		assertNotEquals("an in-place truncate+rewrite keeps the same file; an atomic rename must not",
				identityAfterFirstWrite, fileKeyOf(out.toPath()));
		assertTrue("the smaller payload must fully replace the larger one, not be written over it",
				Files.size(out.toPath()) < sizeAfterFirstWrite);
	}

	@Test
	public void writeResultsLeavesValidJsonAndNoTemporaryFileBehind() throws Exception {
		Path dir = Files.createTempDirectory("benchmark-write-results-residue");
		File out = dir.resolve("results.json").toFile();

		BenchmarkRunner.writeResults(cells(40), out);
		BenchmarkRunner.writeResults(cells(3), out);

		JsonArray written = JsonParser.parseString(Files.readString(out.toPath(), StandardCharsets.UTF_8))
				.getAsJsonArray();
		assertEquals("the destination must hold exactly the last payload", 3, written.size());

		List<String> leftOver;
		try (Stream<Path> entries = Files.list(dir)) {
			leftOver = entries.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
		}
		// The temp file is a SIBLING of the destination (ATOMIC_MOVE is only guaranteed within one
		// filesystem, and the system temp dir routinely is not the same one), so a leaked one would
		// land here next to results.json.
		assertEquals("no temp file may survive a successful write", List.of("results.json"), leftOver);
	}

	private static Object fileKeyOf(Path path) throws Exception {
		return Files.readAttributes(path, BasicFileAttributes.class).fileKey();
	}

	private static List<SolverResult> cells(int count) {
		List<SolverResult> results = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			SolverResult result = new SolverResult();
			result.exampleId = "Example" + i;
			result.solver = "DefaultSAT4J";
			result.outcome = "SATISFIABLE";
			results.add(result);
		}
		return results;
	}
}
