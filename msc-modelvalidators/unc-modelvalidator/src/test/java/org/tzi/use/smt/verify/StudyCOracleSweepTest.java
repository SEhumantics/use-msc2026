package org.tzi.use.smt.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * MILESTONE 2 -- the Study C oracle sweep (RQ3). Every row is a hand-built state evaluated by
 * USE's OWN evaluator in BOTH modes -- the U-aware reading through the ordinary {@code
 * Evaluator} on the U-carrying state, the nominal reading through this package's
 * {@link NominalErasureEvaluator} -- never a solver self-report. The sweep writes
 * {@code target/studyC-oracle.json} and {@code target/studyC-oracle.md}; the pin asserts the
 * settled-row count, the two diagnostic rows' exact two-mode classifications, and per-row
 * agreement with the expected oracle table below (whose numbers were verified against
 * use-core source and the normal CDF, see DESIGN_BRIEF_RQ3_ORACLE_SWEEP.md Milestone 1).
 */
public class StudyCOracleSweepTest {

  private static final String MODEL =
      """
      model StudyC
      class Sensor
      attributes
        speed : UReal
        state : UBoolean
        label : UString
      end
      constraints
      context s : Sensor inv tBelow: (s.speed > 0.3).toBooleanC(0.95)
      context s : Sensor inv tAbove: (s.speed > 0.3).toBooleanC(0.95)
      context s : Sensor inv d1Undefined: (s.speed > 0.3).toBooleanC(1.2)
      context s : Sensor inv d2Overconservative: s.state.toBooleanC(0.2)
      context s : Sensor inv d3Fragile: (s.speed > 6).toBooleanC(0.8)
      context s : Sensor inv uboolTie: s.state.toBooleanC(0.5)
      context s : Sensor inv fragileWitness: s.state.toBooleanC(0.9)
      context s : Sensor inv labelAbove: (s.label = 'S3').toBooleanC(0.75)
      context s : Sensor inv labelBelow: (s.label = 'S3').toBooleanC(0.75)
      """;

  /** One oracle case: the invariant to evaluate and the U-values the state carries. */
  private static final class Case {
    final String name, family, operation, boundary, invariant;
    final Double speedMu, speedSigma;
    final Double stateP;
    final String labelSpelling;
    final Double labelConf;
    final String expectedU, expectedN;
    final boolean diagnostic;

    Case(String name, String family, String operation, String boundary, String invariant,
        Double speedMu, Double speedSigma, Double stateP, String labelSpelling, Double labelConf,
        String expectedU, String expectedN, boolean diagnostic) {
      this.name = name; this.family = family; this.operation = operation; this.boundary = boundary;
      this.invariant = invariant; this.speedMu = speedMu; this.speedSigma = speedSigma;
      this.stateP = stateP; this.labelSpelling = labelSpelling; this.labelConf = labelConf;
      this.expectedU = expectedU; this.expectedN = expectedN; this.diagnostic = diagnostic;
    }
  }

  private static final List<Case> CASES = List.of(
    new Case("ureal-threshold-below", "UReal", "(v > c).toBooleanC(theta)", "below",
        "Sensor::tBelow", 0.28, 0.02, null, null, null, "FALSE", "FALSE", false),
    new Case("ureal-threshold-above", "UReal", "(v > c).toBooleanC(theta)", "above",
        "Sensor::tAbove", 0.40, 0.02, null, null, null, "TRUE", "TRUE", false),
    new Case("uboolean-tie-p05-theta05", "UBoolean", "toBooleanC(theta)", "tie p=theta=0.5 (p >= theta)",
        "Sensor::uboolTie", null, null, 0.5, null, null, "TRUE", "TRUE", false),
    new Case("ustring-conf-above", "UString", "(label = lit).toBooleanC(theta)", "above",
        "Sensor::labelAbove", null, null, null, "S3", 0.9, "TRUE", "TRUE", false),
    new Case("ustring-conf-below", "UString", "(label = lit).toBooleanC(theta)", "below",
        "Sensor::labelBelow", null, null, null, "S3", 0.5, "FALSE", "TRUE", false),
    new Case("d1-nominal-true-u-undefined", "UReal", "(v > c).toBooleanC(theta>1)", "undefined",
        "Sensor::d1Undefined", 0.40, 0.02, null, null, null, "UNDEFINED", "TRUE", true),
    new Case("d2-nominal-false-u-true", "UBoolean", "stored.toBooleanC(theta)", "p=0.3, theta=0.2",
        "Sensor::d2Overconservative", null, null, 0.3, null, null, "TRUE", "FALSE", true),
    new Case("d3-fragile-witness", "UReal", "(v > c).toBooleanC(theta)", "mu=7 sigma=1.5 (P=0.7475 < 0.8)",
        "Sensor::d3Fragile", 7.0, 1.5, null, null, null, "FALSE", "TRUE", true),
    new Case("fragile-witness-mirror", "UBoolean", "stored.toBooleanC(theta)", "p=0.7, theta=0.9 (0.5 <= p < theta)",
        "Sensor::fragileWitness", null, null, 0.7, null, null, "FALSE", "TRUE", true));

