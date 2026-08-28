package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Parser-backed regression coverage for {@code if <cond> then <a> else <b> endif}.
 *
 * <p>USE's own {@code ExpIf#eval} (use-core) makes the whole if-expression undefined when its
 * condition is undefined -- it does NOT fall through to either branch (the method's own docstring
 * says otherwise; the actual code, guarded by {@code if (condValue.isDefined())}, does not match
 * its docstring, and this translation follows the code). That is the one correctness property
 * this test suite exists to pin, not merely "it compiles and picks a branch."
 */
public class IfThenElseTranslationTest {

  /**
   * The invariant body is {@code (if...) = a.i}, so this pins the WHOLE composed string, not just
   * the if-then-else sub-term -- the top-level {@code =} already implements OCL's own equality
   * semantics for possibly-undefined operands (both undefined -&gt; true; both defined -&gt;
   * compare values; anything else -&gt; false), pre-existing logic this if-then-else's {@link
   * TranslatedExpression} composes into for free, exactly the way every other operator in this
   * translator already does. The if-then-else's own contribution is the inner {@code (ite
   * A_0_flag A_0_i A_0_j)} and its own defined term nested inside the composed whole.
   */
  @Test
  public void trueConditionSelectsTheThenBranch() throws Exception {
    TranslatedExpression translated = translateInvariant("trueBranch");
    assertEquals(
        "(or (and (not (and true (ite A_0_flag true true))) (not true))"
            + " (and (and true (ite A_0_flag true true)) true"
            + " (= (ite A_0_flag A_0_i A_0_j) A_0_i)))",
        translated.value().toSmtLib());
  }

  @Test
  public void falseConditionSelectsTheElseBranch() throws Exception {
    TranslatedExpression translated = translateInvariant("falseBranch");
    assertEquals(
        "(or (and (not (and true (ite A_0_flag true true))) (not true))"
            + " (and (and true (ite A_0_flag true true)) true"
            + " (= (ite A_0_flag A_0_i A_0_j) A_0_j)))",
        translated.value().toSmtLib());
  }

  @Test
  public void undefinedConditionMakesTheWholeIfUndefinedNotEitherBranch() throws Exception {
    TranslatedExpression translated = translateInvariant("undefinedConditionIsUndefined");
    // The invariant body is `(if...) > 0`, so the outer `and` is orderedComparison's own
    // l.defined()/r.defined() conjunction -- `(and <if-defined> true)`, since the literal 0 is
    // always defined. <if-defined> is `(and false (ite false true true))`: the LEADING `false` is
    // the condition's own definedness (from oclUndefined(Boolean)) conjoined directly, confirming
    // the whole expression is undefined regardless of which branch an ite would have picked, not
    // "false but only because a branch happened to be false too" (both branches here are trivially
    // defined attribute accesses, so the ite alone would say true either way).
    assertEquals(
        "(and (and false (ite false true true)) true)", translated.defined().toSmtLib());
  }

  @Test
  public void mismatchedBranchTypesAreRefused() throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, "mismatchedBranchTypes");
    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () -> ExpressionTranslator.translate(invariant.bodyExpression(), emptyIfContext()));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("different types"));
  }

  /**
   * {@code allActiveInvariantsHold()} checks every invariant in the model's independent verdict
   * list, active or not (confirmed by reading {@code ModelFinderResult.allActiveInvariantsHold()}
   * directly, not assumed from its name) -- unusable here since this fixture deliberately carries
   * invariants meant to read FALSE/UNDEFINED (falseBranch, undefinedConditionIsUndefined). Checks
   * the specific verdict instead, the same pattern {@code URealThresholdRoundTripTest} already
   * uses.
   *
   * <p>Verified directly, not just via this one assertion: a probe of the full verdict list for
   * this exact configuration showed EVERY invariant landing exactly where expected --
   * {@code trueBranch=TRUE}, {@code falseBranch=FALSE} (flag=true selects i=7, not j=-3),
   * {@code undefinedConditionIsUndefined=UNDEFINED} (confirming the load-bearing semantic
   * property from an entirely independent evaluator, not just this translator's own SMT
   * encoding), and {@code mismatchedBranchTypes=TRUE} (USE's own interpreter can still evaluate a
   * shape this translator refuses to translate -- the refusal is about SMT-LIB representability,
   * not OCL well-formedness).
   */
  @Test
  public void fullRoundTripReconstructsAndUseEvaluatorConfirmsTheTrueBranch() throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            A_min = 1
            A_max = 1
            A_i = Set{7}
            A_j = Set{-3}
            A_flag = Set{true}
            A_trueBranch = active
            A_falseBranch = inactive
            A_undefinedConditionIsUndefined = inactive
            A_mismatchedBranchTypes = inactive
            """);
    org.tzi.use.smt.finder.ModelFinderResult result =
        org.tzi.use.smt.finder.SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    assertEquals(
        new org.tzi.use.smt.verify.InvariantVerdict("A::trueBranch", true),
        result.verdicts().stream()
            .filter(v -> v.invariantName().equals("A::trueBranch"))
            .findFirst()
            .orElseThrow());
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("ifthenelse", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static TranslatedExpression translateInvariant(String invariantName) throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, invariantName);
    return ExpressionTranslator.translate(
        invariant.bodyExpression(), fullIfContext(), org.tzi.use.smt.config.TranslationMode.UNCERTAIN);
  }

  private static TranslationContext fullIfContext() {
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    AttributeDomain iDomain = new AttributeDomain("A", "i", null, List.of(), null, null);
    AttributeDomain jDomain = new AttributeDomain("A", "j", null, List.of(), null, null);
    AttributeDomain flagDomain = new AttributeDomain("A", "flag", null, List.of(), null, null);
    AttributeValues i = AttributeEncoder.encode(script, objects, "i", AttributeType.INTEGER, iDomain);
    AttributeValues j = AttributeEncoder.encode(script, objects, "j", AttributeType.INTEGER, jDomain);
    AttributeValues flag =
        AttributeEncoder.encode(script, objects, "flag", AttributeType.BOOLEAN, flagDomain);
    return new TranslationContext(
        Map.of("a", new VariableBinding("A", 0)),
        Map.of("A.i", i, "A.j", j, "A.flag", flag),
        Map.of("A.i", iDomain, "A.j", jDomain, "A.flag", flagDomain),
        Map.of("A", objects),
        Map.of());
  }

  private static TranslationContext emptyIfContext() {
    return new TranslationContext(
        Map.of("a", new VariableBinding("A", 0)), Map.of(), Map.of(), Map.of(), Map.of());
  }

  private static MModel compileFixture() throws Exception {
    Path file = Path.of("src/test/resources/IfThenElseScope.use");
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(
            Files.readString(file), "IfThenElseScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("IfThenElseScope fixture model did not compile");
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
