package org.tzi.use.smt.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;
import org.tzi.use.smt.encode.FragmentChecker;
import org.tzi.use.smt.encode.InvariantClassification;
import org.tzi.use.smt.encode.QueryCompiler;
import org.tzi.use.smt.solver.Smt;

/**
 * REPRODUCIBILITY regression: the emitted conjunct order for the active invariants must be a
 * function of the configuration alone, never of the JVM launch.
 *
 * <p>{@code AnalysisConfiguration} used to store {@code Set.copyOf(activeInvariants)}. That
 * returns an {@code ImmutableCollections.SetN} whose iteration order derives from a SALT seeded
 * from {@code System.nanoTime()} at class-initialization time, so {@code QueryCompiler.allAreTrue}
 * and {@code others} -- both of which simply iterate this set -- emitted their conjuncts in a
 * different order on every launch for one identical model+configuration.
 *
 * <p>A single-JVM test CANNOT observe that: the salt is drawn once per JVM, so every construction
 * inside one test run shares it and the emitted text looks perfectly stable even with the bug
 * present. So this test does not rely on repetition alone. It asserts the ORDER CONTRACT directly
 * -- the stored set iterates in qualified-name order, whatever order the caller supplied -- which
 * IS observable in one JVM, because the salted order of a five-element set is essentially never
 * the sorted one, and it pins the property (sorted) rather than the symptom (looks stable here).
 */
public class ActiveInvariantOrderTest {

  private static final List<String> NAMES =
      List.of("Zeta::last", "Alpha::first", "Mid::beta", "Alpha::second", "Mid::alpha");

  private static final List<String> SORTED =
      List.of("Alpha::first", "Alpha::second", "Mid::alpha", "Mid::beta", "Zeta::last");

  /**
   * The contract, asserted directly: whatever order the caller's set iterates in, the stored set
   * iterates in qualified-name order. This is what makes the emission reproducible across JVMs.
   */
  @Test
  public void theStoredSetIteratesInQualifiedNameOrder() {
    assertEquals(SORTED, new ArrayList<>(configWith(new LinkedHashSet<>(NAMES)).activeInvariants()));
  }

  /**
   * The caller's own order is IRRELEVANT -- every permutation of the same names stores the same
   * sequence. This is the property that a {@code Set.copyOf} store cannot offer, and the reason
   * preserving insertion order would not have been enough: {@code ConfigurationReader} fills its
   * {@code LinkedHashSet} by iterating {@code ConfigurationVocabulary.invariantNames()}, itself a
   * {@code Set.copyOf}, so the salt already leaks in upstream of this constructor.
   */
  @Test
  public void everyCallerOrderStoresTheSameSequence() {
    List<String> shuffled = new ArrayList<>(NAMES);
    Collections.reverse(shuffled);

    assertEquals(SORTED, new ArrayList<>(configWith(new LinkedHashSet<>(NAMES)).activeInvariants()));
    assertEquals(SORTED, new ArrayList<>(configWith(new LinkedHashSet<>(shuffled)).activeInvariants()));
    assertEquals(SORTED, new ArrayList<>(configWith(new TreeSet<>(NAMES)).activeInvariants()));
    // A Set.of literal -- the shape most call sites and tests use -- has salted order of its own.
    assertEquals(
        SORTED,
        new ArrayList<>(configWith(Set.of(NAMES.toArray(new String[0]))).activeInvariants()));
  }

  /**
   * The bug's own witness, made visible inside one JVM: a {@code Set.copyOf} of these five names
   * does NOT iterate in sorted order (its order is salted), so "sorted" is a genuinely different
   * sequence from what the old store produced, not a coincidence of this input.
   */
  @Test
  public void theOldSetCopyOfStoreDoesNotProduceTheSortedOrder() {
    assertNotEquals(
        "if this ever matches, the salt happened to sort the input and this test proves nothing",
        SORTED,
        new ArrayList<>(Set.copyOf(NAMES)));
  }

