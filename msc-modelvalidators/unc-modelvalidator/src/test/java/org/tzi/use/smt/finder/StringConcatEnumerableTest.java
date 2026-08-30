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
 * End-to-end regression for {@code prim.string-operations}: concat with an ENUMERABLE second
 * operand -- {@code x.s.concat(x.t) = 'ab'} where both {@code s} and {@code t} are
 * configured-candidate strings. Per candidate pair the result is computed at translation time
 * (java concat of the two spellings) and compared against the demanded literal; the surviving
 * pairs contribute the conjunction of both sources' configured-index guards.
 *
 * <p>Before this slice concat required a compile-time constant second operand. The
 * forced-cross discriminator proves the second operand's guard is load-bearing: with t forced
 * to 'b2', the demand 'ab' must be UNSAT even though the domain also offers 'b'.
 */
public class StringConcatEnumerableTest {

  private static final String MODEL =
      """
      model StringConcatEnumerable
      class X
      attributes
        s : String
        t : String
      end
      constraints
      context x : X inv ConcatEnumerableIsAb:
        x.s.concat(x.t) = 'ab'
      context x : X inv ConcatEnumerableForcedMismatch:
        x.t = 'b2' and x.s.concat(x.t) = 'ab'
      """;

  /** s = 'a', t = 'b' gives 'ab'. */
  @Test
  public void concatWithAnEnumerableSecondOperandComputesThePair() throws Exception {
    ModelFinderResult result = find("ConcatEnumerableIsAb", List.of("a"), List.of("b"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::ConcatEnumerableIsAb").holds());
  }

  /**
   * The pair guard is load-bearing: with t forced to 'b2', the only result is 'ab2', never
   * 'ab' -- an encoding that drops the second operand's guard would claim SAT on s = 'a' alone.
   */
  @Test
  public void droppingTheSecondOperandsGuardIsDetected() throws Exception {
    ModelFinderResult result =
        find("ConcatEnumerableForcedMismatch", List.of("a"), List.of("b", "b2"));

    assertFalse(
        "s = 'a' with t = 'b2' gives 'ab2', never 'ab'",
        result.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> sDomain, List<String> tDomain) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "s", null, sDomain, null, null),
                new AttributeDomain("X", "t", null, tDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringConcatEnumerable", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
