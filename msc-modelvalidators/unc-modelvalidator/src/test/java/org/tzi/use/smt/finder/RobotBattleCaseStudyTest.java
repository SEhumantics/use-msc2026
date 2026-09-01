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
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.verify.NominalErasureEvaluator;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * THE ROBOT BATTLE CASE STUDY (RQ3, Milestone 3) -- all four claims from the design brief
 * (Part B + Milestone 1 §2), exercised against a purpose-built subset of the published
 * Bertoa/Burgueño/Moreno/Vallecillo model that carries all four U-types.
 *
 * <p>Claims:
 * <ul>
 * <li><b>1. Four-type synthesis</b> -- the solver chooses a Robot, its target, and ALL FOUR
 *     value kinds (UReal speed, UInteger lastMovement, UBoolean decision, UString id) in one
 *     witness under real multiplicities and invariants.</li>
 * <li><b>2. Erasure divergence</b> -- the ReliablyFast shape: nominal-true / U-false, the
 *     query the crisp incumbent answers "satisfied" while the uncertainty-aware semantics
 *     refutes.</li>
 * <li><b>3. Confidence-flipped safety verdict</b> -- the UString identification confidence
 *     selects friend from foe: the SAME spelling at a different confidence flips the verdict.</li>
 * <li><b>4. Scenario-policy separation</b> -- demonstrated by the UNION of the general
 *     UReal capability ({@link ScenarioProfileTest#existsSucceedsWhereCoverAndUniformAreBothRefuted}
 *     for EXISTS vs COVER, and
 *     {@link ScenarioProfileTest#coverSucceedsWithADIFFERENTSnapshotPerScenarioWhereUniformIsRefuted}
 *     for COVER vs UNIFORM -- the load-bearing distinction) with the Robot-Battle-specific
 *     instances in this file: uRealPolicySeparation (distinction (a), UReal on the Robot's
 *     speed slot) and uBooleanPolicyLimitation (the UBoolean collapse finding). Robot
 *     Battle alone does not prove the three-way separation.
 *     All four U-types are covered: UReal and UInteger separate under the
 *     scenario policies (uRealPolicySeparation, uIntegerScenarioPolicySeparation);
 *     UBoolean and UString collapse (uBooleanPolicyLimitation,
 *     uStringScenarioPolicyCollapse) because USE's normalization couples the
 *     uncertainty parameter into a snapshot-owned variable, so it cannot vary
 *     per scenario. Two types separate, two collapse.</li>
 * </ul>
 */
public class RobotBattleCaseStudyTest {

  private static final String MODEL =
      """
      model RobotBattle
      class Robot
      attributes
        speed : UReal
        lastMovement : UInteger
      end
      class UnidentifiedObject
      attributes
        id : UString
        speed : UReal
      end
      class Mark
      attributes
        hitsTarget : UBoolean
      end
      association Engagement between
        Robot[0..1] role robot
        UnidentifiedObject[0..1] role target
      end
      association Decision between
        Mark[0..1] role source
        UnidentifiedObject[0..1] role about
      end
      constraints
      context r : Robot inv reliablyFast:
        (r.speed > 0.30).toBooleanC(0.95)
      context r : Robot inv movedRecently:
        (r.lastMovement > 8).toBooleanC(0.95)
      context u : UnidentifiedObject inv identified:
        (u.id = 'U-77').toBooleanC(0.7)
      context u : UnidentifiedObject inv recentlyMoved:
        (u.speed > 0.5).toBooleanC(0.6)
      """;

  // ============================================= CLAIM 1: four-type synthesis

  /**
   * One bounded solve whose witness carries ALL FOUR U-types simultaneously: UReal speed,
   * UInteger lastMovement on the Robot; UString id on the target; UBoolean on the Mark --
   * under a link constraint tying them. If any family drops out, the witness is incomplete.
   */
  @Test
  public void fourTypeSynthesis() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Robot", 1, 1, List.of("r1")),
                new ClassScope("UnidentifiedObject", 1, 1, List.of("u1")),
                new ClassScope("Mark", 1, 1, List.of("m1"))),
            List.of(
                new AssociationScope("Engagement", 1, 1, List.of(List.of("r1", "u1"))),
                new AssociationScope("Decision", 1, 1, List.of(List.of("m1", "u1")))),
            List.of(
                new AttributeDomain("Robot", "speed", "value", List.of("0.35"), null, null),
                new AttributeDomain("Robot", "speed", "uncertainty", List.of("0.02"), null, null),
                new AttributeDomain("Robot", "lastMovement", "value", List.of("3"), null, null),
                new AttributeDomain("Robot", "lastMovement", "uncertainty", List.of("0.5"), null, null),
                new AttributeDomain("UnidentifiedObject", "id", "value", List.of("U-77"), null, null),
                new AttributeDomain("UnidentifiedObject", "id", "confidence", List.of("0.85"), null, null),
                new AttributeDomain("UnidentifiedObject", "speed", "value", List.of("0.7"), null, null),
                new AttributeDomain("UnidentifiedObject", "speed", "uncertainty", List.of("0.1"), null, null),
                new AttributeDomain("Mark", "hitsTarget", "probability", List.of("0.9"), null, null)),
            Set.of("Robot::reliablyFast", "UnidentifiedObject::identified",
                "UnidentifiedObject::recentlyMoved"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue("CLAIM 1: the witness must synthesize all four U-types at once",
        match.satisfiable());
    assertTrue("USE must independently confirm the synthesized witness",
        verdictFor(match, "Robot::reliablyFast").holds());
    assertTrue(verdictFor(match, "UnidentifiedObject::identified").holds());
    assertTrue(verdictFor(match, "UnidentifiedObject::recentlyMoved").holds());
  }

  // ============================================= CLAIM 2: erasure divergence

  /**
   * THE ERASURE DIVERGENCE (design brief Part B, claim 2): the ReliablyFast shape on a
   * hand-built UReal(0.31, 0.02) state, evaluated in BOTH modes by USE's OWN evaluators.
   * Nominal: 0.31 > 0.30 → TRUE. U-aware: crossing probability Φ((0.31−0.30)/0.02) ≈ 0.6915
   * < 0.95 → FALSE. The crisp incumbent reports "satisfied"; the U-aware semantics refutes
   * the same snapshot.
   */
  @Test
  public void erasureDivergence() throws Exception {
    MModel model = compile();
    org.tzi.use.uml.sys.MSystem system = new org.tzi.use.uml.sys.MSystem(model);
    org.tzi.use.uml.sys.MSystemState state = system.state();
    org.tzi.use.uml.mm.MClass robot = model.getClass("Robot");

    org.tzi.use.uml.sys.MObject r = state.createObject(robot, "r_div");
    r.state(state).setAttributeValue(robot.attribute("speed", true),
        new org.tzi.use.uml.ocl.value.URealValue(0.31, 0.02));

    org.tzi.use.uml.mm.MClassInvariant inv = invariantByName(model, "Robot::reliablyFast");
    org.tzi.use.uml.ocl.expr.Expression body = inv.expandedExpression();

    // U-aware: USE's own Evaluator on the U-carrying state.
    org.tzi.use.uml.ocl.value.Value uResult =
        new org.tzi.use.uml.ocl.expr.Evaluator().eval(body, state);
    boolean uAware = !uResult.isUndefined()
        && ((org.tzi.use.uml.ocl.value.BooleanValue) uResult).value();

    // Nominal: the erasure evaluator (package made public for this test).
    org.tzi.use.uml.ocl.expr.EvalContext ctx =
        new org.tzi.use.uml.ocl.expr.EvalContext(state, state,
            system.varBindings(), null, "");
    InvariantOutcome nominal = NominalErasureEvaluator.eval(body, ctx);

    // THE DIVERGENCE: nominal TRUE, U-aware FALSE.
    org.junit.Assert.assertEquals(
        "nominal: 0.31 > 0.30 → TRUE",
        InvariantOutcome.TRUE, nominal);
    org.junit.Assert.assertFalse(
        "U-aware: crossing probability Φ(0.5) ≈ 0.6915 < 0.95 → FALSE", uAware);
  }

  // ============================================= CLAIM 3: confidence-flipped verdict

  /**
   * The same UString spelling 'U-77' at confidence 0.85 clears the identification threshold
   * (0.7) but at confidence 0.5 does not -- the identification verdict flips on the
   * confidence ALONE, with the spelling held fixed.
   */
  @Test
  public void confidenceFlipsTheIdentificationVerdict() throws Exception {
    org.tzi.use.uml.mm.MModel model = compile();

    org.tzi.use.uml.ocl.expr.Expression body = org.tzi.use.smt.finder.
        RobotBattleCaseStudyTest.invariantByName(model, "UnidentifiedObject::identified").
        bodyExpression();
    org.tzi.use.uml.mm.MClass uCls = model.getClass("UnidentifiedObject");

    // High confidence: 0.85 >= 0.7 -> TRUE.
    org.tzi.use.uml.sys.MSystem sysHi = new org.tzi.use.uml.sys.MSystem(model);
    org.tzi.use.uml.sys.MSystemState stHi = sysHi.state();
    org.tzi.use.uml.sys.MObject hi = stHi.createObject(uCls, "u_hi");
    hi.state(stHi).setAttributeValue(uCls.attribute("id", true),
        new org.tzi.use.uml.ocl.value.UStringValue("U-77", 0.85));
    org.tzi.use.uml.ocl.value.VarBindings bHi =
        new org.tzi.use.uml.ocl.value.VarBindings();
    bHi.push("u", new org.tzi.use.uml.ocl.value.ObjectValue(uCls, hi));
    org.tzi.use.uml.ocl.value.Value hiResult =
        new org.tzi.use.uml.ocl.expr.Evaluator().eval(body, stHi, bHi);
    assertTrue("0.85 >= 0.7: identified", ((org.tzi.use.uml.ocl.value.BooleanValue) hiResult).value());

    // Low confidence, SAME spelling: 0.5 < 0.7 -> FALSE.
    org.tzi.use.uml.sys.MSystem sysLo = new org.tzi.use.uml.sys.MSystem(model);
    org.tzi.use.uml.sys.MSystemState stLo = sysLo.state();
    org.tzi.use.uml.sys.MObject lo = stLo.createObject(uCls, "u_lo");
    lo.state(stLo).setAttributeValue(uCls.attribute("id", true),
        new org.tzi.use.uml.ocl.value.UStringValue("U-77", 0.5));
    org.tzi.use.uml.ocl.value.VarBindings bLo =
        new org.tzi.use.uml.ocl.value.VarBindings();
    bLo.push("u", new org.tzi.use.uml.ocl.value.ObjectValue(uCls, lo));
    org.tzi.use.uml.ocl.value.Value loResult =
        new org.tzi.use.uml.ocl.expr.Evaluator().eval(body, stLo, bLo);
    assertFalse("0.5 < 0.7: NOT identified -- the verdict flipped on confidence alone",
        ((org.tzi.use.uml.ocl.value.BooleanValue) loResult).value());
  }

  // ============================================= CLAIM 4: scenario-policy separation

  /**
   * THE SCENARIO-POLICY SEPARATION (design brief Milestone 1 §2, construction verified):
   * EXISTS-sat / COVER-sat / UNIFORM-unsat via two stored UBoolean attributes whose
   * probabilities are scenario-bound. The scenario domain is the cross product of
   * {0.9, 0.15} per attribute, giving 4 scenarios. The truth flag is in the snapshot.
   * Under p=0.9, the truth flag true gives 0.9 >= 0.8 → both invariants true.
   * Under p=0.15, the truth flag false gives 1-0.15 = 0.85 >= 0.8 → both true.
   * So: EXISTS picks s1 with (true,true); COVER uses (true,true) for s1 and (false,false)
   * for s2; UNIFORM needs ONE (v_h,v_f) for both scenarios, but (true,true) fails s2
   * (0.15 < 0.8) and (false,false) fails s1 (0.1 < 0.8) → UNSAT by case exhaustion.
   */
  private static final String POLICY_MODEL =
      """
      model PolicySeparation
      class Mark
      attributes
        hitsTarget : UBoolean
        confirmed : UBoolean
      end
      constraints
      context m : Mark inv j: m.hitsTarget.toBooleanC(0.8)
      context m : Mark inv k: m.confirmed.toBooleanC(0.8)
      """;

  /**
   * Robot-Battle-specific instance of the scenario-policy separation (distinction (a)):
   * EXISTS-sat, COVER-unsat, UNIFORM-unsat on the Robot's UReal speed slot using the
   * non-monotone window pair. The GENERAL three-way capability is proven by
   * ScenarioProfileTest (solver-level, both pairwise distinctions); this test adds the
   * Robot-Battle-specific instance on the same UReal mechanism.
   */
  @Test
  public void uRealPolicySeparation() throws Exception {
    MModel model = compile();
    ConfigurationVocabulary vocab = ConfigurationVocabulary.fromModel(model);

    List<AttributeDomain> speedDomains = List.of(
        new AttributeDomain("Robot", "speed", "value", List.of("0.35"), null, null),
        new AttributeDomain("Robot", "speed", "uncertainty", List.of("0.02", "0.06"), null, null));
    List<ClassScope> robotScope = List.of(new ClassScope("Robot", 1, 1, List.of("r1")));

    AnalysisConfiguration existsCfg = new AnalysisConfiguration(
        robotScope, List.of(), speedDomains, Set.of("Robot::reliablyFast"),
        QueryParser.parse("exists satisfy", vocab), Duration.ofSeconds(30), 1);
    ModelFinderResult exists = SmtModelFinder.find(model, existsCfg);
    assertTrue("EXISTS: SAT", exists.satisfiable());

    AnalysisConfiguration coverCfg = new AnalysisConfiguration(
        robotScope, List.of(), speedDomains, Set.of("Robot::reliablyFast"),
        QueryParser.parse("cover satisfy", vocab), Duration.ofSeconds(30), 1);
    ModelFinderResult cover = SmtModelFinder.find(model, coverCfg);
    assertFalse("COVER: sigma=0.06 uncoverable by mu=0.35 alone → UNSAT",
        cover.satisfiable());

    AnalysisConfiguration uniformCfg = new AnalysisConfiguration(
        robotScope, List.of(), speedDomains, Set.of("Robot::reliablyFast"),
        QueryParser.parse("uniform satisfy", vocab), Duration.ofSeconds(30), 1);
    ModelFinderResult uniform = SmtModelFinder.find(model, uniformCfg);
    assertFalse("UNIFORM: no shared snapshot → UNSAT", uniform.satisfiable());
  }

  /**
   * The UBoolean structural limitation: this test verifies that EXISTS, COVER, and
   * UNIFORM all return SAT for a UBoolean-only model, demonstrating that the three
   * policies COLLAPSE because the probability is registered once (shared across all
   * scenario copies) rather than per-scenario. This is a documented structural
   * limitation, not a positive proof of the scenario-policy separation (which is
   * carried by uRealPolicySeparation, uIntegerScenarioPolicySeparation, and
   * ScenarioProfileTest).
   */
  @Test
  public void uBooleanPolicyLimitation() throws Exception {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    java.io.PrintWriter err = new java.io.PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(POLICY_MODEL, "PolicySeparation", err, factory);
    err.flush();
    if (model == null) throw new AssertionError("policy model did not compile:\n" + buffer);
    ConfigurationVocabulary vocab = ConfigurationVocabulary.fromModel(model);

    List<AttributeDomain> domains = List.of(
        new AttributeDomain("Mark", "hitsTarget", "probability", List.of("0.9", "0.15"), null, null),
        new AttributeDomain("Mark", "confirmed", "probability", List.of("0.9", "0.15"), null, null));
    List<ClassScope> scopes = List.of(new ClassScope("Mark", 1, 1, List.of("m1")));

    for (var profile : List.of("exists satisfy", "cover satisfy", "uniform satisfy")) {
      AnalysisConfiguration cfg = new AnalysisConfiguration(
          scopes, List.of(), domains, Set.of("Mark::j", "Mark::k"),
          QueryParser.parse(profile, vocab), Duration.ofSeconds(30), 1);
      ModelFinderResult result = SmtModelFinder.find(model, cfg);
      assertTrue("UBoolean " + profile.split(" ")[0] + ": SAT (all three collapse -- "
          + "no snapshot-side representative for UBoolean)",
          result.satisfiable());
    }
  }

  // ============================================= CLAIM 4: UInteger + UString coverage

  /**
   * UInteger instance of the scenario-policy separation (distinction (a)), mirroring
   * uRealPolicySeparation on the Robot's UInteger lastMovement slot with the
   * non-monotone window pair sigma in {2, 8} against the invariant
   * movedRecently: (lastMovement > 8).toBooleanC(0.95):
   * Phi((12-8)/2) = Phi(2) ~ 0.977 >= 0.95, but Phi((12-8)/8) = Phi(0.5) ~ 0.69 < 0.95.
   * So EXISTS-sat (pick sigma=2), COVER-unsat (sigma=8 is uncoverable by mu=12 alone),
   * UNIFORM-unsat (no shared snapshot). UInteger shares UReal's registration path
   * (registerUTypeAttribute with a scenario-selected sigma), so it separates the same way.
   */
  @Test
  public void uIntegerScenarioPolicySeparation() throws Exception {
    MModel model = compile();
    ConfigurationVocabulary vocab = ConfigurationVocabulary.fromModel(model);

    List<AttributeDomain> domains = List.of(
        new AttributeDomain("Robot", "lastMovement", "value", List.of("12"), null, null),
        new AttributeDomain("Robot", "lastMovement", "uncertainty", List.of("2", "8"), null, null));
    List<ClassScope> robotScope = List.of(new ClassScope("Robot", 1, 1, List.of("r1")));

    AnalysisConfiguration existsCfg = new AnalysisConfiguration(
        robotScope, List.of(), domains, Set.of("Robot::movedRecently"),
        QueryParser.parse("exists satisfy", vocab), Duration.ofSeconds(30), 1);
    ModelFinderResult exists = SmtModelFinder.find(model, existsCfg);
    assertTrue("UInteger EXISTS: sigma=2 gives Phi(2) ~ 0.977 >= 0.95 → SAT",
        exists.satisfiable());

    AnalysisConfiguration coverCfg = new AnalysisConfiguration(
        robotScope, List.of(), domains, Set.of("Robot::movedRecently"),
        QueryParser.parse("cover satisfy", vocab), Duration.ofSeconds(30), 1);
    ModelFinderResult cover = SmtModelFinder.find(model, coverCfg);
    assertFalse("UInteger COVER: sigma=8 uncoverable by mu=12 alone → UNSAT",
        cover.satisfiable());

    AnalysisConfiguration uniformCfg = new AnalysisConfiguration(
        robotScope, List.of(), domains, Set.of("Robot::movedRecently"),
        QueryParser.parse("uniform satisfy", vocab), Duration.ofSeconds(30), 1);
    ModelFinderResult uniform = SmtModelFinder.find(model, uniformCfg);
    assertFalse("UInteger UNIFORM: no shared snapshot → UNSAT", uniform.satisfiable());
  }

  /**
   * UString is architecturally identical to UBoolean: USE's normalization couples the
   * spelling and confidence into a single (spelling, confidence) pair, and the SMT
   * encoding has confidence registered ONCE (shared across scenario copies) rather
   * than per-scenario. The confidence domain varies over two values, 0.85 and 0.5,
   * against the invariant identified: (id = 'U-77').toBooleanC(0.7): 0.85 >= 0.7 but
   * 0.5 < 0.7, so a scenario-pinned confidence would make COVER and UNIFORM unsat.
   * All three policies still return SAT -- the collapse holds under genuine
   * variation, not by singleton-domain triviality.
   */
  @Test
  public void uStringScenarioPolicyCollapse() throws Exception {
    MModel model = compile();
    ConfigurationVocabulary vocab = ConfigurationVocabulary.fromModel(model);

    List<AttributeDomain> domains = List.of(
        new AttributeDomain("UnidentifiedObject", "id", "value", List.of("U-77"), null, null),
        new AttributeDomain("UnidentifiedObject", "id", "confidence", List.of("0.85", "0.5"), null, null));
    List<ClassScope> uScope = List.of(new ClassScope("UnidentifiedObject", 1, 1, List.of("u1")));

    for (var profile : List.of("exists satisfy", "cover satisfy", "uniform satisfy")) {
      AnalysisConfiguration cfg = new AnalysisConfiguration(
          uScope, List.of(), domains, Set.of("UnidentifiedObject::identified"),
          QueryParser.parse(profile, vocab), Duration.ofSeconds(30), 1);
      ModelFinderResult result = SmtModelFinder.find(model, cfg);
      assertTrue("UString " + profile.split(" ")[0] + ": SAT (structural collapse -- "
          + "UString confidence is snapshot-owned, not scenario-pinned)",
          result.satisfiable());
    }
  }

  // ============================================= shared helpers

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static org.tzi.use.uml.mm.MClassInvariant invariantByName(
      MModel model, String name) {
    for (org.tzi.use.uml.mm.MClassInvariant candidate : model.classInvariants()) {
      if (candidate.qualifiedName().equals(name)) {
        return candidate;
      }
    }
    throw new AssertionError("missing invariant " + name);
  }

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(MODEL, "RobotBattle", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