  /** The stored set is unmodifiable, exactly as the {@code Set.copyOf} it replaces was. */
  @Test
  public void theStoredSetIsUnmodifiable() {
    Set<String> stored = configWith(new LinkedHashSet<>(NAMES)).activeInvariants();

    assertThrows(UnsupportedOperationException.class, () -> stored.add("Other::extra"));
  }

  /**
   * End to end through the real emitter: the SMT-LIB text {@code QueryCompiler} produces for
   * {@code satisfy} is byte-identical across repeated construction AND spells the conjuncts in
   * qualified-name order.
   */
  @Test
  public void theEmittedConjunctOrderIsStableAndSorted() {
    String first = emittedSatisfyText(new LinkedHashSet<>(NAMES));

    List<String> shuffled = new ArrayList<>(NAMES);
    for (int i = 0; i < 8; i++) {
      Collections.rotate(shuffled, 1);
      assertEquals(
          "the emitted text must not depend on the caller's iteration order",
          first,
          emittedSatisfyText(new LinkedHashSet<>(shuffled)));
    }

    List<Integer> positions = new ArrayList<>();
    for (String name : SORTED) {
      int at = first.indexOf("|def-" + name + "|");
      assertTrue("every active invariant appears in the emitted term: " + name, at >= 0);
      positions.add(at);
    }
    List<Integer> ascending = new ArrayList<>(positions);
    Collections.sort(ascending);
    assertEquals("the conjuncts are emitted in qualified-name order", ascending, positions);
  }

  /**
   * {@code others} (the {@code counterexample(j)} expansion) iterates the same set, so it inherits
   * the same stability -- checked separately because it walks a COPY of the set with the target
   * removed.
   */
  @Test
  public void theOthersExpansionIsStableToo() {
    String first = emittedText(new QueryExpr.Counterexample("Mid::beta"), new LinkedHashSet<>(NAMES));

    List<String> shuffled = new ArrayList<>(NAMES);
    for (int i = 0; i < 8; i++) {
      Collections.rotate(shuffled, 1);
      assertEquals(first, emittedText(new QueryExpr.Counterexample("Mid::beta"), new LinkedHashSet<>(shuffled)));
    }
  }

  private static String emittedSatisfyText(Set<String> activeInvariants) {
    return emittedText(QueryExpr.SATISFY, activeInvariants);
  }

  private static String emittedText(QueryExpr query, Set<String> activeInvariants) {
    AnalysisConfiguration config = configWith(activeInvariants, query);
    return QueryCompiler.compile(
            config.query(), config.activeInvariants(), classifications(config.activeInvariants()))
        .constraint()
        .toSmtLib();
  }

  /** Hand-built stand-ins for the reified {@code def}/{@code val} symbols of each invariant. */
  private static Map<FragmentChecker.ClassificationKey, InvariantClassification> classifications(
      Set<String> activeInvariants) {
    Map<FragmentChecker.ClassificationKey, InvariantClassification> reified = new LinkedHashMap<>();
    for (String name : activeInvariants) {
      for (TranslationMode mode : Arrays.asList(TranslationMode.values())) {
        String suffix = name + "-" + mode;
        reified.put(
            new FragmentChecker.ClassificationKey(name, mode),
            new InvariantClassification(
                name,
                mode,
                "|def-" + suffix + "|",
                "|val-" + suffix + "|",
                Smt.sym("|def-" + name + "|"),
                Smt.sym("|val-" + name + "|")));
      }
    }
    return reified;
  }

  private static AnalysisConfiguration configWith(Set<String> activeInvariants) {
    return configWith(activeInvariants, QueryExpr.SATISFY);
  }

  private static AnalysisConfiguration configWith(Set<String> activeInvariants, QueryExpr query) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Alpha", 1, 1)),
        List.of(),
        List.of(),
        activeInvariants,
        query,
        Duration.ofSeconds(10),
        1);
  }
}
