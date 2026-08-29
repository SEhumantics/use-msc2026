package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for the FIRST SLICE of {@code ocl.query-operation-inlining}: a
 * zero-argument query operation ({@code doubled(): Integer = self.base * 2}) called on a bare
 * variable is INLINED -- the body is translated with {@code self} bound to the receiver's slot,
 * so the solver computes the operation's value from the object's actual state instead of the
 * call being an error. Nested calls inline recursively; direct or transitive recursion is
 * detected and refused; parameterized operations stay refused (slice boundary).
 *
 * <p>This is the construct CompanyERSchema's {@code Component::acyclic} needs
 * ({@code contained()}/{@code containedPlus()} are query operations whose bodies ride the
 * select/exists and closure idioms this translation already supports).
 */
public class QueryOperationInliningTest {

  private static final String MODEL =
      """
      model QueryOpInlining
      class X
      attributes
        base : Integer
      operations
        doubled(): Integer = self.base * 2
        negated(): Integer = 0 - self.doubled()
      end
      constraints
      context x : X inv DoubledIsSix:
        x.doubled() = 6
      context x : X inv DoubledPositive:
        x.doubled() >= 8
      context x : X inv NegatedUsesNestedCall:
        x.negated() = -12
""";

  /** The inlined body computes from the object's state: satisfiable exactly when base can be 3. */
  @Test
  public void inlinedOperationComputesFromTheObjectState() throws Exception {
    ModelFinderResult match = find("DoubledIsSix", List.of("3"));
    assertTrue("base = 3 makes doubled() = 6 hold", match.satisfiable());
    assertTrue(verdictFor(match, "X::DoubledIsSix").holds());

    ModelFinderResult miss = find("DoubledIsSix", List.of("4"));
    assertFalse("base = 4 makes doubled() = 8, so = 6 is genuinely unsatisfiable",
        miss.satisfiable());
  }

  /**
   * A NESTED call inside the operation body inlines recursively: negated() calls doubled(),
   * which reads base. With base = 6, negated() = -12.
   */
  @Test
  public void nestedOperationCallsInlineRecursively() throws Exception {
    ModelFinderResult result = find("NegatedUsesNestedCall", List.of("6"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::NegatedUsesNestedCall").holds());
  }

  /**
   * Direct recursion is detected and refused at TRANSLATION level. (This cannot be tested
   * end to end through find(): a recursive operation's body loops USE's own evaluator forever,
   * which is exactly why the translator must refuse it before any witness could be re-checked.)
   */
  @Test
  public void directRecursionFailsClosedWithALocatedRefusal() throws Exception {
    String model =
        """
        model RecOp
        class X
        attributes
          base : Integer
        operations
          selfLoop(): Integer = self.selfLoop()
        end
        constraints
        context x : X inv UsesRec:
          x.selfLoop() = 0
        """;
    ModelFactory factory = new ModelFactory();
    java.io.PrintWriter err = new java.io.PrintWriter(System.err, true);
    MModel compiled = org.tzi.use.parser.use.USECompiler.compileSpecification(
        model, "RecOp", err, factory);
    err.flush();
    var invariant = compiled.classInvariants().iterator().next();
    org.tzi.use.smt.solver.SmtScript script = new org.tzi.use.smt.solver.SmtScript("QF_LIA");
    var slots = org.tzi.use.smt.encode.ObjectSlotEncoder.encode(
        script, List.of(new ClassScope("X", 1, 1))).get("X");
    var context = new org.tzi.use.smt.encode.TranslationContext(
        java.util.Map.of("x", new org.tzi.use.smt.encode.VariableBinding("X", 0)),
        java.util.Map.of(), java.util.Map.of(),
        java.util.Map.of("X", slots), java.util.Map.of());
    org.tzi.use.smt.encode.SmtTranslationException thrown =
        org.junit.Assert.assertThrows(
            org.tzi.use.smt.encode.SmtTranslationException.class,
            () -> org.tzi.use.smt.encode.ExpressionTranslator.translate(
                invariant.bodyExpression(), context));
    assertTrue(
        "the refusal must name the recursive operation",
        thrown.getMessage().contains("recursive operation call 'selfLoop'"));
  }

  private static ModelFinderResult find(String invariantName, List<String> baseDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("X", "base", null, baseDomain, null, null)),
            Set.of("X::" + invariantName),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "QueryOpInlining", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
