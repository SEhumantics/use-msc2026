package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.FileReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kodkod.engine.Solution;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.LegacyListDelimiterHandler;
import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;
import org.tzi.kodkod.InvariantIndepChecker;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.config.Options;
import org.tzi.use.config.Options.WarningType;
import org.tzi.use.kodkod.UseKodkodModelValidator;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.finder.ActiveSetOutcome;
import org.tzi.use.smt.finder.IndependenceSweepResult;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;

/**
 * Milestone 4.7b's incumbent-parity evidence for spec §9 Study E: the query algebra's DERIVED
 * {@code satisfy} and {@code counterexample} macros agree with the incumbent Kodkod validator's own
 * {@code -validate} and {@code -invIndep} commands, driven headlessly in one JVM over the same
 * unmodified {@code .properties} files. This extends Task 3.8b's {@link KodkodSmtDifferentialTest}
 * from a single Library SAT/UNSAT pair to the complete set of corpus rows on which BOTH engines
 * reach a real verdict.
 *
 * <p><b>The denominator is stated, not hidden.</b> The 43-row corpus splits three ways: Kodkod
 * reaches a real SATISFIABLE/UNSATISFIABLE verdict on 33 rows, this project reaches one on 16, and
 * the INTERSECTION -- the only set on which "parity" is even a meaningful word -- is the 6 rows
 * listed in {@link #INTERSECTION}. Parity is claimed on those 6 and nowhere else. The 27 rows this
 * project refuses (fail-closed {@code ConfigurationReadException}/{@code SmtTranslationException})
 * are not parity failures but they are not parity successes either, and they are excluded from the
 * numerator AND the denominator alike.
 *
 * <p><b>{@code TRIVIALLY_SATISFIABLE} is not agreement.</b> On the ten U-type/scenario rows Kodkod
 * cannot express, it does not error -- it silently drops the keys it does not recognize and reports
 * {@code TRIVIALLY_SATISFIABLE} for a problem it never actually posed. {@link
 * #kodkodsScenarioProfileVerdictsAreSilentDropArtefactsNotASecondOpinion()} pins that behaviour down
 * as the artefact it is, so it can never be miscounted as a second opinion agreeing with ours.
 */
public class QueryMacroParityTest {

  /** One corpus row: the manifest id, its directory, model, properties file and section. */
  private record Row(String id, String directory, String useFile, String propertiesFile,
      String section) {}

  /**
   * The 6 rows on which BOTH engines reach a real verdict -- derived directly from a real
   * BenchmarkRunner sweep of all 43 manifest rows, not chosen for convenience. Every other row is
   * either refused by this project (27 rows) or answered only by Kodkod (10 rows, all of them the
   * silent-drop artefact described in the class comment).
   */
  private static final List<Row> INTERSECTION = List.of(
      new Row("Library", "Library", "Library.use", "Library.properties", null),
      new Row("Library-UNSAT", "Library", "Library.use", "Library.properties", "titleCollision"),
      new Row("Inheritance", "Inheritance", "Vehicle.use", "Vehicle.properties", null),
      new Row("Inheritance-UNSAT", "Inheritance", "Vehicle.use", "Vehicle.properties",
          "nonpositivewheels"),
      new Row("MultipleInheritance", "MultipleInheritance", "MultipleInheritance.use",
          "MultipleInheritance.properties", null),
      new Row("MultipleInheritance-UNSAT", "MultipleInheritance", "MultipleInheritance.use",
          "MultipleInheritance.properties", "collision"));

  /**
   * The 3 intersection rows whose ACTIVE INVARIANT SET has a model -- the only rows on which a
   * per-invariant independence verdict is a meaningful thing to compare at all.
   */
  private static final List<Row> JOINTLY_SATISFIABLE = INTERSECTION.stream()
      .filter(row -> !row.id().endsWith("-UNSAT")).toList();

