package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.config.ScenarioProfile;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Drives {@link SmtModelFinder} against the real, unmodified {@code Library.properties} -- the
 * incumbent Kodkod validator's own fixture config, never before pointed at by anything on the SMT
 * side (every prior test hand-built a small ad-hoc scope). Both of its sections are exercised: the
 * unnamed section (a genuine 3/3/3 satisfiable scope) and {@code [titleCollision]}, a real,
 * already-documented-and-solver-verified UNSATISFIABLE counterpart (Book_title shrunk to 2
 * candidates against Book_min=Book_max=3, forcing a pigeonhole violation of Book_titleIsKey).
 */
public class SmtModelFinderTest {

  /**
   * The overload a live GUI plugin action must use: proves the whole pipeline (encode, solve,
   * reconstruct, re-evaluate) ends up targeting the caller's own session, not a throwaway one.
   */
  @Test
  public void findingIntoAnExistingSessionReusesItsSystemInsteadOfAThrowawayOne() throws Exception {
    MModel model = compileLibrary();
    AnalysisConfiguration config = readConfig(model, null);

    Session session = new Session();
    MSystem originalSystem = new MSystem(model);
    session.setSystem(originalSystem);
    UseSystemApi.create(session).createObjectEx(model.getClass("User"), "StaleUser");

    ModelFinderResult result = SmtModelFinder.find(session, model, config);

    assertTrue("expected SAT", result.satisfiable());
    assertSame(
        "must reuse the session's own MSystem instance, not construct a new one",
        originalSystem,
        result.system());
    MSystemState state = result.system().state();
    assertNull(
        "the stale pre-existing object must be gone after reconstruction",
        state.objectByName("StaleUser"));
    assertEquals(3, state.objectsOfClass(model.getClass("User")).size());
  }

  @Test
  public void theRealLibraryPropertiesDefaultSectionIsSatisfiableWithAllInvariantsHolding()
      throws Exception {
    MModel model = compileLibrary();
    AnalysisConfiguration config = readConfig(model, null);

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    assertTrue("expected every enforced invariant to hold", result.allActiveInvariantsHold());
    assertEquals(9, result.verdicts().size());

    MSystemState state = result.system().state();
    assertEquals(3, state.objectsOfClass(model.getClass("User")).size());
    assertEquals(3, state.objectsOfClass(model.getClass("Copy")).size());
    assertEquals(3, state.objectsOfClass(model.getClass("Book")).size());
  }

  /**
   * The genuinely discriminating case: proves the finder can report UNSAT, not just SAT, on a real,
   * deliberately contradictory configuration that a human already hand-verified against Z3 (see the
   * properties file's own section comment) -- not one invented for this test.
   */
  @Test
  public void theTitleCollisionSectionIsUnsatisfiable() throws Exception {
    MModel model = compileLibrary();
    AnalysisConfiguration config = readConfig(model, "titleCollision");

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertFalse("expected UNSAT", result.satisfiable());
    assertTrue("no witness to reconstruct on UNSAT", result.verdicts().isEmpty());
  }

  /**
   * Proves the {@link SolverProcess}-accepting overload genuinely threads the caller's own instance
   * through to the solve -- both the SAT default section and the UNSAT {@code titleCollision}
   * section, back to back on the SAME persistent instance, must still reach the exact same real
   * verdicts as the two tests above (which each use a fresh one-shot process internally). If this
   * overload silently ignored the given process and constructed its own instead, this test would
   * still pass by coincidence; {@code SolverProcessTest}'s own {@code
   * reusingTheSameConstNameAcrossCallsIsIsolatedByReset} is what actually proves reuse-with-reset
   * is safe at the SolverProcess layer -- this test is about the wiring above it.
   */
  @Test
  public void findWithAnExternalSolverProcessReusesItAcrossSatAndUnsat() throws Exception {
    MModel model = compileLibrary();
    try (SolverProcess shared =
        SolverProcess.persistent(SolverBinary.resolve(), Duration.ofSeconds(30))) {
      AnalysisConfiguration satConfig = readConfig(model, null);
      ModelFinderResult satResult = SmtModelFinder.find(model, satConfig, shared);
      assertTrue("expected SAT", satResult.satisfiable());
      assertTrue("expected every enforced invariant to hold", satResult.allActiveInvariantsHold());

      AnalysisConfiguration unsatConfig = readConfig(model, "titleCollision");
      ModelFinderResult unsatResult = SmtModelFinder.find(model, unsatConfig, shared);
      assertFalse("expected UNSAT", unsatResult.satisfiable());
    }
  }

