package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Milestone 4.3's macro-parity evidence, against the real unmodified {@code Library.properties}
 * fixture the incumbent Kodkod validator itself uses. SATISFY must keep behaving exactly as before,
 * and a targeted {@code counterexample(j)} must deliver a witness that USE's own evaluator
 * independently classifies as: target {@code j} DEFINED-FALSE, every other active invariant TRUE.
 */
public class CounterexampleQueryTest {

  @Test
  public void targetedCounterexampleIsolatesExactlyTheTargetAsDefinedFalse() throws Exception {
    MModel model = compileLibrary();
    AnalysisConfiguration config =
        withQuery(model, readConfig(model, null), "counterexample(Book::titleIsKey)");

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("a title collision is reachable in this scope", result.satisfiable());
    Map<String, InvariantOutcome> outcomes = outcomes(result);
    assertEquals(
        "the target must be a DEFINED violation, never merely 'not true'",
        InvariantOutcome.FALSE,
        outcomes.get("Book::titleIsKey"));
    for (String active : config.activeInvariants()) {
      if (active.equals("Book::titleIsKey")) {
        continue;
      }
      assertEquals(
          "non-target " + active + " must be independently true in the witness",
          InvariantOutcome.TRUE,
          outcomes.get(active));
    }
  }

  /**
   * SATISFY and a targeted counterexample for the same invariant are mutually exclusive over one
   * snapshot (proposal, "Common core and complete query-mode semantics"). This is the SATISFY half
   * of the parity claim: the same configuration, only the query changed, still reproduces the
   * incumbent {@code mv -validate} outcome -- SAT with all nine invariants independently true.
   */
  @Test
  public void satisfyStillReproducesTheIncumbentValidateOutcome() throws Exception {
    MModel model = compileLibrary();
    AnalysisConfiguration explicit = withQuery(model, readConfig(model, null), "satisfy");

    ModelFinderResult result = SmtModelFinder.find(model, explicit);

    assertTrue(result.satisfiable());
    assertTrue(result.allActiveInvariantsHold());
    assertEquals(9, result.verdicts().size());
    assertEquals(
        SmtModelFinder.find(model, readConfig(model, null)).allActiveInvariantsHold(),
        result.allActiveInvariantsHold());
  }

  /**
   * The incumbent invariant-independence check ({@code InvariantIndepChecker}: activate everything,
   * negate one invariant at a time, solve) reproduced as a sweep of targeted counterexamples -- one
   * obligation per active invariant, each attributed to its own target alone.
   *
   * <p>The expected partition is a property of the REAL {@code Library.properties} bounds, not a
   * convenience: an invariant is independent here only if the configured domains can actually
   * produce a violating value. The four key/structure invariants can (three objects drawing from
   * three candidate strings may repeat one; a User may borrow two Copies of one Book). The five
   * format/range invariants cannot: {@code User_name}, {@code User_address}, {@code
   * Copy_signature}, {@code Book_title} and {@code Book_auth} offer no empty and no undefined
   * string, and {@code Book_year = Set&#123;2000,2005,2010&#125;} has no value below 1455. Bounded
   * non-independence is a real, qualified result, and the sweep reports it rather than pretending
   * the invariant was checked unboundedly.
   */
  @Test
  public void sweepingEveryTargetReproducesIncumbentInvariantIndependence() throws Exception {
    MModel model = compileLibrary();
    AnalysisConfiguration config =
        withQuery(model, readConfig(model, null), "invariant-independence");

    Map<String, ModelFinderResult> sweep = SmtModelFinder.independenceSweep(model, config);

    assertEquals(
        "one obligation per active invariant", config.activeInvariants().size(), sweep.size());
    assertEquals(config.activeInvariants(), sweep.keySet());

    Set<String> independent =
        sweep.entrySet().stream()
            .filter(entry -> entry.getValue().satisfiable())
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    assertEquals(
        new java.util.TreeSet<>(
            java.util.List.of(
                "Book::titleIsKey",
                "Copy::signatureIsKey",
                "User::nameIsKey",
                "User::noDoubleBorrowings")),
        independent);

    for (Map.Entry<String, ModelFinderResult> entry : sweep.entrySet()) {
      if (!entry.getValue().satisfiable()) {
        continue;
      }
      Map<String, InvariantOutcome> outcomes = outcomes(entry.getValue());
      assertEquals(
          entry.getKey() + " must be the only violated invariant in its own witness",
          InvariantOutcome.FALSE,
          outcomes.get(entry.getKey()));
      for (String other : config.activeInvariants()) {
        if (!other.equals(entry.getKey())) {
          assertEquals(
              other + " must stay true in " + entry.getKey() + "'s witness",
              InvariantOutcome.TRUE,
              outcomes.get(other));
        }
      }
    }
  }