  /**
   * The 3 intersection rows whose active set is JOINTLY UNSATISFIABLE, each by construction: the
   * section narrows exactly one bound until exactly one named invariant has no model (Library's
   * {@code titleCollision} breaks {@code Book::titleIsKey} by pigeonhole, Vehicle's
   * {@code nonpositivewheels} breaks {@code Vehicle::PositiveWheels} by domain,
   * MultipleInheritance's {@code collision} breaks {@code D::LevelsDiffer} by singleton pools --
   * all three documented in
   * the .properties files themselves). Mapped to that invariant, because it is the one the
   * incumbent's sweep inverts.
   */
  private static final Map<String, String> UNSATISFIABLE_BY = Map.of(
      "Library-UNSAT", "Book::titleIsKey",
      "Inheritance-UNSAT", "Vehicle::PositiveWheels",
      "MultipleInheritance-UNSAT", "D::LevelsDiffer");

  private static final List<Row> JOINTLY_UNSATISFIABLE = INTERSECTION.stream()
      .filter(row -> row.id().endsWith("-UNSAT")).toList();

  /** The six {@code ScenarioProfiles} rows, whose {@code query} key Kodkod has no concept of. */
  private static final List<Row> SCENARIO_PROFILE_ROWS = List.of(
      scenarioRow("ScenarioProfiles-ExistsOnly", "existsOnly"),
      scenarioRow("ScenarioProfiles-ExistsOnlyCover", "existsOnlyCover"),
      scenarioRow("ScenarioProfiles-ExistsOnlyUniform", "existsOnlyUniform"),
      scenarioRow("ScenarioProfiles-CoverNotUniformCover", "coverNotUniformCover"),
      scenarioRow("ScenarioProfiles-CoverNotUniformUniform", "coverNotUniformUniform"),
      scenarioRow("ScenarioProfiles-UniformShared", "uniformShared"));

  private static Row scenarioRow(String id, String section) {
    return new Row(id, "ScenarioProfiles", "ScenarioProfiles.use", "ScenarioProfiles.properties",
        section);
  }

  private enum Bucket {
    SAT,
    UNSAT
  }

  /**
   * Study E, macro-expansion equivalence, half one: the derived {@code satisfy} macro
   * ({@code uncertain all are true}) agrees with the incumbent's {@code mv -validate} on every row
   * of the intersection. Both engines are driven from the SAME unmodified properties file/section.
   */
  @Test
  public void derivedSatisfyAgreesWithIncumbentValidateOnTheWholeIntersection() throws Exception {
    Map<String, String> report = new LinkedHashMap<>();
    for (Row row : INTERSECTION) {
      MModel model = compile(row);
      Bucket kodkod = kodkodValidateBucket(model, row);
      Bucket smt = smtSatisfyBucket(model, row);
      report.put(row.id(), kodkod + " / " + smt);
      assertEquals(
          row.id() + ": derived `satisfy` must agree with the incumbent's own -validate", kodkod,
          smt);
    }
    assertEquals("parity is claimed on the intersection and nowhere else", 6, report.size());
    assertEquals(
        "the intersection's own expected split, from the real 43-row benchmark sweep",
        List.of("SAT / SAT", "UNSAT / UNSAT", "SAT / SAT", "UNSAT / UNSAT", "SAT / SAT",
            "UNSAT / UNSAT"),
        List.copyOf(report.values()));
  }

