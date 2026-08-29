package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
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
 * End-to-end regression for STRING-typed derived attributes: {@code mirror : String derive:
 * self.first} pins mirror's value to first's value BY CONTENT. The two attributes' configured
 * domains may list the same strings at different positions (or be disjoint), so the assertion
 * maps each side's domain index through the canonical content space instead of comparing raw
 * indices (which would be the positional-index unsoundness the content-aware comparison slices
 * fixed).
 *
 * <p>Pre-fix behavior: the derivation was ignored entirely -- with first = {'alice'} forced and
 * mirror = {'bob'} (disjoint content), the model was wrongly SATISFIABLE with a mirror value
 * that contradicts its own derivation; USE's re-evaluation would reject it.
 */
public class StringDerivedAttributeTest {

  private static final String MODEL =
      """
      model StringDerived
      class X
      attributes
        first : String
        mirror : String derive: self.first
        greeting : String derive: 'hello'
      end
      constraints
      context x : X inv MirrorEqFirst:
        x.mirror = x.first
      context x : X inv GreetingIsHello:
        x.greeting = 'hello'
      """;

  /**
   * The derivation discriminator: first is forced to 'alice' (so the derivation forces mirror
   * = 'alice' by content) while mirror's configured domain offers ONLY 'bob'. No instance
   * satisfies both -- UNSATISFIABLE, never a witness carrying the derivation-violating 'bob'.
   */
  @Test
  public void aStringDerivedAttributeIsConstrainedByItsDerivationAgainstTheConfiguredDomain()
      throws Exception {
    ModelFinderResult result = find(
        "MirrorEqFirst", List.of("alice"), List.of("bob"), List.of("hello"));

    assertFalse(
        "first = 'alice' forces mirror = 'alice' by derivation, but mirror's domain only"
            + " offers 'bob': genuinely unsatisfiable (pre-fix this returned a witness with"
            + " mirror = 'bob', violating the model's own derivation)",
        result.satisfiable());
  }

  /** The agreeing configuration: mirror's domain contains the derived content 'alice'. */
  @Test
  public void aConsistentDomainFindsAWitnessWhoseMirrorCarriesTheDerivedContent()
      throws Exception {
    ModelFinderResult result = find(
        "MirrorEqFirst", List.of("alice"), List.of("alice"), List.of("hello"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::MirrorEqFirst").holds());
    var witnessState = result.witnesses().get(0).system().state();
    var x = witnessState.objectsOfClass(
        result.witnesses().get(0).system().model().getClass("X")).iterator().next();
    String mirror = x.state(witnessState).attributeValue("mirror").toString();
    // USE StringValue prints with single quotes.
    assertEquals("'alice'", mirror);
  }

  /** A String literal derivation pins the attribute to the literal's content. */
  @Test
  public void aLiteralDerivationPinsTheAttributeToTheLiteralContent() throws Exception {
    // mirror's derivation (mirror = first) must stay consistent too: first = 'alice'.
    ModelFinderResult result = find(
        "GreetingIsHello", List.of("alice"), List.of("alice"), List.of("hello"));

    assertTrue("'hello' is mirror-... greeting's configured content, so the derivation holds",
        result.satisfiable());
    assertTrue(verdictFor(result, "X::GreetingIsHello").holds());
  }

  /**
   * The derivation's CONFLICT case for the literal form: greeting derives 'hello' but its
   * configured domain only offers 'goodbye' -- genuinely unsatisfiable.
   */
  @Test
  public void aLiteralDerivationContradictingTheDomainIsUnsatisfiable() throws Exception {
    ModelFinderResult result = find(
        "GreetingIsHello", List.of("alice"), List.of("goodbye"), List.of("hello"));

    assertFalse(
        "greeting derives 'hello' but the domain only offers 'goodbye': genuinely"
            + " unsatisfiable",
        result.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName,
      List<String> firstDomain,
      List<String> mirrorDomain,
      List<String> greetingDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "first", null, firstDomain, null, null),
                new AttributeDomain("X", "mirror", null, mirrorDomain, null, null),
                new AttributeDomain("X", "greeting", null, greetingDomain, null, null)),
            Set.of("X::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringDerived", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
