package org.tzi.use.smt.encode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtTerm;

/**
 * Encodes an ASSOCIATION CLASS's link identity as a pair of per-slot INDEX POINTERS, rather than
 * the ordinary N-by-M link-boolean grid {@link AssociationLinkEncoder} builds for a plain
 * association. An association-class instance has its own object identity (and its own attributes,
 * encoded separately by the ordinary {@link AttributeEncoder} path -- {@code MAssociationClass
 * extends MClass} too) distinct from either end's class, so a boolean grid cell -- which only ever
 * answers "is THIS end-object linked to THAT end-object" -- has no way to also carry "and this IS
 * WHICH association-class instance". Each association-class slot instead gets two Int-sorted
 * pointer symbols, one per end, existence-guarded to a valid candidate slot of that end's class --
 * reusing {@link AttributeEncoder}'s own bounded-Integer range guard for the declaration itself, so
 * the only genuinely new piece here is the per-end-object DEGREE constraint (how many
 * association-class instances may point at the SAME end object), which has no grid-cell
 * counterpart to reuse from {@link AssociationLinkEncoder#degree}.
 */
public final class AssociationClassPointerEncoder {
  private AssociationClassPointerEncoder() {}

  public static final String END0_POINTER_ATTRIBUTE = "$assocClassEnd0";
  public static final String END1_POINTER_ATTRIBUTE = "$assocClassEnd1";

  public record PointerAttributes(AttributeValues end0Pointer, AttributeValues end1Pointer) {}

  /**
   * @param sharedByAtMostPerEnd0Value the multiplicity BOUNDING how many association-class
   *     instances may share the SAME end0 pointer value -- i.e. end1's OWN declared multiplicity,
   *     the same cross-wiring {@link AssociationLinkEncoder#encode}'s own {@code linksPerB}/{@code
   *     linksPerA} parameters already use (end1's role label is what an end0 object navigates
   *     through to reach end1, so end1's declared multiplicity bounds the count PER end0 object).
   * @param sharedByAtMostPerEnd1Value the mirror bound for end1 (end0's own declared multiplicity).
   */
  public static PointerAttributes encode(
      SmtScript script,
      ObjectSlots associationClassSlots,
      ObjectSlots end0Slots,
      Multiplicity sharedByAtMostPerEnd0Value,
      ObjectSlots end1Slots,
      Multiplicity sharedByAtMostPerEnd1Value) {
    AttributeValues end0Pointer =
        declarePointer(script, associationClassSlots, END0_POINTER_ATTRIBUTE, end0Slots.capacity());
    AttributeValues end1Pointer =
        declarePointer(script, associationClassSlots, END1_POINTER_ATTRIBUTE, end1Slots.capacity());

    requirePointsToAnExistingObject(script, associationClassSlots, end0Pointer, end0Slots);
    requirePointsToAnExistingObject(script, associationClassSlots, end1Pointer, end1Slots);

    degreeByPointerValue(
        script, associationClassSlots, end0Pointer, end0Slots.capacity(), sharedByAtMostPerEnd0Value);
    degreeByPointerValue(
        script, associationClassSlots, end1Pointer, end1Slots.capacity(), sharedByAtMostPerEnd1Value);

    return new PointerAttributes(end0Pointer, end1Pointer);
  }

  private static AttributeValues declarePointer(
      SmtScript script, ObjectSlots associationClassSlots, String attributeName, int targetCapacity) {
    AttributeDomain domain =
        new AttributeDomain(
            associationClassSlots.className(),
            attributeName,
            null,
            List.of(),
            BigDecimal.ZERO,
            BigDecimal.valueOf(Math.max(targetCapacity - 1, 0)));
    return AttributeEncoder.encode(
        script, associationClassSlots, attributeName, AttributeType.INTEGER, domain);
  }

