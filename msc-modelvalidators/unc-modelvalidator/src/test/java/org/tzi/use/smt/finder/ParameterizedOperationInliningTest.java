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
 * End-to-end regression for PARAMETERIZED query-operation inlining: {@code x.plus(a, b)} inlines
 * the body with each parameter bound (through a per-call SMT {@code let}) to the translated
 * ARGUMENT's value and definedness -- constants carry their literal, attribute arguments carry
 * the attribute's own symbol, so the operation computes over the caller's actual state.
 *
 * <p>Together with the zero-argument slice this closes the main body of
 * {@code ocl.query-operation-inlining}: operations whose parameters and body translate in the
 * supported fragment now inline. One boundary surfaced by the work: a body MULTIPLYING a
 * parameter by an attribute trips the documented QF_LIA nonlinear-arithmetic guard (the
 * parameter is a symbol at translation time; only constant propagation through the let would
 * reveal literal arguments), so bodies are subject to the same linear-arithmetic discipline as
 * every other fragment term. Also still refused: String/Enum-typed parameters (their values
 * are domain indices, and binding one to a caller-side symbol needs the canonical string
 * table), non-variable receivers, and recursion.
 */
public class ParameterizedOperationInliningTest {

  private static final String MODEL =
      """
      model ParamOpInlining
      class X
      attributes
        i : Integer
        j : Integer
      operations
        plus(a : Integer, b : Integer): Integer = a + b
        echo(v : Integer): Integer = v
      end
      constraints
      context x : X inv TwoPlusThree:
        x.plus(2, 3) = 5
      context x : X inv TwoPlusThreeIsSix:
        x.plus(2, 3) = 6
      context x : X inv EchoJ:
        x.echo(x.j) = 12
      """;

  /** Parameters bind to literal arguments: plus(2, 3) is 5, never 6. */
  @Test
  public void parametersBindToLiteralArguments() throws Exception {
    ModelFinderResult match = find("TwoPlusThree", List.of("1"), List.of("1"));
    assertTrue("plus(2, 3) = 5 is a constant truth", match.satisfiable());
    assertTrue(verdictFor(match, "X::TwoPlusThree").holds());

    ModelFinderResult wrong = find("TwoPlusThreeIsSix", List.of("1"), List.of("1"));
    assertFalse("plus(2, 3) can never be 6", wrong.satisfiable());
  }

  /**
   * An ATTRIBUTE argument binds the parameter to the attribute's own symbol: echo(j) = 12 is
   * satisfiable exactly when j can be 12, proving the parameter carries the caller's state.
   */
  @Test
  public void attributeArgumentCarriesTheCallersState() throws Exception {
    ModelFinderResult match = find("EchoJ", List.of("3"), List.of("12"));
    assertTrue("j = 12 makes echo(j) = 12 hold", match.satisfiable());
    assertTrue(verdictFor(match, "X::EchoJ").holds());

    ModelFinderResult misses = find("EchoJ", List.of("3"), List.of("13"));
    assertFalse("j can only be 13, so echo(j) = 12 is genuinely unsatisfiable",
        misses.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> iDomain, List<String> jDomain) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "i", null, iDomain, null, null),
                new AttributeDomain("X", "j", null, jDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "ParamOpInlining", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
