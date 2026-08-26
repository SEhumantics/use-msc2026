package org.tzi.use.smt.verify;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.smt.config.InvariantOutcome;

/**
 * Holds a delivered witness to the classification its query claimed for it, using ONLY the
 * independent USE-evaluator verdicts -- never the solver's own {@code def}/{@code val} assignment.
 * SATISFY claims every active invariant is true; {@code counterexample(j)} additionally claims
 * {@code j} is DEFINED-false while every other active invariant is true, which is exactly what
 * makes the report an attribution rather than "something, somewhere, failed".
 */
public final class QueryWitnessChecker {
  private QueryWitnessChecker() {}

  public static void requireExpectedOutcomes(
      Map<String, InvariantOutcome> expectedOutcomes, List<InvariantVerdict> verdicts) {
    Map<String, InvariantOutcome> observed = new LinkedHashMap<>();
    for (InvariantVerdict verdict : verdicts) {
      observed.put(verdict.invariantName(), verdict.outcome());
    }
    List<String> mismatches = new ArrayList<>();
    expectedOutcomes.forEach(
        (invariantName, expected) -> {
          InvariantOutcome actual = observed.get(invariantName);
          if (actual != expected) {
            mismatches.add(invariantName + " expected " + expected + " but USE reported " + actual);
          }
        });
    if (!mismatches.isEmpty()) {
      throw new WitnessAttributionException(
          "the reconstructed witness does not match the classification its query claimed"
              + " (a translation or numerical error, not a finding): "
              + String.join("; ", mismatches));
    }
  }
}