  /**
   * Study E, macro-expansion equivalence, half two: sweeping the derived
   * {@code counterexample(j)} macro over every active invariant -- exactly what {@link
   * SmtModelFinder#independenceSweep} does -- reproduces the incumbent {@link
   * InvariantIndepChecker}'s own per-invariant verdict, obligation for obligation.
   *
   * <p>Kodkod reports three distinct strings: {@code Independent} (its negated-invariant solve came
   * back SATISFIABLE or TRIVIALLY_SATISFIABLE), {@code Dependent} (TRIVIALLY_UNSATISFIABLE) and
   * {@code Not independent for given properties} (UNSATISFIABLE). The last two are the same verdict
   * split by how Kodkod's own translator happened to discharge it, so both map to "not
   * independent"; only the independent/not-independent distinction is compared.
   *
   * <p><b>The denominator dropped from 30 to 15, and that is the point.</b> This test used to sweep
   * all 6 intersection rows and report 30/30 agreement. 15 of those 30 obligations were over the
   * three {@code -UNSAT} rows, whose active invariant set has NO model -- and over such a set every
   * {@code counterexample(j)} answer is inverted (see {@link
   * #theSweepRefusesTheVerdictsTheIncumbentInvertsOnAJointlyUnsatisfiableActiveSet()}). Both
   * engines agreed there because both engines had the SAME defect, which is agreement about
   * nothing. This
   * project now refuses those 15, so parity is claimed on the 15 obligations that were ever
   * meaningful and nowhere else.
   */
  @Test
  public void derivedCounterexampleSweepAgreesWithIncumbentInvIndepObligationForObligation()
      throws Exception {
    int obligations = 0;
    for (Row row : JOINTLY_SATISFIABLE) {
      MModel model = compile(row);
      Map<String, Boolean> kodkod = kodkodIndependence(model, row);
      Map<String, Boolean> smt = smtIndependence(model, row);
      assertEquals(row.id() + ": both engines must sweep the same invariant set", kodkod.keySet(),
          smt.keySet());
      assertFalse(row.id() + ": a sweep of nothing proves nothing", kodkod.isEmpty());
      assertEquals(row.id() + ": every counterexample(j) obligation must agree with -invIndep",
          kodkod, smt);
      obligations += kodkod.size();
    }
    assertEquals("9 (Library) + 3 (Inheritance) + 3 (MultipleInheritance)", 15, obligations);
  }

  /**
   * The other 15 obligations, and why they are not parity evidence. On each {@code -UNSAT} row the
   * section deliberately makes exactly ONE active invariant unsatisfiable, so the active set has no
   * model and {@code COUNTEREXAMPLE(j) = F_U(j) AND (AND over i != j of T_U(i))} inverts: the
   * pathological invariant's own obligation is satisfiable (its conjunct only has to be FALSE) and
   * every sound invariant's is refuted (the pathological one sits in its "all others are true"
   * conjunct).
   *
   * <p>Measured here, not assumed: on all three rows the incumbent publishes a full verdict set in
   * which the ONE unsatisfiable invariant -- and only it -- is reported {@code Independent}, which
   * is the exact inverse of the truth. This project reports {@link
   * ActiveSetOutcome#JOINTLY_UNSATISFIABLE} and no per-invariant verdict at all.
   */
  @Test
  public void theSweepRefusesTheVerdictsTheIncumbentInvertsOnAJointlyUnsatisfiableActiveSet()
      throws Exception {
    int refused = 0;
    for (Row row : JOINTLY_UNSATISFIABLE) {
      MModel model = compile(row);
      AnalysisConfiguration config = readConfig(model, row, "invariant-independence");
      IndependenceSweepResult sweep = SmtModelFinder.independenceSweep(model, config);

      assertEquals(row.id() + ": this section has no model for its own active set",
          ActiveSetOutcome.JOINTLY_UNSATISFIABLE, sweep.activeSet());
      assertTrue(row.id() + ": no verdict may be published over an unusable premise",
          sweep.entries().isEmpty());
      assertThrows(IllegalStateException.class, sweep::independent);

      Map<String, Boolean> kodkod = kodkodIndependence(compile(row), row);
      assertEquals(row.id() + ": the incumbent publishes a verdict for every active invariant"
          + " regardless", config.activeInvariants().size(), kodkod.size());
      assertEquals(row.id() + ": and exactly the UNSATISFIABLE invariant is the one it calls"
          + " Independent -- the inversion, measured",
          List.of(UNSATISFIABLE_BY.get(row.id())),
          kodkod.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList());
      refused += config.activeInvariants().size();
    }
    assertEquals("9 (Library-UNSAT) + 3 (Inheritance-UNSAT) + 3 (MultipleInheritance-UNSAT)", 15,
        refused);
  }

