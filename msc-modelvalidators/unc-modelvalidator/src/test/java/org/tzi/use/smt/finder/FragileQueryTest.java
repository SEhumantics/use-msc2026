package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.sys.MObject;

/**
 * Milestone 4.5: {@code fragile(j)} as a first-class nominal/U-aware discrepancy query.
 *
 * <p>The witness predicate is the proposal's, verbatim:
 *
 * <pre>
 *   W_FRAGILE(j)(S,s) = B_K(S) and T_N(E(I_j),S,s) and F_U(I_j,S,s)
 *                       and AND over i in A_K\{j} of T_U(I_i,S,s)
 * </pre>
 *
 * <p>All three conjuncts demand DEFINEDNESS: the erased target must be defined-true, the U-aware
 * target defined-false, and every other active invariant defined-true. "Not true" is never enough,
 * so an undefined target is not fragility.
 *
 * <p>Nominal erasure is defined over EXPRESSIONS, not only over stored values. In particular the
 * confidence projection is REMOVED after erasing its UBoolean operand, and {@code E_B} turns a
 * numeric comparison into its CRISP comparison -- the {@code p >= 0.5} rule applies only to a
 * STORED UBoolean probability. Several fixtures here sit exactly on the tie {@code mu == literal},
 * which is precisely where those two readings can disagree.
 */
public class FragileQueryTest {

  private static final String RELIABLY_FAST = "UnidentifiedObject::ReliablyFast";

  /**
   * The definition of done's named witness. Under SATISFY the same section is correctly UNSAT --
   * USE's own evaluator assigns {@code speed > 0.30} only p = 0.6914624677876595, far below the
   * required 0.95 -- while nominal erasure reads the crisp {@code 0.31 > 0.30} and accepts. That
   * discrepancy is exactly what {@code fragile(j)} asks Z3 to synthesise.
   */
  @Test
  public void fragileFindsTheNominalErasureWitnessThatSatisfyCorrectlyRejects() throws Exception {
    MModel model = compile("ReliablyFast.use");

    ModelFinderResult satisfy =
        SmtModelFinder.find(model, config(model, "ReliablyFast.properties", "nominalErasure"));
    assertFalse(
        "the U-aware reading rejects speed=UReal(0.31,0.02) at 0.95 confidence",
        satisfy.satisfiable());

    ModelFinderResult fragile =
        SmtModelFinder.find(
            model, config(model, "ReliablyFast.properties", "nominalErasureFragile"));

    assertTrue("the erased reading accepts the very same snapshot", fragile.satisfiable());
    assertEquals(1, fragile.verdicts().size());
    assertEquals(InvariantOutcome.FALSE, outcomes(fragile).get(RELIABLY_FAST));
    assertEquals(
        "exactly one UnidentifiedObject is reconstructed",
        1,
        fragile.system().state().objectsOfClass(model.getClass("UnidentifiedObject")).size());
    URealValue speed = reconstructedSpeed(model, fragile);
    assertEquals(0.31, speed.value(), 0.0);
    assertEquals(0.02, speed.uncertainty(), 0.0);
    assertTrue("nominal erasure accepts it: 0.31 > 0.30", speed.value() > 0.30);
  }

  /**
   * The proposal proves {@code W_FRAGILE(j) => W_CEX(j)}. Checked on the delivered witness rather
   * than argued: the fragile snapshot's own independent USE verdicts are exactly a targeted
   * counterexample's (target defined-false, every other active invariant defined-true), and the
   * counterexample query is independently satisfiable on the same configuration.
   */
  @Test
  public void everyFragileWitnessIsAlsoACounterexample() throws Exception {
    MModel model = compile("ReliablyFast.use");

    ModelFinderResult fragile =
        SmtModelFinder.find(
            model, config(model, "ReliablyFast.properties", "nominalErasureFragile"));
    ModelFinderResult counterexample =
        SmtModelFinder.find(
            model, config(model, "ReliablyFast.properties", "nominalErasureCounterexample"));

    assertTrue(fragile.satisfiable());
    assertTrue(counterexample.satisfiable());

    Map<String, InvariantOutcome> observed = outcomes(fragile);
    assertEquals(
        "the fragile witness has the target defined-FALSE, which is W_CEX's own requirement",
        InvariantOutcome.FALSE,
        observed.get(RELIABLY_FAST));
    observed.forEach(
        (name, outcome) -> {
          if (!name.equals(RELIABLY_FAST)) {
            assertEquals(
                "every other active invariant is U-aware true", InvariantOutcome.TRUE, outcome);
          }
        });
    assertEquals(
        reconstructedSpeed(model, fragile).value(),
        reconstructedSpeed(model, counterexample).value(),
        0.0);
  }

