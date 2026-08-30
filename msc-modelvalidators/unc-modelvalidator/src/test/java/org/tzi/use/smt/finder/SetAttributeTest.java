package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for SET-TYPED ATTRIBUTES with size bounds
 * ({@code attr.collection-typed-with-size-bounds}): {@code tags : Set(Integer)} with a
 * configured candidate POOL ({@code X_tags = Set{...}} — the incumbent's allowed elements) and
 * configured SIZE bounds ({@code X_tags_minSize}/{@code X_tags_maxSize}, the incumbent's
 * {@code attributeColSizeMin/Max} keys, defaulting 0/unbounded). The encoding is a membership
 * Bool per (slot, pool element); the size bounds constrain the membership cardinality; the
 * consumers (includes/excludes, size, isEmpty) read the membership symbols.
 *
 * <p>Edges: singleton pool, exactly-at-bound size, empty-set bounds (both polarities), and a
 * pool conflict (including an element outside the configured pool is unsatisfiable, not merely
 * a false predicate that some other value could satisfy).
 */
public class SetAttributeTest {

  private static final String MODEL =
      """
      model SetAttr
      class X
      attributes
        tags : Set(Integer)
      end
      constraints
      context x : X inv hasTag:
        x.tags->includes(1)
      context x : X inv hasSeven:
        x.tags->includes(7)
      context x : X inv sizeIsBound:
        x.tags->size() = 1
      context x : X inv tagsEmpty:
        x.tags->isEmpty()
      """;

  /** General case: pool {1,2,3}, size bounds [1,2], includes(1) — satisfiable. */
  @Test
  public void includesFromPoolWithSizeBounds() throws Exception {
    ModelFinderResult match = find(Set.of("X::hasTag"), "1,2,3", "1", "2");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::hasTag").holds());
  }

  /**
   * POOL CONFLICT: includes(1) with pool {2,3} — 1 can never be a member because it is not in
   * the configured pool. UNSATISFIABLE, not merely false-and-avoidable.
   */
  @Test
  public void elementOutsidePoolRefutes() throws Exception {
    ModelFinderResult miss = find(Set.of("X::hasTag"), "2,3", "1", "2");
    assertFalse("1 is outside the configured pool: the membership is impossible",
        miss.satisfiable());
  }

  /** Exactly-at-bound: size bounds [1,1] with size() = 1 — satisfiable. */
  @Test
  public void exactlyAtBoundSatisfies() throws Exception {
    ModelFinderResult match = find(Set.of("X::sizeIsBound"), "1,2,3", "1", "1");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::sizeIsBound").holds());
  }

  /** Boundary: bounds [0,0] force the empty set — isEmpty holds. */
  @Test
  public void emptySetBoundsMakeIsEmptyHold() throws Exception {
    ModelFinderResult empty = find(Set.of("X::tagsEmpty"), "1,2,3", "0", "0");
    assertTrue("bounds [0,0] force the empty set; isEmpty holds", empty.satisfiable());
    assertTrue(verdictFor(empty, "X::tagsEmpty").holds());
  }

  /** Boundary, other polarity: bounds [0,0] forbid every member — includes(1) refutes. */
  @Test
  public void emptySetBoundsRefuteMembership() throws Exception {
    ModelFinderResult member = find(Set.of("X::hasTag"), "1,2,3", "0", "0");
    assertFalse("bounds [0,0] forbid every member: includes(1) is impossible",
        member.satisfiable());
  }

  /** Singleton pool: the set can only ever be {7}, so includes(7) holds. */
  @Test
  public void singletonPoolActsAsItsElement() throws Exception {
    ModelFinderResult match = find(Set.of("X::hasSeven"), "7", "1", "1");
    assertTrue("pool {7}: the set is exactly {7}, so includes(7) holds",
        match.satisfiable());
    assertTrue(verdictFor(match, "X::hasSeven").holds());
  }

  /** Singleton pool, other polarity: includes(1) is impossible (1 outside {7}). */
  @Test
  public void singletonPoolRefutesForeignElement() throws Exception {
    ModelFinderResult miss = find(Set.of("X::hasTag"), "7", "1", "1");
    assertFalse("1 is outside the singleton pool {7}", miss.satisfiable());
  }

  private static ModelFinderResult find(
      Set<String> invariants, String pool, String minSize, String maxSize) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "tags", null,
                    List.of(pool.split(",")),
                    new BigDecimal(minSize), new BigDecimal(maxSize))),
            invariants,
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "SetAttr", err, factory);
    err.flush();
    return model;
  }
}
