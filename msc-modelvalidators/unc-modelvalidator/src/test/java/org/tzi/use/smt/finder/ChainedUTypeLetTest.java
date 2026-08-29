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
 * End-to-end regression for {@code ocl.let}'s CHAINED U-type lets: {@code let u2 : T = u1 in ...}
 * where {@code u1} is itself a U-type let binding. The chained binding aliases its predecessor's
 * symbol(s) -- representative/uncertainty for the paired families, probability for UBoolean,
 * spelling/confidence for UString -- and INHERITS its let source, so the case-enumerating
 * consumers treat the whole chain as the one value it is; in particular the read-once aliasing
 * rule spans the chain (reading {@code u1} and {@code u2} in one projected expression is two
 * reads of one value and refuses).
 *
 * <p>Before this slice a U-type let's initializer had to be a bare attribute access; a chained
 * let variable refused with a located message. Navigated initializers
 * ({@code let u = self.role.uAttr}) remain refused: a folded end's slots carry per-concrete-class
 * configured domains, and enumerating candidates over them is a slice of its own.
 */
public class ChainedUTypeLetTest {

  private static final String USTRING_MODEL =
      """
      model UStringChain
      class Camera
      attributes
        id : UString
      end
      constraints
      context self : Camera inv ChainBelowConfidence:
        let s1 : UString = self.id in
        let s2 : UString = s1 in
        (s2 = 'ALLY-7').toBooleanC(0.6)
      context self : Camera inv ChainAboveConfidence:
        let s1 : UString = self.id in
        let s2 : UString = s1 in
        (s2 = 'ALLY-7').toBooleanC(0.8)
      context self : Camera inv ChainAliasedRead:
        let s1 : UString = self.id in
        let s2 : UString = s1 in
        (s1 = s2).toBooleanC(0.5)
      """;

  private static final String UREAL_MODEL =
      """
      model URealChain
      class Sensor
      attributes
        speed : UReal
      end
      constraints
      context self : Sensor inv ChainAboveBoundary:
        let u1 : UReal = self.speed in
        let u2 : UReal = u1 in
        (u2 > 0.30).toBooleanC(0.95)
      context self : Sensor inv ChainBelowBoundary:
        let u1 : UReal = self.speed in
        let u2 : UReal = u1 in
        (u2 > 0.30).toBooleanC(0.95)
      """;

  private static final String UBOOLEAN_MODEL =
      """
      model UBooleanChain
      class Detection
      attributes
        hitsTarget : UBoolean
      end
      constraints
      context self : Detection inv ThreeLinkChain:
        let b1 : UBoolean = self.hitsTarget in
        let b2 : UBoolean = b1 in
        let b3 : UBoolean = b2 in
        b3.toBooleanC(0.85)
      context self : Detection inv ChainAliasedComposition:
        let b1 : UBoolean = self.hitsTarget in
        let b2 : UBoolean = b1 in
        (b1 and b2).toBooleanC(0.5)
      """;

