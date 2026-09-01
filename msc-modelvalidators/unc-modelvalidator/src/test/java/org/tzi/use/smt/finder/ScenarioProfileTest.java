package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.config.Scenario;
import org.tzi.use.smt.config.ScenarioProfile;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * Milestone 4.6: EXISTS, COVER and UNIFORM are three DIFFERENT strengths of the same witness
 * predicate, and this suite exists to make a weaker one visibly unable to pass for a stronger one.
 *
 * <p>The fixture model deliberately pairs a positive confidence demand with a NEGATED one so the
 * satisfying window SHIFTS with the measurement uncertainty rather than shrinking into itself. With
 * nested windows COVER would trivially imply UNIFORM and the "no profile is accepted by silently
 * running a weaker one" requirement would be untestable.
 */
public class ScenarioProfileTest {

  private static final String FAST = "UnidentifiedObject::ConfidentlyFast";
  private static final String NOT_VERY_FAST = "UnidentifiedObject::NotConfidentlyVeryFast";

  // ---------------------------------------------------------------- (a) EXISTS alone

  @Test
  public void existsSucceedsWhereCoverAndUniformAreBothRefuted() throws Exception {
    MModel model = compile("ScenarioProfiles.use");

    ModelFinderResult exists = SmtModelFinder.find(model, configuration(model, "existsOnly"));
    assertEquals(ScenarioProfile.EXISTS, exists.profile());
    assertEquals(ProfileOutcome.SATISFIED, exists.outcome());
    assertTrue(exists.satisfiable());
    assertEquals(1, exists.scenarios().size());
    assertEquals(0.02, uncertaintyOf(exists.scenarios().get(0)), 0.0);
    assertEquals(0.35, representativeOf(exists.scenarios().get(0).system(), model), 0.0);

    ModelFinderResult cover = SmtModelFinder.find(model, configuration(model, "existsOnlyCover"));
    assertEquals(ScenarioProfile.COVER, cover.profile());
    assertEquals(ProfileOutcome.REFUTED, cover.outcome());
    assertFalse(cover.satisfiable());
    assertEquals("both configured scenarios are reported on", 2, cover.scenarios().size());
    assertEquals(
        List.of(ScenarioOutcome.WITNESSED, ScenarioOutcome.REFUTED),
        cover.scenarios().stream().map(ScenarioReport::outcome).toList());

    ModelFinderResult uniform =
        SmtModelFinder.find(model, configuration(model, "existsOnlyUniform"));
    assertEquals(ScenarioProfile.UNIFORM, uniform.profile());
    assertEquals(ProfileOutcome.REFUTED, uniform.outcome());
    assertFalse(uniform.satisfiable());
  }

  // ------------------------------------------------- (b) COVER without a UNIFORM witness

  @Test
  public void coverSucceedsWithADIFFERENTSnapshotPerScenarioWhereUniformIsRefuted()
      throws Exception {
    MModel model = compile("ScenarioProfiles.use");

    ModelFinderResult cover =
        SmtModelFinder.find(model, configuration(model, "coverNotUniformCover"));
    assertEquals(ProfileOutcome.SATISFIED, cover.outcome());
    assertEquals(2, cover.scenarios().size());

    ScenarioReport low = cover.scenarios().get(0);
    ScenarioReport high = cover.scenarios().get(1);
    assertEquals(ScenarioOutcome.WITNESSED, low.outcome());
    assertEquals(ScenarioOutcome.WITNESSED, high.outcome());
    assertEquals(0.02, uncertaintyOf(low), 0.0);
    assertEquals(0.06, uncertaintyOf(high), 0.0);

    double lowSpeed = representativeOf(low.system(), model);
    double highSpeed = representativeOf(high.system(), model);
    assertEquals(0.35, lowSpeed, 0.0);
    assertEquals(0.42, highSpeed, 0.0);
    assertNotEquals(
        "COVER is only interesting here because the snapshots really do differ",
        lowSpeed,
        highSpeed,
        0.0);
    for (ScenarioReport report : cover.scenarios()) {
      assertTrue(
          "USE independently checked every delivered snapshot",
          report.verdicts().stream().allMatch(InvariantVerdict::holds));
    }

    ModelFinderResult uniform =
        SmtModelFinder.find(model, configuration(model, "coverNotUniformUniform"));
    assertEquals(
        "one shared representative cannot satisfy two disjoint windows",
        ProfileOutcome.REFUTED,
        uniform.outcome());
    assertNull(uniform.system());
  }

  // ----------------------------------------------------- (c) a genuine UNIFORM witness

