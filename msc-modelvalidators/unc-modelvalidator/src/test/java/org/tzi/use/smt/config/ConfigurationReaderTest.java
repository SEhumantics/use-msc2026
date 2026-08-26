package org.tzi.use.smt.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.Test;

public class ConfigurationReaderTest {

  private static final ConfigurationVocabulary LIBRARY =
      ConfigurationVocabulary.of(
          Set.of("User", "Copy", "Book"), Set.of("Borrows", "BelongsTo"),
          Set.of("User_name", "Book_title"), Set.of("User_nameIsKey", "Book_titleIsKey"));

  @Test
  public void readsDefaultSectionAndNormalizesKnownLegacyKeys() throws Exception {
    Path file =
        temporaryConfiguration(
            """
            -- USE comments must not become property keys.
            User_min = 2
            User_max = 3
            User_name = Set{'Ada', 'Bob'}
            Book_title = Set{'One', 'Two'}
            Borrows_min = 0
            Borrows_max = -1
            User_nameIsKey = active
            Book_titleIsKey = inactive
            Integer_min = -5
            Integer_max = 9
            timeout = 12
            modelLimit = 4
            aggregationcyclefreeness = on
            """);

    ConfigurationReader.NormalizedConfiguration normalized =
        ConfigurationReader.normalize(ConfigurationReader.read(file, null), LIBRARY);
    AnalysisConfiguration configuration = normalized.configuration();

    assertTrue(configuration.classScopes().contains(new ClassScope("User", 2, 3)));
    assertTrue(configuration.associationScopes().contains(new AssociationScope("Borrows", 0, -1)));
    assertTrue(
        configuration.attributeDomains().stream()
            .anyMatch(
                domain ->
                    domain.className().equals("User")
                        && domain.attributeName().equals("name")
                        && domain.enumeratedValues().equals(java.util.List.of("'Ada'", "'Bob'"))));
    assertEquals(Set.of("User::nameIsKey"), configuration.activeInvariants());
    assertEquals(Duration.ofSeconds(12), configuration.timeout());
    assertEquals(4, configuration.modelLimit());
    assertEquals(QueryExpr.SATISFY, configuration.query());
    assertEquals("aggregationcyclefreeness", normalized.diagnostics().getFirst().key());
  }

  @Test
  public void parsesAQueryAndRemovesTheDeferredDiagnosticOnlyWhenSuccessful() throws Exception {
    Path file =
        temporaryConfiguration(
            "User_min = 1\nquery = uncertain nameIsKey is false and uncertain others are true\n");

    ConfigurationReader.NormalizedConfiguration normalized =
        ConfigurationReader.normalize(ConfigurationReader.read(file, null), LIBRARY);

    assertTrue(normalized.diagnostics().isEmpty());
    assertEquals(
        new QueryExpr.Profiled(
            ScenarioProfile.EXISTS,
            new QueryExpr.And(
                new QueryExpr.Classification(
                    TranslationMode.UNCERTAIN, "User::nameIsKey", InvariantOutcome.FALSE),
                new QueryExpr.Aggregate(
                    TranslationMode.UNCERTAIN, QueryExpr.AggregateScope.OTHERS))),
        normalized.requireSupported().query());
  }

  @Test
  public void malformedQueryFailsClosedInsteadOfBecomingADeferredDiagnostic() throws Exception {
    Path file = temporaryConfiguration("User_min = 1\nquery = uncertain Missing is true\n");

    ConfigurationReadException exception =
        assertThrows(
            ConfigurationReadException.class,
            () -> ConfigurationReader.normalize(ConfigurationReader.read(file, null), LIBRARY));

    assertTrue(exception.getMessage(), exception.getMessage().contains("query at position 11"));
    assertTrue(exception.getMessage(), exception.getMessage().contains("unknown invariant 'Missing'"));
  }

