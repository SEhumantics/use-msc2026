package org.tzi.use.smt.finder;

import java.util.List;
import org.tzi.use.smt.encode.FragmentCoverageLedger;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.sys.MSystem;

/**
 * The outcome of one {@link SmtModelFinder#find} run: whether a satisfying instance exists, the
 * coverage ledger for the invariants the caller asked to enforce, the reconstructed system's
 * independent re-evaluation verdicts (empty when unsatisfiable), and the live system itself (null
 * when unsatisfiable -- there is nothing to reconstruct).
 */
public record ModelFinderResult(
    boolean satisfiable,
    FragmentCoverageLedger ledger,
    List<InvariantVerdict> verdicts,
    MSystem system) {

  /** True only for a satisfiable instance whose independently re-evaluated invariants all hold. */
  public boolean allActiveInvariantsHold() {
    return satisfiable && verdicts.stream().allMatch(InvariantVerdict::holds);
  }
}