  @Test
  public void uniformSharesOneSnapshotAcrossEveryScenarioAndIsCheckedInEachOne() throws Exception {
    MModel model = compile("ScenarioProfiles.use");

    ModelFinderResult uniform =
        SmtModelFinder.find(model, configuration(model, "uniformSharedUniform"));
    assertEquals(ProfileOutcome.SATISFIED, uniform.outcome());
    assertEquals(2, uniform.scenarios().size());

    List<Double> uncertainties = new ArrayList<>();
    Set<Double> representatives = new LinkedHashSet<>();
    Set<MSystem> systems = new LinkedHashSet<>();
    for (ScenarioReport report : uniform.scenarios()) {
      assertEquals(ScenarioOutcome.WITNESSED, report.outcome());
      assertNotNull(report.system());
      uncertainties.add(uncertaintyOf(report));
      representatives.add(representativeOf(report.system(), model));
      systems.add(report.system());
      // The independent check must have run with THIS scenario's measurement quality substituted
      // into the reconstructed U-value, not with some other scenario's.
      assertEquals(
          "the reconstructed U-value carries this scenario's uncertainty",
          uncertaintyOf(report),
          ((URealValue) speedOf(report.system(), model)).uncertainty(),
          0.0);
      assertEquals(
          "every active invariant independently re-evaluated in this scenario",
          2,
          report.verdicts().size());
      assertTrue(report.verdicts().stream().allMatch(InvariantVerdict::holds));
    }
    assertEquals(List.of(0.02, 0.03), uncertainties);
    assertEquals(
        "UNIFORM shares objects, links and REPRESENTATIVE values across scenarios",
        Set.of(0.37),
        representatives);
    assertEquals(
        "the shared snapshot is reconstructed and checked once PER SCENARIO", 2, systems.size());

    // COVER is the weaker profile and must therefore also succeed here.
    assertEquals(
        ProfileOutcome.SATISFIED,
        SmtModelFinder.find(model, configuration(model, "uniformSharedCover")).outcome());
  }

  @Test
  public void uniformIsRefutedWhenTheSharedSnapshotFailsASingleScenario() throws Exception {
    MModel model = compile("ScenarioProfiles.use");

    ModelFinderResult refuted =
        SmtModelFinder.find(model, configuration(model, "uniformSharedRefuted"));
    assertEquals(ProfileOutcome.REFUTED, refuted.outcome());
    assertFalse(refuted.satisfiable());
  }

  // ------------------------------------------- targets are fixed outside the quantifiers

  @Test
  public void coverKeepsTheCounterexampleTargetFixedAcrossEveryScenario() throws Exception {
    MModel model = compile("ScenarioProfiles.use");

    ModelFinderResult cover =
        SmtModelFinder.find(model, configuration(model, "coverTargetedCounterexample"));
    assertEquals(ProfileOutcome.SATISFIED, cover.outcome());
    assertEquals(2, cover.scenarios().size());
    for (ScenarioReport report : cover.scenarios()) {
      assertEquals(
          "the SAME invariant is diagnosed in every scenario",
          InvariantOutcome.FALSE,
          outcomeOf(report.verdicts(), NOT_VERY_FAST));
      assertEquals(InvariantOutcome.TRUE, outcomeOf(report.verdicts(), FAST));
    }
    assertNotEquals(
        "the two scenarios needed different representatives",
        representativeOf(cover.scenarios().get(0).system(), model),
        representativeOf(cover.scenarios().get(1).system(), model),
        0.0);
  }

  @Test
  public void uniformFragileIsCheckedInBothModesInEveryScenario() throws Exception {
    MModel model = compile("ScenarioProfiles.use");

    ModelFinderResult uniform =
        SmtModelFinder.find(model, configuration(model, "uniformTargetedFragile"));
    assertEquals(ProfileOutcome.SATISFIED, uniform.outcome());
    assertEquals(2, uniform.scenarios().size());
    for (ScenarioReport report : uniform.scenarios()) {
      assertEquals(InvariantOutcome.TRUE, outcomeOf(report.nominalVerdicts(), FAST));
      assertEquals(InvariantOutcome.FALSE, outcomeOf(report.verdicts(), FAST));
      assertEquals(InvariantOutcome.TRUE, outcomeOf(report.verdicts(), NOT_VERY_FAST));
      assertEquals(0.31, ((URealValue) speedOf(report.system(), model)).value(), 0.0);
    }
    assertEquals(
        List.of(0.02, 0.06),
        uniform.scenarios().stream().map(ScenarioProfileTest::uncertaintyOf).toList());
  }

  // --------------------------------------------------------------- fail-closed guards

  @Test
  public void anUntargetedDisjunctionIsRefusedUnderCoverButAllowedUnderExists() throws Exception {
    MModel model = compile("ScenarioProfiles.use");

    IllegalArgumentException rejected =
        org.junit.Assert.assertThrows(
            IllegalArgumentException.class,
            () -> SmtModelFinder.find(model, configuration(model, "untargetedDisjunctionCover")));
    assertTrue(rejected.getMessage(), rejected.getMessage().contains("untargeted disjunction"));

    assertEquals(
        ProfileOutcome.SATISFIED,
        SmtModelFinder.find(model, configuration(model, "untargetedDisjunctionExists")).outcome());
  }

