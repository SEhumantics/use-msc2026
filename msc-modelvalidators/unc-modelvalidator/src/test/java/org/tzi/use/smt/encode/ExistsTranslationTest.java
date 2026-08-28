package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Parser-backed regression coverage for {@code X.allInstances()->exists(v | body)} -- the
 * SINGLE-variable shape {@code visitExists} refused unconditionally before it was widened, reusing
 * {@code visitForAll}'s own {@code populationOf}/{@code tuplesOf} machinery instead of the
 * navigation-only, always-two-variable cross product it was originally built for (Library's
 * {@code noDoubleBorrowings}, still covered separately by {@code NavigationTranslationTest}, whose
 * SMT-LIB output this refactor keeps byte-identical). Real corpus motivation: ZebraPuzzle's
 * {@code Clue06_GreenRightOfIvory} ({@code House.allInstances->exists(h2 | h2.color=#Green and
 * h2.position=self.position+1)}) and three sibling clues -- all single-variable {@code exists}
 * over {@code allInstances}, none a navigation at all.
 */
public class ExistsTranslationTest {

  @Test
  public void oneVariableExistsOverAllInstancesIsSatisfiableWhenAMatchExists() throws Exception {
    assertEquals(SolverOutcome.SAT, solveSomeBig(15));
  }

  @Test
  public void oneVariableExistsOverAllInstancesIsUnsatisfiableWhenForcedAndNoMatchExists()
      throws Exception {
    // val=5 for the only Item: no candidate exceeds 10, so the invariant (forced active) is
    // genuinely unsatisfiable -- not vacuously true from an empty population.
    assertEquals(SolverOutcome.UNSAT, solveSomeBig(5));
  }

  private static SolverOutcome solveSomeBig(int value) throws Exception {
    String modelSource =
        """
        model ExistsScope
        class Item
        attributes
          val : Integer
        end
        constraints
        context i : Item inv someBig:
          Item.allInstances->exists(x | x.val > 10)
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(modelSource, "ExistsScope", err, new ModelFactory());
    err.flush();
    MClassInvariant inv = findInvariant(model, "someBig");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots items =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Item", 1, 1))).get("Item");
    AttributeDomain valDomain = new AttributeDomain("Item", "val", null, List.of(), null, null);
    AttributeValues val =
        AttributeEncoder.encode(script, items, "val", AttributeType.INTEGER, valDomain);
    TranslationContext ctx =
        new TranslationContext(
            Map.of(),
            Map.of("Item.val", val),
            Map.of("Item.val", valDomain),
            Map.of("Item", items),
            Map.of());
    var translated =
        ExpressionTranslator.translate(inv.bodyExpression(), ctx, TranslationMode.UNCERTAIN);

    script.assertThat(Smt.sym("Item_0_exists"));
    script.assertThat(Smt.eq(Smt.sym(val.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(value))));
    script.assertThat(translated.trueTerm());

    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
        .run(script.toSmtLib())
        .outcome();
  }

  /**
   * {@code source.role->exists(v | body)} -- the single-variable form of the SAME
   * collection-valued-navigation range {@code NavigationTranslationTest}'s two-variable tests
   * already cover, confirming {@code populationOf}'s navigation branch composes correctly with
   * {@code visitExists}'s new single-variable path too, not just its allInstances one.
   */
  @Test
  public void oneVariableExistsOverACollectionValuedNavigation() throws Exception {
    String modelSource =
        """
        model DeptExistsScope
        class Department
        end
        class Employee
        attributes
          salary : Integer
        end
        association Employs between
          Department[1] role dept
          Employee[*] role staff
        end
        constraints
        context d : Department inv someHighEarner:
          d.staff->exists(e | e.salary > 100)
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(modelSource, "DeptExistsScope", err, new ModelFactory());
    err.flush();
    MClassInvariant inv = findInvariant(model, "someHighEarner");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots depts =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Department", 1, 1)))
            .get("Department");
    ObjectSlots employees =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Employee", 2, 2)))
            .get("Employee");
    AttributeDomain salaryDomain =
        new AttributeDomain("Employee", "salary", null, List.of(), null, null);
    AttributeValues salary =
        AttributeEncoder.encode(script, employees, "salary", AttributeType.INTEGER, salaryDomain);
    AssociationLinks employs =
        AssociationLinkEncoder.encode(
            script,
            "Employs",
            depts,
            new Multiplicity(1, 1),
            employees,
            new Multiplicity(0, -1),
            new org.tzi.use.smt.config.AssociationScope("Employs", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("d", new VariableBinding("Department", 0)),
            Map.of("Employee.salary", salary),
            Map.of("Employee.salary", salaryDomain),
            Map.of("Department", depts, "Employee", employees),
            Map.of("Employs", employs));
    var translated =
        ExpressionTranslator.translate(inv.bodyExpression(), ctx, TranslationMode.UNCERTAIN);

    script.assertThat(Smt.sym("Department_0_exists"));
    script.assertThat(Smt.sym("Employee_0_exists"));
    script.assertThat(Smt.sym("Employee_1_exists"));
    script.assertThat(Smt.sym(employs.linkNames()[0][0]));
    script.assertThat(Smt.sym(employs.linkNames()[0][1]));
    script.assertThat(
        Smt.eq(Smt.sym(salary.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(150))));
    script.assertThat(
        Smt.eq(Smt.sym(salary.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(50))));
    script.assertThat(translated.trueTerm());

    SolverOutcome outcome =
        new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
            .run(script.toSmtLib())
            .outcome();
    assertEquals(SolverOutcome.SAT, outcome);
  }

  private static MClassInvariant findInvariant(MModel model, String name) {
    for (MClassInvariant invariant : model.classInvariants()) {
      if (invariant.name().equals(name)) {
        return invariant;
      }
    }
    throw new IllegalStateException("invariant not found: " + name);
  }
}
