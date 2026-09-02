package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.*;

/**
 * Encoder-level (raw {@link SmtScript}, direct solve) coverage for {@link
 * NaryAssociationLinkEncoder}'s per-end fiber-degree constraint, mirroring {@link
 * AssociationLinkEncoderTest}'s style one arity level up. {@code NaryAssociationTest} covers the
 * same discriminator through the full {@link org.tzi.use.smt.finder.SmtModelFinder#find} pipeline
 * (reconstruction + independent USE-evaluator re-verification); these tests pin the same
 * arithmetic directly against the encoder, fast and without a model compile.
 */
public class NaryAssociationLinkEncoderTest {

  @Test
  public void declaresOneLinkBooleanPerTuple() {
    SmtScript s = new SmtScript("QF_LIA");
    NaryAssociationLinks l =
        NaryAssociationLinkEncoder.encode(
            s,
            "Tern",
            List.of(slots(s, "A", 2), slots(s, "B", 1), slots(s, "C", 2)),
            List.of(unbounded(), unbounded(), unbounded()),
            new AssociationScope("Tern", 0, -1));
    assertEquals(4, l.tupleCount());
    assertEquals(4, l.linkNames().length);
  }

  /**
   * THE DISCRIMINATOR, at the encoder level: two tuples sharing the same (A,B) pair but landing
   * in two different C slots -- {@code (a0,b0,c0)} and {@code (a0,b0,c1)} -- must be UNSAT under
   * C's {@code 0..1}, because UML/OCL n-ary multiplicity binds C's count per FIXED (A,B)
   * combination, and the single combination here, {@code (a0,b0)}, has two C's linked to it. A
   * and B stay unbounded so neither of THEIR degree constraints can produce an UNSAT unrelated to
   * the discriminator.
   */
  @Test
  public void sharedOtherEndsCombinationWithTwoLinkedCSlotsViolatesCsUpperBound() {
    SmtScript s = new SmtScript("QF_LIA");
    NaryAssociationLinks l =
        NaryAssociationLinkEncoder.encode(
            s,
            "Tern",
            List.of(slots(s, "A", 2), slots(s, "B", 1), slots(s, "C", 2)),
            List.of(unbounded(), unbounded(), List.of(new Multiplicity(0, 1))),
            new AssociationScope("Tern", 2, 2));
    s.assertThat(Smt.sym(l.linkName(0, 0, 0)));
    s.assertThat(Smt.sym(l.linkName(0, 0, 1)));
    assertEquals(SolverOutcome.UNSAT, solve(s).outcome());
  }

  /**
   * The mirror SAT case: the SAME two tuples, but now through two DIFFERENT (A,B) combinations --
   * {@code (a0,b0,c0)} and {@code (a1,b0,c0)} both land in C slot 0, yet each is its own (A,B)
   * combination, so C's {@code 0..1} is checked separately for each and both are satisfied (one
   * linked C apiece). Confirms the fix does not just refuse everything through C's degree.
   */
  @Test
  public void distinctOtherEndsCombinationsEachWithinCsBoundAreSat() {
    SmtScript s = new SmtScript("QF_LIA");
    NaryAssociationLinks l =
        NaryAssociationLinkEncoder.encode(
            s,
            "Tern",
            List.of(slots(s, "A", 2), slots(s, "B", 1), slots(s, "C", 2)),
            List.of(unbounded(), unbounded(), List.of(new Multiplicity(0, 1))),
            new AssociationScope("Tern", 2, 2));
    s.assertThat(Smt.sym(l.linkName(0, 0, 0)));
    s.assertThat(Smt.sym(l.linkName(1, 0, 0)));
    assertEquals(SolverOutcome.SAT, solve(s).outcome());
  }

  /**
   * The 256-combination convention (see {@code UBooleanProbability#MAX_CASES}, {@code
   * ScenarioSpace#MAX_SCENARIOS}), applied to the per-end fiber-degree constraint count: end A's
   * capacity-1 axis needs one constraint per combination of B and C's slots -- 17*17=289,
   * over the cap -- so this must refuse with a LOCATED exception rather than build 289 constraints.
   */
  @Test
  public void perEndFiberCombinationCountExceedingTheCapIsRefused() {
    SmtScript s = new SmtScript("QF_LIA");
    List<ObjectSlots> endViews = List.of(slots(s, "A", 1), slots(s, "B", 17), slots(s, "C", 17));
    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                NaryAssociationLinkEncoder.encode(
                    s,
                    "Big",
                    endViews,
                    List.of(unbounded(), unbounded(), unbounded()),
                    new AssociationScope("Big", 0, -1)));
    assertEquals(FragmentBoundary.TIER_3, thrown.boundary());
    assertTrue(
        "refusal must name the actual combination count",
        thrown.getMessage().contains("289"));
  }

  private static List<Multiplicity> unbounded() {
    return List.of(new Multiplicity(0, -1));
  }

  private static ObjectSlots slots(SmtScript s, String n, int count) {
    Map<String, ObjectSlots> encoded =
        ObjectSlotEncoder.encode(s, List.of(new ClassScope(n, count, count)));
    return encoded.get(n);
  }

  private static SolverResult solve(SmtScript s) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(s.toSmtLib());
  }
}
