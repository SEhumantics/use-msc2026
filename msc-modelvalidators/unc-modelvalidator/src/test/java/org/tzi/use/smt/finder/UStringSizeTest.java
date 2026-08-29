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
 * End-to-end regression for {@code prim.string-operations}' UString slice: {@code
 * (self.id.size() > 2).toBooleanC(0.8)} over a configured UString attribute. USE's
 * {@code UString.size()} returns a UINTEGER carrying the spelling length as its representative
 * and the UString's confidence as its uncertainty (use-core bytecode: {@code UStringValue
 * .uSize()}), so the threshold enumerates the configured spellings' lengths with the confidence
 * symbol as the uncertainty -- the same normal-CDF boundary the attribute path uses.
 *
 * <p>Two USE type-checking facts pinned here (both verified against the real compiler):
 * {@code UString.size()} yields UInteger, so crisp-integer comparisons of it do not type-check;
 * the supported consumer is the ordered-threshold projection through the UReal widening.
 * Before this slice any read of a UString attribute outside the confidence-threshold
 * projection refused.
 */
public class UStringSizeTest {

  private static final String MODEL =
      """
      model UStringSize
      class Camera
      attributes
        id : UString
      end
      constraints
      context self : Camera inv LongTagClears:
        (self.id.size() > 3).toBooleanC(0.8)
      context self : Camera inv ShortTagFails:
        (self.id.size() > 3).toBooleanC(0.8)
      context self : Camera inv PositiveTagClears:
        (self.id.size() > 0).toBooleanC(0.8)
      """;

  /** Two spellings of lengths 2 and 5: the demand forces the 5-long 'HELLO' slot. */
  @Test
  public void theSizeThresholdForcesTheLongSpelling() throws Exception {
    ModelFinderResult result =
        find("LongTagClears", List.of("HI", "HELLO"), List.of("0.9"));

    assertTrue("the HELLO spelling (length 5) clears the size-3 threshold at 0.8", result.satisfiable());
    assertTrue(verdictFor(result, "Camera::LongTagClears").holds());
  }

  /** Only the 2-long spelling is configured: 2 > 3 fails at any confidence -- UNSAT. */
  @Test
  public void whenNoSpellingIsLongEnoughTheDemandFails() throws Exception {
    ModelFinderResult result = find("ShortTagFails", List.of("HI"), List.of("0.9"));

    assertFalse("'HI' has length 2, which never clears the size-3 threshold", result.satisfiable());
  }

  /** The lower-threshold polarity: the 2-long spelling clears size > 0. */
  @Test
  public void theShortSpellingClearsTheLowerThreshold() throws Exception {
    ModelFinderResult result = find("PositiveTagClears", List.of("HI"), List.of("0.9"));

    assertTrue("'HI' (length 2) clears the size-0 threshold at 0.8", result.satisfiable());
    assertTrue(verdictFor(result, "Camera::PositiveTagClears").holds());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> spellings, List<String> confidence)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Camera", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("Camera", "id", "value", spellings, null, null),
                new AttributeDomain("Camera", "id", "confidence", confidence, null, null)),
            Set.of("Camera::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "UStringSize", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
