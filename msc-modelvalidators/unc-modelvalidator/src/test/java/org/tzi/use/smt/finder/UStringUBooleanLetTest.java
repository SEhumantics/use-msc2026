package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for {@code ocl.let}'s UString/UBoolean slice: {@code let s : UString =
 * self.id in (s = 'ALLY-7').toBooleanC(0.6)} and {@code let b : UBoolean = self.hitsTarget in
 * b.toBooleanC(0.85)}.
 *
 * <p>Both families have single-component or derived encodings -- a UString is a configured
 * spelling index plus a configured confidence, a stored UBoolean IS its configured probability
 * (its truth is the p >= 0.5 rule) -- so the let binding aliases the source attribute's symbol(s)
 * and records the source's configured candidate domains, which the confidence-threshold
 * consumers enumerate case by case exactly as they do for the attribute. The aliasing rule is
 * preserved: a let variable read twice in one projected expression is refused, because USE's own
 * {@code uEquals}/{@code UBoolean.and} answer with certainty (or plain p) for aliased reads while
 * the source's product rules assume independence.
 *
 * <p>Before this slice UString/UBoolean let variables refused outright (only UReal/UInteger lets
 * existed). The discriminators: confidence 0.7 clears 0.6 but not 0.8 -- a binding that lost the
 * confidence component would wrongly report the latter SAT.
 */
public class UStringUBooleanLetTest {

  private static final String USTRING_MODEL =
      """
      model UStringLet
      class Camera
      attributes
        id : UString
      end
      constraints
      context self : Camera inv LetIdentityBelowConfidence:
        let s : UString = self.id in (s = 'ALLY-7').toBooleanC(0.6)
      context self : Camera inv LetIdentityAboveConfidence:
        let s : UString = self.id in (s = 'ALLY-7').toBooleanC(0.8)
      context self : Camera inv LetAliasedRead:
        let s : UString = self.id in (s = s).toBooleanC(0.5)
      """;

  private static final String UBOOLEAN_MODEL =
      """
      model UBooleanLet
      class Detection
      attributes
        hitsTarget : UBoolean
      end
      constraints
      context self : Detection inv LetTargetHolds:
        let b : UBoolean = self.hitsTarget in b.toBooleanC(0.85)
      context self : Detection inv LetTargetAbove:
        let b : UBoolean = self.hitsTarget in b.toBooleanC(0.95)
      context self : Detection inv LetTargetNotComposed:
        let b : UBoolean = self.hitsTarget in (not b).toBooleanC(0.05)
      context self : Detection inv LetTargetNotAbove:
        let b : UBoolean = self.hitsTarget in (not b).toBooleanC(0.2)
      """;

  /** p(s = 'ALLY-7') = c_s = 0.7 clears 0.6: the spelling/confidence pair flows through. */
  @Test
  public void aUStringLetCarriesSpellingAndConfidenceBelowTheThreshold() throws Exception {
    ModelFinderResult result =
        find(
            USTRING_MODEL,
            "Camera",
            "id",
            List.of(new AttributeDomain("Camera", "id", "value", List.of("ALLY-7"), null, null),
                new AttributeDomain("Camera", "id", "confidence", List.of("0.7"), null, null)),
            "Camera::LetIdentityBelowConfidence");

    assertTrue("c = 0.7 clears the 0.6 threshold", result.satisfiable());
    assertTrue(verdictFor(result, "Camera::LetIdentityBelowConfidence").holds());
  }

  /** The pair discriminator: 0.7 does not clear 0.8 -- losing the confidence would read p = 1. */
  @Test
  public void aUStringLetAboveItsConfidenceIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult result =
        find(
            USTRING_MODEL,
            "Camera",
            "id",
            List.of(new AttributeDomain("Camera", "id", "value", List.of("ALLY-7"), null, null),
                new AttributeDomain("Camera", "id", "confidence", List.of("0.7"), null, null)),
            "Camera::LetIdentityAboveConfidence");