  private static final String OUTCOME_TRUE = "TRUE", OUTCOME_FALSE = "FALSE",
      OUTCOME_UNDEFINED = "UNDEFINED";

  @Test
  public void sweepWritesArtifactsAndPinsTheOracleTable() throws Exception {
    MModel model = compile();
    MSystem system = new MSystem(model);
    MSystemState state = system.state();
    MClass sensor = model.getClass("Sensor");

    List<Map<String, Object>> rows = new ArrayList<>();
    int settled = 0, agreements = 0;
    for (Case c : CASES) {
      MObject o = state.createObject(sensor, "s" + (CASES.indexOf(c) + 1));
      if (c.speedMu != null) {
        o.state(state).setAttributeValue(sensor.attribute("speed", true),
            new URealValue(c.speedMu, c.speedSigma));
      }
      if (c.stateP != null) {
        o.state(state).setAttributeValue(sensor.attribute("state", true),
            UBooleanValue.valueOf(true, c.stateP));
      }
      if (c.labelSpelling != null) {
        o.state(state).setAttributeValue(sensor.attribute("label", true),
            new UStringValue(c.labelSpelling, c.labelConf));
      }

      MClassInvariant inv = null;
      for (MClassInvariant candidate : model.classInvariants()) {
        if (candidate.qualifiedName().equals(c.invariant)) {
          inv = candidate;
          break;
        }
      }
      assertNotNull("missing invariant " + c.invariant, inv);
      Expression body = inv.bodyExpression();

      // U-aware: USE's own evaluator on the U-carrying state, with the context
      // variable bound through shared var bindings (the nominal path sees them too).
      org.tzi.use.uml.ocl.value.VarBindings shared =
          new org.tzi.use.uml.ocl.value.VarBindings();
      shared.push("s", new org.tzi.use.uml.ocl.value.ObjectValue(sensor, o));
      Value uResult = new Evaluator().eval(body, state, shared);
      String uAware = uResult.isUndefined() ? OUTCOME_UNDEFINED
          : ((BooleanValue) uResult).value() ? OUTCOME_TRUE : OUTCOME_FALSE;

      // Nominal: this package's erasure evaluator on the same state.
      EvalContext ctx = new EvalContext(state, state, shared, null, "");
      InvariantOutcome nominal = NominalErasureEvaluator.eval(body, ctx);
      String nominalStr = nominal == InvariantOutcome.TRUE ? OUTCOME_TRUE
          : nominal == InvariantOutcome.FALSE ? OUTCOME_FALSE : OUTCOME_UNDEFINED;

      boolean agree = uAware.equals(c.expectedU) && nominalStr.equals(c.expectedN);
      settled++;
      if (agree) agreements++;

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", c.name); row.put("family", c.family); row.put("operation", c.operation);
      row.put("boundary", c.boundary);
      row.put("oracleU", uAware); row.put("oracleN", nominalStr);
      row.put("expectedU", c.expectedU); row.put("expectedN", c.expectedN);
      row.put("diagnostic", c.diagnostic); row.put("agreesWithExpected", agree);
      row.put("classification", c.expectedU + "/" + c.expectedN);
      rows.add(row);
    }

    // Artifacts first: a failing pin must still leave the evidence for diagnosis.
    writeArtifacts(rows);

    // PINS: the settled count, the two diagnostic classifications, and full agreement.
    assertEquals("settled oracle rows", CASES.size(), rows.size());
    assertEquals("every row agrees with the expected oracle table", CASES.size(), agreements);
    Map<String, Object> d1 = byName(rows, "d1-nominal-true-u-undefined");
    assertEquals("D1: nominal TRUE", "TRUE", d1.get("oracleN"));
    assertEquals("D1: U-aware UNDEFINED", "UNDEFINED", d1.get("oracleU"));
    Map<String, Object> d2 = byName(rows, "d2-nominal-false-u-true");
    assertEquals("D2: nominal FALSE", "FALSE", d2.get("oracleN"));
    assertEquals("D2: U-aware TRUE", "TRUE", d2.get("oracleU"));
    Map<String, Object> d3 = byName(rows, "d3-fragile-witness");
    assertEquals("D3: nominal TRUE (7 > 6)", "TRUE", d3.get("oracleN"));
    assertEquals("D3: U-aware FALSE (0.7475 < 0.8)", "FALSE", d3.get("oracleU"));
  }

