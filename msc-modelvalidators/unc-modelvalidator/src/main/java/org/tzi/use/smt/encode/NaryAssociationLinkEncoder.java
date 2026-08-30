package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtSort;
import org.tzi.use.smt.solver.SmtTerm;

/**
 * Encodes a guarded N-ary link grid (arity &ge; 3) with cross-wired end multiplicities -- the
 * n-ary generalization of {@link AssociationLinkEncoder}'s binary grid, in its own class so the
 * binary contract stays untouched. One Bool per TUPLE over the end views in declared end order,
 * guarded by every end's slot-exists; per (end, slot) a degree constraint over that end's fiber
 * with the end's own declared multiplicity ranges; the association-scope link-count bound over
 * all tuples unguarded -- each the direct analog of the binary encoder's rules, which are reused
 * (the helpers were widened from private to package scope, not duplicated).
 */
public final class NaryAssociationLinkEncoder {
  private NaryAssociationLinkEncoder() {}

  /**
   * @param endViews the folded slot view per end, in the association's DECLARED end order.
   * @param multiplicities end e's OWN declared multiplicity, as one {@link Multiplicity} per
   *     declared range ({@code 1,3..5} = two entries); bounds each slot's fiber sum within end e.
   */
  public static NaryAssociationLinks encode(
      SmtScript script,
      String associationName,
      List<ObjectSlots> endViews,
      List<List<Multiplicity>> multiplicities,
      AssociationScope aggregate) {
    int arity = endViews.size();
    int[] capacities = new int[arity];
    int tupleCount = 1;
    for (int e = 0; e < arity; e++) {
      capacities[e] = endViews.get(e).capacity();
      tupleCount *= capacities[e];
    }
    String[] names = new String[tupleCount];
    List<SmtTerm> all = new ArrayList<>();
    for (int flat = 0; flat < tupleCount; flat++) {
      String name = associationName + "_" + flat;
      script.declareConst(name, SmtSort.BOOL);
      names[flat] = name;
      SmtTerm link = Smt.sym(name);
      all.add(link);
      // The tuple can only be present when EVERY participating object exists -- the n-ary
      // generalization of the binary grid's two per-cell guards.
      int remainder = flat;
      for (int e = arity - 1; e >= 0; e--) {
        int index = remainder % capacities[e];
        remainder /= capacities[e];
        script.assertThat(
            Smt.app("=>", link, Smt.sym(endViews.get(e).existsNames().get(index))));
      }
    }
    // Per (end, slot) fiber degree: an existing slot's tuple count must land in the end's own
    // declared ranges (the multi-range disjunction, exactly the binary degree rule, guarded by
    // the slot's exists symbol for the same UML-instances-of-objects reason the binary encoder
    // documents at length).
    for (int e = 0; e < arity; e++) {
      for (int slot = 0; slot < capacities[e]; slot++) {
        List<SmtTerm> fiber = new ArrayList<>();
        for (int flat = 0; flat < tupleCount; flat++) {
          if (digitAt(flat, e, capacities) == slot) {
            fiber.add(Smt.sym(names[flat]));
          }
        }
        AssociationLinkEncoder.degree(
            script, fiber, Smt.sym(endViews.get(e).existsNames().get(slot)), multiplicities.get(e));
      }
    }
    // The association's own configured link-count bound is a bound on the link count ITSELF,
    // not a per-object UML multiplicity, so it stays unguarded (binary precedent).
    AssociationLinkEncoder.degree(script, all, new Multiplicity(aggregate.min(), aggregate.max()));
    return new NaryAssociationLinks(associationName, endViews, capacities, names);
  }

  /** The positional digit of a row-major flat index at end position {@code e}. */
  private static int digitAt(int flat, int e, int[] capacities) {
    int[] digits = new int[capacities.length];
    int remainder = flat;
    for (int d = capacities.length - 1; d >= 0; d--) {
      digits[d] = remainder % capacities[d];
      remainder /= capacities[d];
    }
    return digits[e];
  }
}
