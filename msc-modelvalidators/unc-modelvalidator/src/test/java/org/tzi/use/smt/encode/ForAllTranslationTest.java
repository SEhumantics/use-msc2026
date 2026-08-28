package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.*;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

public class ForAllTranslationTest {

  @Test
  public void titleIsKeyOnTheRealAstRejectsTwoExistingBooksWithTheSameTitle() throws Exception {
    MModel model = compileLibrary();
    MClassInvariant inv = findInvariant(model, "titleIsKey");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots books =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 1, 2))).get("Book");
    AttributeDomain titleDomain =
        new AttributeDomain(
            "Book", "title", null, List.of("DBforDummies", "IntrotoAI", "PrincsofNW"), null, null);
    AttributeValues titleValues =
        AttributeEncoder.encode(script, books, "title", AttributeType.STRING, titleDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("b1", new VariableBinding("Book", 0)),
            Map.of("Book.title", titleValues),
            Map.of("Book.title", titleDomain),
            Map.of("Book", books),
            Map.of());
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Book_0_exists"));
    script.assertThat(Smt.sym("Book_1_exists"));
    script.assertThat(
        Smt.eq(Smt.sym(titleValues.valueNames().get(0)), Smt.sym(titleValues.valueNames().get(1))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  @Test
  public void titleIsKeyOnTheRealAstIsSatisfiableWithDistinctTitles() throws Exception {
    MModel model = compileLibrary();
    MClassInvariant inv = findInvariant(model, "titleIsKey");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots books =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 1, 2))).get("Book");
    AttributeDomain titleDomain =
        new AttributeDomain(
            "Book", "title", null, List.of("DBforDummies", "IntrotoAI", "PrincsofNW"), null, null);
    AttributeValues titleValues =
        AttributeEncoder.encode(script, books, "title", AttributeType.STRING, titleDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("b1", new VariableBinding("Book", 0)),
            Map.of("Book.title", titleValues),
            Map.of("Book.title", titleDomain),
            Map.of("Book", books),
            Map.of());
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Book_0_exists"));
    script.assertThat(Smt.sym("Book_1_exists"));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  /** Proves the existence guard is load-bearing: a duplicate on a non-existent slot is harmless. */
  @Test
  public void titleIsKeyIsVacuouslySatisfiedWhenTheSecondSlotDoesNotExist() throws Exception {
    MModel model = compileLibrary();
    MClassInvariant inv = findInvariant(model, "titleIsKey");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots books =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 1, 2))).get("Book");
    AttributeDomain titleDomain =
        new AttributeDomain(
            "Book", "title", null, List.of("DBforDummies", "IntrotoAI", "PrincsofNW"), null, null);
    AttributeValues titleValues =
        AttributeEncoder.encode(script, books, "title", AttributeType.STRING, titleDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("b1", new VariableBinding("Book", 0)),
            Map.of("Book.title", titleValues),
            Map.of("Book.title", titleDomain),
            Map.of("Book", books),
            Map.of());
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Book_0_exists"));
    script.assertThat(Smt.not(Smt.sym("Book_1_exists")));
    script.assertThat(
        Smt.eq(Smt.sym(titleValues.valueNames().get(0)), Smt.sym(titleValues.valueNames().get(1))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  /** A different class, different variable names, rules out anything accidentally Book-specific. */
  @Test
  public void nameIsKeyOnADifferentClassAlsoRejectsADuplicate() throws Exception {
    MModel model = compileLibrary();
    MClassInvariant inv = findInvariant(model, "nameIsKey");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots users =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("User", 1, 2))).get("User");
    AttributeDomain nameDomain =
        new AttributeDomain("User", "name", null, List.of("Ada", "Bob", "Cyd"), null, null);
    AttributeValues nameValues =
        AttributeEncoder.encode(script, users, "name", AttributeType.STRING, nameDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("u1", new VariableBinding("User", 0)),
            Map.of("User.name", nameValues),
            Map.of("User.name", nameDomain),
            Map.of("User", users),
            Map.of());
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("User_0_exists"));
    script.assertThat(Smt.sym("User_1_exists"));
    script.assertThat(
        Smt.eq(Smt.sym(nameValues.valueNames().get(0)), Smt.sym(nameValues.valueNames().get(1))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  /** The third target invariant, same shape, on a third class — completes coverage of all three. */
  @Test
  public void signatureIsKeyOnAThirdClassAlsoRejectsADuplicate() throws Exception {
    MModel model = compileLibrary();
    MClassInvariant inv = findInvariant(model, "signatureIsKey");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots copies =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Copy", 1, 2))).get("Copy");
    AttributeDomain sigDomain =
        new AttributeDomain(
            "Copy", "signature", null, List.of("DBS42", "DBS43", "NW21"), null, null);
    AttributeValues sigValues =
        AttributeEncoder.encode(script, copies, "signature", AttributeType.STRING, sigDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("c1", new VariableBinding("Copy", 0)),
            Map.of("Copy.signature", sigValues),
            Map.of("Copy.signature", sigDomain),
            Map.of("Copy", copies),
            Map.of());
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Copy_0_exists"));
    script.assertThat(Smt.sym("Copy_1_exists"));
    script.assertThat(
        Smt.eq(Smt.sym(sigValues.valueNames().get(0)), Smt.sym(sigValues.valueNames().get(1))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  /**
   * {@code source.role->forAll(body)}, the shape that was refused before {@code visitForAll} was
   * widened to reuse {@link ExpressionTranslator#populationOf populationOf} -- the real corpus
   * motivation is Genealogy's {@code p.child->forAll(c | p.yearB+15<=c.yearB)}. Uses a dedicated,
   * non-reflexive Department/Employee fixture rather than a self-referential one deliberately, to
   * isolate this from {@code ReflexiveAssociationTranslationTest}'s own separate coverage.
   */
  @Test
  public void forAllOverACollectionValuedNavigationOnTheRealAst() throws Exception {
    String modelSource =
        """
        model DeptScope
        class Department
        attributes
          budget : Integer
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
        context d : Department inv allStaffEarnLessThanBudget:
          d.staff->forAll(e | e.salary < d.budget)
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(modelSource, "DeptScope", err, new ModelFactory());
    err.flush();
    MClassInvariant inv = findInvariant(model, "allStaffEarnLessThanBudget");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots depts =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Department", 1, 1)))
            .get("Department");
    ObjectSlots employees =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Employee", 2, 2)))
            .get("Employee");
    AttributeDomain budgetDomain =
        new AttributeDomain("Department", "budget", null, List.of(), null, null);
    AttributeDomain salaryDomain = new AttributeDomain("Employee", "salary", null, List.of(), null, null);
    AttributeValues budget =
        AttributeEncoder.encode(script, depts, "budget", AttributeType.INTEGER, budgetDomain);
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
            new AssociationScope("Employs", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("d", new VariableBinding("Department", 0)),
            Map.of("Department.budget", budget, "Employee.salary", salary),
            Map.of("Department.budget", budgetDomain, "Employee.salary", salaryDomain),
            Map.of("Department", depts, "Employee", employees),
            Map.of("Employs", employs));
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Department_0_exists"));
    script.assertThat(Smt.sym("Employee_0_exists"));
    script.assertThat(Smt.sym("Employee_1_exists"));
    script.assertThat(Smt.sym(employs.linkNames()[0][0]));
    script.assertThat(Smt.sym(employs.linkNames()[0][1]));
    script.assertThat(
        Smt.eq(Smt.sym(budget.valueNames().get(0)), Smt.intLit(java.math.BigInteger.valueOf(100))));
    script.assertThat(
        Smt.eq(Smt.sym(salary.valueNames().get(0)), Smt.intLit(java.math.BigInteger.valueOf(50))));
    script.assertThat(
        Smt.eq(Smt.sym(salary.valueNames().get(1)), Smt.intLit(java.math.BigInteger.valueOf(150))));
    script.assertThat(translated);

    // Employee_1's salary (150) exceeds the budget (100), so the invariant genuinely fails --
    // confirms this isn't vacuously true from an empty or unlinked population.
    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  /**
   * {@code X.allInstances()->forAll(v1, v2 | v1<>v2 implies ...)} -- ZebraPuzzle's own
   * {@code DistinctColor}/{@code DistinctNationality}/etc. shape (six invariants, all identical
   * modulo the compared attribute), previously refused outright at more than one loop variable.
   * Widened by generalising the single-variable loop into a full cross product over the SAME
   * population (mirroring {@link ExpressionTranslator#visitExists visitExists}'s own two-variable
   * pattern, confirmed against {@code ExpQuery.evalForAll0}/use-core: real USE ranges every
   * variable over the full population independently, including the {@code v1==v2} pair, relying on
   * the body's own {@code <>} guard to neutralise it -- not excluded by this translation either).
   */
  @Test
  public void twoVariableForAllOverAllInstancesRejectsADuplicateAndAllowsDistinctValues()
      throws Exception {
    String modelSource =
        """
        model DistinctScope
        class Item
        attributes
          val : Integer
        end
        constraints
        context i : Item inv distinctVal:
          Item.allInstances->forAll(i1, i2 |
            i1 <> i2 implies i1.val <> i2.val)
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(modelSource, "DistinctScope", err, new ModelFactory());
    err.flush();
    MClassInvariant inv = findInvariant(model, "distinctVal");

    // Two existing Items forced to the SAME value: the cross product must catch the (i1=0,i2=1)
    // and (i1=1,i2=0) pair, so this must be UNSAT.
    assertEquals(SolverOutcome.UNSAT, solveDistinctVal(model, inv, true));
    // Two existing Items with DIFFERENT values: no pair violates it, so this must be SAT.
    assertEquals(SolverOutcome.SAT, solveDistinctVal(model, inv, false));
  }

  private static SolverOutcome solveDistinctVal(MModel model, MClassInvariant inv, boolean sameValue)
      throws Exception {
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots items =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Item", 2, 2))).get("Item");
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
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx, TranslationMode.UNCERTAIN);

    script.assertThat(Smt.sym("Item_0_exists"));
    script.assertThat(Smt.sym("Item_1_exists"));
    script.assertThat(
        Smt.eq(Smt.sym(val.valueNames().get(0)), Smt.intLit(java.math.BigInteger.valueOf(1))));
    script.assertThat(
        Smt.eq(
            Smt.sym(val.valueNames().get(1)),
            Smt.intLit(java.math.BigInteger.valueOf(sameValue ? 1 : 2))));
    script.assertThat(translated.trueTerm());

    return solve(script).outcome();
  }

  private static MModel compileLibrary() throws Exception {
    Path file = Path.of("../benchmark/examples/Library/Library.use");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/Library/Library.use");
    }
    String source = Files.readString(file);
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "Library", err, factory);
    err.flush();
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