  /**
   * The honesty guard the gate explicitly asks for: on the six {@code ScenarioProfiles} rows Kodkod
   * returns {@code TRIVIALLY_SATISFIABLE} for ALL of them, because it silently ignores the
   * {@code query} key and every uncertainty key alongside it -- the SAME answer for
   * {@code exists satisfy}, {@code cover satisfy} and {@code uniform satisfy} on the same model.
   * That is not a second opinion; it is a measurement of the incumbent's blind spot. This project
   * genuinely distinguishes the three profiles on the same six rows (3 satisfied, 3 refuted), which
   * is only meaningful BECAUSE Kodkod's cells are constant.
   */
  @Test
  public void kodkodsScenarioProfileVerdictsAreSilentDropArtefactsNotASecondOpinion()
      throws Exception {
    Map<String, Solution.Outcome> kodkodCells = new LinkedHashMap<>();
    Map<String, Boolean> smtCells = new LinkedHashMap<>();
    for (Row row : SCENARIO_PROFILE_ROWS) {
      MModel model = compile(row);
      kodkodCells.put(row.id(), kodkodValidateOutcome(model, row));
      smtCells.put(row.id(), smtSatisfyBucket(model, row) == Bucket.SAT);
    }

    assertEquals(6, kodkodCells.size());
    for (Map.Entry<String, Solution.Outcome> cell : kodkodCells.entrySet()) {
      assertEquals(
          cell.getKey()
              + ": Kodkod never posed this problem -- it dropped the query key and answered the"
              + " empty one",
          Solution.Outcome.TRIVIALLY_SATISFIABLE, cell.getValue());
    }
    assertEquals(
        "this project genuinely separates EXISTS from COVER from UNIFORM on the same six rows",
        Map.of("ScenarioProfiles-ExistsOnly", true, "ScenarioProfiles-ExistsOnlyCover", false,
            "ScenarioProfiles-ExistsOnlyUniform", false, "ScenarioProfiles-CoverNotUniformCover",
            true, "ScenarioProfiles-CoverNotUniformUniform", false, "ScenarioProfiles-UniformShared",
            true),
        smtCells);
  }

  // ---------------------------------------------------------------- incumbent (Kodkod) side

  private static Bucket kodkodValidateBucket(MModel model, Row row) throws Exception {
    return switch (kodkodValidateOutcome(model, row)) {
      case SATISFIABLE, TRIVIALLY_SATISFIABLE -> Bucket.SAT;
      case UNSATISFIABLE, TRIVIALLY_UNSATISFIABLE -> Bucket.UNSAT;
    };
  }

  private static Solution.Outcome kodkodValidateOutcome(MModel model, Row row) throws Exception {
    Session session = newSession(model);
    IModel kodkodModel = configuredKodkodModel(model, row);
    UseKodkodModelValidator validator = new UseKodkodModelValidator(session);
    validator.validate(kodkodModel);
    return validator.solution().outcome();
  }

  /** Captures {@link InvariantIndepChecker}'s own reported verdict per invariant, from its log. */
  private static Map<String, Boolean> kodkodIndependence(MModel model, Row row) throws Exception {
    Session session = newSession(model);
    IModel kodkodModel = configuredKodkodModel(model, row);

    List<String> lines = new ArrayList<>();
    Appender capture = new AppenderSkeleton() {
      @Override
      protected void append(LoggingEvent event) {
        lines.add(String.valueOf(event.getMessage()));
      }

      @Override
      public void close() {}

      @Override
      public boolean requiresLayout() {
        return false;
      }
    };
    Logger logger = Logger.getLogger(InvariantIndepChecker.class);
    logger.addAppender(capture);
    try {
      new InvariantIndepChecker(session).validate(kodkodModel);
    } finally {
      logger.removeAppender(capture);
    }

    Map<String, Boolean> verdicts = new TreeMap<>();
    for (String line : lines) {
      int split = line.lastIndexOf(": ");
      if (split < 0) {
        continue;
      }
      String invariant = line.substring(0, split);
      String verdict = line.substring(split + 2);
      Boolean independent = switch (verdict) {
        case "Independent" -> Boolean.TRUE;
        case "Dependent", "Not independent for given properties" -> Boolean.FALSE;
        default -> null;
      };
      if (independent != null) {
        verdicts.put(invariant, independent);
      }
    }
    return verdicts;
  }

