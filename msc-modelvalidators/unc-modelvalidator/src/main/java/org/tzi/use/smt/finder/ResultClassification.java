package org.tzi.use.smt.finder;

import java.util.Map;
import java.util.Set;
import org.tzi.use.smt.verify.InvariantVerdict;

/**
 * The single vocabulary in which a finder run is reported -- on the CLI, in the benchmark's raw
 * results, in the corpus manifest and in the paper's tables.
 *
 * <p>It exists because {@link ProfileOutcome} alone is not reportable. Three distinct situations
 * reach {@code ProfileOutcome.REFUTED} or leave it, and collapsing any pair of them publishes a
 * claim the run did not establish:
 *
 * <ul>
 *   <li>a refutation resting on exact linear arithmetic, which IS a bounded refutation;
 *   <li>a refutation touching a U-type confidence threshold, which rests on an enclosure of USE's
 *       CDF approximation and therefore refutes nothing inside the boundary band;
 *   <li>an unresolved solve -- {@code unknown} or a timeout -- which refutes nothing at all.
 * </ul>
 *
 * <p>The last collapse was live: the benchmark runner reported {@code sat ? "SATISFIABLE" :
 * "UNSATISFIABLE"} from {@code satisfiable()}, and {@code satisfiable()} is {@code outcome ==
 * SATISFIED}, so a {@link ProfileOutcome#PARTIAL} run was recorded as a refutation -- exactly what
 * {@code ProfileOutcome}'s own documentation forbids.
 */
public enum ResultClassification {

  /**
   * The requested profile was satisfied AND every active invariant's verdict, taken from USE's own
   * evaluator over the reconstructed state, holds on every witnessed scenario. This is the only
   * classification that asserts a witness exists.
   */
  SAT_VALIDATED,

  /**
   * The requested profile was refuted and no quantile enclosure entered the encoding, so the
   * refutation is exact WITHIN the configured scopes, domains and scenario policy. Still bounded:
   * see {@link BoundedCompletenessQualification}.
   */
  UNSAT_EXACT,

  /**
   * The requested profile was refuted, but at least one U-type confidence threshold was encoded
   * against an enclosure of USE's CDF approximation. Assignments inside that boundary band are not
   * ruled out, so this is NOT a refutation and must never be counted as one.
   */
  INCONCLUSIVE_NUMERICAL,

  /**
   * The configuration or model lies outside the implemented OCL fragment and the translation
   * refused it rather than approximating. No verdict was produced.
   */
  UNSUPPORTED,

  /**
   * The solver returned {@code unknown}, or a solve timed out, leaving at least one scenario
   * unresolved. No verdict was produced.
   */
  SOLVER_UNKNOWN,

  /**
   * The solver reported a model but reconstruction or the independent USE re-evaluation disagreed
   * with it. This is a defect signal about the encoding or the oracle, never a property of the
   * model under analysis.
   */
  VALIDATION_ERROR;

  /**
   * Classifies a completed run. {@code activeInvariants} is the caller's OWN active set: the
   * re-evaluator reports a verdict for every invariant declared in the model, and an invariant the
   * configuration never activated is not solver-enforced, so a witness leaving one false is not a
   * validation failure.
   *
   * <p>{@link #UNSUPPORTED} is not reachable here. A fragment refusal throws before a result
   * exists, so callers classify that from the exception; see {@code ofRefusal()}.
   */
  public static ResultClassification of(ModelFinderResult result, Set<String> activeInvariants) {
    return switch (result.outcome()) {
      case PARTIAL -> SOLVER_UNKNOWN;
      case REFUTED ->
          result.quantileEnclosures() > 0 ? INCONCLUSIVE_NUMERICAL : UNSAT_EXACT;
      case SATISFIED -> oracleAgrees(result, activeInvariants) ? SAT_VALIDATED : VALIDATION_ERROR;
    };
  }

  /** The classification of a run the fragment refused outright. */
  public static ResultClassification ofRefusal() {
    return UNSUPPORTED;
  }

  /** The classification of a run interrupted by an unexpected exception. */
  public static ResultClassification ofUnexpectedException() {
    return SOLVER_UNKNOWN;
  }

  /**
   * True when a snapshot was reconstructed and either (a) the model has no active OCL
   * invariants or (b) every ACTIVE invariant has exactly one present, HOLDING verdict over
   * the reconstructed state. Duplicate verdict entries for one invariant are rejected as
   * ill-defined. Reconstruction builds objects, attributes and links from the solver
   * assignment; it does not independently re-check multiplicities or bounds against the
   * original model.
   */
  static boolean oracleAgrees(ModelFinderResult result, Set<String> activeInvariants) {
    if (result.system() == null) {
      return false;
    }
    if (activeInvariants.isEmpty()) {
      return true; // no OCL invariants to verify
    }
    Map<String, InvariantVerdict> byName = new java.util.LinkedHashMap<>();
    for (InvariantVerdict verdict : result.verdicts()) {
      if (!activeInvariants.contains(verdict.invariantName())) {
        continue;
      }
      if (byName.put(verdict.invariantName(), verdict) != null) {
        return false; // duplicate verdict for one invariant: the oracle set is ill-defined
      }
    }
    if (byName.size() != activeInvariants.size()) {
      return false; // some active invariant has no oracle verdict at all
    }
    return byName.values().stream().allMatch(InvariantVerdict::holds);
  }
}
