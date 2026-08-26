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
 * Milestone 4.4: the full Boolean classification algebra of {@code THESIS_SMT_MODEL_FINDER_PLAN.md}
 * §5.2 -- explicit {@code true}/{@code false}/{@code undefined} atoms, {@code and}/{@code
 * or}/{@code not}, and the {@code all}/{@code others} aggregates -- run end to end through the real
 * solver AND the independent USE oracle, not merely compiled.
 *
 * <p>The disjunctive cases are the reason the witness check had to be generalised. §8's obligation
 * 6 requires that a SAT result is delivered only when "the observed true/false/undefined
 * classifications satisfy the compiled query term". Up to Milestone 4.3 that was implemented by
 * pinning every active invariant to one expected outcome, which works only because SATISFY and
 * COUNTEREXAMPLE each do pin every invariant. {@code uncertain j is false or uncertain k is false}
 * has no such map: {@link #aDisjunctionIsSatisfiedByEitherDisjunctSoNoSingleOutcomeMapDescribesIt}
 * exhibits two witnesses for ONE query whose outcome maps are disjoint.
 */
public class BooleanQueryAlgebraTest {

  private static final String MODEL =
      """
      model JointFragility
      class Sample
      attributes
        p : Integer
        q : Integer
      end
      constraints
      context s : Sample inv PIsOne: s.p = 1
      context s : Sample inv QIsOne: s.q = 1
      """;

  /** Spelled-out atoms plus {@code and} must reproduce what the SATISFY macro already does. */
  @Test
  public void explicitTrueAtomsUnderConjunctionReproduceSatisfy() throws Exception {
    MModel model = compile();
    Map<String, InvariantOutcome> spelledOut =
        outcomes(
            requireSat(
                SmtModelFinder.find(
                    model,
                    config(
                        model,
                        "uncertain Sample::PIsOne is true and uncertain Sample::QIsOne is true"))));

    assertEquals(InvariantOutcome.TRUE, spelledOut.get("Sample::PIsOne"));
    assertEquals(InvariantOutcome.TRUE, spelledOut.get("Sample::QIsOne"));
    assertEquals(
        outcomes(requireSat(SmtModelFinder.find(model, config(model, "satisfy")))), spelledOut);
  }

  /** The {@code all} aggregate outside any macro is the same witness predicate. */
  @Test
  public void theAllAggregateOutsideAMacroIsSatisfy() throws Exception {
    MModel model = compile();
    Map<String, InvariantOutcome> observed =
        outcomes(requireSat(SmtModelFinder.find(model, config(model, "uncertain all are true"))));

    assertEquals(InvariantOutcome.TRUE, observed.get("Sample::PIsOne"));
    assertEquals(InvariantOutcome.TRUE, observed.get("Sample::QIsOne"));
  }

  /**
   * {@code others} outside a macro resolves against the ONE referenced target and expands to the
   * complete active set minus that target -- the spelled-out form of {@code counterexample(j)}.
   */
  @Test
  public void theOthersAggregateExpandsToTheActiveSetMinusTheOneReferencedTarget()
      throws Exception {
    MModel model = compile();
    Map<String, InvariantOutcome> observed =
        outcomes(
            requireSat(
                SmtModelFinder.find(
                    model,
                    config(
                        model,
                        "uncertain Sample::PIsOne is false and uncertain others are true"))));

    assertEquals(InvariantOutcome.FALSE, observed.get("Sample::PIsOne"));
    assertEquals(InvariantOutcome.TRUE, observed.get("Sample::QIsOne"));
  }

  /**
   * §5.3's non-distributivity, in its operational form: {@code not (j is true)} admits a
   * defined-FALSE reading, and the delivered witness really is defined-false here, not undefined.
   */
  @Test
  public void negationOfATrueAtomIsSatisfiedByADefinedFalseWitness() throws Exception {
    MModel model = compile();
    Map<String, InvariantOutcome> observed =
        outcomes(
            requireSat(
                SmtModelFinder.find(
                    model,
                    config(
                        model,
                        "not (uncertain Sample::PIsOne is true) and uncertain others are true"))));

    assertEquals(InvariantOutcome.FALSE, observed.get("Sample::PIsOne"));
    assertEquals(InvariantOutcome.TRUE, observed.get("Sample::QIsOne"));
  }

  /**
   * §5.2's third "the incumbent cannot state" query: disjunctive / joint fragility. One query, two
   * witnesses, disjoint outcome maps -- so an oracle that compares against a single
   * expected-outcome map cannot be right for both, and generalising the check to evaluating the
   * compiled query over the observed verdicts is forced, not a stylistic preference.
   */
  @Test
  public void aDisjunctionIsSatisfiedByEitherDisjunctSoNoSingleOutcomeMapDescribesIt()
      throws Exception {
    MModel model = compile();
    String disjunction = "uncertain Sample::PIsOne is false or uncertain Sample::QIsOne is false";

    Map<String, InvariantOutcome> anyWitness =
        outcomes(requireSat(SmtModelFinder.find(model, config(model, disjunction))));
    assertTrue(
        "at least one disjunct must genuinely hold of the delivered witness: " + anyWitness,
        anyWitness.get("Sample::PIsOne") == InvariantOutcome.FALSE
            || anyWitness.get("Sample::QIsOne") == InvariantOutcome.FALSE);

    Map<String, InvariantOutcome> viaSecondDisjunct =
        outcomes(
            requireSat(
                SmtModelFinder.find(
                    model,
                    config(model, "uncertain Sample::PIsOne is true and (" + disjunction + ")"))));
    assertEquals(InvariantOutcome.TRUE, viaSecondDisjunct.get("Sample::PIsOne"));
    assertEquals(InvariantOutcome.FALSE, viaSecondDisjunct.get("Sample::QIsOne"));

    Map<String, InvariantOutcome> viaFirstDisjunct =
        outcomes(
            requireSat(
                SmtModelFinder.find(
                    model,
                    config(model, "uncertain Sample::QIsOne is true and (" + disjunction + ")"))));
    assertEquals(InvariantOutcome.FALSE, viaFirstDisjunct.get("Sample::PIsOne"));
    assertEquals(InvariantOutcome.TRUE, viaFirstDisjunct.get("Sample::QIsOne"));
  }

  /**
   * An algebraically unsatisfiable query returns no witness rather than a weakened one: both
   * invariants here are always defined, so demanding neither-true-nor-false demands undefined.
   */
  @Test
  public void aQueryOnlyAnUndefinedReadingCouldSatisfyReturnsNoWitness() throws Exception {
    MModel model = compile();
    ModelFinderResult result =
        SmtModelFinder.find(
            model,
            config(
                model,
                "not (uncertain Sample::PIsOne is true) and not (uncertain Sample::PIsOne is"
                    + " false)"));

    assertFalse(
        "a configured Integer attribute is always defined, so this demands the impossible",
        result.satisfiable());
  }

  /**
   * §5.2's other two "the incumbent cannot state" queries both mention {@code nominal}. Milestone
   * 4.4 could only compile them, because no independent NOMINAL-erasure oracle over the
   * reconstructed witness existed; Milestone 4.5 supplies one, so they now run end to end.
   *
   * <p>This model is CRISP, which is §5.1's degenerate case where the two modes coincide -- so both
   * diagnostic queries ask for a discrepancy that cannot exist here and are correctly UNSAT, while
   * the agreeing query is satisfiable. The positive case is what stops the two refusals from being
   * vacuous: it proves the nominal oracle really does report on this invariant rather than the
   * solve failing for some unrelated reason.
   */
  @Test
  public void nominalAtomsRunEndToEndAndCoincideWithUncertainOnACrispModel() throws Exception {
    MModel model = compile();
    for (String query :
        List.of(
            "nominal Sample::PIsOne is true and uncertain Sample::PIsOne is undefined",
            "nominal Sample::PIsOne is false and uncertain Sample::PIsOne is true")) {
      assertFalse(
          "'" + query + "' asks for a nominal/uncertain discrepancy a crisp model cannot have",
          SmtModelFinder.find(model, config(model, query)).satisfiable());
    }

    ModelFinderResult agreeing =
        SmtModelFinder.find(
            model,
            config(
                model, "nominal Sample::PIsOne is true and uncertain Sample::PIsOne is" + " true"));
    assertTrue(
        "the two modes coincide on a crisp invariant, and BOTH were independently checked",
        agreeing.satisfiable());
    assertEquals(InvariantOutcome.TRUE, outcomes(agreeing).get("Sample::PIsOne"));
  }

  private static ModelFinderResult requireSat(ModelFinderResult result) {
    assertTrue("expected a witness", result.satisfiable());
    return result;
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
                "Sample", "p", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(2)),
            new AttributeDomain(
                "Sample", "q", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(2))),
        Set.of("Sample::PIsOne", "Sample::QIsOne"),
        QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
  }

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "JointFragility", err, factory);
    err.flush();
    return model;
  }
}
