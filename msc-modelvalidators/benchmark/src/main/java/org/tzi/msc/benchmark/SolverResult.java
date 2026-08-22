package org.tzi.msc.benchmark;

import java.util.List;

/** One (example, solver) aggregate result -- serialized straight to JSON. */
public class SolverResult {
	public String exampleId;
	public String solver;
	public int repeats;
	public int warmups; // discarded iterations run before the measured `repeats`, for JIT warm-up
	public String outcome; // SATISFIABLE | UNSATISFIABLE | ERROR
	public int numSearches; // internal solve/retry attempts within one -validate call
	public double medianWallMs; // this benchmark's own nanoTime() wrap around validate()
	public double minWallMs;
	public double maxWallMs;
	public long medianKodkodSolvingMs; // Kodkod's own Statistics.solvingTime() (ms resolution)
	public long medianKodkodTranslationMs;
	public String witnessDigest; // first repeat's digest; null on UNSATISFIABLE/ERROR
	public List<String> allWitnessDigests; // every repeat's digest, to show solver-to-solver / run-to-run agreement
	public String error; // non-null only when outcome == ERROR
}
