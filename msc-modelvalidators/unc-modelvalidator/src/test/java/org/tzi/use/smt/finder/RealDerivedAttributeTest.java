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
 * End-to-end regression for {@code attr.derived}'s Real slice: a crisp {@code Real}-typed
 * {@code derive:} attribute is constrained by its derivation exactly like the Integer slice --
 * the value symbol is Real-sorted, so the equality is well-sorted for a Real-typed derivation,
 * and an Integer-typed derivation lifts with {@code to_real} (the same widening USE applies when
 * an Integer value feeds a Real slot; there is no rounding involved for crisp Reals).
 *
 * <p>Test values are exactly representable in both the solver's exact rationals and USE's double
 * arithmetic, so the independent witness re-evaluation agrees without rounding machinery.
 */
public class RealDerivedAttributeTest {

  private static final String MODEL =
      """
      model RealDerivedAttr
      class X
      attributes
        base : Real
        doubled : Real derive: self.base * 2
        lifted : Real derive: self.i * 2
        i : Integer
      end
      constraints
      context x : X inv BaseIsOnePointFive:
        x.base = 1.5
      context x : X inv IntIsTwo:
        x.i = 2
      """;

  /** base forced to 1.5 derives doubled = 3.0; a domain offering only 2.9 is genuinely UNSAT. */
  @Test
  public void aRealDerivationConstrainsAgainstTheConfiguredDomain() throws Exception {
    ModelFinderResult miss =
        find("BaseIsOnePointFive", List.of("1.5"), List.of("2.9"), List.of("0.0"), List.of("0"));

    assertFalse(
        "base = 1.5 forces doubled = 3.0 by derivation; a domain offering only 2.9 cannot hold",
        miss.satisfiable());
  }

  /** The agreeing domain admits a witness whose reconstructed doubled is the derived 3.0. */
  @Test
  public void aConsistentDomainYieldsTheDerivedValue() throws Exception {
    ModelFinderResult result =
        find("BaseIsOnePointFive", List.of("1.5"), List.of("3.0"), List.of("0.0"), List.of("0"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::BaseIsOnePointFive").holds());
    assertEquals(
        "the reconstructed doubled must be the DERIVED value 3.0",
        3.0,
        realValueOf(result, "doubled"),
        1e-9);
  }

  /** A superset domain: the derivation eliminates the non-derivable candidate. */
  @Test
  public void theDerivationEliminatesNonDerivableCandidates() throws Exception {
    ModelFinderResult result =
        find("BaseIsOnePointFive", List.of("1.5"), List.of("3.0", "2.9"), List.of("0.0"), List.of("0"));

    assertTrue(result.satisfiable());
    assertEquals(
        "only doubled = 3.0 is derivable from base = 1.5",
        3.0,
        realValueOf(result, "doubled"),
        1e-9);
  }

  /** An Integer derivation lifts: i = 2 forces lifted = 4.0 via to_real, never a truncation. */
  @Test
  public void anIntegerDerivationLiftsToTheRealSlot() throws Exception {
    ModelFinderResult result =
        find("IntIsTwo", List.of("0.0"), List.of("0.0"), List.of("4.0"), List.of("2"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::IntIsTwo").holds());
    assertEquals(
        "lifted = i * 2 must land on 4.0 exactly",
        4.0,
        realValueOf(result, "lifted"),
        1e-9);

    ModelFinderResult miss =
        find("IntIsTwo", List.of("0.0"), List.of("0.0"), List.of("5.0"), List.of("2"));
    assertFalse("i = 2 derives lifted = 4.0; a domain offering only 5.0 cannot hold",
        miss.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName,
      List<String> baseDomain,
      List<String> doubledDomain,
      List<String> liftedDomain,
      List<String> iDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "base", null, baseDomain, null, null),
                new AttributeDomain("X", "doubled", null, doubledDomain, null, null),
                new AttributeDomain("X", "lifted", null, liftedDomain, null, null),
                new AttributeDomain("X", "i", null, iDomain, null, null)),
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

  private static double realValueOf(ModelFinderResult result, String attributeName) {
    var witnessState = result.witnesses().get(0).system().state();
    var x = witnessState.objectsOfClass(
        result.witnesses().get(0).system().model().getClass("X")).iterator().next();
    return ((org.tzi.use.uml.ocl.value.RealValue)
        x.state(witnessState).attributeValue(attributeName)).value();
  }

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "RealDerivedAttr", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