  /** The chain carries the spelling/confidence pair: c = 0.7 clears 0.6. */
  @Test
  public void aTwoLinkUStringChainCarriesSpellingAndConfidence() throws Exception {
    ModelFinderResult result = find(USTRING_MODEL, "Camera::ChainBelowConfidence");

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "Camera::ChainBelowConfidence").holds());
  }

  /** The chain's confidence survives to the second link: 0.7 does not clear 0.8. */
  @Test
  public void aTwoLinkUStringChainAboveItsConfidenceIsUnsatisfiable() throws Exception {
    ModelFinderResult result = find(USTRING_MODEL, "Camera::ChainAboveConfidence");

    assertFalse("c = 0.7 does not clear 0.8 through the chain", result.satisfiable());
  }

  /**
   * The read-once rule spans the chain: s1 and s2 alias ONE value, so comparing them is an
   * aliased read and refuses (USE's uEquals would answer with certainty).
   */
  @Test
  public void aChainWideAliasedReadIsRefused() throws Exception {
    try {
      find(USTRING_MODEL, "Camera::ChainAliasedRead");
      fail("reading both links of one chain must refuse as an aliased read");
    } catch (SmtTranslationException expected) {
      assertTrue(
          "the refusal must name the aliasing rule, got: " + expected.getMessage(),
          expected.getMessage().contains("more than once")
              || expected.getMessage().contains("independen"));
    }
  }

  /** The paired family chains: the uncertainty flows through two aliases (0.34 clears 0.30). */
  @Test
  public void aTwoLinkURealChainCarriesTheUncertainty() throws Exception {
    ModelFinderResult above =
        find(
            UREAL_MODEL,
            "Sensor::ChainAboveBoundary",
            List.of(new AttributeDomain("Sensor", "speed", "value", List.of("0.34"), null, null),
                new AttributeDomain("Sensor", "speed", "uncertainty", List.of("0.02"), null, null)));

    assertTrue("0.34 with sigma 0.02 clears 0.30 at 95% through the chain", above.satisfiable());
    assertTrue(verdictFor(above, "Sensor::ChainAboveBoundary").holds());
  }

  /** The chain-polarity discriminator: 0.31 with sigma 0.02 does not clear 0.30 at 95%. */
  @Test
  public void aTwoLinkURealChainBelowTheBoundaryIsUnsatisfiable() throws Exception {
    ModelFinderResult below =
        find(
            UREAL_MODEL,
            "Sensor::ChainBelowBoundary",
            List.of(new AttributeDomain("Sensor", "speed", "value", List.of("0.31"), null, null),
                new AttributeDomain("Sensor", "speed", "uncertainty", List.of("0.02"), null, null)));

    assertFalse(
        "nominal erasure would accept 0.31 > 0.30; the chained uncertainty must reject it",
        below.satisfiable());
  }

  /** A three-link UBoolean chain still enumerates its stored probability: 0.9 clears 0.85. */
  @Test
  public void aThreeLinkUBooleanChainCarriesItsProbability() throws Exception {
    ModelFinderResult result =
        find(
            UBOOLEAN_MODEL,
            "Detection::ThreeLinkChain",
            List.of(new AttributeDomain("Detection", "hitsTarget", "probability", List.of("0.9"), null, null)));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "Detection::ThreeLinkChain").holds());
  }

  /** Two links of one chain composed = two reads of one value: refused, never p*p. */
  @Test
  public void aChainAliasedCompositionIsRefused() throws Exception {
    try {
      find(
          UBOOLEAN_MODEL,
          "Detection::ChainAliasedComposition",
          List.of(new AttributeDomain("Detection", "hitsTarget", "probability", List.of("0.9"), null, null)));
      fail("composing two links of one chain must refuse as an aliased read");
    } catch (SmtTranslationException expected) {
      assertTrue(
          "the refusal must name the aliasing rule, got: " + expected.getMessage(),
          expected.getMessage().contains("more than once")
              || expected.getMessage().contains("independen"));
    }
  }

  private static List<AttributeDomain> ustringDomains() {
    return List.of(
        new AttributeDomain("Camera", "id", "value", List.of("ALLY-7"), null, null),
        new AttributeDomain("Camera", "id", "confidence", List.of("0.7"), null, null));
  }

  private static ModelFinderResult find(String modelText, String invariantName) throws Exception {
    return find(modelText, invariantName, modelText.contains("Camera") ? ustringDomains() : List.of());
  }

  private static ModelFinderResult find(
      String modelText, String invariantName, List<AttributeDomain> domains) throws Exception {
    MModel model = compile(modelText);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope(classOf(modelText), 1, 1)),
            List.of(),
            domains,
            Set.of(invariantName),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static String classOf(String modelText) {
    if (modelText.contains("class Camera")) {
      return "Camera";
    }
    if (modelText.contains("class Sensor")) {
      return "Sensor";
    }
    return "Detection";
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
    MModel model = USECompiler.compileSpecification(modelText, "ChainedUTypeLet", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
