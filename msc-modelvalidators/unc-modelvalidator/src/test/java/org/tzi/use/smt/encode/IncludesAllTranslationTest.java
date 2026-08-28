package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.*;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * {@code X->includesAll(Y)} over collection-valued navigations reaching the same destination
 * class -- found while sweeping unc-modelvalidator against USE's own bundled example models (not
 * our own curated benchmark corpus): {@code self.department.employee->includesAll(self.employee)}
 * appears, verbatim or near-verbatim, in three of them ({@code Documentation/Demo/Demo.use},
 * {@code Others/Ex/ex.use}, {@code Others/Project/Project.use}). That exact shape's OUTER
 * navigation ({@code self.department.employee}) is itself two hops -- originally a separate,
 * unsupported limitation, closed once {@link ExpressionTranslator#populationOf} gained the general
 * multi-hop form ({@link ExpressionTranslator#navigationHop}); {@link
 * #includesAllOverAMultiHopNavigationDiscriminatesOnARealCorpusShape} now exercises the real
 * corpus shape directly, matching {@link ExpressionTranslator#collectionIncludesAll}'s own
 * (now-widened) scope precisely.
 */
public class IncludesAllTranslationTest {

  @Test
  public void includesAllIsSatWhenEveryDirectEmployeeIsAlsoADeptEmployee() throws Exception {
    assertEquals(SolverOutcome.SAT, solveWithLinks("AllDirectAreDeptEmployees", true));
  }

  @Test
  public void includesAllIsUnsatWhenADirectEmployeeIsNotADeptEmployee() throws Exception {
    assertEquals(SolverOutcome.UNSAT, solveWithLinks("AllDirectAreDeptEmployees", false));
  }

  /**
   * An empty argument population makes {@code includesAll} vacuously true, matching OCL's own
   * semantics -- confirmed by forcing the RHS (direct employees) to none exist at all while the
   * LHS is also forced empty (so the invariant genuinely gets exercised on a real, non-degenerate
   * population elsewhere in the same solve, not just an all-empty coincidence).
   */
  @Test
  public void includesAllIsVacuouslyTrueWhenTheArgumentPopulationIsEmpty() throws Exception {
    MModel model = compileScope();
    MClassInvariant inv = findInvariant(model, "AllDirectAreDeptEmployees");

    SmtScript script = new SmtScript("QF_LIA");
    Map<String, ObjectSlots> slots =
        ObjectSlotEncoder.encode(
            script,
            List.of(
                new ClassScope("Department", 1, 1),
                new ClassScope("Employee", 2, 2)));
    AssociationLinks deptEmployees =
        AssociationLinkEncoder.encode(
            script,
            "DeptEmployees",
            slots.get("Department"),
            new Multiplicity(1, 1),
            slots.get("Employee"),
            new Multiplicity(0, -1),
            new AssociationScope("DeptEmployees", 0, -1));
    AssociationLinks directEmployees =
        AssociationLinkEncoder.encode(
            script,
            "DirectEmployees",
            slots.get("Department"),
            new Multiplicity(0, 1),
            slots.get("Employee"),
            new Multiplicity(0, -1),
            new AssociationScope("DirectEmployees", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("d", new VariableBinding("Department", 0)),
            Map.of(),
            Map.of(),
            slots,
            Map.of("DeptEmployees", deptEmployees, "DirectEmployees", directEmployees));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Department_0_exists"));
    script.assertThat(Smt.sym("Employee_0_exists"));
    script.assertThat(Smt.sym("Employee_1_exists"));
    // Neither association has ANY forced link at all -- both populations are empty.
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  /**
   * The real corpus shape (verbatim from {@code Documentation/Demo/Demo.use}'s {@code
   * EmployeesInControllingDepartment}: {@code self.department.employee->
   * includesAll(self.employee)}) has a multi-hop LEFT operand -- {@code p.department} (single-
   * valued, {@code Controls}) then {@code .employee} (collection-valued, {@code WorksIn}).
   * Previously refused outright by {@link ExpressionTranslator#populationOf}'s own single-hop-only
   * restriction; now resolved via {@link ExpressionTranslator#navigationHop}'s general recursive
   * form, full pipeline confirmed through real Z3, discriminating a genuine SAT/UNSAT pair rather
   * than just "no longer throws".
   */
  @Test
  public void includesAllOverAMultiHopNavigationDiscriminatesOnARealCorpusShape() throws Exception {
    assertEquals(SolverOutcome.SAT, solveMultiHop(true));
    assertEquals(SolverOutcome.UNSAT, solveMultiHop(false));
  }

  private static SolverOutcome solveMultiHop(boolean everyDirectEmployeeAlsoWorksInDept)
      throws Exception {
    MModel model =
        compileModel(
            """
            model MultiHopIncludesAll
            class Project
            end
            class Department
            end
            class Employee
            end
            association Controls between
              Department[1] role department
              Project[*] role itsProjects
            end
            association WorksIn between
              Employee[*] role employee
              Department[1..*] role dept
            end
            association WorksOn between
              Employee[*] role employee2
              Project[*] role owner
            end
            constraints
            context p: Project inv MultiHop:
              p.department.employee->includesAll(p.employee2)
            """,
            "MultiHopIncludesAll");
    MClassInvariant inv = findInvariant(model, "MultiHop");

    SmtScript script = new SmtScript("QF_LIA");
    Map<String, ObjectSlots> slots =
        ObjectSlotEncoder.encode(
            script,
            List.of(
                new ClassScope("Project", 1, 1),
                new ClassScope("Department", 1, 1),
                new ClassScope("Employee", 2, 2)));
    AssociationLinks controls =
        AssociationLinkEncoder.encode(
            script,
            "Controls",
            slots.get("Department"),
            new Multiplicity(1, 1),
            slots.get("Project"),
            new Multiplicity(0, -1),
            new AssociationScope("Controls", 0, -1));
    AssociationLinks worksIn =
        AssociationLinkEncoder.encode(
            script,
            "WorksIn",
            slots.get("Employee"),
            new Multiplicity(0, -1),
            slots.get("Department"),
            new Multiplicity(1, -1),
            new AssociationScope("WorksIn", 0, -1));
    AssociationLinks worksOn =
        AssociationLinkEncoder.encode(
            script,
            "WorksOn",
            slots.get("Employee"),
            new Multiplicity(0, -1),
            slots.get("Project"),
            new Multiplicity(0, -1),
            new AssociationScope("WorksOn", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("p", new VariableBinding("Project", 0)),
            Map.of(),
            Map.of(),
            slots,
            Map.of("Controls", controls, "WorksIn", worksIn, "WorksOn", worksOn));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Project_0_exists"));
    script.assertThat(Smt.sym("Department_0_exists"));
    script.assertThat(Smt.sym("Employee_0_exists"));
    script.assertThat(Smt.sym("Employee_1_exists"));
    // The one Department controls the one Project.
    script.assertThat(Smt.sym(controls.linkNames()[0][0]));
    // Both Employees work on the Project (WorksOn) -- these are the "direct" employees
    // includesAll's argument (p.employee2) draws from.
    script.assertThat(Smt.sym(worksOn.linkNames()[0][0]));
    script.assertThat(Smt.sym(worksOn.linkNames()[1][0]));
    // Employee 0 always works in the controlling Department (the department.employee side).
    script.assertThat(Smt.sym(worksIn.linkNames()[0][0]));
    if (everyDirectEmployeeAlsoWorksInDept) {
      script.assertThat(Smt.sym(worksIn.linkNames()[1][0]));
    } else {
      // Employee 1 works ON the project but NOT in its controlling department -- a genuine
      // counterexample to EmployeesInControllingDepartment.
      script.assertThat(Smt.not(Smt.sym(worksIn.linkNames()[1][0])));
    }
    script.assertThat(translated);

    return solve(script).outcome();
  }

  @Test
  public void includesAllBetweenDifferentDestinationClassesFailsClosed() throws Exception {
    MModel model =
        compileModel(
            """
            model MixedIncludesAll
            class Department
            end
            class Employee
            end
            class Contractor
            end
            association DeptEmployees between
              Department[1] role department
              Employee[*] role employee
            end
            association DeptContractors between
              Department[1] role department2
              Contractor[*] role contractor
            end
            constraints
            context d: Department inv Mixed:
              d.employee->includesAll(d.contractor)
            """,
            "MixedIncludesAll");
    MClassInvariant inv = findInvariant(model, "Mixed");

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    inv.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));

    assertEquals(FragmentBoundary.TIER_3, thrown.boundary());
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("includesAll"));
  }

  /**
   * Shared bind-then-solve helper for the two real-Z3 discriminating tests: one {@code Department}
   * and two {@code Employee} candidates, both linked via {@code DeptEmployees}; {@code
   * DirectEmployees} links either BOTH (the SAT case) or just the one {@code DeptEmployees}
   * doesn't reach (the UNSAT case).
   */
  private static SolverOutcome solveWithLinks(String invariantName, boolean bothDirectLinksAreAlsoDeptLinks)
      throws Exception {
    MModel model = compileScope();
    MClassInvariant inv = findInvariant(model, invariantName);

    SmtScript script = new SmtScript("QF_LIA");
    Map<String, ObjectSlots> slots =
        ObjectSlotEncoder.encode(
            script,
            List.of(
                new ClassScope("Department", 1, 1),
                new ClassScope("Employee", 2, 2)));
    AssociationLinks deptEmployees =
        AssociationLinkEncoder.encode(
            script,
            "DeptEmployees",
            slots.get("Department"),
            new Multiplicity(1, 1),
            slots.get("Employee"),
            new Multiplicity(0, -1),
            new AssociationScope("DeptEmployees", 0, -1));
    AssociationLinks directEmployees =
        AssociationLinkEncoder.encode(
            script,
            "DirectEmployees",
            slots.get("Department"),
            new Multiplicity(0, 1),
            slots.get("Employee"),
            new Multiplicity(0, -1),
            new AssociationScope("DirectEmployees", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("d", new VariableBinding("Department", 0)),
            Map.of(),
            Map.of(),
            slots,
            Map.of("DeptEmployees", deptEmployees, "DirectEmployees", directEmployees));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Department_0_exists"));
    script.assertThat(Smt.sym("Employee_0_exists"));
    script.assertThat(Smt.sym("Employee_1_exists"));
    // Employee 0 is a DeptEmployee (via DeptEmployees) either way.
    script.assertThat(Smt.sym(deptEmployees.linkNames()[0][0]));
    // Employee 0 is a DirectEmployee too, in both cases.
    script.assertThat(Smt.sym(directEmployees.linkNames()[0][0]));
    // Employee 1 is a DirectEmployee, but ONLY a DeptEmployee in the SAT case.
    script.assertThat(Smt.sym(directEmployees.linkNames()[0][1]));
    if (bothDirectLinksAreAlsoDeptLinks) {
      script.assertThat(Smt.sym(deptEmployees.linkNames()[0][1]));
    } else {
      script.assertThat(Smt.not(Smt.sym(deptEmployees.linkNames()[0][1])));
    }
    script.assertThat(translated);

    return solve(script).outcome();
  }

  private static MModel compileScope() {
    return compileModel(
        """
        model IncludesAllScope
        class Department
        end
        class Employee
        end
        association DeptEmployees between
          Department[1] role department
          Employee[*] role employee
        end
        association DirectEmployees between
          Department[0..1] role owner
          Employee[*] role direct
        end
        constraints
        context d: Department inv AllDirectAreDeptEmployees:
          d.employee->includesAll(d.direct)
        """,
        "IncludesAllScope");
  }

  private static MModel compileModel(String source, String name) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError(name + " fixture model did not compile:\n" + source);
    }
    return model;
  }

  private static MClassInvariant findInvariant(MModel model, String name) {
    for (MClassInvariant inv : model.classInvariants()) {
      if (inv.name().equals(name)) {
        return inv;
      }
    }
    throw new IllegalStateException("invariant not found: " + name);
  }

  private static SolverResult solve(SmtScript script) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
  }
}
