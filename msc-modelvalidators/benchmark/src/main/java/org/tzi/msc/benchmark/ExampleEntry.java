package org.tzi.msc.benchmark;

import java.util.List;

/** One row of {@code manifest.json} -- mirrors the JSON field names exactly for Gson. */
public class ExampleEntry {
	public String id;
	public String directory;
	public String useFile;
	public String propertiesFile;
	public String section;
	public int bitwidth;
	public String category; // "expressiveness" | "performance"
	public String mode; // "finding" | "validating" | "finding+validating"

	/**
	 * True if this example's directory ships hand-written valid-instance.cmd/invalid-instance.cmd
	 * SOIL validation fixtures (checked in, manually confirmed at authoring time -- see each .cmd
	 * file's own header comment). {@link BenchmarkRunner} does NOT execute these (it only runs
	 * {@code -validate}/Kodkod search) -- {@link SoilValidationRunner} does, via a real
	 * {@code use-gui.jar -nogui} subprocess, and writes its own verified pass/fail per fixture to
	 * {@code soil-validation-results.json}. This flag only records "fixtures exist and are claimed
	 * genuine"; it is {@link SoilValidationRunner}'s output, not this flag, that is the actual
	 * verification.
	 */
	public boolean hasValidationTests;

	/**
	 * Invariants that valid-instance.cmd's own header comment documents as deliberately out of
	 * scope for that fixture's "valid" claim (e.g. a supplementary invariant demanding a property --
	 * like a perfectly balanced tree -- the hand-built instance was never constructed to satisfy).
	 * Nullable/omitted for the common case (a genuinely valid instance has zero failures at all).
	 * {@link SoilValidationRunner} subtracts this set before deciding pass/fail, so a documented,
	 * expected failure here isn't misreported as a regression.
	 */
	public List<String> soilKnownOutOfScopeInvariants;
	public String provenanceType; // "plugin-original" | "adapted-from" | "use-bundled" | "authored-for-thesis"
	public String provenanceDetail;
	public String provenanceCitation; // nullable

	/**
	 * Feature IDs from {@code docs/modelvalidator-feature-matrix.json} that this scenario's SAT,
	 * UNSAT, or SOIL-validation evidence actually demonstrates for kk-modelvalidator -- derived
	 * mechanically from that matrix (which feature rows cite this scenario's directory), not
	 * hand-curated here. This is the plugin-capability axis: same feature-ID space the matrix uses
	 * for unc-modelvalidator once that module has support data of its own, so a scenario's coverage
	 * is comparable across plugins. Distinct from {@link #scenarioTags}.
	 */
	public List<String> features;

	/**
	 * Free-text descriptors of this scenario's own test design (scale, structural pattern, or
	 * benchmark role -- e.g. "combinatorial-stress-test", "paired-unsat-mutation") rather than a
	 * plugin capability. Carried over verbatim from this benchmark's prior curation; not sourced from
	 * or comparable against the feature matrix.
	 */
	public List<String> scenarioTags;

	public String question;
	public Integer repeats; // nullable: per-example override for genuinely slow (performance) scenarios
	public Integer warmups; // nullable: per-example override for the global --warmups default

	/** Declared oracle for finding-mode examples; null means "no expectation recorded yet". */
	public Expected expected;

	/**
	 * Reasons (if any) to distrust {@link #expected} beyond the usual regression-oracle caveat --
	 * e.g. a known-defect feature this scenario exercises whose bug could plausibly force the
	 * observed SAT/UNSAT outcome rather than the model's real semantics forcing it. Empty/omitted
	 * means no such risk was identified; presence does not mean the oracle IS wrong, only that it
	 * hasn't been independently re-derived and a specific, named risk exists. See each entry's cited
	 * feature ID in the matrix for the underlying mechanism.
	 */
	public List<String> oracleCaveats;

	/**
	 * The outcome a finding-mode example is expected to reach. Populated from this benchmark's own
	 * observed ground truth (see benchmark/src/main/resources/latest-results.json) at the time each
	 * entry was added -- not independently re-derived from the model/solver here, so it is a
	 * regression oracle ("does this still match what we last confirmed"), not a proof of what the
	 * *correct* answer objectively is. See {@link #oracleCaveats} for named, scenario-specific risks
	 * to that assumption.
	 */
	public static class Expected {
		public String classification; // "sat" | "unsat"
		public String outcome; // "SATISFIABLE" | "UNSATISFIABLE" | "TRIVIALLY_SATISFIABLE" | "TRIVIALLY_UNSATISFIABLE"
	}
}
