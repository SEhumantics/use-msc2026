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
 * End-to-end regression for {@code mod} and constant-divisor {@code div} over crisp Integers.
 *
 * <p>USE's {@code Op_integer_mod} is Java {@code %}: truncation toward zero, sign following the
 * dividend. {@code div} likewise truncates toward zero. The encoding maps each case to linear
 * ite terms over the (constant) divisor magnitude, refusing zero divisors with a located
 * message (mod/div by zero is undefined for every value, so the demanding invariant is
 * genuinely unsatisfiable).
 */
public class ModDivTest {

  private static final String MODEL =
      """
      model ModDiv
      class Y
      attributes
        a : Integer
      end
      constraints
      context y : Y inv ModNeg:
        y.a.mod(2) = -1
      context y : Y inv ModPos:
        y.a.mod(2) = 1
      context y : Y inv DivNeg:
        y.a div 2 = -3
      context y : Y inv DivPos:
        y.a div 2 = 3
      context y : Y inv ModByZero:
        y.a.mod(0) = 0
      context y : Y inv DivByZero:
        y.a div 0 = 0
      """;

  /**
   * a = -7: mod 2 follows the dividend's sign → -1 (truncated), never the Euclidean +1.
   * div 2 truncates toward zero → -3; div 2 = 3 is the wrong polarity.
   */
  @Test
  public void modAndDivOnANegativeDividendFollowTruncationTowardZero() throws Exception {
    ModelFinderResult neg = find("ModNeg", List.of("-7"));
    assertTrue("a = -7 forces a mod 2 = -1 (truncation)", neg.satisfiable());
    assertTrue(verdictFor(neg, "Y::ModNeg").holds());

    ModelFinderResult pos = find("ModPos", List.of("-7"));
    assertFalse("a mod 2 = 1 cannot hold when a = -7", pos.satisfiable());

    ModelFinderResult divNeg = find("DivNeg", List.of("-7"));
    assertTrue("-7 div 2 truncates toward zero: -3", divNeg.satisfiable());
    assertTrue(verdictFor(divNeg, "Y::DivNeg").holds());

    ModelFinderResult divPos = find("DivPos", List.of("-7"));
    assertFalse("-7 div 2 truncates toward zero (-3), so = 3 cannot hold",
        divPos.satisfiable());
  }

  /** The zero-divisor cases are genuinely undefined for every value: unsatisfiable. */
  @Test
  public void zeroDivisorsAreGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult modZero = find("ModByZero", List.of("-7"));
    assertFalse("mod by zero is undefined for every value", modZero.satisfiable());

    ModelFinderResult divZero = find("DivByZero", List.of("-7"));
    assertFalse("div by zero is undefined for every value", divZero.satisfiable());
  }

  private static ModelFinderResult find(String invariantName, List<String> aDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Y", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("Y", "a", null, aDomain, null, null)),
            Set.of("Y::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "ModDiv", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
