package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.*;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * {@code self.<one-hop, collection-valued association end>->size()} -- the single population source
 * named by THESIS_SMT_MODEL_FINDER_PLAN.md 7.1's Tier 3 list and confirmed as the sole refusal
 * blocking {@code CollectionSemantics}/{@code CollectionSemantics-UNSAT} (real invariant {@code
 * Playlist::hasThreeSongs}, {@code self.songs->size() = 3}).
 *
 * <p>Deliberately narrower than {@code isUnique}'s two supported population sources: only the
 * {@code self.<role>} association-end shape is supported for {@code size()}. {@code
 * X.allInstances()->size()} is a different shape the real corpus does not evidence, so it is
 * refused rather than silently generalized -- see {@link #allInstancesSourceFailsClosed}.
 */
public class SizeTranslationTest {

  // ---------------------------------------------------------------------
  // The real corpus shape: CollectionSemantics::hasThreeSongs, self.songs->size() = 3
  // ---------------------------------------------------------------------

  @Test
  public void hasThreeSongsOnTheRealAstIsSatWhenAllThreeSongsAreLinked() throws Exception {
    MModel model = compileCollectionSemantics();
    MClassInvariant inv = findInvariant(model, "hasThreeSongs");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots playlists =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Playlist", 1, 1))).get("Playlist");
    ObjectSlots songs =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Song", 3, 3))).get("Song");
    AssociationLinks contains =
        AssociationLinkEncoder.encode(
            script,
            "Contains",
            playlists,
            new Multiplicity(0, -1),
            songs,
            new Multiplicity(0, -1),
            new AssociationScope("Contains", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("self", new VariableBinding("Playlist", 0)),
            Map.of(),
            Map.of(),
            Map.of("Playlist", playlists, "Song", songs),
            Map.of("Contains", contains));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Playlist_0_exists"));
    script.assertThat(Smt.sym("Song_0_exists"));
    script.assertThat(Smt.sym("Song_1_exists"));
    script.assertThat(Smt.sym("Song_2_exists"));
    script.assertThat(Smt.sym(contains.linkNames()[0][0]));
    script.assertThat(Smt.sym(contains.linkNames()[0][1]));
    script.assertThat(Smt.sym(contains.linkNames()[0][2]));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  /**
   * Adversarial: FOUR candidate Songs exist, but only THREE are actually linked -- and the unlinked
   * one (slot 2) is neither the first nor the last, so the correct count genuinely depends on which
   * specific subset is linked, not on "all slots" or "a contiguous prefix/suffix". An
   * implementation that counted EXISTENCE instead of LINKAGE, or that assumed the population is
   * always fully linked, would wrongly compute 4 here instead of 3.
   */
  @Test
  public void hasThreeSongsOnTheRealAstIsSatWhenExactlyThreeOfFourCandidateSongsAreLinked()
      throws Exception {
    MModel model = compileCollectionSemantics();
    MClassInvariant inv = findInvariant(model, "hasThreeSongs");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots playlists =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Playlist", 1, 1))).get("Playlist");
    ObjectSlots songs =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Song", 4, 4))).get("Song");
    AssociationLinks contains =
        AssociationLinkEncoder.encode(
            script,
            "Contains",
            playlists,
            new Multiplicity(0, -1),
            songs,
            new Multiplicity(0, -1),
            new AssociationScope("Contains", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("self", new VariableBinding("Playlist", 0)),
            Map.of(),
            Map.of(),
            Map.of("Playlist", playlists, "Song", songs),
            Map.of("Contains", contains));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Playlist_0_exists"));
    script.assertThat(Smt.sym("Song_0_exists"));
    script.assertThat(Smt.sym("Song_1_exists"));
    script.assertThat(Smt.sym("Song_2_exists"));
    script.assertThat(Smt.sym("Song_3_exists"));
    // Slots 0, 1, 3 are genuinely linked; slot 2 EXISTS but is a decoy -- not part of this
    // Playlist's songs (e.g. it belongs to some other Playlist in a larger scope).
    script.assertThat(Smt.sym(contains.linkNames()[0][0]));
    script.assertThat(Smt.sym(contains.linkNames()[0][1]));
    script.assertThat(Smt.not(Smt.sym(contains.linkNames()[0][2])));
    script.assertThat(Smt.sym(contains.linkNames()[0][3]));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  /**
   * The other direction of the same adversarial shape: only TWO of THREE candidates are linked (a
   * non-adjacent decoy at slot 1), so the count is genuinely 2, and {@code size() = 3} must be
   * UNSAT.
   */
  @Test
  public void hasThreeSongsOnTheRealAstIsUnsatWhenOnlyTwoOfThreeCandidateSongsAreLinked()
      throws Exception {
    MModel model = compileCollectionSemantics();
    MClassInvariant inv = findInvariant(model, "hasThreeSongs");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots playlists =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Playlist", 1, 1))).get("Playlist");
    ObjectSlots songs =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Song", 3, 3))).get("Song");
    AssociationLinks contains =
        AssociationLinkEncoder.encode(
            script,
            "Contains",
            playlists,
            new Multiplicity(0, -1),
            songs,
            new Multiplicity(0, -1),
            new AssociationScope("Contains", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("self", new VariableBinding("Playlist", 0)),
            Map.of(),
            Map.of(),
            Map.of("Playlist", playlists, "Song", songs),
            Map.of("Contains", contains));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Playlist_0_exists"));
    script.assertThat(Smt.sym("Song_0_exists"));
    script.assertThat(Smt.sym("Song_1_exists"));
    script.assertThat(Smt.sym("Song_2_exists"));
    script.assertThat(Smt.sym(contains.linkNames()[0][0]));
    // Slot 1 exists but is NOT linked -- a decoy in the middle, not a trailing gap.
    script.assertThat(Smt.not(Smt.sym(contains.linkNames()[0][1])));
    script.assertThat(Smt.sym(contains.linkNames()[0][2]));
    script.assertThat(translated);

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  // ---------------------------------------------------------------------
  // A second comparator, end to end: self.<role>->size() < N (SizeScope::csSizeLessThanFour)
  // ---------------------------------------------------------------------

  /**
   * Proves {@code size()} composes with {@code orderedComparison}, not just {@code =} -- and
   * repeats the non-contiguous-subset adversarial shape (slots 0, 2, 4 linked; 1 and 3 decoys) so
   * this is not merely "the same fixture with a different operator name".
   */
  @Test
  public void csSizeLessThanFourOnTheRealAstIsSatWithANonContiguousSubsetOfThreeLinkedSlots()
      throws Exception {
    MModel model = compileSizeScope();
    MClassInvariant inv = findInvariant(model, "csSizeLessThanFour");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots bs = ObjectSlotEncoder.encode(script, List.of(new ClassScope("B", 1, 1))).get("B");
    ObjectSlots cs = ObjectSlotEncoder.encode(script, List.of(new ClassScope("C", 5, 5))).get("C");
    AssociationLinks bc =
        AssociationLinkEncoder.encode(
            script,
            "BC",
            bs,
            new Multiplicity(0, -1),
            cs,
            new Multiplicity(0, -1),
            new AssociationScope("BC", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("b", new VariableBinding("B", 0)),
            Map.of(),
            Map.of(),
            Map.of("B", bs, "C", cs),
            Map.of("BC", bc));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("B_0_exists"));
    for (int i = 0; i < 5; i++) {
      script.assertThat(Smt.sym("C_" + i + "_exists"));
    }
    script.assertThat(Smt.sym(bc.linkNames()[0][0]));
    script.assertThat(Smt.not(Smt.sym(bc.linkNames()[0][1])));
    script.assertThat(Smt.sym(bc.linkNames()[0][2]));
    script.assertThat(Smt.not(Smt.sym(bc.linkNames()[0][3])));
    script.assertThat(Smt.sym(bc.linkNames()[0][4]));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  /** Fencepost check: all four candidates linked (count = 4) must fail {@code size() < 4}. */
  @Test
  public void csSizeLessThanFourOnTheRealAstIsUnsatWhenAllFourAreLinked() throws Exception {
    MModel model = compileSizeScope();
    MClassInvariant inv = findInvariant(model, "csSizeLessThanFour");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots bs = ObjectSlotEncoder.encode(script, List.of(new ClassScope("B", 1, 1))).get("B");
    ObjectSlots cs = ObjectSlotEncoder.encode(script, List.of(new ClassScope("C", 4, 4))).get("C");
    AssociationLinks bc =
        AssociationLinkEncoder.encode(
            script,
            "BC",
            bs,
            new Multiplicity(0, -1),
            cs,
            new Multiplicity(0, -1),
            new AssociationScope("BC", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("b", new VariableBinding("B", 0)),
            Map.of(),
            Map.of(),
            Map.of("B", bs, "C", cs),
            Map.of("BC", bc));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("B_0_exists"));
    for (int i = 0; i < 4; i++) {
      script.assertThat(Smt.sym("C_" + i + "_exists"));
      script.assertThat(Smt.sym(bc.linkNames()[0][i]));
    }
    script.assertThat(translated);

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  // ---------------------------------------------------------------------
  // Scope boundary: fail closed on anything other than self.<role>->size()
  // ---------------------------------------------------------------------

  @Test
  public void chainedTwoHopNavigationFailsClosed() throws Exception {
    MModel model = compileSizeScope();
    MClassInvariant inv = findInvariant(model, "chainedTooDeep");

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    inv.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));

    assertEquals(FragmentBoundary.TIER_3, thrown.boundary());
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("more than one hop"));
  }

  @Test
  public void selectFilteredSourceFailsClosed() throws Exception {
    MModel model = compileSizeScope();
    MClassInvariant inv = findInvariant(model, "filteredSource");

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    inv.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));

    assertEquals(FragmentBoundary.TIER_3, thrown.boundary());
    assertTrue(
        thrown.getMessage(),
        thrown.getMessage().contains("single-hop, collection-valued association navigation"));
  }

  /**
   * {@code X.allInstances()->size()} is deliberately OUT of scope, even though {@code isUnique}
   * supports {@code allInstances} as a population source: the real corpus only evidences {@code
   * self.<role>->size()}, and the task's own scope line names exactly that shape.
   */
  @Test
  public void allInstancesSourceFailsClosed() throws Exception {
    MModel model = compileSizeScope();
    MClassInvariant inv = findInvariant(model, "allInstancesSource");

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    inv.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));

    assertEquals(FragmentBoundary.TIER_3, thrown.boundary());
    assertTrue(
        thrown.getMessage(),
        thrown.getMessage().contains("single-hop, collection-valued association navigation"));
  }

  /**
   * {@code String->size()} already has its own dedicated meaning (character count) and must not be
   * confused with collection cardinality -- it never reaches an {@code ExpNavigation} receiver at
   * all, so it fails closed the same way any other non-navigation receiver does.
   */
  @Test
  public void stringSizeIsNotConfusedWithCollectionSizeAndFailsClosed() throws Exception {
    MModel model = compileSizeScope();
    MClassInvariant inv = findInvariant(model, "stringSizeNotConfused");

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    inv.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));

    assertEquals(FragmentBoundary.TIER_3, thrown.boundary());
    assertTrue(
        thrown.getMessage(),
        thrown.getMessage().contains("single-hop, collection-valued association navigation"));
  }

  /**
   * The exact shape behind {@code AssociationClass::AtMostOneEmployer} ({@code p.employer->size()
   * <= 1}): a single-valued 0..1 navigation coerced into a set via OCL's own "uniform syntax" rule
   * ({@code ExpObjAsSet}), NOT a genuinely collection-valued navigation. Must fail closed rather
   * than being silently accepted as if it were {@code self.<role>->size()}.
   */
  @Test
  public void singleValuedNavigationCoercedToASetIsNotConfusedAndFailsClosed() throws Exception {
    MModel model = compileSizeScope();
    MClassInvariant inv = findInvariant(model, "singleValuedNavAsSetNotConfused");

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    inv.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));

    assertEquals(FragmentBoundary.TIER_3, thrown.boundary());
    assertTrue(
        thrown.getMessage(),
        thrown.getMessage().contains("single-hop, collection-valued association navigation"));
  }

  private static MModel compileCollectionSemantics() throws Exception {
    Path file = Path.of("../benchmark/examples/CollectionSemantics/CollectionSemantics.use");
    if (!Files.isRegularFile(file)) {
      file =
          Path.of(
              "msc-modelvalidators/benchmark/examples/CollectionSemantics/CollectionSemantics.use");
    }
    String source = Files.readString(file);
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "CollectionSemantics", err, factory);
    err.flush();
    return model;
  }

  private static MModel compileSizeScope() {
    String source =
        """
        model SizeScope
        class A
        attributes
          name : String
        end
        class B
        attributes
          dummy : Integer
        end
        class C
        attributes
          val : Integer
        end
        class D
        attributes
          dummy2 : Integer
        end
        association AB between
          A[1] role owner
          B[1] role linkedB
        end
        association BC between
          B[1] role ownerB
          C[*] role cs
        end
        association BD between
          D[0..1] role linkedD
          B[*] role bs
        end
        constraints
        context a: A inv chainedTooDeep:
          a.linkedB.cs->size() = 1
        context b: B inv filteredSource:
          b.cs->select(val > 0)->size() = 1
        context b: B inv allInstancesSource:
          C.allInstances()->size() = 1
        context a: A inv stringSizeNotConfused:
          a.name.size() = 3
        context b: B inv singleValuedNavAsSetNotConfused:
          b.linkedD->size() <= 1
        context b: B inv csSizeLessThanFour:
          b.cs->size() < 4
        """;
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "SizeScope", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("SizeScope fixture model did not compile:\n" + source);
    }
    return model;
  }

  private static MClassInvariant findInvariant(MModel model, String name) {
    for (MClassInvariant inv : model.classInvariants()) {
      if (inv.name().equals(name)) {
        return inv;
      }
    }
    throw new IllegalStateException("invariant not found: " + name);
  }

  private static SolverResult solve(SmtScript script) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
  }
}