  private static IModel configuredKodkodModel(MModel model, Row row) throws Exception {
    invalidatePluginModelFactoryCache();
    IModel kodkodModel = PluginModelFactory.INSTANCE.getModel(model);

    INIConfiguration ini = new INIConfiguration();
    ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
    try (FileReader reader = new FileReader(propertiesFile(row).toFile())) {
      ini.read(reader);
    }
    Configuration section = ini.getSection(row.section());

    PrintWriter warnings = new PrintWriter(System.err, true);
    PropertyConfigurationVisitor visitor = new PropertyConfigurationVisitor(section, warnings);
    kodkodModel.accept(visitor);
    if (visitor.containErrors()) {
      throw new IllegalStateException(
          "Kodkod configuration reported errors for " + row.id() + " -- this row is not in the"
              + " intersection after all, and the parity claim over it would be false");
    }
    return kodkodModel;
  }

  private static Session newSession(MModel model) {
    Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
    Options.doPLUGIN = false;
    Session session = new Session();
    session.setSystem(new MSystem(model));
    return session;
  }

  /**
   * {@code PluginModelFactory.INSTANCE} caches one transformed {@link IModel} behind a private
   * {@code reTransform} gate; this test compiles many models in one JVM (same technique as {@link
   * KodkodSmtDifferentialTest}).
   */
  private static void invalidatePluginModelFactoryCache() throws Exception {
    Field reTransform = PluginModelFactory.class.getDeclaredField("reTransform");
    reTransform.setAccessible(true);
    reTransform.set(PluginModelFactory.INSTANCE, true);
  }

  // ---------------------------------------------------------------- this project's side

  private static Bucket smtSatisfyBucket(MModel model, Row row) throws Exception {
    ModelFinderResult result = SmtModelFinder.find(model, readConfig(model, row, null));
    return result.satisfiable() ? Bucket.SAT : Bucket.UNSAT;
  }

  /**
   * This project's per-invariant independence verdicts for one row. {@link
   * IndependenceSweepResult#independent()} throws unless the sweep's baseline established that the
   * active set has a model, so a row whose set is jointly unsatisfiable cannot silently produce a
   * comparable-looking map here.
   */
  private static Map<String, Boolean> smtIndependence(MModel model, Row row) throws Exception {
    AnalysisConfiguration config = readConfig(model, row, "invariant-independence");
    IndependenceSweepResult sweep = SmtModelFinder.independenceSweep(model, config);
    Map<String, Boolean> verdicts = new TreeMap<>();
    sweep.independent().forEach(target -> verdicts.put(target, Boolean.TRUE));
    sweep.notIndependent().forEach(target -> verdicts.put(target, Boolean.FALSE));
    assertTrue(row.id() + ": an unresolved obligation is not a verdict and must not be compared"
        + " as one", sweep.unresolved().isEmpty());
    return verdicts;
  }

  /**
   * Reads the row's real configuration, then -- when {@code query} is given -- replaces ONLY the
   * query, so the parity comparison changes the requested witness predicate and nothing else about
   * the bounds. A null {@code query} keeps whatever the file itself says (SATISFY when it says
   * nothing, which is exactly the legacy behaviour the incumbent's -validate implements).
   */
  private static AnalysisConfiguration readConfig(MModel model, Row row, String query)
      throws Exception {
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw = ConfigurationReader.read(propertiesFile(row), row.section());
    AnalysisConfiguration config = ConfigurationReader.normalize(raw, vocabulary).requireSupported();
    if (query == null) {
      return config;
    }
    return new AnalysisConfiguration(config.classScopes(), config.associationScopes(),
        config.attributeDomains(), config.activeInvariants(), QueryParser.parse(query, vocabulary),
        config.timeout(), config.modelLimit());
  }

  // ---------------------------------------------------------------- shared fixtures

  private static Path examplesDir() {
    Path dir = Path.of("examples");
    return Files.isDirectory(dir) ? dir : Path.of("msc-modelvalidators/benchmark/examples");
  }

  private static Path propertiesFile(Row row) {
    return examplesDir().resolve(row.directory()).resolve(row.propertiesFile());
  }

  private static MModel compile(Row row) throws Exception {
    Path file = examplesDir().resolve(row.directory()).resolve(row.useFile());
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(Files.readString(file), row.useFile(), err,
            new ModelFactory());
    err.flush();
    assertTrue(row.id() + " must compile", model != null);
    return model;
  }
}
