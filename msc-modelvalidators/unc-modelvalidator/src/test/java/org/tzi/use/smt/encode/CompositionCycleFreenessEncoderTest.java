package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.smt.solver.SolverResult;

/**
 * Encoder-level checks for {@link CompositionCycleFreenessEncoder}, isolating the two properties
 * its own javadoc claims: combining TWO different associations' edges into one graph before
 * checking reachability (a single association's own grid is checked independently elsewhere and
 * would miss a cycle that only closes across both), and not over-constraining a genuinely acyclic
 * scenario. Mirrors {@link AssociationLinkEncoderTest}'s own assert-real-Z3-outcome style.
 */
public class CompositionCycleFreenessEncoderTest {

  @Test
  public void aTwoHopCycleAcrossTwoDifferentAssociationsIsRejected() {
    // Mirrors FileSystem.use's real PrimaryContains/AltContains shape directly, over two raw
    // slots (0 and 1, no named objects needed): slot 1's primaryParent is slot 0 (via
    // PrimaryContains), slot 0's altParent is slot 1 (via AltContains) -- a 2-hop cycle (0->1->0)
    // closing ACROSS the two associations, which neither association's own grid contains a
    // self-loop in on its own.
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots folders =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Folder", 2, 2))).get("Folder");
    AssociationLinks primaryContains =
        AssociationLinkEncoder.encode(
            script,
            "PrimaryContains",
            folders,
            new Multiplicity(0, 1),
            folders,
            new Multiplicity(0, -1),
            new AssociationScope("PrimaryContains", 0, -1));
    AssociationLinks altContains =
        AssociationLinkEncoder.encode(
            script,
            "AltContains",
            folders,
            new Multiplicity(0, 1),
            folders,
            new Multiplicity(0, -1),
            new AssociationScope("AltContains", 0, -1));
    // slot 1's whole (end0) is slot 0, via PrimaryContains: linkNames[whole=0][part=1].
    script.assertThat(Smt.sym(primaryContains.linkNames()[0][1]));
    // slot 0's whole (end0) is slot 1, via AltContains: linkNames[whole=1][part=0].
    script.assertThat(Smt.sym(altContains.linkNames()[1][0]));

    CompositionCycleFreenessEncoder.assertAcyclic(
        script, "|test-cycle-", folders, List.of(primaryContains, altContains));

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  @Test
  public void theSameForcedLinksAreSatisfiableWithoutTheAcyclicityAssertion() {
    // The same forced links as above, WITHOUT calling assertAcyclic -- confirms the UNSAT above
    // is genuinely caused by the acyclicity constraint, not some other unrelated contradiction in
    // the forced links themselves.
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots folders =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Folder", 2, 2))).get("Folder");
    AssociationLinks primaryContains =
        AssociationLinkEncoder.encode(
            script,
            "PrimaryContains",
            folders,
            new Multiplicity(0, 1),
            folders,
            new Multiplicity(0, -1),
            new AssociationScope("PrimaryContains", 0, -1));
    AssociationLinks altContains =
        AssociationLinkEncoder.encode(
            script,
            "AltContains",
            folders,
            new Multiplicity(0, 1),
            folders,
            new Multiplicity(0, -1),
            new AssociationScope("AltContains", 0, -1));
    script.assertThat(Smt.sym(primaryContains.linkNames()[0][1]));
    script.assertThat(Smt.sym(altContains.linkNames()[1][0]));

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  @Test
  public void aGenuinelyAcyclicForcedTreeStaysSatisfiableUnderTheSameAssertion() {
    // slot 1's whole is slot 0 (a plain tree, no cycle) via ONE association only -- the
    // acyclicity assertion must not reject a genuinely acyclic scenario (no false positives).
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots folders =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Folder", 2, 2))).get("Folder");
    AssociationLinks primaryContains =
        AssociationLinkEncoder.encode(
            script,
            "PrimaryContains",
            folders,
            new Multiplicity(0, 1),
            folders,
            new Multiplicity(0, -1),
            new AssociationScope("PrimaryContains", 0, -1));
    script.assertThat(Smt.sym(primaryContains.linkNames()[0][1]));

    CompositionCycleFreenessEncoder.assertAcyclic(
        script, "|test-tree-", folders, List.of(primaryContains));

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  @Test
  public void aDirectSelfLoopOnOneAssociationAloneIsAlsoRejected() {
    // The length-1 case (slot 0 is its own whole) via a SINGLE association -- the degenerate case
    // the real corpus's own notOwnPrimaryParent invariant checks directly in OCL, confirmed here
    // at the encoder level too since assertAcyclic must catch it regardless of hop count.
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots folders =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Folder", 1, 1))).get("Folder");
    AssociationLinks primaryContains =
        AssociationLinkEncoder.encode(
            script,
            "PrimaryContains",
            folders,
            new Multiplicity(0, 1),
            folders,
            new Multiplicity(0, -1),
            new AssociationScope("PrimaryContains", 0, -1));
    script.assertThat(Smt.sym(primaryContains.linkNames()[0][0]));

    CompositionCycleFreenessEncoder.assertAcyclic(
        script, "|test-selfloop-", folders, List.of(primaryContains));

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  private static SolverResult solve(SmtScript script) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
  }
}
