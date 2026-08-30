package org.tzi.use.smt.encode;

import java.util.List;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;

/**
 * Forces the PREDEFINED LINKS of one association: every tuple the configuration wrote down becomes
 * an asserted-true cell of the link grid.
 *
 * <p>FORCED, not permitted, is the incumbent's semantics and not a choice made here. {@code
 * AssociationConfigurator.lowerBound} (kk-modelvalidator, lines 39-67) puts each listed tuple in
 * the relation's LOWER bound, so every solution contains it. Asserting the grid boolean is the
 * exact counterpart, and it needs no separate existence assertion: {@code AssociationLinkEncoder}
 * already asserts {@code link => exists} on both ends (lines 33-34), so forcing a link forces its
 * objects.
 *
 * <p>END ORDER IS RESOLVED FROM THE MODEL, NEVER FROM POSITION. A configured tuple is written in
 * the association's DECLARED end order -- that is the order {@code
 * PropertyConfigurationVisitor.readComplexElements} validates each element's class against (lines
 * 461-467). The grid's own axes are a separate fact: {@link AssociationLinks#aEnd()} and {@link
 * AssociationLinks#bEnd()} are whatever the encoder happened to put there, and Task 3.6 already
 * found once that the SMT grid's end order can differ from {@code associationEnds()}. So the
 * declared end classes are passed in and matched against the grid's own end classes here, in both
 * orientations, rather than assumed to line up. A translation that skipped this step and indexed
 * {@code linkNames[tuple.get(0)][tuple.get(1)]} would still pass every symmetric fixture (both
 * directions of every edge are listed, so the transpose is the same set) and silently invert an
 * asymmetric one.
 *
 * <p>The one case model matching cannot decide is a REFLEXIVE association, where both declared ends
 * have the same class and both grid axes are literally the same {@link ObjectSlots}. There the
 * identity orientation is the only one the grid's construction can mean -- axis A is the end whose
 * multiplicity bounds the OTHER axis's degree -- and it is what is used.
 */
public final class PredefinedLinkEncoder {
  private PredefinedLinkEncoder() {}

  /**
   * @param declaredEndClassNames the association's end classes in {@code associationEnds()} order,
   *     read off the model by the caller; the same order the configured tuples are written in.
   */
  public static void encode(
      SmtScript script,
      AssociationScope scope,
      AssociationLinks links,
      List<String> declaredEndClassNames) {
    if (scope.links().isEmpty()) {
      return;
    }
    boolean swapped = resolveOrientation(scope, links, declaredEndClassNames);
    for (List<String> tuple : scope.links()) {
      if (tuple.size() != 2) {
        // The n-ary slice relaxed the CONFIG READER to any N-tuple; the arity check moved here,
        // where the model is known: a binary association still refuses anything but a 2-tuple
        // (a 3-tuple on a binary association is the incumbent's association-class spelling).
        throw new SmtTranslationException(
            FragmentBoundary.TIER_3,
            "predefined link of '"
                + scope.associationName()
                + "' names "
                + tuple.size()
                + " objects but the association declares 2 ends");
      }
      String rowName = swapped ? tuple.get(1) : tuple.get(0);
      String columnName = swapped ? tuple.get(0) : tuple.get(1);
      int row = slotOf(scope, links.aEnd(), rowName);
      int column = slotOf(scope, links.bEnd(), columnName);
      script.assertThat(Smt.sym(links.linkNames()[row][column]));
    }
  }

  /**
   * False when grid axis A is declared end 0, true when the grid's axes are the other way round.
   */
  private static boolean resolveOrientation(
      AssociationScope scope, AssociationLinks links, List<String> declaredEndClassNames) {
    if (declaredEndClassNames.size() != 2) {
      throw new SmtTranslationException(
          FragmentBoundary.TIER_3,
          "predefined links need a binary association, but '"
              + scope.associationName()
              + "' declares "
              + declaredEndClassNames.size()
              + " ends");
    }
    String a = links.aEnd().className();
    String b = links.bEnd().className();
    if (declaredEndClassNames.get(0).equals(a) && declaredEndClassNames.get(1).equals(b)) {
      return false;
    }
    if (declaredEndClassNames.get(0).equals(b) && declaredEndClassNames.get(1).equals(a)) {
      return true;
    }
    throw new SmtTranslationException(
        FragmentBoundary.TIER_3,
        "predefined links for '"
            + scope.associationName()
            + "' cannot be placed: the link grid connects "
            + a
            + " and "
            + b
            + " but the association declares ends "
            + declaredEndClassNames);
  }

  /**
   * The n-ary counterpart (arity &ge; 3): {@link NaryAssociationLinks}' end views ARE in the
   * association's declared end order by construction (the caller builds them that way), so each
   * configured tuple position indexes its own end view directly -- no orientation resolution is
   * possible or needed. What stays is the arity check and the per-position object-name check.
   */
  public static void encode(SmtScript script, AssociationScope scope, NaryAssociationLinks links) {
    if (scope.links().isEmpty()) {
      return;
    }
    for (List<String> tuple : scope.links()) {
      if (tuple.size() != links.arity()) {
        throw new SmtTranslationException(
            FragmentBoundary.TIER_3,
            "predefined link of '"
                + scope.associationName()
                + "' names "
                + tuple.size()
                + " objects but the association declares "
                + links.arity()
                + " ends");
      }
      int[] indices = new int[tuple.size()];
      for (int e = 0; e < tuple.size(); e++) {
        indices[e] = slotOf(scope, links.endView(e), tuple.get(e));
      }
      script.assertThat(Smt.sym(links.linkName(indices)));
    }
  }

  private static int slotOf(AssociationScope scope, ObjectSlots slots, String objectName) {
    int slot = slots.slotOf(objectName);
    if (slot < 0 || slot >= slots.capacity()) {
      throw new SmtTranslationException(
          FragmentBoundary.TIER_3,
          "predefined link of '"
              + scope.associationName()
              + "' names '"
              + objectName
              + "', which is not a predefined object of "
              + slots.className());
    }
    return slot;
  }
}
