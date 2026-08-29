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
 * End-to-end regression for {@code prim.integer-arithmetic}'s Real-division slice: {@code x.n / d}
 * over crisp Integer operands. USE's own {@code Op_number_div} is REAL division on Integers
 * ({@code evalRealResult}, confirmed against the use-core bytecode), so the encoding lifts the
 * dividend with {@code to_real} and divides in the Reals -- a numeral divisor is linear; a
 * variable divisor whose configured domain is a finite literal set case-splits exactly the way
 * the integer {@code div}/{@code mod} slices do. Comparisons against Integer-typed operands
 * lift them, keeping the emitted text sort-correct under strict SMT-LIB (the UInteger
 * {@code to_real} portability precedent).
 *
 * <p>Before this slice {@code /} refused outright and free-standing Real literals refused
 * everywhere ({@code visitConstReal} threw BEYOND_FIRST_FRAGMENT). Test values are exactly
 * representable in both the solver's exact rationals and USE's double arithmetic (halves,
 * quarters), so the two agree without any rounding-enclosure machinery.
 */
public class RealDivisionTest {

  private static final String MODEL =
      """
      model RealDivision
      class X
      attributes
        n : Integer
        d : Integer
      end
      constraints
      context x : X inv DivConstIsThreePointFive:
        x.n / 2 = 3.5
      context x : X inv DivConstIsFour:
        x.n / 2 = 4
      context x : X inv DivConstGtThree:
        x.n / 2 > 3
      context x : X inv DivVarIsThreePointFive:
        x.n / x.d = 3.5
      context x : X inv DivVarByZero:
        x.d = 0 and x.n / x.d = 3.5
      context x : X inv DivConstIntLiteral:
        x.n / 4 = 2
      context x : X inv DivLetRoundTrip:
        let r : Real = x.n / 2 in r = 3.5
      """;

  /** 7 / 2 = 3.5: the basic constant-divisor shape against a Real literal. */
  @Test
  public void constantDivisorCarriesTheExactRationalValue() throws Exception {
    ModelFinderResult match = find("DivConstIsThreePointFive", List.of("7"), List.of("2"));

    assertTrue("7 / 2 = 3.5", match.satisfiable());
    assertTrue(verdictFor(match, "X::DivConstIsThreePointFive").holds());
  }

  /** 8 / 2 = 4.0, never 3.5: the wrong dividend is genuinely unsatisfiable. */
  @Test
  public void aWrongDividendIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult miss = find("DivConstIsThreePointFive", List.of("8"), List.of("2"));

    assertFalse("8 / 2 = 4.0, never 3.5", miss.satisfiable());
  }

  /** Ordered comparison through the Real result: 7/2 > 3 holds, 6/2 > 3 does not. */
  @Test
  public void orderedComparisonReadsTheRealResult() throws Exception {
    ModelFinderResult above = find("DivConstGtThree", List.of("7"), List.of("2"));
    assertTrue("7 / 2 = 3.5 > 3", above.satisfiable());

    ModelFinderResult at = find("DivConstGtThree", List.of("6"), List.of("2"));
    assertFalse("6 / 2 = 3.0, not > 3", at.satisfiable());
  }

  /** The variable divisor case-split: only d = 2 makes 7 / d land on 3.5. */
  @Test
  public void variableDivisorSelectsTheBranchMatchingTheDemand() throws Exception {
    ModelFinderResult byTwo = find("DivVarIsThreePointFive", List.of("7"), List.of("2"));
    assertTrue("7 / 2 = 3.5", byTwo.satisfiable());
    assertTrue(verdictFor(byTwo, "X::DivVarIsThreePointFive").holds());

    ModelFinderResult byFour = find("DivVarIsThreePointFive", List.of("7"), List.of("4"));
    assertFalse("7 / 4 = 1.75, never 3.5", byFour.satisfiable());
  }

  /** A forced zero divisor is undefined: the equality cannot hold (never a branch fallback). */
  @Test
  public void aForcedZeroDivisorIsUndefinedNotAFallbackValue() throws Exception {
    ModelFinderResult result = find("DivVarByZero", List.of("7"), List.of("0", "2"));

    assertFalse(
        "d forced to 0 makes the division undefined, so the equality cannot hold",
        result.satisfiable());
  }

  /** Integer literals are well-sorted against the Real result (numerals are sort-polymorphic). */
  @Test
  public void anIntegerLiteralComparesAgainstTheRealResult() throws Exception {
    ModelFinderResult match = find("DivConstIntLiteral", List.of("8"), List.of("2"));

    assertTrue("8 / 4 = 2.0, compared against the integer literal 2", match.satisfiable());
    assertTrue(verdictFor(match, "X::DivConstIntLiteral").holds());
  }

  /** A Real-typed let binding composes with the division result. */
  @Test
  public void aRealLetBindingCarriesTheDivisionResult() throws Exception {
    ModelFinderResult result = find("DivLetRoundTrip", List.of("7"), List.of("2"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::DivLetRoundTrip").holds());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> nDomain, List<String> dDomain) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "n", null, nDomain, null, null),
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
    MModel model = USECompiler.compileSpecification(MODEL, "RealDivision", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
