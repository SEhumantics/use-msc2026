package org.tzi.use.smt.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import org.junit.Test;

public class QueryParserTest {
  private static final ConfigurationVocabulary VOCABULARY =
      ConfigurationVocabulary.of(
          Set.of("Reading"),
          Set.of(),
          Set.of(),
          Set.of("Reading_ReliablyFast", "Reading_WellFormed"));

  @Test
  public void parsesAtomsConnectivesAggregatesAndPrecedence() {
    QueryExpr parsed =
        QueryParser.parse(
            "nominal ReliablyFast is true or not uncertain WellFormed is false"
                + " and uncertain all are true",
            VOCABULARY);

    assertEquals(
        new QueryExpr.Profiled(
            ScenarioProfile.EXISTS,
            new QueryExpr.Or(
                new QueryExpr.Classification(
                    TranslationMode.NOMINAL, "Reading::ReliablyFast", InvariantOutcome.TRUE),
                new QueryExpr.And(
                    new QueryExpr.Not(
                        new QueryExpr.Classification(
                            TranslationMode.UNCERTAIN,
                            "Reading::WellFormed",
                            InvariantOutcome.FALSE)),
                    new QueryExpr.Aggregate(
                        TranslationMode.UNCERTAIN, QueryExpr.AggregateScope.ALL)))),
        parsed);
  }

  @Test
  public void parsesFunctionalAtomsMacrosAndProfiles() {
    assertEquals(
        new QueryExpr.Profiled(
            ScenarioProfile.EXISTS,
            new QueryExpr.Classification(
                TranslationMode.UNCERTAIN,
                "Reading::ReliablyFast",
                InvariantOutcome.UNDEFINED)),
        QueryParser.parse("undef(uncertain, ReliablyFast)", VOCABULARY));
    assertEquals(
        new QueryExpr.Profiled(
            ScenarioProfile.COVER, new QueryExpr.Counterexample("Reading::ReliablyFast")),
        QueryParser.parse("cover counterexample(ReliablyFast)", VOCABULARY));
    assertEquals(
        new QueryExpr.Profiled(
            ScenarioProfile.UNIFORM, new QueryExpr.Fragile("Reading::ReliablyFast")),
        QueryParser.parse("uniform fragile(Reading::ReliablyFast)", VOCABULARY));
    assertEquals(QueryExpr.SATISFY, QueryParser.parse("satisfy", VOCABULARY));
  }

  @Test
  public void rejectsMalformedUnknownAmbiguousAndInvalidTargetFormsWithPositions() {
    assertFailure("uncertain ReliablyFast true", "position 24", "expected 'is'");
    assertFailure("uncertain Missing is true", "position 11", "unknown invariant 'Missing'");
    assertFailure(
        "uncertain ReliablyFast is false and uncertain WellFormed is true"
            + " and uncertain others are true",
        "position 80",
        "others requires exactly one target invariant");
    assertFailure(
        "counterexample(ReliablyFast, WellFormed)",
        "position 28",
        "expected ')' after one target invariant");
    assertFailure(
        "uniform invariant-independence",
        "position 9",
        "invariant-independence cannot be combined with a scenario profile");
  }

  private static void assertFailure(String query, String position, String message) {
    ConfigurationReadException exception =
        assertThrows(
            ConfigurationReadException.class, () -> QueryParser.parse(query, VOCABULARY));
    assertTrue(exception.getMessage(), exception.getMessage().contains(position));
    assertTrue(exception.getMessage(), exception.getMessage().contains(message));
  }
}
