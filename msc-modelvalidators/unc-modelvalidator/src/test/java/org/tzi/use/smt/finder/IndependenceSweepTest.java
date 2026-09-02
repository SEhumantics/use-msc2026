package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.config.ScenarioProfile;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * The independence sweep's PREMISE, and the three ways a per-entry verdict can be read as more than
 * it is.
 *
 * <p>{@code COUNTEREXAMPLE(j)} is {@code F_U(j) AND (AND over i != j of T_U(i))}, which only means
 * "j is independent" when the active set has a model in the first place. Before this test existed,
 * the sweep never established that, and a single unsatisfiable active invariant inverted every
 * verdict it produced: the pathological invariant's own obligation is satisfiable (its conjunct
 * only has to be FALSE, which it always is), while every other obligation is refuted by the
 * pathological invariant sitting in its "all others are true" conjunct. Measured on the fixture
 * below, pre-fix:
 *
 * <pre>
 *   C::BelowTen      -> not independent (unsat)
 *   C::Contradiction -> INDEPENDENT (sat)     &lt;-- the unsatisfiable one
 *   C::Positive      -> not independent (unsat)
 *   baseline satisfy over the same active set -> sat=false   (never run by the sweep)
 * </pre>
 */
public class IndependenceSweepTest {

  private static final String CONTRADICTION_MODEL =
      """
      model Inversion
      class C
      attributes
        n : Integer
      end
      constraints
      context c : C inv Contradiction:
        c.n > 3 and c.n < 3
      context c : C inv Positive:
        c.n > 0
      context c : C inv BelowTen:
        c.n < 10
      """;

  private static final String VACUOUS_CONTEXT_MODEL =
      """
      model VacuousContext
      class Ghost
      attributes
        n : Integer
      end
      class Solid
      attributes
        n : Integer
      end
      constraints
      context g : Ghost inv GhostPositive:
        g.n > 0
      context r : Solid inv SolidPositive:
        r.n > 0
      """;

  private static final String TAUTOLOGY_MODEL =
      """
      model Tautology
      class E
      attributes
        n : Integer
      end
      constraints
      context e : E inv AlwaysTrue:
        true
      context e : E inv Positive:
        e.n > 0
      """;

  private static final String AGGREGATION_CYCLE_MODEL =
      """
      model AggCycle
      class F
      attributes
        tag : String
      end
      aggregation ParentOf between
        F[0..*] role parent
        F[0..1] role child
      end
      constraints
      context f : F inv FTag:
        f.tag = 't1'
      """;

  private static final Set<String> CONTRADICTION_SET =
      Set.of("C::Contradiction", "C::Positive", "C::BelowTen");

  /**
   * The defect itself: with one unsatisfiable active invariant the sweep must emit NO independence
   * verdicts, because every verdict it could emit would be the inverse of the truth.
   */
  @Test
  public void aJointlyUnsatisfiableActiveSetProducesNoIndependenceVerdictsAtAll() throws Exception {
    MModel model = compile(CONTRADICTION_MODEL, "Inversion");

    IndependenceSweepResult sweep =
        SmtModelFinder.independenceSweep(
            model, contradictionConfig(model, "invariant-independence"));

    assertEquals(
        "C::Contradiction is unsatisfiable, so the active set has no model and no per-invariant"
            + " obligation over it means anything",
        ActiveSetOutcome.JOINTLY_UNSATISFIABLE,
        sweep.activeSet());
    assertEquals(ProfileOutcome.REFUTED, sweep.baseline().outcome());
    assertFalse(sweep.hasVerdicts());
    assertTrue(
        "an entry map with anything in it is exactly what a caller would misread",
        sweep.entries().isEmpty());
    assertTrue(
        "the refusal must name the joint unsatisfiability, not just say nothing",
        sweep.statement().contains("JOINTLY UNSATISFIABLE"));
    assertTrue(
        "an UNSAT stays qualified by its bounds here as everywhere else",
        sweep.statement().contains("this result holds only within the configured bounds"));
  }

