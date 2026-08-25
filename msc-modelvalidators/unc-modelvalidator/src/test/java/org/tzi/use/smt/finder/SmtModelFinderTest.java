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