  /**
   * The tie trap, in the direction the definition of done names. With the representative EXACTLY
   * equal to the literal, the crisp comparison {@code 0.30 > 0.30} is defined-FALSE, so the erased
   * target is not true and the snapshot is not fragile at all.
   *
   * <p>The second half proves that UNSAT is not vacuous: a witness at the tie really does exist,
   * and the erasure really does classify it defined-FALSE rather than refusing to classify it.
   */
  @Test
  public void theExactTieIsNotFragileBecauseTheCrispComparisonIsFalse() throws Exception {
    MModel model = compile("ReliablyFast.use");

    assertFalse(
        "0.30 > 0.30 is false, so nominal erasure does not accept the tie",
        SmtModelFinder.find(model, config(model, "ReliablyFast.properties", "tieFragile"))
            .satisfiable());

    ModelFinderResult nominalFalse =
        SmtModelFinder.find(model, config(model, "ReliablyFast.properties", "tieNominalFalse"));
    assertTrue(
        "the same tie snapshot is a defined-FALSE reading in BOTH modes",
        nominalFalse.satisfiable());
    assertEquals(InvariantOutcome.FALSE, outcomes(nominalFalse).get(RELIABLY_FAST));
    assertEquals(0.30, reconstructedSpeed(model, nominalFalse).value(), 0.0);
  }

  /**
   * The same tie against {@code >=}, where the crisp reading flips: {@code 0.30 >= 0.30} is
   * defined-TRUE while USE's own evaluator still assigns the uncertain comparison only p =
   * 0.4999999994746490. That is a genuine fragile witness, and one the "apply the stored-UBoolean p
   * &gt;= 0.5 rule to the un-erased comparison" shortcut cannot produce -- the shortcut reads the
   * nominal target as FALSE here.
   */
  @Test
  public void theExactTieOnAGreaterOrEqualThresholdIsGenuinelyFragile() throws Exception {
    MModel model = compile("ReliablyFastOrEqual.use");

    ModelFinderResult fragile =
        SmtModelFinder.find(model, config(model, "ReliablyFastOrEqual.properties", "tieFragile"));

    assertTrue(fragile.satisfiable());
    assertEquals(
        InvariantOutcome.FALSE, outcomes(fragile).get("UnidentifiedObject::ReliablyFastOrEqual"));
    URealValue speed = reconstructedSpeed(model, fragile);
    assertEquals(0.30, speed.value(), 0.0);
    assertEquals(0.02, speed.uncertainty(), 0.0);
  }

  /**
   * The mirror-image tie against {@code <}: the crisp reading {@code 0.30 < 0.30} is defined-FALSE
   * while USE assigns the uncertain comparison p = 0.5000000005253510, so the {@code p >= 0.5}
   * shortcut would call the erased target TRUE. Only the crisp comparison is the spec's rule, and
   * the independent oracle is what enforces it here.
   */
  @Test
  public void nominalErasureUsesTheCrispComparisonNotTheStoredProbabilityRule() throws Exception {
    MModel model = compile("ReliablySlow.use");

    ModelFinderResult result =
        SmtModelFinder.find(model, config(model, "ReliablySlow.properties", "tieNominalFalse"));

    assertTrue(result.satisfiable());
    assertEquals(InvariantOutcome.FALSE, outcomes(result).get("UnidentifiedObject::ReliablySlow"));
    assertEquals(0.30, reconstructedSpeed(model, result).value(), 0.0);
  }

  /**
   * W_FRAGILE's "every OTHER active invariant is U-aware true" conjunct is load-bearing, not
   * decoration. The unsatisfiable section's only configured rank violates {@code RankIsOne}, so no
   * snapshot can hold it; the satisfiable section offers a rank that does, and the delivered
   * witness must actually use it.
   */
  @Test
  public void aFalseNonTargetPreventsFragileAttribution() throws Exception {
    MModel model = compile("FragileWithCompanion.use");

    assertFalse(
        "no snapshot can make RankIsOne true, so nothing is fragile here",
        SmtModelFinder.find(
                model, config(model, "FragileWithCompanion.properties", "companionUnsatisfiable"))
            .satisfiable());

    ModelFinderResult fragile =
        SmtModelFinder.find(
            model, config(model, "FragileWithCompanion.properties", "companionSatisfiable"));
    assertTrue(fragile.satisfiable());
    Map<String, InvariantOutcome> observed = outcomes(fragile);
    assertEquals(InvariantOutcome.FALSE, observed.get("UnidentifiedObject::ReliablyFast"));
    assertEquals(InvariantOutcome.TRUE, observed.get("UnidentifiedObject::RankIsOne"));
    assertEquals(IntegerValue.valueOf(1), reconstructedRank(model, fragile));
  }