  @Test
  public void coverIsRefusedWhenTheScenarioSpaceIsNotFinite() throws Exception {
    MModel model = compile("ScenarioProfiles.use");

    AnalysisConfiguration unbounded =
        new AnalysisConfiguration(
            List.of(new ClassScope("UnidentifiedObject", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain(
                    "UnidentifiedObject", "speed", "value", List.of("0.35"), null, null),
                new AttributeDomain(
                    "UnidentifiedObject",
                    "speed",
                    "uncertainty",
                    List.of(),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.06"))),
            Set.of(FAST, NOT_VERY_FAST),
            new QueryExpr.Profiled(ScenarioProfile.COVER, new QueryExpr.Satisfy()),
            Duration.ofSeconds(30),
            1);

    IllegalArgumentException refused =
        org.junit.Assert.assertThrows(
            IllegalArgumentException.class, () -> SmtModelFinder.find(model, unbounded));
    assertTrue(refused.getMessage(), refused.getMessage().contains("finite"));
  }

  /**
   * BUG A, end-to-end through the real {@code SmtModelFinder.find()} pipeline (not just {@code
   * ScenarioSpace.enumerate()} in isolation): a FINITE but over-cap scenario space -- capacity 3,
   * seven configured uncertainty candidates, 7^3 = 343 -- must refuse with a located {@code
   * SmtTranslationException} before any script is built, exactly like the already-covered infinite
   * case above refuses before any sampling. UNIFORM is the profile the bug report calls out as more
   * serious (one joint script encoding every scenario), so this exercises that profile specifically.
   */
  @Test
  public void uniformIsRefusedWhenTheScenarioSpaceExceedsThe256CombinationCap() throws Exception {
    MModel model = compile("ScenarioProfiles.use");

    List<String> sevenCandidates =
        List.of("0.01", "0.02", "0.03", "0.04", "0.05", "0.06", "0.07");
    AnalysisConfiguration overCap =
        new AnalysisConfiguration(
            List.of(new ClassScope("UnidentifiedObject", 3, 3)),
            List.of(),
            List.of(
                new AttributeDomain(
                    "UnidentifiedObject", "speed", "value", List.of("0.35"), null, null),
                new AttributeDomain(
                    "UnidentifiedObject", "speed", "uncertainty", sevenCandidates, null, null)),
            Set.of(FAST, NOT_VERY_FAST),
            new QueryExpr.Profiled(ScenarioProfile.UNIFORM, new QueryExpr.Satisfy()),
            Duration.ofSeconds(30),
            1);

    org.tzi.use.smt.encode.SmtTranslationException refused =
        org.junit.Assert.assertThrows(
            org.tzi.use.smt.encode.SmtTranslationException.class,
            () -> SmtModelFinder.find(model, overCap));
    assertEquals(org.tzi.use.smt.encode.FragmentBoundary.UTYPE_CORE, refused.boundary());
    assertTrue(refused.getMessage(), refused.getMessage().contains("343"));
    assertTrue(refused.getMessage(), refused.getMessage().contains("256"));
  }

  // ------------------------------------------------------------------------- helpers

  private static InvariantOutcome outcomeOf(List<InvariantVerdict> verdicts, String name) {
    return verdicts.stream()
        .filter(verdict -> verdict.invariantName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no independent verdict for " + name))
        .outcome();
  }

  private static double uncertaintyOf(ScenarioReport report) {
    Scenario scenario = report.scenario();
    assertEquals("one U-type slot per fixture", 1, scenario.bindings().size());
    return scenario.bindings().get(0).value().doubleValue();
  }

  private static double representativeOf(MSystem system, MModel model) {
    return ((URealValue) speedOf(system, model)).value();
  }

  private static Object speedOf(MSystem system, MModel model) {
    MObject object =
        system.state().objectsOfClass(model.getClass("UnidentifiedObject")).iterator().next();
    return object.state(system.state()).attributeValue("speed");
  }

  private static AnalysisConfiguration configuration(MModel model, String section)
      throws URISyntaxException {
    return ConfigurationReader.normalize(
            ConfigurationReader.read(resourcePath("ScenarioProfiles.properties"), section),
            ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compile(String resource) throws Exception {
    Path file = resourcePath(resource);
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(
            Files.newInputStream(file), file.getFileName().toString(), err, new ModelFactory());
    err.flush();
    return Objects.requireNonNull(model, "failed to compile " + resource);
  }

  private static Path resourcePath(String resource) throws URISyntaxException {
    return Path.of(
        Objects.requireNonNull(
                ScenarioProfileTest.class.getClassLoader().getResource(resource), resource)
            .toURI());
  }
}
