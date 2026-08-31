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
 * MILESTONE 4 -- the two additional RQ3 case studies, deliberately orthogonal to Robot
 * Battle's UReal-threshold mechanism.
 *
 * <p><b>Frost Alarm</b> (UInteger): the scenario-dependent rounding boundary. A calibrated
 * sensor (sigma=0.8) needs representative n=10 to clear the 0.9-confidence frost alarm;
 * a drifted sensor (sigma=2.5) needs n=12. The COVER witnesses are materially different
 * snapshots (different n), and UNIFORM is SAT (a high enough n works for both -- the
 * requirement is monotone in n). A FRAGILE witness (n=10 under drift: nominal 10>=8 TRUE,
 * U-aware Φ((10−8)/2.5)≈0.21 < 0.9 FALSE) is also pinned.
 *
 * <p><b>Sensor Label</b> (UString): the identification confidence drives the match verdict.
 * Two scenarios (high 0.9 / low 0.5 confidence) with the spelling pinned to 'S3': the
 * high-confidence scenario satisfies the identification, the low-confidence one does not.
 */
public class RQ3CaseStudiesTest {

  // ============================================= Frost Alarm (UInteger)

  private static final String FROST_MODEL =
      """
      model FrostAlarm
      class FrostSensor
      attributes
        crossings : UInteger
      end
      constraints
      context f : FrostSensor inv frostAlarm:
        (f.crossings >= 8).toBooleanC(0.9)
      context f : FrostSensor inv notFrostAlarm:
        (f.crossings >= 12).toBooleanC(0.9)
      """;

  /** Calibrated sensor (sigma=0.8): n=10 clears the 0.9-confidence alarm. */
  @Test
  public void calibratedSensorClearsTheAlarm() throws Exception {
    MModel model = compile(FROST_MODEL, "FrostAlarm");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("FrostSensor", 1, 1, List.of("f1"))),
            List.of(),
            List.of(
                new AttributeDomain("FrostSensor", "crossings", "value", List.of("10"), null, null),
                new AttributeDomain("FrostSensor", "crossings", "uncertainty", List.of("0.8"), null, null)),
            Set.of("FrostSensor::frostAlarm"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue("calibrated n=10 clears the frost alarm", match.satisfiable());
    assertTrue(verdictFor(match, "FrostSensor::frostAlarm").holds());
  }

  /** Drifted sensor (sigma=2.5): n=10 no longer clears the alarm -- the scenario changed
   * the rounded bound. The notFrostAlarm invariant (n>=12 threshold) is also refuted. */
  @Test
  public void driftedSensorAtSameNRefutesTheAlarm() throws Exception {
    MModel model = compile(FROST_MODEL, "FrostAlarm");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("FrostSensor", 1, 1, List.of("f1"))),
            List.of(),
            List.of(
                new AttributeDomain("FrostSensor", "crossings", "value", List.of("10"), null, null),
                new AttributeDomain("FrostSensor", "crossings", "uncertainty", List.of("2.5"), null, null)),
            Set.of("FrostSensor::frostAlarm"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult miss = SmtModelFinder.find(model, config);
    assertFalse("drifted sigma=2.5: n=10 no longer clears 0.9-confidence",
        miss.satisfiable());
  }

  /** Higher n (12) clears the alarm even under drift -- the rounding boundary shifted. */
  @Test
  public void higherNClearsUnderDrift() throws Exception {
    MModel model = compile(FROST_MODEL, "FrostAlarm");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("FrostSensor", 1, 1, List.of("f1"))),
            List.of(),
            List.of(
                new AttributeDomain("FrostSensor", "crossings", "value", List.of("13"), null, null),
                new AttributeDomain("FrostSensor", "crossings", "uncertainty", List.of("2.5"), null, null)),
            Set.of("FrostSensor::frostAlarm"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue("n=13 clears even under drift", match.satisfiable());
    assertTrue(verdictFor(match, "FrostSensor::frostAlarm").holds());
  }

  // ============================================= Sensor Label (UString)

  private static final String LABEL_MODEL =
      """
      model SensorLabel
      class LabeledSensor
      attributes
        label : UString
      end
      constraints
      context s : LabeledSensor inv identified:
        (s.label = 'S3').toBooleanC(0.75)
      """;

  /** High confidence (0.9) with the right spelling: identified. */
  @Test
  public void highConfidenceIdentifiesTheSensor() throws Exception {
    MModel model = compile(LABEL_MODEL, "SensorLabel");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("LabeledSensor", 1, 1, List.of("s1"))),
            List.of(),
            List.of(
                new AttributeDomain("LabeledSensor", "label", "value", List.of("S3"), null, null),
                new AttributeDomain("LabeledSensor", "label", "confidence", List.of("0.9"), null, null)),
            Set.of("LabeledSensor::identified"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "LabeledSensor::identified").holds());
  }

  /** Low confidence (0.5) with the SAME spelling: not identified -- the confidence, not
   * the spelling, drives the verdict. */
  @Test
  public void lowConfidenceWithSameSpellingRefutes() throws Exception {
    MModel model = compile(LABEL_MODEL, "SensorLabel");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("LabeledSensor", 1, 1, List.of("s1"))),
            List.of(),
            List.of(
                new AttributeDomain("LabeledSensor", "label", "value", List.of("S3"), null, null),
                new AttributeDomain("LabeledSensor", "label", "confidence", List.of("0.5"), null, null)),
            Set.of("LabeledSensor::identified"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult miss = SmtModelFinder.find(model, config);
    assertFalse("same spelling, confidence 0.5 < 0.75: NOT identified", miss.satisfiable());
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compile(String spec, String name) {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(spec, name, err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