  /**
   * The other half of the same guarantee: reading independence off this sweep is impossible, not
   * merely discouraged. An EMPTY {@code independent()} would itself be a false claim ("nothing is
   * independent"), so the accessors throw.
   */
  @Test
  public void independenceCannotBeReadOffAnUnusableSweep() throws Exception {
    MModel model = compile(CONTRADICTION_MODEL, "Inversion");
    IndependenceSweepResult sweep =
        SmtModelFinder.independenceSweep(
            model, contradictionConfig(model, "invariant-independence"));

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, sweep::independent);
    assertTrue(thrown.getMessage().contains("JOINTLY UNSATISFIABLE"));
    assertThrows(IllegalStateException.class, sweep::notIndependent);
    assertThrows(IllegalStateException.class, sweep::unresolved);
    assertThrows(IllegalStateException.class, () -> sweep.entry("C::Contradiction"));
  }

  /**
   * The inverted signal the sweep now refuses to publish is REAL, not hypothetical: solved
   * one-by-one over the same active set, the unsatisfiable invariant's own obligation comes back
   * SAT and both sound invariants' come back UNSAT. This is the exact per-entry data the pre-fix
   * sweep returned as independence verdicts.
   */
  @Test
  public void theInvertedPerObligationSignalIsMeasuredNotAssumed() throws Exception {
    MModel model = compile(CONTRADICTION_MODEL, "Inversion");

    assertEquals(
        "the pathological invariant's own obligation only needs it to be FALSE, which it always"
            + " is -- pre-fix this was published as C::Contradiction INDEPENDENT",
        ProfileOutcome.SATISFIED,
        counterexample(model, "C::Contradiction").outcome());
    assertEquals(
        "every sound invariant's obligation is refuted by the pathological one sitting in its"
            + " 'all others are true' conjunct -- pre-fix this was published as NOT INDEPENDENT",
        ProfileOutcome.REFUTED,
        counterexample(model, "C::Positive").outcome());
    assertEquals(
        ProfileOutcome.REFUTED, counterexample(model, "C::BelowTen").outcome());
    assertEquals(
        "and the baseline the sweep now runs first is what makes all three meaningless",
        ProfileOutcome.REFUTED,
        SmtModelFinder.find(model, contradictionConfig(model, "satisfy")).outcome());
  }

  /**
   * An UNRESOLVED baseline (timeout, {@code unknown}) is not a jointly-unsatisfiable one, and must
   * not be reported as one: nothing was established either way, so nothing is claimed. Driven by a
   * real Z3 timeout on the NQueens {@code large} section, the same shape {@code SmtModelFinderTest}
   * already uses to pin {@link ProfileOutcome#PARTIAL}.
   */
  @Test
  public void aTimedOutBaselineIsUnresolvedRatherThanJointlyUnsatisfiable() throws Exception {
    MModel model = compileNQueens();
    AnalysisConfiguration legacy = nqueensConfig(model);
    AnalysisConfiguration impatient =
        new AnalysisConfiguration(
            legacy.classScopes(),
            legacy.associationScopes(),
            legacy.attributeDomains(),
            legacy.activeInvariants(),
            QueryParser.parse(
                "invariant-independence", ConfigurationVocabulary.fromModel(model)),
            Duration.ofMillis(50),
            legacy.modelLimit());

    IndependenceSweepResult sweep = SmtModelFinder.independenceSweep(model, impatient);

    assertEquals(ProfileOutcome.PARTIAL, sweep.baseline().outcome());
    assertEquals(
        "a timeout is not a proof that the active set has no model",
        ActiveSetOutcome.UNRESOLVED,
        sweep.activeSet());
    assertTrue(sweep.entries().isEmpty());
    assertTrue(sweep.statement().contains("unresolved"));
    assertThrows(IllegalStateException.class, sweep::notIndependent);
  }

  /**
   * The per-entry half of the same three-valued discipline. {@code ModelFinderResult.satisfiable()}
   * is {@code outcome() == SATISFIED}, so reading independence off it turns a timed-out obligation
   * into the substantive claim "the other invariants already force this one"; the mapping the sweep
   * actually uses is total over {@link ProfileOutcome} and keeps the two apart.
   */
  @Test
  public void anUnresolvedObligationIsNeverReadAsNotIndependent() {
    assertEquals(IndependenceVerdict.INDEPENDENT, IndependenceVerdict.of(ProfileOutcome.SATISFIED));
    assertEquals(
        IndependenceVerdict.NOT_INDEPENDENT, IndependenceVerdict.of(ProfileOutcome.REFUTED));
    assertEquals(IndependenceVerdict.UNRESOLVED, IndependenceVerdict.of(ProfileOutcome.PARTIAL));
  }

  /**
   * A context class the configuration gives ZERO object slots makes its invariant vacuously true,
   * so {@code counterexample(j)} is unsatisfiable for a reason that has nothing to do with the
   * other invariants. The verdict stays the literally true NOT INDEPENDENT, but the entry says
   * which bound produced it.
   */
  @Test
  public void aZeroCapacityContextClassIsFlaggedAsTheBoundThatDecidedTheEntry() throws Exception {
    MModel model = compile(VACUOUS_CONTEXT_MODEL, "VacuousContext");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Ghost", 0, 0), new ClassScope("Solid", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain(
                    "Ghost", "n", null, List.of(), BigDecimal.valueOf(-5), BigDecimal.valueOf(20)),
                new AttributeDomain(
                    "Solid", "n", null, List.of(), BigDecimal.valueOf(-5), BigDecimal.valueOf(20))),
            Set.of("Ghost::GhostPositive", "Solid::SolidPositive"),
            QueryParser.parse("invariant-independence", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);

    IndependenceSweepResult sweep = SmtModelFinder.independenceSweep(model, config);

    assertEquals(ActiveSetOutcome.SATISFIABLE, sweep.activeSet());
    IndependenceEntry ghost = sweep.entry("Ghost::GhostPositive");
    assertEquals(IndependenceVerdict.NOT_INDEPENDENT, ghost.verdict());
    assertEquals(0, ghost.contextCapacity());
    assertTrue(
        "no snapshot in scope contains a Ghost at all, so nothing about the OTHER invariants"
            + " produced this verdict",
        ghost.boundedScopeArtefact());
    assertTrue(ghost.statement().contains("zero object slots"));

    IndependenceEntry solid = sweep.entry("Solid::SolidPositive");
    assertEquals(IndependenceVerdict.INDEPENDENT, solid.verdict());
    assertEquals(1, solid.contextCapacity());
    assertFalse(solid.boundedScopeArtefact());
  }

  /**
   * A TAUTOLOGY is NOT INDEPENDENT, and that is the answer rather than a further artefact: nothing
   * can violate it, so it constrains nothing the rest of the set does not already allow. Pinned
   * explicitly so it is never confused with the zero-capacity case above -- its context class has
   * slots, and the flag stays off.
   */
  @Test
  public void aTautologyIsNotIndependentAndIsNotABoundedScopeArtefact() throws Exception {
    MModel model = compile(TAUTOLOGY_MODEL, "Tautology");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("E", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain(
                    "E", "n", null, List.of(), BigDecimal.valueOf(-5), BigDecimal.valueOf(20))),
            Set.of("E::AlwaysTrue", "E::Positive"),
            QueryParser.parse("invariant-independence", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);

    IndependenceSweepResult sweep = SmtModelFinder.independenceSweep(model, config);

    assertEquals(ActiveSetOutcome.SATISFIABLE, sweep.activeSet());
    IndependenceEntry tautology = sweep.entry("E::AlwaysTrue");
    assertEquals(IndependenceVerdict.NOT_INDEPENDENT, tautology.verdict());
    assertEquals(1, tautology.contextCapacity());
    assertFalse(
        "the scope is not what makes a tautology unviolatable -- the invariant is",
        tautology.boundedScopeArtefact());
    assertEquals(Set.of("E::Positive"), sweep.independent());
  }

  /**
   * Every solve in the sweep must be bounded by the configuration the caller actually passed. The
   * {@code aggregationcyclefreeness} toggle is the discriminator: with it ON the forced 2-cycle is
   * unreachable, so the active set is jointly unsatisfiable; with it OFF the same configuration has
   * a witness and the sweep produces entries. Building each step with {@link
   * AnalysisConfiguration}'s 7-argument constructor silently defaults the toggle to {@code false}
   * and makes the ON case indistinguishable from the OFF one.
   */
  @Test
  public void theSweepCarriesTheAggregationCycleFreenessToggleIntoEverySolve() throws Exception {
    assertEquals(
        "with cycle-freeness ON the forced 2-cycle has no model, so there is nothing to sweep",
        ActiveSetOutcome.JOINTLY_UNSATISFIABLE,
        cycleSweep(true).activeSet());

    IndependenceSweepResult off = cycleSweep(false);
    assertEquals(ActiveSetOutcome.SATISFIABLE, off.activeSet());
    assertEquals(Set.of("F::FTag"), off.entries().keySet());
  }

  private static IndependenceSweepResult cycleSweep(boolean cycleFree) throws Exception {
    MModel model = compile(AGGREGATION_CYCLE_MODEL, "AggCycle");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("F", 2, 2, List.of("F0", "F1"))),
            List.of(
                new AssociationScope(
                    "ParentOf", 2, 2, List.of(List.of("F0", "F1"), List.of("F1", "F0")))),
            List.of(new AttributeDomain("F", "tag", null, List.of("t1"), null, null)),
            Set.of("F::FTag"),
            QueryParser.parse("invariant-independence", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1,
            cycleFree);
    return SmtModelFinder.independenceSweep(model, config);
  }

  private static ModelFinderResult counterexample(MModel model, String target) throws Exception {
    AnalysisConfiguration base = contradictionConfig(model, "satisfy");
    AnalysisConfiguration targeted =
        new AnalysisConfiguration(
            base.classScopes(),
            base.associationScopes(),
            base.attributeDomains(),
            base.activeInvariants(),
            new QueryExpr.Profiled(ScenarioProfile.EXISTS, new QueryExpr.Counterexample(target)),
            base.timeout(),
            base.modelLimit());
    return SmtModelFinder.find(model, targeted);
  }

  private static AnalysisConfiguration contradictionConfig(MModel model, String query)
      throws Exception {
    return new AnalysisConfiguration(
        List.of(new ClassScope("C", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain(
                "C", "n", null, List.of(), BigDecimal.valueOf(-5), BigDecimal.valueOf(20))),
        CONTRADICTION_SET,
        QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
  }

  private static AnalysisConfiguration nqueensConfig(MModel model) throws Exception {
    Path file = Path.of("../benchmark/examples/NQueens/NQueens.properties");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/NQueens/NQueens.properties");
    }
    RawConfiguration raw = ConfigurationReader.read(file, "large");
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compileNQueens() throws Exception {
    Path file = Path.of("../benchmark/examples/NQueens/NQueens.use");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/NQueens/NQueens.use");
    }
    return compile(Files.readString(file), "NQueens");
  }

  private static MModel compile(String source, String name) {
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile: " + name);
    }
    return model;
  }
}
