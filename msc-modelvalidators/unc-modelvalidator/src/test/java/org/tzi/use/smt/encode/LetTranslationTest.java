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

  @Test
  public void objectTypedLetFailsClosedBeforeItsUnsupportedAnyExpression() throws Exception {
    assertUnsupportedBinding("objectLet", "type A", "object- and collection-typed");
  }

  @Test
  public void collectionTypedLetFailsClosedBeforeItsUnsupportedBody() throws Exception {
    assertUnsupportedBinding("collectionLet", "Set(A)", "object- and collection-typed");
  }

  @Test
  public void objectAnyLetSelectsTheMatchingFiniteSlot() throws Exception {
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
        Smt.eq(Smt.sym(target.valueNames().get(0)), Smt.intLit(BigInteger.ONE)));
    script.assertThat(Smt.eq(Smt.sym(limit.valueNames().get(0)), Smt.intLit(BigInteger.TEN)));
    script.assertThat(Smt.eq(Smt.sym(key.valueNames().get(0)), Smt.intLit(BigInteger.ZERO)));
    script.assertThat(Smt.eq(Smt.sym(value.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(99))));
    script.assertThat(Smt.eq(Smt.sym(key.valueNames().get(1)), Smt.intLit(BigInteger.ONE)));
    script.assertThat(
        Smt.eq(Smt.sym(value.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(11))));
    script.assertThat(ExpressionTranslator.translate(invariant.bodyExpression(), context));

    assertEquals(
        SolverOutcome.SAT,
        new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
            .run(script.toSmtLib())
            .outcome());
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

  private static MClassInvariant findInvariant(MModel model, String name) {
    for (MClassInvariant invariant : model.classInvariants()) {
      if (invariant.name().equals(name)) {
        return invariant;
      }
    }
    throw new IllegalStateException("invariant not found: " + name);
  }
}
