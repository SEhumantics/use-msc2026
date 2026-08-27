package org.tzi.use.smt.finder;

import java.util.List;
import org.tzi.use.smt.config.ScenarioProfile;
import org.tzi.use.smt.encode.FragmentCoverageLedger;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.sys.MSystem;

/**
 * The outcome of one {@link SmtModelFinder#find} run: the coverage ledger for the invariants the
 * caller asked to enforce, the scenario PROFILE the query requested, the aggregate answer for that
 * profile, and one {@link ScenarioReport} per scenario actually solved.
 *
 * <p>The reported mode is a PAIR, e.g. {@code SATISFY/EXISTS} or {@code FRAGILE/COVER}: the witness
 * predicate lives in the query and the strength lives in {@link #profile()}. "An existential result
 * never implies either stronger profile", so a caller that wants UNIFORM has to read {@link
 * #profile()} and not merely {@link #satisfiable()}.
 *
 * <p>Milestone 4.5 could still describe a result with one system and one verdict list. 4.6 cannot:
 * COVER may deliver a DIFFERENT snapshot per scenario and UNIFORM delivers ONE snapshot that must
 * be independently checked ONCE PER SCENARIO. The legacy {@link #system()}/{@link #verdicts()}
 * accessors therefore project the FIRST witnessed scenario, which is exactly the old value for the
 * single-scenario EXISTS runs every pre-4.6 caller makes, and callers that care about the whole
 * profile read {@link #scenarios()}.
 *
 * @param scenarios every scenario the profile actually solved, in enumeration order. EXISTS reports
 *     the ONE scenario the solver chose (empty when the whole query is unsatisfiable, since no
 *     scenario was selected at all); COVER and UNIFORM report the complete configured scenario set.
 */
public record ModelFinderResult(
    FragmentCoverageLedger ledger,
    ScenarioProfile profile,
    ProfileOutcome outcome,
    List<ScenarioReport> scenarios) {

  public ModelFinderResult {
    scenarios = List.copyOf(scenarios);
  }

  /** True only when the requested profile itself was satisfied -- never a weaker one. */
  public boolean satisfiable() {
    return outcome == ProfileOutcome.SATISFIED;
  }

  /** The scenarios that produced an independently checked witness. */
  public List<ScenarioReport> witnesses() {
    return scenarios.stream().filter(s -> s.outcome() == ScenarioOutcome.WITNESSED).toList();
  }

  /** The first witnessed scenario's U-aware verdicts; empty when nothing was witnessed. */
  public List<InvariantVerdict> verdicts() {
    return witnesses().stream().findFirst().map(ScenarioReport::verdicts).orElse(List.of());
  }

  /** The first witnessed scenario's nominal-erasure verdicts; empty when there are none. */
  public List<InvariantVerdict> nominalVerdicts() {
    return witnesses().stream().findFirst().map(ScenarioReport::nominalVerdicts).orElse(List.of());
  }

  /** The first witnessed scenario's reconstructed system, or null when nothing was witnessed. */
  public MSystem system() {
    return witnesses().stream().findFirst().map(ScenarioReport::system).orElse(null);
  }

  /** True only for a satisfied profile whose independently re-evaluated invariants all hold. */
  public boolean allActiveInvariantsHold() {
    return satisfiable()
        && !witnesses().isEmpty()
        && witnesses().stream()
            .allMatch(witness -> witness.verdicts().stream().allMatch(InvariantVerdict::holds));
  }
}
