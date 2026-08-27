package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.SmtScript;

/**
 * The placement rules for predefined links, isolated from configuration reading and solving.
 *
 * <p>The load-bearing one is {@link #reflexiveTupleDirectionIsNotSymmetricAndMustNotBeTransposed}.
 * Every REFLEXIVE fixture in the corpus is where a transposed link grid hides: for {@code
 * GraphColoring}'s {@code Adjacent} both directions of each edge are listed, so the transpose of
 * the whole tuple set equals the set itself and any orientation bug is invisible. {@code
 * Genealogy}'s {@code Parenthood} is the counterexample -- {@code (gp,pa)} means gp is pa's PARENT
 * and its transpose is a different, wrong model -- so that is the shape pinned here.
 */
public class PredefinedLinkEncoderTest {

  @Test
  public void aPredefinedLinkIsAssertedTrueAtTheCellItsTupleNames() {
    SmtScript script = new SmtScript("QF_LIRA");
    ObjectSlots shelves = slots(script, "Shelf", 2, List.of("s1", "s2"));
    ObjectSlots crates = slots(script, "Crate", 2, List.of("c1", "c2"));
    AssociationLinks links = grid(script, "Holds", shelves, crates);

    PredefinedLinkEncoder.encode(
        script,
        new AssociationScope("Holds", 1, 1, List.of(List.of("s2", "c1"))),
        links,
        List.of("Shelf", "Crate"));

    assertTrue(rendered(script).contains("(assert Holds_1_0)"));
  }

  /**
   * The grid's axes are matched against the model's declared ends, not assumed to line up with
   * them. Here the grid is built the OTHER way round -- axis A is the Crate end -- so a correct
   * placement must transpose the tuple, and a positional one would look the row name up in the
   * wrong class.
   */
  @Test
  public void gridAxesAreResolvedAgainstTheDeclaredEndsRatherThanAssumed() {
    SmtScript script = new SmtScript("QF_LIRA");
    ObjectSlots crates = slots(script, "Crate", 2, List.of("c1", "c2"));
    ObjectSlots shelves = slots(script, "Shelf", 2, List.of("s1", "s2"));
    AssociationLinks links = grid(script, "Holds", crates, shelves);

    PredefinedLinkEncoder.encode(
        script,
        new AssociationScope("Holds", 1, 1, List.of(List.of("s2", "c1"))),
        links,
        List.of("Shelf", "Crate"));

    assertTrue(
        "the Crate end is axis A here, so (s2,c1) belongs at [c1][s2]",
        rendered(script).contains("(assert Holds_0_1)"));
  }

  /**
   * A reflexive association is the one shape class matching cannot arbitrate, so the tuple order
   * carries all the meaning: {@code (gp,pa)} must land at [gp][pa] and nowhere else.
   */
  @Test
  public void reflexiveTupleDirectionIsNotSymmetricAndMustNotBeTransposed() {
    SmtScript script = new SmtScript("QF_LIRA");
    ObjectSlots people = slots(script, "Person", 2, List.of("gp", "pa"));
    AssociationLinks links = grid(script, "Parenthood", people, people);

    PredefinedLinkEncoder.encode(
        script,
        new AssociationScope("Parenthood", 1, 1, List.of(List.of("gp", "pa"))),
        links,
        List.of("Person", "Person"));

    String rendered = rendered(script);
    assertTrue(
        "(gp,pa) must force the parent-of direction", rendered.contains("(assert Parenthood_0_1)"));
    assertEquals(
        "and must NOT force the reverse; a transposed grid would be invisible on a symmetric"
            + " fixture like GraphColoring's Adjacent",
        -1,
        rendered.indexOf("(assert Parenthood_1_0)"));
  }

  @Test
  public void anEndNamingNoSlotOfThatClassFailsClosed() {
    SmtScript script = new SmtScript("QF_LIRA");
    ObjectSlots shelves = slots(script, "Shelf", 1, List.of("s1"));
    ObjectSlots crates = slots(script, "Crate", 1, List.of("c1"));
    AssociationLinks links = grid(script, "Holds", shelves, crates);

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                PredefinedLinkEncoder.encode(
                    script,
                    new AssociationScope("Holds", 1, 1, List.of(List.of("c1", "s1"))),
                    links,
                    List.of("Shelf", "Crate")));

    assertTrue(thrown.getMessage().contains("c1"));
  }

  @Test
  public void anAssociationWithNoPredefinedLinksAssertsNothing() {
    SmtScript script = new SmtScript("QF_LIRA");
    ObjectSlots shelves = slots(script, "Shelf", 1, List.of());
    ObjectSlots crates = slots(script, "Crate", 1, List.of());
    AssociationLinks links = grid(script, "Holds", shelves, crates);
    String before = rendered(script);

    PredefinedLinkEncoder.encode(
        script, new AssociationScope("Holds", 0, 1), links, List.of("Shelf", "Crate"));

    assertEquals(
        "nothing may be added for a scenario that predefines no links", before, rendered(script));
  }

  private static ObjectSlots slots(
      SmtScript script, String className, int capacity, List<String> objectNames) {
    return ObjectSlotEncoder.encode(
            script, List.of(new ClassScope(className, 0, capacity, objectNames)))
        .get(className);
  }

  private static AssociationLinks grid(
      SmtScript script, String name, ObjectSlots aEnd, ObjectSlots bEnd) {
    return AssociationLinkEncoder.encode(
        script,
        name,
        aEnd,
        new Multiplicity(0, -1),
        bEnd,
        new Multiplicity(0, -1),
        new AssociationScope(name, 0, -1));
  }

  private static String rendered(SmtScript script) {
    return script.toSmtLib();
  }
}
