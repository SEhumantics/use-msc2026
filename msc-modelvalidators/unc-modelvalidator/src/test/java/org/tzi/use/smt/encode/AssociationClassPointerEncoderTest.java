package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.smt.solver.SolverResult;

/**
 * Encoder-level checks for {@link AssociationClassPointerEncoder}, the index-pointer
 * representation an OCL association-class instance's link identity gets (see the class's own
 * javadoc for why: an association class is simultaneously its own object AND a binary
 * association, so a plain link-boolean grid has no way to also carry "and this IS which
 * association-class instance"). Mirrors {@link AssociationLinkEncoderTest}'s own
 * assert-real-Z3-outcome style, one property per test, rather than inspecting emitted SMT-LIB
 * text directly.
 */
public class AssociationClassPointerEncoderTest {

  @Test
  public void declaresOnePointerPerEndPerAssociationClassSlot() {
    SmtScript s = new SmtScript("QF_LIA");
    ObjectSlots employment = slots(s, "Employment", 2);
    ObjectSlots person = slots(s, "Person", 2);
    ObjectSlots company = slots(s, "Company", 2);
    AssociationClassPointerEncoder.PointerAttributes pointers =
        AssociationClassPointerEncoder.encode(
            s, employment, person, new Multiplicity(1, 1), company, new Multiplicity(0, -1));
    assertEquals(2, pointers.end0Pointer().valueNames().size());
    assertEquals(2, pointers.end1Pointer().valueNames().size());
  }

  @Test
  public void anExistingSlotMustPointAtAnExistingEnd0Object() {
    SmtScript s = new SmtScript("QF_LIA");
    ObjectSlots employment = slots(s, "Employment", 1, 0);
    ObjectSlots person = slots(s, "Person", 1, 0);
    ObjectSlots company = slots(s, "Company", 1);
    AssociationClassPointerEncoder.encode(
        s, employment, person, new Multiplicity(0, 1), company, new Multiplicity(0, -1));
    // Employment#0 exists, but Person#0 (the only structurally valid pointer target) does not --
    // the pointer's own 0..capacity-1 range guard alone would happily allow this; only the
    // existence guard rules it out.
    s.assertThat(Smt.sym(employment.existsNames().get(0)));
    s.assertThat(Smt.not(Smt.sym(person.existsNames().get(0))));
    assertEquals(SolverOutcome.UNSAT, solve(s).outcome());
  }

  @Test
  public void anExistingSlotPointingAtAnExistingEnd0ObjectIsSatisfiable() {
    SmtScript s = new SmtScript("QF_LIA");
    ObjectSlots employment = slots(s, "Employment", 1, 0);
    ObjectSlots person = slots(s, "Person", 1, 0);
    ObjectSlots company = slots(s, "Company", 1);
    AssociationClassPointerEncoder.encode(
        s, employment, person, new Multiplicity(0, 1), company, new Multiplicity(0, -1));
    s.assertThat(Smt.sym(employment.existsNames().get(0)));
    s.assertThat(Smt.sym(person.existsNames().get(0)));
    assertEquals(SolverOutcome.SAT, solve(s).outcome());
  }

  @Test
  public void end0DegreeBoundIsCrossWiredToEnd1sOwnMultiplicity() {
    // Person [0..*] employee -- Company [0..1] employer: an employee can navigate to at most
    // ONE employer, so the bound on how many Employment slots may share the SAME end0 (Person)
    // pointer value is END1's declared multiplicity (Company's [0..1]) -- exactly the
    // AssociationLinkEncoder linksPerB/linksPerA convention this encoder's own javadoc documents.
    // Forcing two Employment slots to point at the SAME Person violates that cross-wired bound.
    SmtScript s = new SmtScript("QF_LIA");
    ObjectSlots employment = slots(s, "Employment", 2, 0);
    ObjectSlots person = slots(s, "Person", 1, 0);
    ObjectSlots company = slots(s, "Company", 1, 0);
    AssociationClassPointerEncoder.PointerAttributes pointers =
        AssociationClassPointerEncoder.encode(
            s, employment, person, new Multiplicity(0, 1), company, new Multiplicity(0, -1));
    s.assertThat(Smt.sym(employment.existsNames().get(0)));
    s.assertThat(Smt.sym(employment.existsNames().get(1)));
    s.assertThat(Smt.sym(person.existsNames().get(0)));
    s.assertThat(Smt.sym(company.existsNames().get(0)));
    // Both Employment slots point at the same (sole) Person and the same (sole) Company.
    s.assertThat(
        Smt.eq(Smt.sym(pointers.end0Pointer().valueNames().get(0)), Smt.intLit(BigInteger.ZERO)));
    s.assertThat(
        Smt.eq(Smt.sym(pointers.end0Pointer().valueNames().get(1)), Smt.intLit(BigInteger.ZERO)));
    assertEquals(SolverOutcome.UNSAT, solve(s).outcome());
  }

  private static ObjectSlots slots(SmtScript s, String n, int count) {
    return slots(s, n, count, count);
  }

  private static ObjectSlots slots(SmtScript s, String n, int max, int min) {
    return ObjectSlotEncoder.encode(s, List.of(new ClassScope(n, min, max))).get(n);
  }

  private static SolverResult solve(SmtScript s) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(s.toSmtLib());
  }
}