  /**
   * Undefined is deliberately excluded from all three of W_FRAGILE's conjuncts. {@code s.marker >
   * oclUndefined(Integer)} is UNDEFINED in both modes -- the ordered comparators are strict -- so
   * it is not fragile, and an implementation reading "nominal true" as "not false" or "uncertain
   * false" as "not true" would wrongly deliver it.
   */
  @Test
  public void anUndefinedTargetIsNotFragility() throws Exception {
    MModel model =
        compileSource(
            """
            model UndefinedTarget
            class Sample
            attributes
              marker : Integer
            end
            constraints
            context s : Sample inv MarkerAboveUndefined: s.marker > oclUndefined(Integer)
            """,
            "UndefinedTarget");

    assertFalse(
        "an undefined target is not a fragile one",
        SmtModelFinder.find(model, sampleConfig(model, "fragile(Sample::MarkerAboveUndefined)"))
            .satisfiable());

    ModelFinderResult undefined =
        SmtModelFinder.find(
            model,
            sampleConfig(
                model,
                "undef(nominal, Sample::MarkerAboveUndefined) and undef(uncertain,"
                    + " Sample::MarkerAboveUndefined)"));
    assertTrue(
        "the target really is UNDEFINED in both modes, so the refusal above is not vacuous",
        undefined.satisfiable());
    assertEquals(
        InvariantOutcome.UNDEFINED, outcomes(undefined).get("Sample::MarkerAboveUndefined"));
  }

  /**
   * THESIS_SMT_MODEL_FINDER_PLAN.md §5.2's second "query the incumbent cannot state", now running
   * end to end rather than only compiling: an OVER-CONSERVATIVE requirement, where nominal erasure
   * rejects what uncertainty-aware evaluation accepts. Milestone 4.4 could not run it because
   * nothing independently checked a nominal classification.
   */
  @Test
  public void theOverConservativeNominalDiagnosticQueryRunsEndToEnd() throws Exception {
    MModel model = compile("OverConservative.use");

    ModelFinderResult result =
        SmtModelFinder.find(
            model, config(model, "OverConservative.properties", "overConservative"));

    assertTrue(result.satisfiable());
    assertEquals(
        "USE assigns p = 0.1586552596 >= 0.05, so the U-aware target is defined-TRUE",
        InvariantOutcome.TRUE,
        outcomes(result).get("UnidentifiedObject::OverConservative"));
    URealValue speed = reconstructedSpeed(model, result);
    assertEquals(0.28, speed.value(), 0.0);
    assertTrue("while nominal erasure rejects it: 0.28 > 0.30 is false", speed.value() < 0.30);
  }

  private static Map<String, InvariantOutcome> outcomes(ModelFinderResult result) {
    Map<String, InvariantOutcome> outcomes = new LinkedHashMap<>();
    for (InvariantVerdict verdict : result.verdicts()) {
      outcomes.put(verdict.invariantName(), verdict.outcome());
    }
    return outcomes;
  }

  private static URealValue reconstructedSpeed(MModel model, ModelFinderResult result) {
    return (URealValue) attribute(model, result, "speed");
  }

  private static IntegerValue reconstructedRank(MModel model, ModelFinderResult result) {
    return (IntegerValue) attribute(model, result, "rank");
  }

  private static org.tzi.use.uml.ocl.value.Value attribute(
      MModel model, ModelFinderResult result, String name) {
    MObject object =
        result
            .system()
            .state()
            .objectsOfClass(model.getClass("UnidentifiedObject"))
            .iterator()
            .next();
    return object.state(result.system().state()).attributeValue(name);
  }

  private static AnalysisConfiguration sampleConfig(MModel model, String query) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Sample", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain(
                "Sample", "marker", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(3))),
        Set.of("Sample::MarkerAboveUndefined"),
        QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
  }

  private static AnalysisConfiguration config(MModel model, String resource, String section)
      throws URISyntaxException {
    return ConfigurationReader.normalize(
            ConfigurationReader.read(resourcePath(resource), section),
            ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compile(String resource) throws Exception {
    Path file = resourcePath(resource);
    return compileSource(Files.readString(file), file.getFileName().toString());
  }

  private static MModel compileSource(String source, String name) {
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new IllegalStateException("model did not compile: " + name);
    }
    return model;
  }

  private static Path resourcePath(String name) throws URISyntaxException {
    return Path.of(Objects.requireNonNull(FragileQueryTest.class.getResource("/" + name)).toURI());
  }
}
