package org.tzi.msc.benchmark;

import java.util.List;

/** One (example, solver) aggregate result -- serialized straight to JSON. */
public class SolverResult {
	public String exampleId;
	public String solver;
	public int repeats;
	public int warmups; // discarded iterations run before the measured `repeats`, for JIT warm-up
	public String outcome; // SATISFIABLE | UNSATISFIABLE | ERROR
	/**
	 * The unambiguous classification of this run: SAT_VALIDATED, UNSAT_EXACT,
	 * INCONCLUSIVE_NUMERICAL, UNSUPPORTED, SOLVER_UNKNOWN or VALIDATION_ERROR. Null on the
	 * Kodkod rows, which have no equivalent notion. The {@code outcome} field above cannot
	 * carry this: it has only three values, so it reports a numerically-qualified negative and
	 * an unresolved solve as if both were refutations.
	 */
	public String classification;
	public int numSearches; // internal solve/retry attempts within one -validate call
	public double medianWallMs; // this benchmark's own nanoTime() wrap around validate()
	public double minWallMs;
	public double maxWallMs;
	/**
	 * Every measured repeat's wall time, in repeat order (warm-ups excluded -- they are discarded
	 * from {@link #medianWallMs} too, so the two must agree). Lets a reader recompute any robust
	 * statistic without trusting our choice of one; empty list on an ERROR cell.
	 */
	public List<Double> wallMsPerRepeat = List.of();
	/**
	 * SMT rows only. The scenario policy the configuration's query requested -- EXISTS, COVER or
	 * UNIFORM -- and the size of the configured scenario space it quantifies over, plus how many
	 * of those scenarios the profile actually solved (EXISTS reports the one chosen; COVER and
	 * UNIFORM report the complete set). Null on the Kodkod rows, which have no scenario notion.
	 */
	public String policy;
	public Integer configuredScenarios;
	public Integer reportedScenarios;
	/** SMT rows only: solver invocations and total SMT-LIB characters of one measured repeat. */
	public Long solverCalls;
	public Long scriptCharacters;
	public long medianKodkodSolvingMs; // Kodkod's own Statistics.solvingTime() (ms resolution)
	public long medianKodkodTranslationMs;
	public String witnessDigest; // first repeat's digest; null on UNSATISFIABLE/ERROR
	public List<String> allWitnessDigests; // every repeat's digest, to show solver-to-solver / run-to-run agreement
	public String error; // non-null only when outcome == ERROR

	/**
	 * True when this backend actually reconstructed a snapshot for this cell. Null means the backend
	 * makes no such claim -- either because the cell reached no verdict (ERROR) or because the backend
	 * does not report it. Deliberately NOT derived from {@link #witnessDigest}: Study A prints
	 * "reconstructed" and "USE-checked" as two independent columns, and collapsing them onto one bit
	 * would make a snapshot that failed re-evaluation indistinguishable from one that passed.
	 */
	public Boolean reconstructed;

	/**
	 * True when this backend independently re-evaluated the reconstructed snapshot with the USE
	 * evaluator and every active invariant held. Null means no such check was performed AT ALL -- which
	 * is the incumbent's permanent state, since it re-checks nothing and self-reports its own verdict.
	 * Null is not false: false would read as "re-evaluated and found wrong", a strictly stronger and
	 * unearned claim.
	 */
	public Boolean useChecked;
}
