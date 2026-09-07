package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
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
 * LIVE AUDIT for the three-valued (strong Kleene) {@code and}/{@code or} over an UNDEFINED operand
 * -- the {@code prim.boolean-undefined-strictness} shape. READ THE OUTCOMES CAREFULLY -- they are
 * NOT interchangeable:
 *
 * <ul>
 *   <li>{@code true and undef} is UNDEFINED (only {@code false and undef} is defined-FALSE); an
 *       enforced invariant whose read is UNDEFINED is not satisfied, so the run is refuted -- but
 *       the refutation is NOT a defined-false counterexample;
 *   <li>{@code false and undef} IS defined-FALSE (the decisive false dominates);
 *   <li>{@code b or undef} holds exactly when b can be true; with b forced false the read is
 *       UNDEFINED, hence violated when enforced.
 * </ul>
 */
public class KleeneStrictnessTest {

  private static final String MODEL =
      """
      model Kleene
      class X
      attributes
        b : Boolean
      end
      constraints
      context x : X inv andUndef:
        x.b and oclUndefined(Boolean)
      context x : X inv orUndef:
        x.b or oclUndefined(Boolean)
      """;

  /**
   * true-and-undef is UNDEFINED under strong Kleene (never definitely true), so enforcing refutes;
   * no quantile enclosure enters a Boolean-only encoding, so the negative is an exact bounded
   * refutation. The verdict-level UNDEFINED-vs-FALSE distinction for these same expressions is
   * pinned by OracleSemanticKernelMatrixTest (the re-evaluation runs only on witnesses).
   */
  @Test
  public void andWithUndefinedWhenTrueRefutesExactly() throws Exception {
    ModelFinderResult result = find("andUndef", List.of("true"));
    assertFalse(
        "true and undef is not definitely true, so enforcing refutes", result.satisfiable());
    assertTrue("a refuted run has no witness and is never re-checked",
        result.verdicts().isEmpty());
    assertEquals(
        "no enclosure, no unknown: the negative is exact within the bounds",
        org.tzi.use.smt.finder.ResultClassification.UNSAT_EXACT,
        org.tzi.use.smt.finder.ResultClassification.of(result, Set.of("X::andUndef")));
  }

  /** false-and-undef is also a refutation, here decided by the DEFINED-FALSE operand. */
  @Test
  public void andWithUndefinedWhenFalseIsDefinedFalse() throws Exception {
    ModelFinderResult result = find("andUndef", List.of("false"));
    assertFalse("false and undef is defined false", result.satisfiable());
  }

  /** or(undef) holds exactly when b can be true. */
  @Test
  public void orWithUndefinedHoldsWhenTheOperandCanHold() throws Exception {
    ModelFinderResult match = find("orUndef", List.of("true"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::orUndef").holds());
  }

  /** or(undef) with b forced false: the read is undefined, hence violated when enforced. */
  @Test
  public void orWithUndefinedIsViolatedWhenTheOperandIsForcedFalse() throws Exception {
    ModelFinderResult miss = find("orUndef", List.of("false"));
    System.out.println("### orUndef/false satisfiable=" + miss.satisfiable() + " outcome=" + miss.outcome());
    miss.verdicts().forEach(v -> System.out.println("### v " + v));
    assertFalse("false or undef is undefined, so enforcing must refute", miss.satisfiable());
  }

  private static ModelFinderResult find(String invariantName, List<String> bDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("X", "b", null, bDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "Kleene", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + err);
    }
    return model;
  }
}