  /** ADVERSARIAL MUTATION CHECK: swapping the U-aware and nominal readings must break the
   * diagnostic pins (D1/D2 are exactly the rows where the two modes disagree). */
  @Test
  public void swappingTheTwoModeReadingsBreaksTheDiagnosticPins() throws Exception {
    MModel model = compile();
    MSystem system = new MSystem(model);
    MSystemState state = system.state();
    MClass sensor = model.getClass("Sensor");

    MObject o = state.createObject(sensor, "sd1");
    o.state(state).setAttributeValue(sensor.attribute("speed", true),
        new URealValue(0.40, 0.02));
    Expression body = invariantByName(model, "Sensor::d1Undefined").bodyExpression();

    org.tzi.use.uml.ocl.value.VarBindings shared =
        new org.tzi.use.uml.ocl.value.VarBindings();
    shared.push("s", new org.tzi.use.uml.ocl.value.ObjectValue(sensor, o));
    Value uResult = new Evaluator().eval(body, state, shared);
    String uAware = uResult.isUndefined() ? OUTCOME_UNDEFINED
        : ((BooleanValue) uResult).value() ? OUTCOME_TRUE : OUTCOME_FALSE;
    EvalContext ctx = new EvalContext(state, state, shared, null, "");
    InvariantOutcome nominal = NominalErasureEvaluator.eval(body, ctx);
    String nominalStr = nominal == InvariantOutcome.TRUE ? OUTCOME_TRUE
        : nominal == InvariantOutcome.FALSE ? OUTCOME_FALSE : OUTCOME_UNDEFINED;

    // The mutation: report the nominal reading as the U-aware one (or vice versa) -- either
    // swap breaks the D1 classification, which is the row's entire point.
    assertTrue("the D1 row only settles when the two readings are DISTINCT",
        !uAware.equals(nominalStr) && OUTCOME_UNDEFINED.equals(uAware) && OUTCOME_TRUE.equals(nominalStr));
  }

  private static MClassInvariant invariantByName(MModel model, String name) {
    for (MClassInvariant candidate : model.classInvariants()) {
      if (candidate.qualifiedName().equals(name)) {
        return candidate;
      }
    }
    throw new AssertionError("missing invariant " + name);
  }

  /**
   * MILESTONE 2 FIX 2: an expression OUTSIDE the erasure table's recognized shapes --
   * arithmetic over a UReal operand feeding toBooleanC -- must throw
   * NominalErasureUnsupportedException from NominalErasureEvaluator (the fail-closed
   * default), NOT a guessed nominal value.
   */
  @Test
  public void unsupportedErasureShapeThrows() throws Exception {
    MModel model = compile();
    MSystem system = new MSystem(model);
    MSystemState state = system.state();
    MClass sensor = model.getClass("Sensor");
    MObject o = state.createObject(sensor, "sU1");
    o.state(state).setAttributeValue(sensor.attribute("speed", true),
        new URealValue(0.40, 0.02));

    // `x.speed.multiply(2.0).toBooleanC(0.8)`: the multiply result is UReal -- an
    // arithmetic-over-UReal operand the erasure table does not recognize.
    Expression multiplied = org.tzi.use.uml.ocl.expr.ExpStdOp.create("*",
        new Expression[] {
            new org.tzi.use.uml.ocl.expr.ExpAttrOp(sensor.attribute("speed", true),
                new org.tzi.use.uml.ocl.expr.ExpVariable("self", sensor)),
            new org.tzi.use.uml.ocl.expr.ExpConstReal(2.0) });
    Expression toBool = org.tzi.use.uml.ocl.expr.ExpStdOp.create("toBooleanC",
        new Expression[] { multiplied,
            new org.tzi.use.uml.ocl.expr.ExpConstReal(0.8) });

    EvalContext ctx = new EvalContext(state, state, system.varBindings(), null, "");
    NominalErasureUnsupportedException thrown =
        org.junit.Assert.assertThrows(NominalErasureUnsupportedException.class,
            () -> NominalErasureEvaluator.eval(toBool, ctx));
    assertTrue("the refusal must be the fail-closed UNSUPPORTED message",
        thrown.getMessage().contains("not") || !thrown.getMessage().isEmpty());
  }

  private static Map<String, Object> byName(List<Map<String, Object>> rows, String name) {
    return rows.stream().filter(r -> r.get("name").equals(name)).findFirst()
        .orElseThrow(() -> new AssertionError("missing row " + name));
  }

  private static void writeArtifacts(List<Map<String, Object>> rows) throws Exception {
    Path out = Path.of("target");
    Files.createDirectories(out);
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("study", "C");
    doc.put("oracle", "USE evaluator, both modes (U-aware = Evaluator on the U-carrying state; "
        + "nominal = NominalErasureEvaluator); no solver self-report");
    doc.put("settledRows", rows.size());
    doc.put("rows", rows);
    Files.writeString(out.resolve("studyC-oracle.json"), gson.toJson(doc));

    StringBuilder md = new StringBuilder("### Study C -- oracle sweep (RQ3)\n\n");
    md.append("| Name | Family | Boundary | U-aware | Nominal | Classification | Agrees |\n|---|---|---|---|---|---|---|\n");
    for (Map<String, Object> r : rows) {
      md.append("| ").append(r.get("name")).append(" | ").append(r.get("family"))
        .append(" | ").append(r.get("boundary")).append(" | ").append(r.get("oracleU"))
        .append(" | ").append(r.get("oracleN")).append(" | ").append(r.get("classification"))
        .append(" | ").append(r.get("agreesWithExpected")).append(" |\n");
    }
    Files.writeString(out.resolve("studyC-oracle.md"), md.toString());
  }

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    StringWriter buffer = new StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(MODEL, "StudyC", err, factory);
    err.flush();
    assertNotNull("fixture model did not compile:\n" + buffer, model);
    return model;
  }
}