  /**
   * The pointer's own range guard ({@link #declarePointer}) only constrains its value to a
   * STRUCTURALLY valid index (0..capacity-1) -- it says nothing about whether the candidate slot
   * AT that index actually exists. Without this, a solver could satisfy an association-class
   * instance's existence by pointing at a well-formed but non-existent candidate slot, which
   * {@code EmployeeAndEmployerAlwaysLinked}-shaped invariants (a link's two ends are ALWAYS bound,
   * by construction, per {@code ExpNavigationClassifierSource#eval}'s own "a link is always
   * connected to objects" comment, use-core) would then read as a real, existing link to an object
   * that never actually appears in the reconstructed witness -- a genuine soundness gap, not
   * merely an incompleteness. Guarded by the association-class slot's OWN existence (an
   * association-class instance that does not exist itself constrains nothing about its unused
   * pointer values), matching every other existence guard in this codebase.
   */
  private static void requirePointsToAnExistingObject(
      SmtScript script,
      ObjectSlots associationClassSlots,
      AttributeValues pointer,
      ObjectSlots targetSlots) {
    for (int k = 0; k < associationClassSlots.capacity(); k++) {
      SmtTerm exists = Smt.sym(associationClassSlots.existsNames().get(k));
      List<SmtTerm> pointsToExistingTarget = new ArrayList<>();
      for (int target = 0; target < targetSlots.capacity(); target++) {
        SmtTerm pointsHere =
            Smt.eq(Smt.sym(pointer.valueNames().get(k)), Smt.intLit(BigInteger.valueOf(target)));
        SmtTerm targetExists = Smt.sym(targetSlots.existsNames().get(target));
        pointsToExistingTarget.add(Smt.and(List.of(pointsHere, targetExists)));
      }
      script.assertThat(Smt.app("=>", exists, Smt.or(pointsToExistingTarget)));
    }
  }

  /**
   * For every candidate slot {@code target} of the POINTED-AT class, counts how many EXISTING
   * association-class slots have {@code pointer == target} and bounds that count by {@code bound}
   * -- the index-pointer analogue of {@link AssociationLinkEncoder#degree}'s per-row/per-column
   * link-count bound, unconditional the same way (no existence guard on {@code target} itself,
   * matching {@code degree}'s own convention exactly: a non-existent target's count is already
   * structurally forced to 0 by every pointer's own existence-guarded range, per {@link
   * #declarePointer}, so a nonzero lower bound would already fail closed for it rather than being
   * silently satisfied).
   */
  private static void degreeByPointerValue(
      SmtScript script,
      ObjectSlots associationClassSlots,
      AttributeValues pointer,
      int targetCapacity,
      Multiplicity bound) {
    if (bound.lower() == 0 && bound.isUnbounded()) {
      return;
    }
    for (int target = 0; target < targetCapacity; target++) {
      List<SmtTerm> matches = new ArrayList<>();
      for (int k = 0; k < associationClassSlots.capacity(); k++) {
        SmtTerm exists = Smt.sym(associationClassSlots.existsNames().get(k));
        SmtTerm pointsHere =
            Smt.eq(Smt.sym(pointer.valueNames().get(k)), Smt.intLit(BigInteger.valueOf(target)));
        matches.add(Smt.and(List.of(exists, pointsHere)));
      }
      SmtTerm count = sum(matches);
      if (bound.lower() > 0) {
        script.assertThat(Smt.app(">=", count, Smt.intLit(BigInteger.valueOf(bound.lower()))));
      }
      if (!bound.isUnbounded()) {
        script.assertThat(Smt.app("<=", count, Smt.intLit(BigInteger.valueOf(bound.upper()))));
      }
    }
  }

  private static SmtTerm sum(List<SmtTerm> terms) {
    if (terms.isEmpty()) {
      return Smt.intLit(BigInteger.ZERO);
    }
    SmtTerm total =
        Smt.ite(terms.getFirst(), Smt.intLit(BigInteger.ONE), Smt.intLit(BigInteger.ZERO));
    for (int i = 1; i < terms.size(); i++) {
      total =
          Smt.app(
              "+",
              total,
              Smt.ite(terms.get(i), Smt.intLit(BigInteger.ONE), Smt.intLit(BigInteger.ZERO)));
    }
    return total;
  }
}
