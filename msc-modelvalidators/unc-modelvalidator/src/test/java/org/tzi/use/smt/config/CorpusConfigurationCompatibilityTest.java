package org.tzi.use.smt.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

/**
 * Ensures every finding entry in the benchmark manifest still has a loadable selected section.
 *
 * <p>Milestone 4.6 added the corpus's FIRST query-carrying scenarios (the six {@code
 * ScenarioProfiles-*} rows), so "the corpus contains no query key at all" stopped being true and
 * stopped being the property worth pinning. What matters for backward compatibility is narrower and
 * is what {@link #everyQuerylessCorpusScenarioNormalizesToTheExactSatisfyExistsSingleton} asserts:
 * a scenario with NO query key still normalizes to the exact {@code QueryExpr.SATISFY} singleton,
 * and the only rows allowed to carry a query key are still the six scenario-profile ones. The
 * counts are split accordingly so a new corpus row cannot be added without a deliberate update here
 * -- the Study B supersession rows added for spec S9 are the most recent such update, and they land
 * on the query-less side.
 */
public class CorpusConfigurationCompatibilityTest {

  /**
   * Corpus rows that configure no query at all: the 37 that predate Milestone 4.6, plus the two
   * Study B supersession rows ({@code IntegerBitwidth-DailyCap}, {@code RealGrid-UnitInterval}),
   * which are ordinary satisfiability questions asked of both backends and so carry no query key
   * either, plus the four {@code UIntegerThreshold-*} rows that landed with the second U-type
   * family, the four {@code UBooleanThreshold-*} rows that landed with the third and the five
   * {@code UStringThreshold-*} rows that landed with the fourth -- ordinary satisfiability
   * questions too, so they land on the query-less side as well, plus {@code PriceCalc}/{@code
   * PriceCalc-UNSAT} (2026-08-29), authored to give binary Integer '*', unary '+', class-bounds
   * named objects, and an association-bounds explicit link-tuple literal a real corpus scenario --
   * also ordinary satisfiability questions, no query key.
   */
  private static final int QUERYLESS_SCENARIOS = 54;

  /** The Milestone 4.6 scenario-profile rows, the first corpus entries to configure a query. */
  private static final int QUERY_CARRYING_SCENARIOS = 6;

  @Test
  public void everyManifestConfigurationLoads() throws Exception {
    Path repository = repositoryRoot();
    Path manifest =
        repository.resolve("msc-modelvalidators/benchmark/src/main/resources/manifest.json");
    Manifest parsed;
    try (Reader reader = Files.newBufferedReader(manifest)) {
      parsed = new Gson().fromJson(reader, Manifest.class);
    }

    int loaded = 0;
    for (Example example : parsed.examples) {
      if (!example.mode.contains("finding")) {
        continue;
      }
      assertNotNull(example.id + ": propertiesFile", example.propertiesFile);
      Path configuration =
          repository
              .resolve("msc-modelvalidators/benchmark/examples")
              .resolve(example.directory)
              .resolve(example.propertiesFile);
      ConfigurationReader.read(configuration, example.section);
      loaded++;
    }

    assertEquals("manifest corpus size", QUERYLESS_SCENARIOS + QUERY_CARRYING_SCENARIOS, loaded);
  }

  @Test
  public void everyQuerylessCorpusScenarioNormalizesToTheExactSatisfyExistsSingleton()
      throws Exception {
    Path repository = repositoryRoot();
    Path manifest =
        repository.resolve("msc-modelvalidators/benchmark/src/main/resources/manifest.json");
    Manifest parsed;
    try (Reader reader = Files.newBufferedReader(manifest)) {
      parsed = new Gson().fromJson(reader, Manifest.class);
    }

    int queryless = 0;
    int withQuery = 0;
    for (Example example : parsed.examples) {
      if (!example.mode.contains("finding")) continue;
      Path configuration =
          repository
              .resolve("msc-modelvalidators/benchmark/examples")
              .resolve(example.directory)
              .resolve(example.propertiesFile);
      RawConfiguration raw = ConfigurationReader.read(configuration, example.section);
      if (raw.entries().containsKey("query")) {
        assertTrue(
            example.id + ": only the Milestone 4.6 scenario-profile rows configure a query",
            example.id.startsWith("ScenarioProfiles-"));
        withQuery++;
        continue;
      }
      assertSame(
          example.id + ": missing query must preserve the exact default object",
          QueryExpr.SATISFY,
          ConfigurationReader.normalize(raw, ConfigurationVocabulary.empty())
              .configuration()
              .query());
      queryless++;
    }

    assertEquals("query-less corpus size", QUERYLESS_SCENARIOS, queryless);
    assertEquals("query-carrying corpus size", QUERY_CARRYING_SCENARIOS, withQuery);
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      if (Files.isRegularFile(
          current.resolve("msc-modelvalidators/benchmark/src/main/resources/manifest.json"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new AssertionError("repository root not found from test working directory");
  }

  private static final class Manifest {
    List<Example> examples;
  }

  private static final class Example {
    String id;
    String directory;
    String propertiesFile;
    String section;
    String mode;
  }
}
