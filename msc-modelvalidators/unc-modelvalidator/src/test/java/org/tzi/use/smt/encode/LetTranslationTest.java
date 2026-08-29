package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.smt.solver.SolverResult;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/** Parser-backed regression coverage for {@code let <var> = <expr> in <body>}. */
public class LetTranslationTest {

  @Test
  public void primitiveLetBindsItsValueInTheBody() throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, "primitiveLet");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    AttributeDomain iDomain = new AttributeDomain("A", "i", null, List.of(), null, null);
    AttributeDomain jDomain = new AttributeDomain("A", "j", null, List.of(), null, null);
    AttributeValues i =
        AttributeEncoder.encode(script, objects, "i", AttributeType.INTEGER, iDomain);
    AttributeValues j =
        AttributeEncoder.encode(script, objects, "j", AttributeType.INTEGER, jDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of("a", new VariableBinding("A", 0)),
            Map.of("A.i", i, "A.j", j),
            Map.of("A.i", iDomain, "A.j", jDomain),
            Map.of("A", objects),
            Map.of());

    SmtTerm translated = ExpressionTranslator.translate(invariant.bodyExpression(), context);

    assertEquals(
        "(let ((|ocl-let-threshold-defined| (and true true))"
            + " (|ocl-let-threshold-value| (+ A_0_i 1)))"
            + " (> |ocl-let-threshold-value| A_0_j))",
        translated.toSmtLib());
  }

  @Test
  public void undefinedBoundValueMakesAStrictBodyUndefined() throws Exception {
    MClassInvariant invariant = findInvariant(compileFixture(), "undefinedLet");
    TranslatedExpression translated =
        ExpressionTranslator.translate(
            invariant.bodyExpression(), emptyContext(), TranslationMode.UNCERTAIN);

    assertEquals(
        "(let ((|ocl-let-threshold-defined| false) (|ocl-let-threshold-value| 0))"
            + " (and |ocl-let-threshold-defined| true))",
        translated.defined().toSmtLib());
  }

  @Test
  public void primitiveLetVariableEqualityDoesNotUseObjectIdentityShortcut() throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, "equalityLet");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    AttributeDomain iDomain = new AttributeDomain("A", "i", null, List.of(), null, null);
    AttributeDomain jDomain = new AttributeDomain("A", "j", null, List.of(), null, null);
    AttributeValues i =
        AttributeEncoder.encode(script, objects, "i", AttributeType.INTEGER, iDomain);
    AttributeValues j =
        AttributeEncoder.encode(script, objects, "j", AttributeType.INTEGER, jDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of("a", new VariableBinding("A", 0)),
            Map.of("A.i", i, "A.j", j),
            Map.of("A.i", iDomain, "A.j", jDomain),
            Map.of("A", objects),
            Map.of());

    assertEquals(
        "(let ((|ocl-let-copy-defined| true) (|ocl-let-copy-value| A_0_i))"
            + " (or (and (not |ocl-let-copy-defined|) (not true))"
            + " (and |ocl-let-copy-defined| true (= |ocl-let-copy-value| A_0_j))))",
        ExpressionTranslator.translate(invariant.bodyExpression(), context).toSmtLib());
  }

  /**
   * String is the fourth primitive let-bound type ({@code visitLet}'s own type gate previously
   * accepted only Integer/Boolean/Real) -- found while auditing prim.oclundefined-literal's sibling
   * entry {@code ocl.let}: nothing about the let mechanism itself (a native SMT-LIB {@code let} over
   * whatever sort the bound expression already produces) is Integer/Boolean/Real-specific, and a
   * String attribute is ALREADY encoded as an Integer domain index, so the gate was excluding a case
   * the underlying machinery already handled correctly. Scoped narrowly: comparing the let-bound
   * variable against a STRING LITERAL still fails closed (resolve(ExpConstString,...) only special-
   * cases a literal against a direct ExpAttrOp receiver, not a let-bound ExpVariable) -- this closes
   * comparison against another String-valued expression (an attribute), not the literal shape.
   */
  @Test
  public void stringLetBindsAnAttributeAndComparesAgainstAnotherStringAttribute() throws Exception {
    assertEquals(SolverOutcome.UNSAT, solveStringLet(0, 1));
    assertEquals(SolverOutcome.SAT, solveStringLet(0, 0));
  }

  private static SolverOutcome solveStringLet(int nameIndex, int otherNameIndex) throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, "stringLet");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    AttributeDomain nameDomain =
        new AttributeDomain("A", "name", null, List.of("alice", "bob"), null, null);
    AttributeDomain otherNameDomain =
        new AttributeDomain("A", "otherName", null, List.of("alice", "bob"), null, null);
    AttributeValues name =
        AttributeEncoder.encode(script, objects, "name", AttributeType.STRING, nameDomain);
    AttributeValues otherName =
        AttributeEncoder.encode(script, objects, "otherName", AttributeType.STRING, otherNameDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of("a", new VariableBinding("A", 0)),
            Map.of("A.name", name, "A.otherName", otherName),
            Map.of("A.name", nameDomain, "A.otherName", otherNameDomain),
            Map.of("A", objects),
            Map.of());

    TranslatedExpression translated =
        ExpressionTranslator.translate(invariant.bodyExpression(), context, TranslationMode.UNCERTAIN);

    script.assertThat(Smt.sym("A_0_exists"));
    pin(script, name, 0, nameIndex);
    pin(script, otherName, 0, otherNameIndex);
    script.assertThat(translated.trueTerm());
    return solve(script);
  }

  /**
   * 2026-08-29: the non-any object let over a CONTEXT VARIABLE is now a supported CHAINED object
   * let -- the alias IS the source binding, so {@code chosen.i} reads a's own slot symbols. This
   * superseded the old TIER_3 refusal pin for this shape; a truly UNBOUND object initializer
   * still fails closed with the unbound-variable error (see the next test).
   */
  @Test
  public void objectTypedContextAliasLetReadsTheAliasedSlot() throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, "objectLet");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    AttributeDomain iDomain = new AttributeDomain("A", "i", null, List.of(), null, null);
    AttributeValues i =
        AttributeEncoder.encode(script, objects, "i", AttributeType.INTEGER, iDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of("a", new VariableBinding("A", 0)),
            Map.of("A.i", i),
            Map.of("A.i", iDomain),
            Map.of("A", objects),
            Map.of());

    TranslatedExpression translated =
        ExpressionTranslator.translate(invariant.bodyExpression(), context, TranslationMode.UNCERTAIN);

    assertEquals("(> A_0_i 0)", translated.value().toSmtLib());
  }

  @Test
  public void objectTypedLetWithAnUnboundInitializerStillFailsClosed() throws Exception {
    MClassInvariant invariant = findInvariant(compileFixture(), "objectLet");
    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () -> ExpressionTranslator.translate(invariant.bodyExpression(), emptyContext()));
    assertEquals(FragmentBoundary.ENCODING_SCOPE, thrown.boundary());
    assertTrue(
        thrown.getMessage(), thrown.getMessage().contains("unbound OCL variable"));
  }

  @Test
  public void collectionTypedLetFailsClosedBeforeItsUnsupportedBody() throws Exception {
    assertUnsupportedBinding("collectionLet", "Set(A)", "object- and collection-typed");
  }

  @Test
  public void objectAnyLetSelectsTheMatchingFiniteSlot() throws Exception {
    ObjectAnyCase encoded = encodeObjectAnyLet(1, 10, 0, 99, 1, 11);
    encoded.script().assertThat(encoded.expression().trueTerm());
    assertEquals(SolverOutcome.SAT, solve(encoded.script()));
  }

  @Test
  public void objectAnyLetUsesTheFirstSlotWhenMoreThanOneMatches() throws Exception {
    ObjectAnyCase encoded = encodeObjectAnyLet(1, 10, 1, 9, 1, 11);
    encoded.script().assertThat(encoded.expression().trueTerm());
    assertEquals(SolverOutcome.UNSAT, solve(encoded.script()));
  }

  @Test
  public void objectAnyLetIsUndefinedWhenNoSlotMatches() throws Exception {
    ObjectAnyCase encoded = encodeObjectAnyLet(1, 10, 0, 99, 2, 99);
    encoded.script().assertThat(Smt.not(encoded.expression().defined()));
    assertEquals(SolverOutcome.SAT, solve(encoded.script()));
  }

  @Test
  public void companyDepartmentBudgetLetTranslatesItsRealParsedAst() throws Exception {
    MClassInvariant invariant =
        findInvariant(compileCompany(), "DepartmentBudget_greater_allEmployeeSalary");
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots employees =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Employee", 2, 2)))
            .get("Employee");
    ObjectSlots departments =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Department", 2, 2)))
            .get("Department");
    AttributeDomain employeeNameDomain =
        new AttributeDomain(
            "Employee", "dname", null, List.of("Accounting", "Research"), null, null);
    AttributeDomain departmentNameDomain =
        new AttributeDomain(
            "Department", "dname", null, List.of("Accounting", "Research"), null, null);
    AttributeDomain salaryDomain =
        new AttributeDomain("Employee", "salary", null, List.of(), null, null);
    AttributeDomain budgetDomain =
        new AttributeDomain("Department", "budget", null, List.of(), null, null);
    AttributeValues employeeName =
        AttributeEncoder.encode(
            script, employees, "dname", AttributeType.STRING, employeeNameDomain);
    AttributeValues departmentName =
        AttributeEncoder.encode(
            script, departments, "dname", AttributeType.STRING, departmentNameDomain);
    AttributeValues salary =
        AttributeEncoder.encode(script, employees, "salary", AttributeType.INTEGER, salaryDomain);
    AttributeValues budget =
        AttributeEncoder.encode(
            script, departments, "budget", AttributeType.INTEGER, budgetDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of("e", new VariableBinding("Employee", 0)),
            Map.of(
                "Employee.dname", employeeName,
                "Employee.salary", salary,
                "Department.dname", departmentName,
                "Department.budget", budget),
            Map.of(
                "Employee.dname", employeeNameDomain,
                "Employee.salary", salaryDomain,
                "Department.dname", departmentNameDomain,
                "Department.budget", budgetDomain),
            Map.of("Employee", employees, "Department", departments),
            Map.of());

    pin(script, employeeName, 0, 0);
    pin(script, employeeName, 1, 1);
    pin(script, departmentName, 0, 0);
    pin(script, departmentName, 1, 1);
    pin(script, salary, 0, 3);
    pin(script, salary, 1, 9);
    pin(script, budget, 0, 20);
    pin(script, budget, 1, 30);
    TranslatedExpression translated =
        ExpressionTranslator.translate(
            invariant.bodyExpression(), context, TranslationMode.UNCERTAIN);
    script.assertThat(translated.trueTerm());

    SolverResult result = solveResult(script);
    assertEquals(result.rawOutput(), SolverOutcome.SAT, result.outcome());
  }

  private static ObjectAnyCase encodeObjectAnyLet(
      int targetValue, int limitValue, int key0, int value0, int key1, int value1)
      throws Exception {
    MClassInvariant invariant = findInvariant(compileFixture(), "objectAnyLet");
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots holders =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Holder", 1, 1))).get("Holder");
    ObjectSlots candidates =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Candidate", 2, 2)))
            .get("Candidate");
    AttributeDomain targetDomain =
        new AttributeDomain("Holder", "target", null, List.of(), null, null);
    AttributeDomain limitDomain =
        new AttributeDomain("Holder", "limit", null, List.of(), null, null);
    AttributeDomain keyDomain =
        new AttributeDomain("Candidate", "key", null, List.of(), null, null);
    AttributeDomain valueDomain =
        new AttributeDomain("Candidate", "value", null, List.of(), null, null);
    AttributeValues target =
        AttributeEncoder.encode(
            script, holders, "target", AttributeType.INTEGER, targetDomain);
    AttributeValues limit =
        AttributeEncoder.encode(script, holders, "limit", AttributeType.INTEGER, limitDomain);
    AttributeValues key =
        AttributeEncoder.encode(script, candidates, "key", AttributeType.INTEGER, keyDomain);
    AttributeValues value =
        AttributeEncoder.encode(script, candidates, "value", AttributeType.INTEGER, valueDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of("h", new VariableBinding("Holder", 0)),
            Map.of(
                "Holder.target", target,
                "Holder.limit", limit,
                "Candidate.key", key,
                "Candidate.value", value),
            Map.of(
                "Holder.target", targetDomain,
                "Holder.limit", limitDomain,
                "Candidate.key", keyDomain,
                "Candidate.value", valueDomain),
            Map.of("Holder", holders, "Candidate", candidates),
            Map.of());

    script.assertThat(
        Smt.eq(
            Smt.sym(target.valueNames().get(0)),
            Smt.intLit(BigInteger.valueOf(targetValue))));
    script.assertThat(
        Smt.eq(
            Smt.sym(limit.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(limitValue))));
    script.assertThat(
        Smt.eq(Smt.sym(key.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(key0))));
    script.assertThat(
        Smt.eq(Smt.sym(value.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(value0))));
    script.assertThat(
        Smt.eq(Smt.sym(key.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(key1))));
    script.assertThat(
        Smt.eq(Smt.sym(value.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(value1))));
    return new ObjectAnyCase(
        script,
        ExpressionTranslator.translate(
            invariant.bodyExpression(), context, TranslationMode.UNCERTAIN));
  }

  private static SolverOutcome solve(SmtScript script) {
    return solveResult(script).outcome();
  }

  private static SolverResult solveResult(SmtScript script) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
  }

  private record ObjectAnyCase(SmtScript script, TranslatedExpression expression) {}

  private static void pin(SmtScript script, AttributeValues values, int slot, int value) {
    script.assertThat(
        Smt.eq(
            Smt.sym(values.valueNames().get(slot)), Smt.intLit(BigInteger.valueOf(value))));
  }

  private static void assertUnsupportedBinding(String invariantName, String type, String scope)
      throws Exception {
    MClassInvariant invariant = findInvariant(compileFixture(), invariantName);
    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () -> ExpressionTranslator.translate(invariant.bodyExpression(), emptyContext()));
    assertEquals(FragmentBoundary.TIER_3, thrown.boundary());
    assertTrue(thrown.getMessage(), thrown.getMessage().contains(type));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains(scope));
  }

  private static TranslationContext emptyContext() {
    return new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
  }

  private static MModel compileFixture() throws Exception {
    Path file = Path.of("src/test/resources/LetScope.use");
    String source = Files.readString(file);
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "LetScope", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("LetScope fixture model did not compile");
    }
    return model;
  }

  private static MModel compileCompany() throws Exception {
    Path file = Path.of("../benchmark/examples/CompanyERSchema/CompanyER.use");
    String source = Files.readString(file);
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "CompanyER", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("CompanyER fixture model did not compile");
    }
    return model;
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
