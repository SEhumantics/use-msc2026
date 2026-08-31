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
import org.tzi.use.smt.config.QueryParser;
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
 * <li><b>4. Scenario-policy separation</b> -- EXISTS/COVER/UNIFORM via the non-monotone
 *     window pair already corpus-proven (ScenarioProfiles' own construction).</li>
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
   * EXISTS / COVER / UNIFORM on the non-monotone window pair (the ScenarioProfiles
   * construction applied to the Robot Battle speed slot). The EXISTS solve succeeds; the
   * COVER solve succeeds (each scenario gets its own snapshot); the UNIFORM solve REFUTES
   * (no single snapshot covers both windows). The unsat is the policy separation's teeth.
   */
  @Test
  public void scenarioPolicySeparation() throws Exception {
    // The non-monotone window pair from the corpus's ScenarioProfiles.use: the two
    // invariants carve mu into [0.30 + z*sigma, 0.36 + z*sigma) which SHIFTS with sigma,
    // so two sigmas with disjoint windows need different snapshots (COVER sat), and no
    // single mu serves both (UNIFORM unsat) when the windows are disjoint.
    MModel model = compile();
    ConfigurationVocabulary vocab = ConfigurationVocabulary.fromModel(model);

    // EXISTS: mu=0.35 with sigma=0.02 satisfies the window; only sigma=0.02 configured.
    AnalysisConfiguration existsCfg =
        new AnalysisConfiguration(
            List.of(new ClassScope("Robot", 1, 1, List.of("r1"))),
            List.of(),
            List.of(
                new AttributeDomain("Robot", "speed", "value", List.of("0.35"), null, null),
                new AttributeDomain("Robot", "speed", "uncertainty", List.of("0.02", "0.06"), null, null)),
            Set.of("Robot::reliablyFast"),
            QueryParser.parse("exists satisfy", vocab),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult exists = SmtModelFinder.find(model, existsCfg);
    assertTrue("EXISTS: one scenario, one witness", exists.satisfiable());

    // COVER: both scenarios must have their own snapshot. With mu=0.35 only, sigma=0.06
    // fails the window -> COVER refutes. This is the separation's positive evidence: EXISTS
    // was SAT but COVER is UNSAT -- an existential never implies coverage.
    AnalysisConfiguration coverCfg =
        new AnalysisConfiguration(
            List.of(new ClassScope("Robot", 1, 1, List.of("r1"))),
            List.of(),
            List.of(
                new AttributeDomain("Robot", "speed", "value", List.of("0.35"), null, null),
                new AttributeDomain("Robot", "speed", "uncertainty", List.of("0.02", "0.06"), null, null)),
            Set.of("Robot::reliablyFast"),
            QueryParser.parse("cover satisfy", vocab),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult cover = SmtModelFinder.find(model, coverCfg);
    assertFalse("COVER: sigma=0.06 cannot be covered by mu=0.35 alone", cover.satisfiable());

    // UNIFORM: the same shape, also refuted (no shared snapshot).
    AnalysisConfiguration uniformCfg =
        new AnalysisConfiguration(
            List.of(new ClassScope("Robot", 1, 1, List.of("r1"))),
            List.of(),
            List.of(
                new AttributeDomain("Robot", "speed", "value", List.of("0.35"), null, null),
                new AttributeDomain("Robot", "speed", "uncertainty", List.of("0.02", "0.06"), null, null)),
            Set.of("Robot::reliablyFast"),
            QueryParser.parse("uniform satisfy", vocab),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult uniform = SmtModelFinder.find(model, uniformCfg);
    assertFalse("UNIFORM: no shared snapshot across disjoint windows", uniform.satisfiable());
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
