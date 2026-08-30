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
 * End-to-end regression for the Real widening of {@code abs}, {@code min}, and {@code max} --
 * the last remaining shape of the {@code prim.integer-arithmetic} row's arithmetic surface.
 * USE's own evaluators confirm the semantics, not assumed: {@code Op_real_abs} (Math.abs over
 * doubles, Real result) and {@code Op_number_min}/{@code Op_number_max}, whose
 * {@code ArithOperation.matches} returns Real when EITHER side is Real ({@code
 * getLeastCommonSupertype}) and whose {@code evalRealResult} applies Math.min/max over doubles
 * -- so a mixed Integer/Real operand pair widens to a Real result, exactly like +/-.
 *
 * <p>The encoding is the same linear ite shape the Integer case uses, with the Int-sorted side
 * lifted by to_real when the operation widened: sort-correct terms, nothing nonlinear.
 */
public class RealAbsMinMaxTest {

  private static final String MODEL =
      """
      model RealAbsMinMax
      class X
      attributes
        r : Real
        s : Real
        d : Real
        i : Integer
      end
      constraints
      context x : X inv RealAbsDiff:
        (x.r - x.s).abs() = x.d
      context x : X inv RealMinIsR:
        x.r.min(x.s) = x.r
      context x : X inv RealMaxIsR:
        x.r.max(x.s) = x.r
      context x : X inv MixedMinIsInt:
        x.i.min(x.r) = x.i
      """;

  /** |r - s| over Reals: satisfiable exactly when d is the absolute difference. */
  @Test
  public void realAbsOfADifferenceIsTheAbsoluteValue() throws Exception {
    ModelFinderResult match = find("RealAbsDiff", List.of("-1.5"), List.of("2.5"), List.of("4.0"));
    assertTrue("|-1.5 - 2.5| = 4.0", match.satisfiable());
    assertTrue(verdictFor(match, "X::RealAbsDiff").holds());

    ModelFinderResult miss = find("RealAbsDiff", List.of("-1.5"), List.of("2.5"), List.of("1.0"));
    assertFalse("|-1.5 - 2.5| = 4.0, so d = 1.0 is genuinely unsatisfiable", miss.satisfiable());
  }

  /** Real min dispatches to the smaller operand: RealMinIsR holds iff r &lt;= s. */
  @Test
  public void realMinReturnsTheSmallerOperand() throws Exception {
    ModelFinderResult holds = find("RealMinIsR", List.of("1.5"), List.of("2.5"), List.of("0.0"));
    assertTrue("r = 1.5 <= s = 2.5, so min = r", holds.satisfiable());
    assertTrue(verdictFor(holds, "X::RealMinIsR").holds());

    ModelFinderResult fails = find("RealMinIsR", List.of("3.5"), List.of("2.5"), List.of("0.0"));
    assertFalse("r = 3.5 > s = 2.5, so min = s, not r", fails.satisfiable());
  }

  /** Real max dispatches to the larger operand: RealMaxIsR holds iff r &gt;= s. */
  @Test
  public void realMaxReturnsTheLargerOperand() throws Exception {
    ModelFinderResult holds = find("RealMaxIsR", List.of("3.5"), List.of("2.5"), List.of("0.0"));
    assertTrue("r = 3.5 >= s = 2.5, so max = r", holds.satisfiable());
    assertTrue(verdictFor(holds, "X::RealMaxIsR").holds());

    ModelFinderResult fails = find("RealMaxIsR", List.of("1.5"), List.of("2.5"), List.of("0.0"));
    assertFalse("r = 1.5 < s = 2.5, so max = s, not r", fails.satisfiable());
  }

  /**
   * MIXED Integer/Real widens to a Real result (USE's ArithOperation.matches via
   * getLeastCommonSupertype): min(2, 3.5) = 2, compared against the Integer attribute. The
   * Int-sorted side lifts with to_real so the ite is Real-sorted end to end.
   */
  @Test
  public void mixedIntegerRealMinWidensAndDispatches() throws Exception {
    MModel model = compile();

    // i pinned to 2: min(2, 3.5) = 2 (the Integer operand), so the invariant holds.
    ModelFinderResult holds = findMixed(model, "MixedMinIsInt", List.of("2"));
    assertTrue("min(2, 3.5) = 2, so the invariant holds", holds.satisfiable());
    assertTrue(verdictFor(holds, "X::MixedMinIsInt").holds());

    // i pinned to 5: min(5, 3.5) = 3.5 -- a Real, NOT the Integer 5 -- so it is violated.
    ModelFinderResult fails = findMixed(model, "MixedMinIsInt", List.of("5"));
    assertFalse("min(5, 3.5) = 3.5, not the Integer 5", fails.satisfiable());
  }

  private static ModelFinderResult findMixed(
      MModel model, String invariantName, List<String> iDomain) throws Exception {
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "r", null, List.of("3.5"), null, null),
                new AttributeDomain("X", "s", null, List.of("2.5"), null, null),
                new AttributeDomain("X", "d", null, List.of("0.0"), null, null),
                new AttributeDomain("X", "i", null, iDomain, null, null)),
            Set.of("X::" + invariantName),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static ModelFinderResult find(
      String invariantName, List<String> rDomain, List<String> sDomain, List<String> dDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "r", null, rDomain, null, null),
                new AttributeDomain("X", "s", null, sDomain, null, null),
                new AttributeDomain("X", "d", null, dDomain, null, null),
                new AttributeDomain("X", "i", null, List.of("2", "5"), null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "RealAbsMinMax", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
