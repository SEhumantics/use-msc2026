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
 * End-to-end regression for {@code abs}, {@code min}, and {@code max} over crisp Integers --
 * three of the operations the {@code prim.integer-arithmetic} row listed as unimplemented. All
 * three are TOTAL on defined Integer operands (no division-by-zero style undefinedness), and
 * all three encode as LINEAR ite terms over the operand symbols:
 *
 * <pre>
 *   abs(x)       = ite(x &gt;= 0, x, -x)
 *   min(a, b)    = ite(a &lt;= b, a, b)
 *   max(a, b)    = ite(a &gt;= b, a, b)
 * </pre>
 *
 * <p>USE's own evaluator confirms the semantics ({@code Op_integer_abs}: {@code Math.abs},
 * {@code Op_number_min}/{@code Op_number_max}: the smaller/larger operand). Real/UReal operands
 * keep their located refusal. The genuinely-undefined operations ({@code /}, {@code div},
 * {@code mod} with a zero divisor) remain out of slice deliberately.
 */
public class AbsMinMaxTest {

  private static final String MODEL =
      """
      model AbsMinMax
      class X
      attributes
        a : Integer
        b : Integer
        d : Integer
      end
      constraints
      context x : X inv AbsDiff:
        (x.a - x.b).abs() = x.d
      context x : X inv MinIsA:
        x.a.min(x.b) = x.a
      context x : X inv MaxIsB:
        x.a.max(x.b) = x.b
      """;

  /** abs of a difference: satisfiable exactly when d is the absolute difference. */
  @Test
  public void absOfADifferenceIsTheAbsoluteValue() throws Exception {
    ModelFinderResult match = find("AbsDiff", List.of("3"), List.of("8"), List.of("5"));
    assertTrue("|3 - 8| = 5", match.satisfiable());
    assertTrue(verdictFor(match, "X::AbsDiff").holds());

    ModelFinderResult miss = find("AbsDiff", List.of("3"), List.of("8"), List.of("1"));
    assertFalse("|3 - 8| = 5, so d = 1 is genuinely unsatisfiable", miss.satisfiable());
  }

  /** min dispatches to the smaller operand: MinIsA holds iff a &lt;= b. */
  @Test
  public void minReturnsTheSmallerOperand() throws Exception {
    ModelFinderResult holds = find("MinIsA", List.of("3"), List.of("8"), List.of("8"));
    assertTrue("a = 3 <= b = 8, so min = a", holds.satisfiable());
    assertTrue(verdictFor(holds, "X::MinIsA").holds());

    ModelFinderResult fails = find("MinIsA", List.of("8"), List.of("3"), List.of("8"));
    assertFalse("a = 8 > b = 3, so min = b, not a", fails.satisfiable());
  }

  /** max dispatches to the larger operand: MaxIsB holds iff a &lt;= b. */
  @Test
  public void maxReturnsTheLargerOperand() throws Exception {
    ModelFinderResult holds = find("MaxIsB", List.of("3"), List.of("8"), List.of("8"));
    assertTrue("a = 3 <= b = 8, so max = b", holds.satisfiable());
    assertTrue(verdictFor(holds, "X::MaxIsB").holds());

    ModelFinderResult fails = find("MaxIsB", List.of("8"), List.of("3"), List.of("8"));
    assertFalse("a = 8 > b = 3, so max = a, not b", fails.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> aDomain, List<String> bDomain, List<String> dDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "a", null, aDomain, null, null),
                new AttributeDomain("X", "b", null, bDomain, null, null),
                new AttributeDomain("X", "d", null, dDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "AbsMinMax", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
