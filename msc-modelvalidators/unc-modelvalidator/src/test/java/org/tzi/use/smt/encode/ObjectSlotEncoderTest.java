package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.smt.solver.SolverResult;

public class ObjectSlotEncoderTest {
  @Test
  public void declaresExactlyMaxSlotsPerClass() {
    SmtScript script = new SmtScript("QF_LIA");
    Map<String, ObjectSlots> slots =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("User", 1, 3)));
    assertEquals(3, slots.get("User").slotNames().size());
    assertEquals(3, slots.get("User").existsNames().size());
  }

  @Test
  public void fixedPopulationForcesExactlyThatManySlotsToExist() {
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlotEncoder.encode(script, List.of(new ClassScope("User", 3, 3)));
    SolverResult result = solve(script);
    assertEquals(SolverOutcome.SAT, result.outcome());
    assertEquals(3, countTrue(result, "User_0_exists", "User_1_exists", "User_2_exists"));
  }

  @Test
  public void rangeScopeAllowsAnyCountWithinBoundsButNotBelowMin() {
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlotEncoder.encode(script, List.of(new ClassScope("Copy", 2, 4)));
    script.assertThat(
        org.tzi.use.smt.solver.Smt.app(
            "<=",
            sum(List.of("Copy_0_exists", "Copy_1_exists", "Copy_2_exists", "Copy_3_exists")),
            org.tzi.use.smt.solver.Smt.intLit(java.math.BigInteger.valueOf(1))));
    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  @Test
  public void symmetryBreakingForbidsASlotExistingWithoutItsPredecessor() {
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 0, 2)));
    script.assertThat(org.tzi.use.smt.solver.Smt.sym("Book_1_exists"));
    script.assertThat(
        org.tzi.use.smt.solver.Smt.not(org.tzi.use.smt.solver.Smt.sym("Book_0_exists")));
    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  @Test
  public void slotIdentityConstantsAreDistinctPerClassNotGloballyShared() {
    SmtScript script = new SmtScript("QF_LIA");
    Map<String, ObjectSlots> slots =
        ObjectSlotEncoder.encode(
            script, List.of(new ClassScope("User", 1, 1), new ClassScope("Copy", 1, 1)));
    assertTrue(slots.get("User").slotNames().get(0).contains("User"));
    assertTrue(slots.get("Copy").slotNames().get(0).contains("Copy"));
  }

  private static org.tzi.use.smt.solver.SmtTerm sum(List<String> names) {
    List<org.tzi.use.smt.solver.SmtTerm> terms =
        names.stream()
            .<org.tzi.use.smt.solver.SmtTerm>map(
                name ->
                    org.tzi.use.smt.solver.Smt.app(
                        "ite",
                        org.tzi.use.smt.solver.Smt.sym(name),
                        org.tzi.use.smt.solver.Smt.intLit(java.math.BigInteger.ONE),
                        org.tzi.use.smt.solver.Smt.intLit(java.math.BigInteger.ZERO)))
            .toList();
    org.tzi.use.smt.solver.SmtTerm total = terms.get(0);
    for (int i = 1; i < terms.size(); i++)
      total = org.tzi.use.smt.solver.Smt.app("+", total, terms.get(i));
    return total;
  }

  private static SolverResult solve(SmtScript script) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
  }

  private static long countTrue(SolverResult result, String... names) {
    var values = org.tzi.use.smt.solver.SmtModelParser.parse(result.modelText());
    return List.of(names).stream()
        .filter(
            name -> values.get(name) instanceof org.tzi.use.smt.solver.SmtValue.Bool b && b.value())
        .count();
  }
}
