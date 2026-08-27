package org.tzi.use.smt.finder;

import java.util.List;
import java.util.Map;
import org.tzi.use.smt.config.Scenario;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.sys.MSystem;

/**
 * The evidence for ONE scenario: which measurement quality it fixed, what happened, and -- when a
 * witness was delivered -- the reconstructed snapshot plus every independent USE verdict taken over
 * it.
 *
 * <p>Milestone 4.6's definition of done requires that "returned evidence identifies every scenario
 * and snapshot used", which one system and one flat verdict list cannot express: COVER delivers N
 * snapshots and UNIFORM delivers one snapshot checked N times. Each of those checks gets its own
 * report here, so "UNIFORM was checked once" is visible in the result rather than hidden inside it.
 */
public record ScenarioReport(
    Scenario scenario,
    ScenarioOutcome outcome,
    MSystem system,
    Map<TranslationMode, List<InvariantVerdict>> verdictsByMode) {

  public ScenarioReport {
    verdictsByMode = Map.copyOf(verdictsByMode);
    if (outcome == ScenarioOutcome.WITNESSED && system == null) {
      throw new IllegalArgumentException(
          "a witnessed scenario must carry its reconstructed system");
    }
    if (outcome != ScenarioOutcome.WITNESSED && system != null) {
      throw new IllegalArgumentException("only a witnessed scenario has a reconstructed system");
    }
  }

  static ScenarioReport unwitnessed(Scenario scenario, ScenarioOutcome outcome) {
    return new ScenarioReport(scenario, outcome, null, Map.of());
  }

  /** The U-AWARE verdicts -- the reading every ordinary caller wants. */
  public List<InvariantVerdict> verdicts() {
    return verdictsByMode.getOrDefault(TranslationMode.UNCERTAIN, List.of());
  }

  /**
   * The NOMINAL-erasure verdicts, present only for the invariants the query actually classifies in
   * that mode (Milestone 4.5 deliberately does not fabricate a refusal for invariants a query never
   * mentions).
   */
  public List<InvariantVerdict> nominalVerdicts() {
    return verdictsByMode.getOrDefault(TranslationMode.NOMINAL, List.of());
  }
}
