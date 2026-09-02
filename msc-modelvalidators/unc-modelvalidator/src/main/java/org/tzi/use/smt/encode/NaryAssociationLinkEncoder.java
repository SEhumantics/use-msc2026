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
 * guarded by every end's slot-exists; the association-scope link-count bound over all tuples
 * unguarded -- each the direct analog of the binary encoder's rules, which are reused (the
 * helpers were widened from private to package scope, not duplicated).
 *
 * <p><b>The per-end degree constraint.</b> UML/OCL n-ary multiplicity binds end e's count per
 * FIXED combination of the OTHER {@code arity - 1} ends' objects -- exactly {@link
 * AssociationLinkEncoder}'s row/column split, generalized from "the other 1 end's slot" to "every
 * combination of the other {@code arity - 1} ends' slots". For each end e and each such
 * combination, the tuple-booleans that match it across all of e's OWN slots are summed and bounded
 * by e's declared multiplicity, guarded by that combination's own existence (the AND of every
 * participating other-end slot's exists symbol -- the n-ary analog of the binary rule's single
 * exists guard). A single aggregate sum per (end, slot) -- summing across every combination of the
 * other ends' slots INSTEAD of once per combination -- was a confirmed soundness bug: it let
 * multiple combinations share one end's slot without individually respecting that end's
 * multiplicity (e.g. a ternary Tern(A, B, C) with C{@code [0..1]} wrongly admitted both
 * {@code (a0,b0,c0)} and {@code (a0,b0,c1)} at once, since the two tuples land in different C
 * slots and the old aggregate never checked the shared {@code (a0,b0)} combination on its own).
 */
public final class NaryAssociationLinkEncoder {
  private NaryAssociationLinkEncoder() {}

  /**
   * The project's established 256-combination convention (see {@code
   * UBooleanProbability#MAX_CASES}, {@code ScenarioSpace#MAX_SCENARIOS},
   * {@code ExpressionTranslator}'s several 256-combination checks), applied to the per-end degree
   * constraint count. Fixing every combination of the OTHER {@code arity - 1} ends' slots turns
   * the per-end constraint count from O(capacity_e) into O(product of the OTHER ends'
   * capacities) -- for a high-arity or high-capacity association that product can be enormous, so
   * it is capped here rather than left to build an unbounded script.
   */
  static final int MAX_FIBER_COMBINATIONS = 256;

  /**
   * @param endViews the folded slot view per end, in the association's DECLARED end order.
   * @param multiplicities end e's OWN declared multiplicity, as one {@link Multiplicity} per
   *     declared range ({@code 1,3..5} = two entries); bounds e's own-axis fiber sum for EACH
   *     fixed combination of the other ends' slots, not one aggregate sum for all of them.
   */
  public static NaryAssociationLinks encode(
      SmtScript script,
      String associationName,
      List<ObjectSlots> endViews,
      List<List<Multiplicity>> multiplicities,
      AssociationScope aggregate) {
    int arity = endViews.size();
    int[] capacities = new int[arity];
    for (int e = 0; e < arity; e++) {
      capacities[e] = endViews.get(e).capacity();
    }
    // Row-major strides, the same place-value scheme NaryAssociationLinks#linkName decodes:
    // strides[e] is the product of every LATER end's capacity, so flat = sum_e digit[e]*strides[e]
    // and the last end varies fastest -- computed right-to-left alongside tupleCount itself.
    int[] strides = new int[arity];
    int tupleCount = 1;
    for (int e = arity - 1; e >= 0; e--) {
      strides[e] = tupleCount;
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
    // Per (end e, FIXED combination of the other arity-1 ends' slots) fiber degree: an existing
    // combination's count along e's own axis must land in e's own declared ranges (the
    // multi-range disjunction, exactly the binary degree rule) -- one constraint per point in the
    // (arity-1)-dimensional space of the OTHER ends' slot combinations, guarded by that
    // combination's own existence (see the class comment for why an aggregate sum across every
    // combination, as this used to compute, is unsound).
    for (int e = 0; e < arity; e++) {
      int otherCombinations = tupleCount / capacities[e];
      if (otherCombinations > MAX_FIBER_COMBINATIONS) {
        throw new SmtTranslationException(
            FragmentBoundary.TIER_3,
            "n-ary association '"
                + associationName
                + "' end '"
                + endViews.get(e).className()
                + "' (declared position "
                + e
                + ") needs one fiber-degree constraint per combination of its "
                + (arity - 1)
                + " other end(s)' slots -- "
                + otherCombinations
                + " combinations, exceeding the project's "
                + MAX_FIBER_COMBINATIONS
                + "-combination convention; refusing rather than building an enormous script");
      }
      for (int combo = 0; combo < otherCombinations; combo++) {
        // Mixed-radix decode of `combo` over the OTHER ends' capacities, most-slowly-varying end
        // first -- the same style the tuple-declaration loop above uses for ALL ends, restricted
        // here to every end except e. Reconstructs that combination's contribution to the flat
        // tuple index (via each other end's own stride) and its existence guard in one pass.
        int remainder = combo;
        int base = 0;
        List<SmtTerm> guards = new ArrayList<>(arity - 1);
        for (int k = arity - 1; k >= 0; k--) {
          if (k == e) {
            continue;
          }
          int otherIndex = remainder % capacities[k];
          remainder /= capacities[k];
          base += otherIndex * strides[k];
          guards.add(Smt.sym(endViews.get(k).existsNames().get(otherIndex)));
        }
        List<SmtTerm> fiber = new ArrayList<>(capacities[e]);
        for (int slot = 0; slot < capacities[e]; slot++) {
          fiber.add(Smt.sym(names[base + slot * strides[e]]));
        }
        AssociationLinkEncoder.degree(script, fiber, Smt.and(guards), multiplicities.get(e));
      }
    }
    // The association's own configured link-count bound is a bound on the link count ITSELF,
    // not a per-object UML multiplicity, so it stays unguarded (binary precedent).
    AssociationLinkEncoder.degree(script, all, new Multiplicity(aggregate.min(), aggregate.max()));
    return new NaryAssociationLinks(associationName, endViews, capacities, names);
  }
}
