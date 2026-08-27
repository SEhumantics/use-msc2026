package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.mm.MClassInvariant;

/**
 * Attempts to assemble every given invariant, catching translation failures individually so ONE
 * unsupported invariant does not prevent reporting the status of the rest -- the point of a
 * coverage ledger is to see everything that failed, not just the first failure.
 */
public final class FragmentChecker {
  private FragmentChecker() {}

  public record Result(FragmentCoverageLedger ledger, Map<String, SmtTerm> assembled) {}

  public record ClassificationKey(String invariantName, TranslationMode mode) {}

  public record ReifiedResult(
      FragmentCoverageLedger ledger,
      Map<ClassificationKey, InvariantClassification> classifications) {}

  public static Result check(List<MClassInvariant> invariants, TranslationContext baseContext) {
    List<InvariantCoverage> coverage = new ArrayList<>();
    Map<String, SmtTerm> assembled = new LinkedHashMap<>();
    for (MClassInvariant invariant : invariants) {
      try {
        SmtTerm term = InvariantAssembler.assemble(invariant, baseContext);
        assembled.put(invariant.name(), term);
        coverage.add(new InvariantCoverage(invariant.name(), true, null));
      } catch (SmtTranslationException e) {
        coverage.add(new InvariantCoverage(invariant.name(), false, e.getMessage()));
      }
    }
    return new Result(new FragmentCoverageLedger(coverage), assembled);
  }

  /** Checks and reifies the exact invariant/mode pairs requested by a query. */
  public static ReifiedResult checkAndReify(
      List<MClassInvariant> invariants,
      Map<String, Set<TranslationMode>> requirements,
      TranslationContext baseContext,
      SmtScript script) {
    return checkAndReify(invariants, requirements, baseContext, script, "");
  }

  /**
   * The same check, reifying into one named SCENARIO COPY (see {@link InvariantAssembler#reify(
   * SmtScript, MClassInvariant, TranslationContext, TranslationMode, String)}). The ledger it
   * returns describes the same invariants in the same modes, so a query that is unsupported in one
   * scenario is unsupported in all of them -- the fragment is a property of the expression, not of
   * the measurement quality.
   */
  public static ReifiedResult checkAndReify(
      List<MClassInvariant> invariants,
      Map<String, Set<TranslationMode>> requirements,
      TranslationContext baseContext,
      SmtScript script,
      String scenarioSuffix) {
    List<InvariantCoverage> coverage = new ArrayList<>();
    Map<ClassificationKey, InvariantClassification> classifications = new LinkedHashMap<>();
    for (MClassInvariant invariant : invariants) {
      for (TranslationMode mode : requirements.getOrDefault(invariant.qualifiedName(), Set.of())) {
        try {
          InvariantClassification classification =
              InvariantAssembler.reify(script, invariant, baseContext, mode, scenarioSuffix);
          classifications.put(
              new ClassificationKey(invariant.qualifiedName(), mode), classification);
          coverage.add(new InvariantCoverage(invariant.qualifiedName(), mode, true, null));
        } catch (SmtTranslationException e) {
          coverage.add(
              new InvariantCoverage(invariant.qualifiedName(), mode, false, e.getMessage()));
        }
      }
    }
    return new ReifiedResult(new FragmentCoverageLedger(coverage), Map.copyOf(classifications));
  }
}