  @Test
  public void readsNamedSectionsWithoutInheritingDefaultValues() throws Exception {
    Path file =
        temporaryConfiguration(
            """
            User_min = 1
            [chosen]
            User_min = 3
            User_max = 3
            """);

    ConfigurationReader.NormalizedConfiguration normalized =
        ConfigurationReader.normalize(ConfigurationReader.read(file, "chosen"), LIBRARY);

    assertTrue(normalized.configuration().classScopes().contains(new ClassScope("User", 3, 3)));
    assertFalse(normalized.configuration().classScopes().contains(new ClassScope("User", 1, 1)));
  }

  @Test
  public void reportsUnknownKeysWithoutSilentlyDroppingThem() throws Exception {
    Path file = temporaryConfiguration("User_min = 1\nUser_max = 1\nfuture_feature = enabled\n");

    ConfigurationReader.NormalizedConfiguration normalized =
        ConfigurationReader.normalize(ConfigurationReader.read(file, null), LIBRARY);

    assertEquals("future_feature", normalized.diagnostics().getFirst().key());
    ConfigurationReadException exception =
        assertThrows(ConfigurationReadException.class, normalized::requireSupported);
    assertTrue(exception.getMessage().contains("unsupported configuration key(s): future_feature"));
  }

  @Test
  public void readsUncertaintyComponentsAsSeparateAttributeDomains() throws Exception {
    ConfigurationVocabulary vocabulary =
        ConfigurationVocabulary.of(Set.of("Sensor"), Set.of(), Set.of("Sensor_speed"), Set.of());
    Path file =
        temporaryConfiguration(
            """
            Sensor_min = 1
            Sensor_max = 1
            Sensor_speed_value = Set{0.30, 0.40}
            Sensor_speed_uncertainty = Set{0.01, 0.02}
            """);

    AnalysisConfiguration configuration =
        ConfigurationReader.normalize(ConfigurationReader.read(file, null), vocabulary)
            .requireSupported();

    assertTrue(
        configuration
            .attributeDomains()
            .contains(
                new AttributeDomain(
                    "Sensor", "speed", "value", java.util.List.of("0.30", "0.40"), null, null)));
    assertTrue(
        configuration
            .attributeDomains()
            .contains(
                new AttributeDomain(
                    "Sensor",
                    "speed",
                    "uncertainty",
                    java.util.List.of("0.01", "0.02"),
                    null,
                    null)));
  }

  @Test
  public void rejectsMissingSectionsAndInvalidBoundsWithActionableMessages() throws Exception {
    Path file = temporaryConfiguration("[present]\nUser_min = 4\nUser_max = 2\n");

    ConfigurationReadException missing =
        assertThrows(
            ConfigurationReadException.class, () -> ConfigurationReader.read(file, "absent"));
    assertTrue(missing.getMessage().contains("section 'absent' does not exist"));

    ConfigurationReadException bounds =
        assertThrows(
            ConfigurationReadException.class,
            () ->
                ConfigurationReader.normalize(ConfigurationReader.read(file, "present"), LIBRARY));
    assertTrue(bounds.getMessage().contains("invalid class bounds for 'User': min=4, max=2"));
  }

  @Test
  public void rejectsMalformedEnumeratedDomainsWithTheOffendingKey() throws Exception {
    Path file = temporaryConfiguration("User_min = 1\nUser_max = 1\nUser_name = Ada, Bob\n");

    ConfigurationReadException exception =
        assertThrows(
            ConfigurationReadException.class,
            () -> ConfigurationReader.normalize(ConfigurationReader.read(file, null), LIBRARY));

    assertTrue(exception.getMessage().contains("invalid enumerated domain for 'User_name'"));
  }

  private static Path temporaryConfiguration(String contents) throws Exception {
    Path file = Files.createTempFile("unc-configuration", ".properties");
    Files.writeString(file, contents);
    file.toFile().deleteOnExit();
    return file;
  }
}
