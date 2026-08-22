package org.tzi.msc.benchmark;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
