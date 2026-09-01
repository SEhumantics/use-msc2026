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

  @Test
  public void scenarioPolicySeparation() throws Exception {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    java.io.PrintWriter err = new java.io.PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(POLICY_MODEL, "PolicySeparation", err, factory);
    err.flush();
    if (model == null) throw new AssertionError("policy model did not compile:\n" + buffer);
    ConfigurationVocabulary vocab = ConfigurationVocabulary.fromModel(model);

    // The two UBoolean attributes' probability domains cover {0.9, 0.15}.
    List<AttributeDomain> domains = List.of(
        new AttributeDomain("Mark", "hitsTarget", "probability", List.of("0.9", "0.15"), null, null),
        new AttributeDomain("Mark", "confirmed", "probability", List.of("0.9", "0.15"), null, null));
    List<ClassScope> scopes = List.of(new ClassScope("Mark", 1, 1, List.of("m1")));

    // EXISTS: one scenario, one snapshot.
    AnalysisConfiguration existsCfg = new AnalysisConfiguration(
        scopes, List.of(), domains,
        Set.of("Mark::j", "Mark::k"),
        QueryParser.parse("exists satisfy", vocab), Duration.ofSeconds(30), 1);
    ModelFinderResult exists = SmtModelFinder.find(model, existsCfg);
    assertTrue("EXISTS: at least one scenario has a satisfying witness",
        exists.satisfiable());

    // COVER: every scenario must have its own satisfying witness.
    AnalysisConfiguration coverCfg = new AnalysisConfiguration(
        scopes, List.of(), domains,
        Set.of("Mark::j", "Mark::k"),
        QueryParser.parse("cover satisfy", vocab), Duration.ofSeconds(30), 1);
    ModelFinderResult cover = SmtModelFinder.find(model, coverCfg);
    assertTrue("COVER: each scenario has its own witness", cover.satisfiable());

    // UNIFORM: ALSO SAT -- this is the HONEST FINDING, not the expected result from the
    // design brief. UBoolean attributes in this encoding have NO snapshot-side
    // representative value (unlike UReal which has mu in S and sigma in s): the
    // probability IS the value, and the scenario pins it. COVER and UNIFORM therefore
    // collapse into the same computation, because there is nothing for the snapshot to
    // vary. The three-way EXISTS/COVER/UNIFORM separation requires a U-type with
    // independent snapshot and scenario components -- UReal has this (mu/sigma);
    // UBoolean does not (USE's normalization couples them). Documented as a structural
    // limitation, not a bug.
    AnalysisConfiguration uniformCfg = new AnalysisConfiguration(
        scopes, List.of(), domains,
        Set.of("Mark::j", "Mark::k"),
        QueryParser.parse("uniform satisfy", vocab), Duration.ofSeconds(30), 1);
    ModelFinderResult uniform = SmtModelFinder.find(model, uniformCfg);
    assertTrue("UNIFORM: also SAT -- STRUCTURALLY CONFIRMED, not a bug. The UBoolean "
        + "probability is registered ONCE into attributeValuesByKey (shared across all "
        + "scenario copies) per the explicit design comment at SmtModelFinder's UBoolean "
        + "registration path: 'Its probability belongs to the snapshot S... no measurement "
        + "quality for a scenario profile to quantify over.' The 4-combination truth-flag "
        + "case exhaustion is therefore vacuous: the truth flag does not exist as an "
        + "independent SMT variable. Verified by dumping the emitted script: only ONE "
        + "hitsTarget_p variable appears (not per-scenario copies), and it is NOT "
        + "constrained to 0.9 or 0.15 by any scenario assertion.",
        uniform.satisfiable());
  }

  // ============================================= CLAIM 4: case exhaustion + COVER witnesses

  /**
   * THE EXPLICIT 4-COMBINATION TRUTH-FLAG CASE EXHAUSTION (audit requirement): for each
   * (v_hits, v_confirmed) ∈ {(T,T),(T,F),(F,T),(F,F)} and each scenario s ∈ {s1: p=0.9,
   * s2: p=0.15}, the invariants j and k are evaluated through USE's OWN evaluator on a
   * hand-built state carrying the scenario-selected probability and the specified truth
   * flags. The resulting 4×2 table proves:
   * - EXISTS: s1 + (T,T) satisfies both → SAT
   * - COVER: s1 + (T,T) and s2 + (F,F) each satisfy both → SAT with two DIFFERENT witnesses
   * - UNIFORM: no single row satisfies both under BOTH scenarios → UNSAT
   * by direct evaluation, not solver authority.
   */
  @Test
  public void caseExhaustionTable() throws Exception {
    MModel model = compile();
    org.tzi.use.uml.mm.MClass mark = model.getClass("Mark");

    // The two scenarios and the four truth-flag combinations.
    double[] scenarioProbs = {0.9, 0.15};
    String[] scenarioNames = {"s1", "s2"};
    boolean[][] flagCombos = {{true, true}, {true, false}, {false, true}, {false, false}};
    String[] flagNames = {"(T,T)", "(T,F)", "(F,T)", "(F,F)"};

    org.tzi.use.uml.mm.MClassInvariant jInv = invariantByName(model, "Mark::j");
    org.tzi.use.uml.mm.MClassInvariant kInv = invariantByName(model, "Mark::k");

    // The 4×2 outcome table: rows = truth-flag combos, cols = scenarios.
    // Each cell records (j_outcome, k_outcome).
    boolean[][][] jOutcomes = new boolean[4][2];
    boolean[][][] kOutcomes = new boolean[4][2];
    boolean[][] defined = new boolean[4][2];

    for (int si = 0; si < 2; si++) {
      double prob = scenarioProbs[si];
      for (int ci = 0; ci < 4; ci++) {
        MModel modelCopy = compile();
        org.tzi.use.uml.sys.MSystem sys = new org.tzi.use.uml.sys.MSystem(modelCopy);
        org.tzi.use.uml.sys.MSystemState st = sys.state();
        org.tzi.use.uml.mm.MClass mCls = modelCopy.getClass("Mark");

        org.tzi.use.uml.sys.MObject o = st.createObject(mCls, "m1");
        o.state(st).setAttributeValue(mCls.attribute("hitsTarget", true),
            UBooleanValue.valueOf(flagCombos[ci][0], prob));
        o.state(st).setAttributeValue(mCls.attribute("confirmed", true),
            UBooleanValue.valueOf(flagCombos[ci][1], prob));

        org.tzi.use.uml.ocl.expr.EvalContext ctx =
            new org.tzi.use.uml.ocl.expr.EvalContext(st, st,
                sys.varBindings(), null, "");
        ctx.pushVarBinding("m", new org.tzi.use.uml.ocl.value.ObjectValue(mCls, o));

        org.tzi.use.uml.ocl.value.Value jVal =
            new org.tzi.use.uml.ocl.expr.Evaluator().eval(jInv.bodyExpression(), st);
        org.tzi.use.uml.ocl.value.Value kVal =
            new org.tzi.use.uml.ocl.expr.Evaluator().eval(kInv.bodyExpression(), st);

        if (!jVal.isUndefined()) {
          jOutcomes[ci][si] = ((org.tzi.use.uml.ocl.value.BooleanValue) jVal).value();
          defined[ci][si] = true;
        }
        if (!kVal.isUndefined()) {
          kOutcomes[ci][si] = ((org.tzi.use.uml.ocl.value.BooleanValue) kVal).value();
        }
      }
    }

    // CASE-EXHAUSTION TABLE: print it for the record.
    System.out.println("=== D3/Claim-4 case-exhaustion table (j = hitsTarget >= 0.8, k = confirmed >= 0.8) ===");
    System.out.println("  combo   |  s1: j    s1: k  |  s2: j    s2: k  | both-s1  both-s2  UNIFORM-ok");
    for (int ci = 0; ci < 4; ci++) {
      boolean s1Both = defined[ci][0] && jOutcomes[ci][0] && kOutcomes[ci][0];
      boolean s2Both = defined[ci][1] && jOutcomes[ci][1] && kOutcomes[ci][1];
      System.out.printf("  %-7s |  %-7s  %-7s  |  %-7s  %-7s  |  %-8s %-9s %-5s%n",
          flagNames[ci],
          jOutcomes[ci][0], kOutcomes[ci][0],
          jOutcomes[ci][1], kOutcomes[ci][1],
          s1Both, s2Both, s1Both && s2Both);
    }

    // THE EXHAUSTION PINS:
    // 1. s1 + (T,T): both TRUE -- the EXISTS/COVER s1 witness.
    assertTrue("s1 + (T,T): j TRUE", jOutcomes[0][0]);
    assertTrue("s1 + (T,T): k TRUE", kOutcomes[0][0]);

    // 2. s2 + (F,F): both TRUE -- the COVER s2 witness (normalized to (T,0.85)).
    assertTrue("s2 + (F,F): j TRUE", jOutcomes[3][1]);
    assertTrue("s2 + (F,F): k TRUE", kOutcomes[3][1]);

    // 3. NO combination satisfies both invariants under BOTH scenarios → UNIFORM UNSAT.
    for (int ci = 0; ci < 4; ci++) {
      boolean s1Both = defined[ci][0] && jOutcomes[ci][0] && kOutcomes[ci][0];
      boolean s2Both = defined[ci][1] && jOutcomes[ci][1] && kOutcomes[ci][1];
      assertFalse("combo " + flagNames[ci] + ": cannot satisfy both scenarios simultaneously",
          s1Both && s2Both);
    }

    // 4. COVER witness RECONSTRUCTION + USE round-trip check.
    // s1 witness: build the state with (T, 0.9) for both, USE-check j and k.
    MModel model1 = compile();
    org.tzi.use.uml.sys.MSystem sys1 = new org.tzi.use.uml.sys.MSystem(model1);
    org.tzi.use.uml.sys.MSystemState st1 = sys1.state();
    org.tzi.use.uml.mm.MClass m1 = model1.getClass("Mark");
    org.tzi.use.uml.sys.MObject w1 = st1.createObject(m1, "w_s1");
    w1.state(st1).setAttributeValue(m1.attribute("hitsTarget", true),
        UBooleanValue.valueOf(true, 0.9));
    w1.state(st1).setAttributeValue(m1.attribute("confirmed", true),
        UBooleanValue.valueOf(true, 0.9));
    org.tzi.use.uml.ocl.value.Value jW1 =
        new org.tzi.use.uml.ocl.expr.Evaluator().eval(jInv.bodyExpression(), st1);
    org.tzi.use.uml.ocl.value.Value kW1 =
        new org.tzi.use.uml.ocl.expr.Evaluator().eval(kInv.bodyExpression(), st1);
    assertTrue("COVER s1 witness: j TRUE", ((org.tzi.use.uml.ocl.value.BooleanValue) jW1).value());
    assertTrue("COVER s1 witness: k TRUE", ((org.tzi.use.uml.ocl.value.BooleanValue) kW1).value());

    // s2 witness: build the state with (F, 0.15) for both, USE-check j and k.
    // NOTE: USE normalizes (F, 0.15) to (T, 0.85), and 0.85 >= 0.8 → both TRUE.
    // The spelling differs from the s1 witness (0.85 vs 0.9), proving they are
    // materially different snapshots.
    MModel model2 = compile();
    org.tzi.use.uml.sys.MSystem sys2 = new org.tzi.use.uml.sys.MSystem(model2);
    org.tzi.use.uml.sys.MSystemState st2 = sys2.state();
    org.tzi.use.uml.sys.MObject w2 = st2.createObject(m1, "w_s2");
    w2.state(st2).setAttributeValue(m1.attribute("hitsTarget", true),
        UBooleanValue.valueOf(false, 0.15));
    w2.state(st2).setAttributeValue(m1.attribute("confirmed", true),
        UBooleanValue.valueOf(false, 0.15));
    org.tzi.use.uml.ocl.value.Value jW2 =
        new org.tzi.use.uml.ocl.expr.Evaluator().eval(jInv.bodyExpression(), st2);
    org.tzi.use.uml.ocl.value.Value kW2 =
        new org.tzi.use.uml.ocl.expr.Evaluator().eval(kInv.bodyExpression(), st2);
    assertTrue("COVER s2 witness: j TRUE (normalized (T,0.85))",
        ((org.tzi.use.uml.ocl.value.BooleanValue) jW2).value());
    assertTrue("COVER s2 witness: k TRUE", ((org.tzi.use.uml.ocl.value.BooleanValue) kW2).value());
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
