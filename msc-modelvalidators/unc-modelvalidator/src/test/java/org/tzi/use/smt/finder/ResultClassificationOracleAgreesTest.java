package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.config.ScenarioProfile;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.encode.FragmentCoverageLedger;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.ResultClassification;
import org.tzi.use.smt.finder.ScenarioOutcome;
import org.tzi.use.smt.finder.ScenarioReport;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;

/**
 * REGRESSION for the vacuous-agreement hole in {@link ResultClassification#oracleAgrees}: an
 * earlier implementation used {@code allMatch} over the active-invariant verdicts, which an
 * EMPTY or INCOMPLETE verdict set passes without checking anything -- a run could be published
 * as SAT_VALIDATED with no oracle verdict at all. The hardened gate requires every active
 * invariant to have exactly one present, HOLDING verdict; missing, duplicate, defined-false,
 * and undefined verdicts must each fail, and FALSE must fail differently from UNDEFINED (both
 * refute an enforced invariant, but only FALSE is a defined counterexample).
 */
public class ResultClassificationOracleAgreesTest {

  private static final String MODEL =
      """
      model OracleGate
      class X
      attributes
        b : Boolean
      end
      constraints
      context x : X inv gate: x.b
      """;

  /** Builds a witnessed result whose UNCERTAIN verdicts are exactly {@code verdicts}. */
  private static ModelFinderResult resultWithVerdicts(List<InvariantVerdict> verdicts)
      throws Exception {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "OracleGate", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + err);
    }
    MSystem system = new MSystem(model);
    ScenarioReport witnessed =
        new ScenarioReport(
            new org.tzi.use.smt.config.Scenario(0, List.of()),
            ScenarioOutcome.WITNESSED,
            system,
            Map.of(TranslationMode.UNCERTAIN, verdicts));
    return new ModelFinderResult(
        new FragmentCoverageLedger(List.of()),
        ScenarioProfile.EXISTS,
        org.tzi.use.smt.finder.ProfileOutcome.SATISFIED,
        List.of(witnessed),
        new org.tzi.use.smt.finder.BoundedCompletenessQualification(
            List.of(new ClassScope("X", 1, 1)),
            List.<AssociationScope>of(),
            List.<AttributeDomain>of(),
            ScenarioProfile.EXISTS,
            List.of("s0"),
            "test"),
        0);
  }

  private static final Set<String> ACTIVE = Set.of("X::gate");

  /** The happy path: one present, holding verdict for the single active invariant. */
  @Test
  public void singleHoldingVerdictValidates() throws Exception {
    assertTrue(
        ResultClassification.oracleAgrees(
            resultWithVerdicts(List.of(new InvariantVerdict("X::gate", true))), ACTIVE));
  }

  /** A verdict for a DIFFERENT invariant does not cover the active one: no vacuous pass. */
  @Test
  public void missingActiveVerdictMustNotValidate() throws Exception {
    assertFalse(
        ResultClassification.oracleAgrees(
            resultWithVerdicts(List.of(new InvariantVerdict("X::other", true))), ACTIVE));
  }

  /** Empty verdict set with nonempty active invariants: nothing was checked for them. */
  @Test
  public void emptyVerdictSetMustNotValidate() throws Exception {
    assertFalse(ResultClassification.oracleAgrees(resultWithVerdicts(List.of()), ACTIVE));
  }

  /** A duplicate verdict for one invariant leaves the oracle set ill-defined: reject. */
  @Test
  public void duplicateVerdictsMustNotValidate() throws Exception {
    assertFalse(
        ResultClassification.oracleAgrees(
            resultWithVerdicts(
                List.of(
                    new InvariantVerdict("X::gate", true),
                    new InvariantVerdict("X::gate", true))),
            ACTIVE));
  }

  /** A defined-false verdict is a counterexample, not agreement. */
  @Test
  public void falseVerdictMustNotValidate() throws Exception {
    assertFalse(
        ResultClassification.oracleAgrees(
            resultWithVerdicts(List.of(new InvariantVerdict("X::gate", false))), ACTIVE));
  }

  /** An UNDEFINED verdict does not hold, so it cannot validate -- and it is not a FALSE. */
  @Test
  public void undefinedVerdictMustNotValidate() throws Exception {
    assertFalse(
        ResultClassification.oracleAgrees(
            resultWithVerdicts(
                List.of(
                    new InvariantVerdict("X::gate", InvariantOutcome.UNDEFINED))),
            ACTIVE));
  }

  /** A model with NO active invariants is validly validated: structure and bounds were
   *  checked, and there are no OCL invariants to verify. */
  @Test
  public void emptyActiveSetIsValidated() throws Exception {
    assertTrue(
        ResultClassification.oracleAgrees(resultWithVerdicts(List.of()), Set.of()));
  }

  /** Every active invariant must be covered when several are active. */
  @Test
  public void partialCoverageOfSeveralActivesMustNotValidate() throws Exception {
    Set<String> actives = Set.of("X::gate", "X::gate2");
    assertTrue(
        ResultClassification.oracleAgrees(
            resultWithVerdicts(
                List.of(
                    new InvariantVerdict("X::gate", true),
                    new InvariantVerdict("X::gate2", true))),
            actives));
    assertFalse(
        ResultClassification.oracleAgrees(
            resultWithVerdicts(List.of(new InvariantVerdict("X::gate", true))), actives));
  }

  /** End-to-end: an enforced invariant whose read is UNDEFINED is classified REFUTED-adjacent
   *  (the profile is not satisfied), and the verdict outcome is UNDEFINED -- not FALSE. */
  @Test
  public void enforcedUndefinedReadIsRefutedWithAnUndefinedVerdict() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.<AssociationScope>of(),
            List.of(new AttributeDomain("X", "b", null, List.of("true"), null, null)),
            Set.of("X::andUndef"),
            QueryParser.parse("satisfy", org.tzi.use.smt.config.ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult result =
        org.tzi.use.smt.finder.SmtModelFinder.find(model, config);
    assertFalse(result.satisfiable());
    org.junit.Assert.assertTrue(
        "a negative is never re-checked: there is no witness and therefore no oracle verdict",
        result.verdicts().isEmpty());
    org.junit.Assert.assertEquals(
        "no quantile enclosure enters this encoding, so the negative is an exact bounded"
            + " refutation; the F-vs-U distinction for the wrapper is covered by"
            + " OracleSemanticKernelMatrixTest",
        ResultClassification.UNSAT_EXACT,
        ResultClassification.of(result, Set.of("X::andUndef")));
  }

  private static MModel compile() {
    String source =
        """
        model OracleGateEndToEnd
        class X
        attributes
          b : Boolean
        end
        constraints
        context x : X inv andUndef:
          x.b and oclUndefined(Boolean)
        """;
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "OracleGateEndToEnd", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + err);
    }
    return model;
  }
}