  /**
   * Adversarial 1: an invariant that can only ever be UNDEFINED (never defined-false) is not a
   * counterexample. If the compiler lowered the target to {@code not val} instead of {@code def and
   * not val}, this would return a witness and an invalid-navigation/illegal-confidence-argument
   * failure would masquerade as a violation.
   */
  @Test
  public void anUndefinedTargetIsRejectedRatherThanReportedAsAViolation() throws Exception {
    MModel model =
        compileSource(
            """
            model UndefinedTarget
            class Sample
            attributes
              marker : Integer
            end
            constraints
            context s : Sample inv NeverDefined: oclUndefined(Boolean)
            context s : Sample inv MarkerIsOne: s.marker = 1
            """,
            "UndefinedTarget");
    AnalysisConfiguration config =
        configuration(
            model,
            "counterexample(Sample::NeverDefined)",
            "Sample::NeverDefined",
            "Sample::MarkerIsOne");

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertFalse(
        "undefined never satisfies a counterexample; only DEFINED-false does",
        result.satisfiable());
  }

  /**
   * Adversarial 2: attribution. {@code SharedCause} is violated by exactly the same states that
   * violate {@code MarkerIsOne}, so no snapshot can violate the target alone. Dropping the "every
   * other active invariant is true" conjunct would make this SAT and attribute a violation to
   * {@code MarkerIsOne} that is not actually isolated to it.
   */
  @Test
  public void aFalseNonTargetPreventsAttributionToTheTarget() throws Exception {
    MModel model =
        compileSource(
            """
            model SharedCause
            class Sample
            attributes
              marker : Integer
            end
            constraints
            context s : Sample inv MarkerIsOne: s.marker = 1
            context s : Sample inv MarkerIsOneToo: s.marker = 1
            """,
            "SharedCause");
    AnalysisConfiguration config =
        configuration(
            model,
            "counterexample(Sample::MarkerIsOne)",
            "Sample::MarkerIsOne",
            "Sample::MarkerIsOneToo");

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertFalse(
        "a violation shared with a non-target cannot be attributed to the target",
        result.satisfiable());
  }

  /** The same two invariants, but only the target active: now the witness is legitimately found. */
  @Test
  public void theSameTargetIsFoundOnceTheSharedNonTargetIsNotActive() throws Exception {
    MModel model =
        compileSource(
            """
            model SharedCause
            class Sample
            attributes
              marker : Integer
            end
            constraints
            context s : Sample inv MarkerIsOne: s.marker = 1
            context s : Sample inv MarkerIsOneToo: s.marker = 1
            """,
            "SharedCause");
    AnalysisConfiguration config =
        configuration(model, "counterexample(Sample::MarkerIsOne)", "Sample::MarkerIsOne");

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue(result.satisfiable());
    assertEquals(InvariantOutcome.FALSE, outcomes(result).get("Sample::MarkerIsOne"));
  }