    assertFalse("c = 0.7 does not clear the 0.8 threshold", result.satisfiable());
  }

  /**
   * The aliasing rule survives the let: reading the same let variable twice in one projected
   * expression is refused with a located message (USE's uEquals answers with certainty for two
   * references to one value; the source's product rules assume independence).
   */
  @Test
  public void anAliasedUStringLetReadIsRefused() throws Exception {
    try {
      find(
          USTRING_MODEL,
          "Camera",
          "id",
          List.of(new AttributeDomain("Camera", "id", "value", List.of("ALLY-7"), null, null),
              new AttributeDomain("Camera", "id", "confidence", List.of("0.7"), null, null)),
          "Camera::LetAliasedRead");
      fail("an aliased let read must be refused, not silently encoded");
    } catch (SmtTranslationException expected) {
      assertTrue(
          "the refusal must name the aliasing rule, got: " + expected.getMessage(),
          expected.getMessage().contains("more than once")
              || expected.getMessage().contains("independen"));
    }
  }

  /** The stored probability flows through the let: 0.9 clears 0.85. */
  @Test
  public void aUBooleanLetCarriesItsStoredProbability() throws Exception {
    ModelFinderResult result =
        find(
            UBOOLEAN_MODEL,
            "Detection",
            "hitsTarget",
            List.of(
                new AttributeDomain("Detection", "hitsTarget", "probability", List.of("0.9"), null, null)),
            "Detection::LetTargetHolds");

    assertTrue("p = 0.9 clears the 0.85 threshold", result.satisfiable());
    assertTrue(verdictFor(result, "Detection::LetTargetHolds").holds());
  }

  /** 0.9 does not clear 0.95: a binding that hardcoded certainty would wrongly read SAT. */
  @Test
  public void aUBooleanLetAboveItsProbabilityIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult result =
        find(
            UBOOLEAN_MODEL,
            "Detection",
            "hitsTarget",
            List.of(
                new AttributeDomain("Detection", "hitsTarget", "probability", List.of("0.9"), null, null)),
            "Detection::LetTargetAbove");

    assertFalse("p = 0.9 does not clear the 0.95 threshold", result.satisfiable());
  }

  /**
   * The let composes with connectives: complement(0.9) = 0.1 clears 0.05 -- the not must read
   * the let's PROBABILITY (0.1), not certainty (which would clear 0.2 as well).
   */
  @Test
  public void aUBooleanLetComposesWithConnectives() throws Exception {
    ModelFinderResult holds =
        find(
            UBOOLEAN_MODEL,
            "Detection",
            "hitsTarget",
            List.of(
                new AttributeDomain("Detection", "hitsTarget", "probability", List.of("0.9"), null, null)),
            "Detection::LetTargetNotComposed");

    assertTrue("complement(0.9) = 0.1 clears the 0.05 threshold", holds.satisfiable());
    assertTrue(verdictFor(holds, "Detection::LetTargetNotComposed").holds());
  }

  /** The composition polarity: complement(0.9) = 0.1 does not clear 0.2. */
  @Test
  public void aUBooleanLetComposedAboveItsComplementIsUnsatisfiable() throws Exception {
    ModelFinderResult miss =
        find(
            UBOOLEAN_MODEL,
            "Detection",
            "hitsTarget",
            List.of(
                new AttributeDomain("Detection", "hitsTarget", "probability", List.of("0.9"), null, null)),
            "Detection::LetTargetNotAbove");

    assertFalse(
        "complement(0.9) = 0.1 does not clear the 0.2 threshold -- the not must read the"
            + " let's probability, not certainty",
        miss.satisfiable());
  }

  private static ModelFinderResult find(
      String modelText,
      String className,
      String attributeName,
      List<AttributeDomain> domains,
      String invariantName)
      throws Exception {
    MModel model = compile(modelText);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope(className, 1, 1)),
            List.of(),
            domains,
            Set.of(invariantName),
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

  private static MModel compile(String modelText) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(modelText, "UStringUBooleanLet", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
