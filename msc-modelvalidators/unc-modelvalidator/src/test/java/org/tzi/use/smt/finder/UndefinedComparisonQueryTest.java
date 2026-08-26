package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Regression cover for defect B1: {@code ExpressionTranslator.comparison} used to short-circuit on
 * an {@code oclUndefined} operand and return a CONSTANT, never translating the other side at all.
 *
 * <p>That erased the other operand's definedness entirely. USE's own {@code Op_equal} (kind {@code
 * SPECIAL}, so {@code ExpStdOp.eval} hands it undefined arguments rather than short-circuiting to
 * undefined) implements a TOTAL rule, verified directly against the real evaluator: {@code x = y}
 * is always defined, and is true exactly when both sides are undefined or both are defined and
 * equal. So {@code x.b <> oclUndefined(B)} is DEFINED-FALSE for an {@code A} with no {@code b} link
 * -- a real, reachable counterexample the constant reported as non-existent.
 */
public class UndefinedComparisonQueryTest {

  private static final String NAVIGATION_MODEL =
      """
      model UndefinedNavigation
      class A end
      class B end
      association AB between
        A[0..1] role a
        B[0..1] role b
      end
      constraints
      context x : A inv HasB: x.b <> oclUndefined(B)
      """;

  /**
   * The executed reproduction from the review, verbatim: one A, one B, an optional link. USE's own
   * evaluator reports {@code A::HasB} FALSE for the unlinked state, so a targeted counterexample
   * MUST find it. The old constant made this UNSAT -- a genuine, USE-confirmed counterexample
   * reported as non-existent, and never caught because the UNSAT path never reaches the oracle.
   */
  @Test
  public void anUnlinkedNavigationIsAGenuineCounterexampleToNotUndefined() throws Exception {
    MModel model = compile(NAVIGATION_MODEL, "UndefinedNavigation");
    ModelFinderResult result =
        SmtModelFinder.find(model, navigationConfig(model, "counterexample(A::HasB)"));

    assertTrue(
        "an A with no b link violates HasB; USE's own evaluator reports FALSE for it",
        result.satisfiable());
    assertEquals(InvariantOutcome.FALSE, outcomes(result).get("A::HasB"));
  }

  /** The positive control: the same scope can also satisfy HasB, by actually linking the pair. */
  @Test
  public void aLinkedNavigationSatisfiesNotUndefined() throws Exception {
    MModel model = compile(NAVIGATION_MODEL, "UndefinedNavigation");
    ModelFinderResult result = SmtModelFinder.find(model, navigationConfig(model, "satisfy"));

    assertTrue(result.satisfiable());
    assertEquals(InvariantOutcome.TRUE, outcomes(result).get("A::HasB"));
  }

  /**
   * The other half of USE's total rule, on an operand whose definedness the encoding really does
   * know: an attribute always carries a value from its configured domain, so {@code s.marker =
   * oclUndefined(Integer)} is DEFINED-FALSE and can never be satisfied, while its negation is
   * DEFINED-TRUE. This is exactly the shape five of Library's nine invariants use.
   */
  @Test
  public void anAlwaysDefinedAttributeIsNeverEqualToOclUndefined() throws Exception {
    MModel model =
        compile(
            """
            model AttributeUndefined
            class Sample
            attributes
              marker : Integer
            end
            constraints
            context s : Sample inv MarkerIsUndefined: s.marker = oclUndefined(Integer)
            context s : Sample inv MarkerIsDefined: s.marker <> oclUndefined(Integer)
            """,
            "AttributeUndefined");

    ModelFinderResult defined =
        SmtModelFinder.find(model, sampleConfig(model, "satisfy", "Sample::MarkerIsDefined"));
    assertTrue(defined.satisfiable());
    assertEquals(InvariantOutcome.TRUE, outcomes(defined).get("Sample::MarkerIsDefined"));

    ModelFinderResult undefined =
        SmtModelFinder.find(model, sampleConfig(model, "satisfy", "Sample::MarkerIsUndefined"));
    assertEquals(
        "a configured attribute is always defined, so '= oclUndefined' is defined-false",
        false,
        undefined.satisfiable());

    ModelFinderResult attributed =
        SmtModelFinder.find(
            model,
            sampleConfig(
                model, "counterexample(Sample::MarkerIsUndefined)", "Sample::MarkerIsUndefined"));
    assertTrue(attributed.satisfiable());
    assertEquals(InvariantOutcome.FALSE, outcomes(attributed).get("Sample::MarkerIsUndefined"));
  }

  /**
   * Where the other operand's definedness genuinely cannot be encoded, the translation must fail
   * closed with a located reason -- never a constant. {@code ->size()} is outside the supported
   * fragment, and the old short-circuit swallowed it whole because it never looked at that operand.
   */
  @Test
  public void anUntranslatableOtherOperandFailsClosedInsteadOfBecomingAConstant() throws Exception {
    MModel model =
        compile(
            """
            model UnsupportedUndefined
            class Sample
            attributes
              marker : Integer
            end
            constraints
            context s : Sample inv SizeIsUndefined:
              Sample.allInstances()->size() = oclUndefined(Integer)
            """,
            "UnsupportedUndefined");
    AnalysisConfiguration config = sampleConfig(model, "satisfy", "Sample::SizeIsUndefined");

    SmtTranslationException exception =
        assertThrows(SmtTranslationException.class, () -> SmtModelFinder.find(model, config));
    assertTrue(
        "the refusal must name the construct it could not translate: " + exception.getMessage(),
        exception.getMessage().contains("size"));
  }

  private static Map<String, InvariantOutcome> outcomes(ModelFinderResult result) {
    Map<String, InvariantOutcome> outcomes = new LinkedHashMap<>();
    for (InvariantVerdict verdict : result.verdicts()) {
      outcomes.put(verdict.invariantName(), verdict.outcome());
    }
    return outcomes;
  }

  private static AnalysisConfiguration navigationConfig(MModel model, String query) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("A", 1, 1), new ClassScope("B", 1, 1)),
        List.of(new AssociationScope("AB", 0, 1)),
        List.of(),
        Set.of("A::HasB"),
        QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
  }

  private static AnalysisConfiguration sampleConfig(
      MModel model, String query, String... activeInvariants) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Sample", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain(
                "Sample", "marker", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(3))),
        Set.of(activeInvariants),
        QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
  }

  private static MModel compile(String source, String name) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, factory);
    err.flush();
    return model;
  }
}
