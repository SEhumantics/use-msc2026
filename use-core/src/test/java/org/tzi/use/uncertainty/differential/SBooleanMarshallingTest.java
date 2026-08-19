package org.tzi.use.uncertainty.differential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SBoolean marshalling, added in S3.
 *
 * <p>Before this, {@code SBooleanValue} was outside {@link HistoricalOracle}'s marshallable receiver
 * set, so all 39 operations of the largest file in the port — {@code StandardOperationsSBoolean},
 * 1502 lines — reported {@code UNSUPPORTED} and had <em>no evidence source of any kind</em>: the
 * fork ships no SBoolean test either.
 *
 * <p>These tests assert that the historical side can now actually be driven. They do not compare
 * against a ported implementation, because none exists until S9 — the value of the work at S3 is
 * that the instrument is built before the thing it measures.
 */
public class SBooleanMarshallingTest {

    @Test
    @DisplayName("an opinion round-trips into the historical SBooleanValue and back")
    void opinionRoundTrips() {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            Object h = oracle.toHistorical(UValue.sBoolean(0.3, 0.2, 0.5, 0.5));
            assertNotNull(h);
            assertEquals("SBooleanValue", h.getClass().getSimpleName());

            UValue back = oracle.fromHistorical(h);
            assertNotNull(back);
            assertTrue(back.canonical().contains("0.3") || back.canonical().contains("0.300"),
                    "the belief mass must survive the round trip: " + back.canonical());
        }
    }

    @Test
    @DisplayName("Builder.build() interns TRUE and FALSE, and the non-interned twins are distinct")
    void absoluteOpinionsAreInterned() {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            Object t1 = oracle.toHistorical(UValue.sBoolean(1.0, 0.0, 0.0, 1.0));
            Object t2 = oracle.toHistorical(UValue.sBoolean(1.0, 0.0, 0.0, 1.0));
            assertSame(t1, t2, "(1,0,0,1) must return the shared interned TRUE");

            Object f1 = oracle.toHistorical(UValue.sBoolean(0.0, 1.0, 0.0, 1.0));
            assertSame(f1, oracle.toHistorical(UValue.sBoolean(0.0, 1.0, 0.0, 1.0)),
                    "(0,1,0,1) must return the shared interned FALSE");
            assertNotSame(t1, f1);

            // Same masses, different base rate: NOT the interned instance. This is the pair that
            // catches an implementation comparing opinions by identity rather than by value.
            Object twin = oracle.toHistorical(UValue.sBoolean(1.0, 0.0, 0.0, 0.5));
            assertNotSame(t1, twin, "(1,0,0,0.5) must not be the interned TRUE");
        }
    }

    @Test
    @DisplayName("SBooleanValue operations really execute and return distinct opinions")
    void operationsProduceMeasurements() throws Throwable {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            UOp and = UOp.binary("SBooleanValue", "and");
            assertTrue(oracle.supports(and), "SBooleanValue.and must be supported since S3");

            List<UValue> corpus = List.of(
                    UValue.sBoolean(0.3, 0.2, 0.5, 0.5),
                    UValue.sBoolean(1.0, 0.0, 0.0, 1.0),
                    UValue.sBoolean(0.0, 0.0, 1.0, 0.5));

            java.util.Set<String> distinct = new java.util.LinkedHashSet<>();
            for (UValue a : corpus) {
                for (UValue b : corpus) {
                    UValue out = oracle.invoke(and, List.of(a, b));
                    assertNotNull(out, "SBooleanValue.and returned no value");
                    assertTrue(out.carriesAnObservation(),
                            "the result must be a real observation, not an absence: " + out.canonical());
                    distinct.add(out.canonical());
                }
            }
            System.out.println("=== SBooleanValue.and over 3x3 opinions ===");
            distinct.forEach(d -> System.out.println("  " + d));

            // Discriminating power (D-15): an operation whose codomain is a single point gives
            // agreement away for free. 9 inputs collapsing to 1 output would be no evidence at all.
            assertTrue(distinct.size() >= 5,
                    "SBooleanValue.and must discriminate across the corpus, got only "
                            + distinct.size() + " distinct results: " + distinct);
        }
    }

    @Test
    @DisplayName("an opinion outside the 0.001 sum tolerance is rejected by the historical side")
    void invalidOpinionsAreRejectedByTheHistoricalSide() {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            // inside the band: constructs
            assertNotNull(oracle.toHistorical(UValue.sBoolean(0.3005, 0.2, 0.5, 0.5)));
            // outside it: the historical constructor throws, and the harness must surface that as a
            // marshalling failure rather than let it be scored against the code under test
            org.junit.jupiter.api.Assertions.assertThrows(HarnessMarshallingException.class,
                    () -> oracle.toHistorical(UValue.sBoolean(0.3015, 0.2, 0.5, 0.5)));
            org.junit.jupiter.api.Assertions.assertThrows(HarnessMarshallingException.class,
                    () -> oracle.toHistorical(UValue.sBoolean(-0.1, 0.6, 0.5, 0.5)));
        }
    }
}
