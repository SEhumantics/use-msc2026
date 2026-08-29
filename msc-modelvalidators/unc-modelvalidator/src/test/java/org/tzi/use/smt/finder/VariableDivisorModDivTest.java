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
 * End-to-end regression for {@code prim.integer-arithmetic}'s variable-divisor slice: {@code mod}
 * and {@code div} by a divisor that is a crisp Integer ATTRIBUTE with a finite configured domain.
 * The divisor's value is provably one of those configured literals, so the translation case-splits
 * over them -- each branch a numeral-divisor rem/div, linear in the pinned QF_LIA logic -- exactly
 * the trick {@code integerDivision} already uses for the filtered-population-count divisor. A
 * divisor domain containing 0 keeps the fail-closed zero semantics: undefined under that branch,
 * violated (never satisfied) there.
 *
 * <p>Before this slice a non-constant divisor refused with a located message ({@code mod} needed a
 * compile-time literal; {@code div} accepted only literals or the filtered-allInstances size).
 * USE's own evaluator confirms the per-branch semantics (Java truncation toward zero).
 */
public class VariableDivisorModDivTest {

  private static final String MODEL =
      """
      model VarDivisor
      class X
      attributes
        n : Integer
        d : Integer
      end
      constraints
      context x : X inv ModVarIsOne:
        x.n.mod(x.d) = 1
      context x : X inv ModVarIsTwo:
        x.n.mod(x.d) = 2
      context x : X inv DivVarIsThree:
        x.n div x.d = 3
      context x : X inv DivVarIsTwo:
        x.n div x.d = 2
      context x : X inv ZeroDivisorModIsOne:
        x.d = 0 and x.n.mod(x.d) = 1
      """;

  private static final List<String> N_SEVEN = List.of("7");

  /** 7 rem 2 = 1: the d = 2 branch satisfies ModVarIsOne over the domain {2, 5}. */
  @Test
  public void modSelectsTheBranchMatchingTheDivisor() throws Exception {
    ModelFinderResult result = find("ModVarIsOne", N_SEVEN, List.of("2", "5"));

    assertTrue("d = 2 gives 7 mod 2 = 1", result.satisfiable());
    assertTrue(verdictFor(result, "X::ModVarIsOne").holds());
  }

  /** 7 rem 5 = 2: the chain's OTHER branch is reachable and selects d = 5. */
  @Test
  public void modSelectsTheOtherBranchWhenTheDemandRequiresIt() throws Exception {
    ModelFinderResult result = find("ModVarIsTwo", N_SEVEN, List.of("2", "5"));

    assertTrue("d = 5 gives 7 mod 5 = 2", result.satisfiable());
    assertTrue(verdictFor(result, "X::ModVarIsTwo").holds());
  }

  /** 7 rem 3 = 1, never 2: with only d = 3 configured the demand is genuinely unsatisfiable. */
  @Test
  public void anUnreachableModDemandIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult miss = find("ModVarIsTwo", N_SEVEN, List.of("3"));

    assertFalse("7 rem 3 = 1, never 2", miss.satisfiable());
  }

  /** A zero-only divisor domain is undefined for every value of n: fail-closed, unsatisfiable. */
  @Test
  public void aZeroOnlyDivisorDomainStaysFailClosed() throws Exception {
    ModelFinderResult result = find("ModVarIsOne", N_SEVEN, List.of("0"));

    assertFalse("mod by 0 is undefined everywhere", result.satisfiable());
  }

  /** 7 div 2 = 3 and 7 div 3 = 2: the div chain picks the divisor matching the demand. */
  @Test
  public void divSelectsTheBranchMatchingTheDivisor() throws Exception {
    ModelFinderResult byTwo = find("DivVarIsThree", N_SEVEN, List.of("2", "3"));
    assertTrue("d = 2 gives 7 div 2 = 3", byTwo.satisfiable());
    assertTrue(verdictFor(byTwo, "X::DivVarIsThree").holds());

    ModelFinderResult byThree = find("DivVarIsTwo", N_SEVEN, List.of("2", "3"));
    assertTrue("d = 3 gives 7 div 3 = 2", byThree.satisfiable());
    assertTrue(verdictFor(byThree, "X::DivVarIsTwo").holds());
  }

  /** 7 div 5 = 1, never 3: only d = 5 configured makes the demand genuinely unsatisfiable. */
  @Test
  public void anUnreachableDivDemandIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult miss = find("DivVarIsThree", N_SEVEN, List.of("5"));

    assertFalse("7 div 5 = 1, never 3", miss.satisfiable());
  }

  /**
   * The zero branch must be UNDEFINED, not a fallback to a sibling branch's value: with d forced
   * to 0, `7 mod 0` has no value, so the equality is undefined (hence violated). An encoding that
   * let the ite chain fall through to the d = 2 branch's value would wrongly report SAT with a
   * witness whose mod-by-zero USE's own evaluator rejects.
   */
  @Test
  public void aZeroValuedDivisorIsUndefinedNotAFallbackValue() throws Exception {
    ModelFinderResult result = find("ZeroDivisorModIsOne", N_SEVEN, List.of("0", "2"));

    assertFalse(
        "d forced to 0 makes mod undefined, so the equality cannot hold -- the ite chain must"
            + " not leak a sibling branch's value",
        result.satisfiable());
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
    MModel model = USECompiler.compileSpecification(MODEL, "VarDivisor", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
