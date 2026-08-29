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
 * End-to-end regression for derived attributes ({@code attribute : T derive: <expr>}): the
 * derivation is a CONSTRAINT on the attribute's value, not decoration. The encoding used to
 * ignore it entirely -- a witness could carry {@code doubled = 7} while {@code base = 3},
 * contradicting the model's own derivation ({@code doubled = base * 2} = 6); USE's dynamic
 * re-evaluation of derived attributes would report 6 for that same reconstructed object, so the
 * witness was not a valid instance of the model at all.
 *
 * <p>The fix asserts, per slot, {@code attributeSymbol = <derive expression translated with
 * self bound to that slot>}, for every derived Integer AND Boolean attribute registration
 * (including the per-subclass registrations of an inherited derived attribute). A configured
 * domain that contradicts the derivation therefore becomes a genuine UNSATISFIABLE, which is
 * the correct reading: no instance satisfies both the configuration and the model. The Boolean
 * attribute's own domain is unconstraining by design (AttributeEncoder's BOOLEAN case is a
 * documented no-op), so the flag's value comes ENTIRELY from its derivation -- which is what
 * these tests assert.
 */
public class DerivedAttributeTest {

  private static final String MODEL =
      """
      model DerivedAttr
      class X
      attributes
        base : Integer
        doubled : Integer derive: self.base * 2
        flag : Boolean derive: self.base = 3
      end
      constraints
      context x : X inv BaseIsThree:
        x.base = 3
      context x : X inv BaseIsFive:
        x.base = 5
      context x : X inv FlagHolds:
        x.flag
      context x : X inv FlagNegated:
        not x.flag
      """;

  /**
   * The discriminator: base is forced to 3 (so the derivation forces doubled = 6) while
   * doubled's configured domain offers ONLY 7. The derivation must win -- no instance satisfies
   * both -- so this is UNSATISFIABLE, never a witness carrying the derivation-violating 7.
   */
  @Test
  public void aDerivedAttributeIsConstrainedByItsDerivationAgainstTheConfiguredDomain()
      throws Exception {
    ModelFinderResult result = find("BaseIsThree", List.of("3"), List.of("7"), List.of("true"));

    assertFalse(
        "base = 3 forces doubled = 6 by derivation, but the domain only offers 7: genuinely"
            + " unsatisfiable (pre-fix this returned a witness with doubled = 7, violating the"
            + " model's own derivation)",
        result.satisfiable());
  }

  /** The agreeing configuration: doubled's domain contains the derived value 6. */
  @Test
  public void aConsistentConfigurationFindsAWitnessWhoseDerivedValueIsTheDerivedOne()
      throws Exception {
    ModelFinderResult result = find("BaseIsThree", List.of("3"), List.of("6"), List.of("true"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::BaseIsThree").holds());
    assertEquals("the reconstructed doubled must be the DERIVED value 6",
        6, intValueOf(result, "doubled"));
  }

  /** A superset domain: the derivation eliminates the non-derivable candidate. */
  @Test
  public void theDerivationEliminatesNonDerivableCandidatesFromASupersetDomain()
      throws Exception {
    ModelFinderResult result = find(
        "BaseIsThree", List.of("3"), List.of("6", "7"), List.of("true"));

    assertTrue("doubled = 6 is derivable", result.satisfiable());
    assertTrue(verdictFor(result, "X::BaseIsThree").holds());
    assertEquals("the derivation must eliminate 7 from the domain",
        6, intValueOf(result, "doubled"));
  }

  /**
   * A Boolean derived attribute pins its symbol to the derivation's truth value: with base
   * forced to 3, flag holds; with base forced to 5, flag is false and the demanding invariant
   * is genuinely unsatisfiable.
   */
  @Test
  public void booleanDerivedAttributeFollowsItsDerivation() throws Exception {
    ModelFinderResult three = find(
        "FlagHolds", List.of("3"), List.of("6"), List.of("true"));
    assertTrue("base = 3 makes the derived flag true", three.satisfiable());
    assertTrue(verdictFor(three, "X::FlagHolds").holds());

    ModelFinderResult five = find(
        "FlagHolds", List.of("5"), List.of("6"), List.of("true"));
    assertFalse("base = 5 makes the derived flag false, so FlagHolds cannot hold",
        five.satisfiable());
  }

  /** The negation polarity: not flag holds exactly when the derivation makes flag false. */
  @Test
  public void booleanDerivedAttributeNegationFollowsItsDerivation() throws Exception {
    ModelFinderResult negated = find(
        "FlagNegated", List.of("5"), List.of("10"), List.of("true"));
    assertTrue("base = 5 makes the derived flag false, so not flag holds",
        negated.satisfiable());
    assertTrue(verdictFor(negated, "X::FlagNegated").holds());

    ModelFinderResult three = find(
        "FlagNegated", List.of("3"), List.of("6"), List.of("true"));
    assertFalse("base = 3 makes the flag true, so not flag cannot hold", three.satisfiable());
  }

  private static int intValueOf(ModelFinderResult result, String attributeName) {
    var witnessState = result.witnesses().get(0).system().state();
    var x = witnessState.objectsOfClass(
        result.witnesses().get(0).system().model().getClass("X")).iterator().next();
    return ((org.tzi.use.uml.ocl.value.IntegerValue)
        x.state(witnessState).attributeValue(attributeName)).value();
  }

  /**
   * Shared runner: X pinned to baseDomain/doubledDomain/flagDomain candidate values with the
   * named invariant active. The Boolean attribute's domain is unconstraining by design
   * (AttributeEncoder's BOOLEAN case is a documented no-op), so the flag's value comes ENTIRELY
   * from its derivation -- which is what these tests assert.
   */
  private static ModelFinderResult find(
      String invariantName,
      List<String> baseDomain,
      List<String> doubledDomain,
      List<String> flagDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "base", null, baseDomain, null, null),
                new AttributeDomain("X", "doubled", null, doubledDomain, null, null),
                new AttributeDomain("X", "flag", null, flagDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "DerivedAttr", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
