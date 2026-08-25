package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
}
