package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;

import java.io.FileReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import kodkod.engine.Solution;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.LegacyListDelimiterHandler;
import org.junit.Test;
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
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;

/**
 * Phase 3's capstone: proves the SMT model finder agrees with the incumbent Kodkod validator on the
 * SAME, unmodified {@code Library.properties} -- both its satisfiable default section and its
 * documented UNSAT {@code titleCollision} counterpart. Kodkod exposes only a coarse SAT-family/
 * UNSAT-family outcome (no per-invariant detail, via a non-standard {@code solution()} accessor
 * this repo's own port added), so that is the only fair, solver-agnostic comparison: does a
 * satisfying instance exist within the same bounded scope -- not whether the two solvers pick the
 * same witness (different search procedures, not expected to agree value-for-value).
 */
public class KodkodSmtDifferentialTest {

  private enum Bucket {
    SAT,
    UNSAT
  }

  @Test
  public void bothValidatorsAgreeTheDefaultSectionIsSatisfiable() throws Exception {
    MModel model = compileLibrary();
    assertEquals(Bucket.SAT, kodkodBucket(model, null));
    assertEquals(Bucket.SAT, smtBucket(model, null));
  }

  @Test
  public void bothValidatorsAgreeTitleCollisionIsUnsatisfiable() throws Exception {
    MModel model = compileLibrary();
    assertEquals(Bucket.UNSAT, kodkodBucket(model, "titleCollision"));
    assertEquals(Bucket.UNSAT, smtBucket(model, "titleCollision"));
  }

  private static Bucket kodkodBucket(MModel model, String section) throws Exception {
    Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
    Options.doPLUGIN = false;

    Session session = new Session();
    MSystem mSystem = new MSystem(model);
    session.setSystem(mSystem);

    invalidatePluginModelFactoryCache();
    IModel kodkodModel = PluginModelFactory.INSTANCE.getModel(model);

    INIConfiguration ini = new INIConfiguration();
    ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
    try (FileReader reader = new FileReader(libraryPropertiesFile().toFile())) {
      ini.read(reader);
    }
    Configuration config = ini.getSection(section);

    PrintWriter warnings = new PrintWriter(System.err, true);
    PropertyConfigurationVisitor configVisitor = new PropertyConfigurationVisitor(config, warnings);
    kodkodModel.accept(configVisitor);
    if (configVisitor.containErrors()) {
      throw new IllegalStateException(
          "Kodkod configuration reported errors for section " + section);
    }

    UseKodkodModelValidator validator = new UseKodkodModelValidator(session);
    validator.validate(kodkodModel);
    Solution.Outcome outcome = validator.solution().outcome();
    return switch (outcome) {
      case SATISFIABLE, TRIVIALLY_SATISFIABLE -> Bucket.SAT;
      case UNSATISFIABLE, TRIVIALLY_UNSATISFIABLE -> Bucket.UNSAT;
    };
  }

  private static Bucket smtBucket(MModel model, String section) throws Exception {
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw = ConfigurationReader.read(libraryPropertiesFile(), section);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, vocabulary).requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);
    return result.allActiveInvariantsHold() ? Bucket.SAT : Bucket.UNSAT;
  }

  /**
   * {@code PluginModelFactory.INSTANCE} caches a single transformed {@link IModel} behind a private
   * {@code reTransform} gate (see {@code EndToEndValidationTest}/{@code BenchmarkRunner} for the
   * same technique) -- required here because this test class transforms more than one {@code
   * MModel} instance across its two test methods within one JVM.
   */
  private static void invalidatePluginModelFactoryCache() throws Exception {
    Field reTransform = PluginModelFactory.class.getDeclaredField("reTransform");
    reTransform.setAccessible(true);
    reTransform.set(PluginModelFactory.INSTANCE, true);
  }

  private static Path libraryPropertiesFile() {
    Path file = Path.of("examples/Library/Library.properties");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/Library/Library.properties");
    }
    return file;
  }

  private static MModel compileLibrary() throws Exception {
    Path file = Path.of("examples/Library/Library.use");
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