  /**
   * Proves the association end multiplicities are derived from the real model in the correct
   * direction, not just with the correct pair of numbers attached to the wrong end -- a swap would
   * still pass both tests above (their aggregate bounds are loose enough on this scope that neither
   * direction breaks SAT), so it needed its own forcing scope. Borrows is declared {@code
   * User[0..1] role user, Copy[0..*] role copy}: each Copy has at most one User. With exactly one
   * Copy and exactly two required Borrows links, every link must land on that one Copy, forcing it
   * to have two Users -- which the real multiplicity forbids. Correct derivation: UNSAT. A
   * get(0)/get(1) multiplicity swap would make this SAT instead (verified by actually performing
   * that swap and rerunning during development, not assumed).
   */
  @Test
  public void borrowsEndMultiplicityIsAppliedToTheCorrectEndNotJustPresent() throws Exception {
    MModel model = compileLibrary();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("User", 2, 2), new ClassScope("Copy", 1, 1)),
            List.of(new AssociationScope("Borrows", 2, 2)),
            List.of(),
            Set.of(),
            QueryExpr.SATISFY,
            Duration.ofSeconds(30),
            1);

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertFalse(
        "one Copy cannot legally have two Users (User[0..1] per Copy); two forced Borrows links"
            + " into a single Copy must be UNSAT",
        result.satisfiable());
  }

  /**
   * A scenario profile over the CRISP corpus, which is the degenerate case of Milestone 4.6.
   *
   * <p>Library declares no U-typed attribute, so {@code Sigma_K} has exactly ONE (empty) scenario
   * and the three profile equations genuinely coincide on it -- {@code exists s} and {@code forall
   * s} over a one-element set are the same statement. Reporting SATISFIED here is therefore the
   * equations' own answer, not a silent degradation to EXISTS, and the result still says COVER so
   * no caller can mistake which question was asked. {@code ScenarioProfileTest} carries the
   * fixtures where the three strengths genuinely diverge.
   */
  @Test
  public void aScenarioProfileOverACrispModelHasExactlyOneScenario() throws Exception {
    MModel model = compileLibrary();
    AnalysisConfiguration legacy = readConfig(model, null);
    AnalysisConfiguration covered =
        new AnalysisConfiguration(
            legacy.classScopes(),
            legacy.associationScopes(),
            legacy.attributeDomains(),
            legacy.activeInvariants(),
            new QueryExpr.Profiled(ScenarioProfile.COVER, new QueryExpr.Satisfy()),
            legacy.timeout(),
            legacy.modelLimit());

    ModelFinderResult result = SmtModelFinder.find(model, covered);

    assertEquals(ScenarioProfile.COVER, result.profile());
    assertEquals(ProfileOutcome.SATISFIED, result.outcome());
    assertEquals("a crisp model has exactly one (empty) scenario", 1, result.scenarios().size());
    assertTrue(result.scenarios().get(0).scenario().bindings().isEmpty());
    assertTrue(result.allActiveInvariantsHold());
  }

  /**
   * A genuinely empty reconstructed witness -- every class AND association scope forced to {@code
   * (0,0)}, so the solver's SAT witness has literally zero objects and zero links of any kind, not
   * merely an untested corner reached by coincidence. Proves {@code SystemStateReconstructor}
   * handles the all-empty case cleanly (no crash, an {@link MSystemState} with zero total objects)
   * and that every one of Library's own 9 real invariants -- each an implicit "for all instances of
   * the context class" -- holds VACUOUSLY over that empty population, independently confirmed by
   * USE's own evaluator rather than merely assumed. {@code borrowsEndMultiplicityIsApplied...}
   * above and {@code theRealLibraryPropertiesDefaultSectionIsSatisfiable...} both reach a NON-empty
   * witness; nothing else in the suite exercises a real, positive, solver-CHOSEN empty population
   * (as opposed to a scope with capacity 0 that never reaches SAT at all, e.g. {@code
   * ExistentialInvariantTest}'s own forced-empty fixture).
   */
  @Test
  public void everyScopeForcedEmptyReconstructsALiterallyEmptySystemWithEveryInvariantVacuouslyTrue()
      throws Exception {
    MModel model = compileLibrary();
    AnalysisConfiguration legacy = readConfig(model, null);
    AnalysisConfiguration allEmpty =
        new AnalysisConfiguration(
            legacy.classScopes().stream()
                .map(scope -> new ClassScope(scope.className(), 0, 0))
                .toList(),
            legacy.associationScopes().stream()
                .map(scope -> new AssociationScope(scope.associationName(), 0, 0))
                .toList(),
            legacy.attributeDomains(),
            legacy.activeInvariants(),
            legacy.query(),
            legacy.timeout(),
            legacy.modelLimit());

    ModelFinderResult result = SmtModelFinder.find(model, allEmpty);

    assertTrue(
        "expected SAT: the empty population trivially satisfies every invariant",
        result.satisfiable());
    assertTrue(
        "expected every invariant to hold vacuously over the empty population",
        result.allActiveInvariantsHold());
    assertEquals(9, result.verdicts().size());
    assertTrue(
        "the reconstructed witness must have literally zero objects",
        result.system().state().allObjects().isEmpty());
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
    String source = Files.readString(file);
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "Library", err, factory);
    err.flush();
    return model;
  }
}