  /**
   * Milestones 4.5-4.6 stay closed: no query shape is silently degraded to a weaker one.
   *
   * <p>{@code uncertain Book::titleIsKey is false} and {@code not satisfy} used to be listed here
   * too. Milestone 4.4 implemented them -- explicit atoms and Boolean connectives, checked against
   * the independent USE oracle by evaluating the compiled query over its verdicts -- so they moved
   * to {@code BooleanQueryAlgebraTest} as working queries rather than refusals. Milestone 4.5 has
   * now done the same for {@code fragile(j)} and for nominal-mode atoms, which it moved into {@link
   * #nominalQueriesNowRunAgainstTheRealLibraryCorpusConfiguration}. Only the scenario profiles
   * remain deferred, and they still refuse BY NAME.
   */
  @Test
  public void unsupportedQueryShapesStillFailClosed() throws Exception {
    MModel model = compileLibrary();
    for (String query : new String[] {"cover satisfy", "uniform satisfy"}) {
      AnalysisConfiguration config = withQuery(model, readConfig(model, null), query);
      IllegalArgumentException exception =
          assertThrows(
              query, IllegalArgumentException.class, () -> SmtModelFinder.find(model, config));
      assertTrue(
          "'" + query + "' failed with: " + exception.getMessage(),
          exception.getMessage().contains("Milestone"));
    }
  }

  /**
   * The nominal oracle on the real, unmodified corpus configuration rather than only on the UReal
   * micro-fixtures. Library is entirely crisp, so §5.1's degenerate case applies and nominal
   * erasure is the identity -- which makes these two the sharpest available check that the erasure
   * evaluator agrees with the U-aware one wherever there is nothing to erase.
   */
  @Test
  public void nominalQueriesNowRunAgainstTheRealLibraryCorpusConfiguration() throws Exception {
    MModel model = compileLibrary();

    ModelFinderResult nominalTrue =
        SmtModelFinder.find(
            model, withQuery(model, readConfig(model, null), "nominal Book::titleIsKey is true"));
    assertTrue(nominalTrue.satisfiable());
    assertEquals(InvariantOutcome.TRUE, outcomes(nominalTrue).get("Book::titleIsKey"));

    ModelFinderResult fragile =
        SmtModelFinder.find(
            model, withQuery(model, readConfig(model, null), "fragile(Book::titleIsKey)"));
    assertFalse(
        "a crisp invariant cannot be fragile: erasure changes nothing, so T_N and F_U conflict",
        fragile.satisfiable());
  }

  private static Map<String, InvariantOutcome> outcomes(ModelFinderResult result) {
    Map<String, InvariantOutcome> outcomes = new LinkedHashMap<>();
    for (InvariantVerdict verdict : result.verdicts()) {
      outcomes.put(verdict.invariantName(), verdict.outcome());
    }
    return outcomes;
  }

  private static AnalysisConfiguration configuration(
      MModel model, String query, String... activeInvariants) {
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    return new AnalysisConfiguration(
        java.util.List.of(new org.tzi.use.smt.config.ClassScope("Sample", 1, 1)),
        java.util.List.of(),
        java.util.List.of(
            new org.tzi.use.smt.config.AttributeDomain(
                "Sample",
                "marker",
                null,
                java.util.List.of(),
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.valueOf(3))),
        java.util.Set.of(activeInvariants),
        QueryParser.parse(query, vocabulary),
        java.time.Duration.ofSeconds(30),
        1);
  }

  private static AnalysisConfiguration withQuery(
      MModel model, AnalysisConfiguration base, String query) {
    QueryExpr parsed = QueryParser.parse(query, ConfigurationVocabulary.fromModel(model));
    return new AnalysisConfiguration(
        base.classScopes(),
        base.associationScopes(),
        base.attributeDomains(),
        base.activeInvariants(),
        parsed,
        base.timeout(),
        base.modelLimit());
  }

  private static AnalysisConfiguration readConfig(MModel model, String section) throws Exception {
    Path file = Path.of("../benchmark/examples/Library/Library.properties");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/Library/Library.properties");
    }
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw = ConfigurationReader.read(file, section);
    return ConfigurationReader.normalize(raw, vocabulary).requireSupported();
  }

  private static MModel compileLibrary() throws Exception {
    Path file = Path.of("../benchmark/examples/Library/Library.use");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/Library/Library.use");
    }
    return compileSource(Files.readString(file), "Library");
  }

  private static MModel compileSource(String source, String name) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, factory);
    err.flush();
    return model;
  }
}
