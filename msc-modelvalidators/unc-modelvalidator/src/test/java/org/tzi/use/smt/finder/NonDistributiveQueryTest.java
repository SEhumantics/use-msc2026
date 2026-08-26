package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * The Milestone 4.4 obligation that {@code not false(m,i)} is NOT usable as a substitute for {@code
 * undef(m,i)} -- demonstrated with real witnesses from the real solver and the real USE oracle, not
 * asserted in a comment.
 *
 * <p>{@code THESIS_SMT_MODEL_FINDER_PLAN.md} §5.3: "`not uncertain[i]` is not `uncertain[not i]`.
 * The first admits undefined; the second requires defined-false." Read through §5.1's atoms, {@code
 * not false(m,i)} is exactly {@code undef(m,i) or true(m,i)} -- strictly weaker than {@code
 * undef(m,i)} whenever the invariant can be defined-true at all.
 *
 * <p>The fixture makes all three classifications live and separable:
 *
 * <ul>
 *   <li>{@code MarkerIsOne} is always DEFINED (a configured Integer attribute always carries a
 *       value), and is true or false according to the value chosen -- so {@code not false} has a
 *       defined-true witness while {@code undef} has none at all.
 *   <li>{@code NeverDefined} is always UNDEFINED, which is what makes the {@code undef} atom itself
 *       non-vacuous rather than trivially unsatisfiable everywhere.
 * </ul>
 */
public class NonDistributiveQueryTest {

  private static final String MODEL =
      """
      model NonDistributive
      class Sample
      attributes
        marker : Integer
      end
      constraints
      context s : Sample inv MarkerIsOne: s.marker = 1
      context s : Sample inv NeverDefined: oclUndefined(Boolean)
      """;

  /**
   * The witness that settles it: a state satisfying {@code not false(uncertain, MarkerIsOne)} in
   * which USE independently reports MarkerIsOne DEFINED-TRUE. Substituting {@code not false} for
   * {@code undef} would have accepted this state as evidence of undefinedness; the real oracle
   * calls it TRUE.
   */
  @Test
  public void notFalseAdmitsADefinedTrueWitnessThatUndefWouldNeverAccept() throws Exception {
    MModel model = compile();

    ModelFinderResult notFalse =
        SmtModelFinder.find(model, config(model, "not (uncertain Sample::MarkerIsOne is false)"));
    assertTrue("'not false' must be satisfiable here", notFalse.satisfiable());
    assertEquals(
        "the witness 'not false' admits is DEFINED-TRUE, which 'undefined' excludes outright",
        InvariantOutcome.TRUE,
        outcomes(notFalse).get("Sample::MarkerIsOne"));

    ModelFinderResult undefined =
        SmtModelFinder.find(model, config(model, "uncertain Sample::MarkerIsOne is undefined"));
    assertFalse(
        "the same invariant is always defined, so the genuine 'undefined' atom has no witness"
            + " at all -- the two queries do not denote the same set of states",
        undefined.satisfiable());
  }

  /**
   * The undefined atom is not vacuously unsatisfiable: on an invariant that really can be undefined
   * it finds a witness USE independently classifies UNDEFINED, so the previous test's UNSAT is a
   * property of {@code MarkerIsOne}, not of the atom's encoding.
   */
  @Test
  public void theUndefinedAtomFindsAGenuinelyUndefinedWitness() throws Exception {
    MModel model = compile();
    ModelFinderResult result =
        SmtModelFinder.find(model, config(model, "uncertain Sample::NeverDefined is undefined"));

    assertTrue(result.satisfiable());
    assertEquals(InvariantOutcome.UNDEFINED, outcomes(result).get("Sample::NeverDefined"));
  }

  /**
   * And {@code not false} is genuinely weaker on BOTH sides: the undefined invariant satisfies it
   * too. So {@code not false(m,i)} denotes {@code {TRUE, UNDEFINED}} while {@code undef(m,i)}
   * denotes {@code {UNDEFINED}} -- a strict containment, both elements exhibited.
   */
  @Test
  public void notFalseAlsoAdmitsTheUndefinedReading() throws Exception {
    MModel model = compile();
    ModelFinderResult result =
        SmtModelFinder.find(model, config(model, "not (uncertain Sample::NeverDefined is false)"));

    assertTrue(result.satisfiable());
    assertEquals(InvariantOutcome.UNDEFINED, outcomes(result).get("Sample::NeverDefined"));
  }

  /** The remaining cell of the truth table: the defined-false reading is reachable too. */
  @Test
  public void theFalseAtomFindsADefinedFalseWitness() throws Exception {
    MModel model = compile();
    ModelFinderResult result =
        SmtModelFinder.find(model, config(model, "uncertain Sample::MarkerIsOne is false"));

    assertTrue(result.satisfiable());
    assertEquals(InvariantOutcome.FALSE, outcomes(result).get("Sample::MarkerIsOne"));
  }

  private static Map<String, InvariantOutcome> outcomes(ModelFinderResult result) {
    Map<String, InvariantOutcome> outcomes = new LinkedHashMap<>();
    for (InvariantVerdict verdict : result.verdicts()) {
      outcomes.put(verdict.invariantName(), verdict.outcome());
    }
    return outcomes;
  }

  private static AnalysisConfiguration config(MModel model, String query) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Sample", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain(
                "Sample", "marker", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(2))),
        Set.of("Sample::MarkerIsOne", "Sample::NeverDefined"),
        QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
  }

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "NonDistributive", err, factory);
    err.flush();
    return model;
  }
}
