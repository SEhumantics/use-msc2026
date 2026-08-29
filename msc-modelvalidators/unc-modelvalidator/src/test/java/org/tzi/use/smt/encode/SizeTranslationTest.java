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
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.*;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * {@code self.<role>->size()} (including a chained, multi-hop navigation -- see {@code
 * ExpressionTranslator#navigationHop}) -- the population source named by
 * THESIS_SMT_MODEL_FINDER_PLAN.md 7.1's Tier 3 list and confirmed as the sole refusal blocking
 * {@code CollectionSemantics}/{@code CollectionSemantics-UNSAT} (real invariant {@code
 * Playlist::hasThreeSongs}, {@code self.songs->size() = 3}).
 *
 * <p>{@code X.allInstances()->size()} is a different shape the real corpus does not evidence, so
 * it is refused rather than silently generalized -- see {@link #allInstancesSourceFailsClosed}.
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

  /**
   * The empty-collection edge case: EVERY candidate link forced false, so the true count is
   * genuinely 0. Directly proves {@code sizeTerm} does not accidentally satisfy {@code size() = 3}
   * for an empty population -- a bug that let it "cheat" to a nonzero value would flip this UNSAT
   * to SAT, whereas the {@code size() < 4} shape tested elsewhere (e.g. {@code
   * csSizeLessThanFourOnTheRealAstIsSatWith...}) cannot distinguish size=0 from any other value
   * below 4, so it alone would not have caught that class of bug.
   */
  @Test
  public void hasThreeSongsOnTheRealAstIsUnsatWhenNoCandidateSongsAreLinkedAtAll()
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
    script.assertThat(Smt.not(Smt.sym(contains.linkNames()[0][0])));
    script.assertThat(Smt.not(Smt.sym(contains.linkNames()[0][1])));
    script.assertThat(Smt.not(Smt.sym(contains.linkNames()[0][2])));
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

  /**
   * {@code a.linkedB.cs->size() = 1}: {@code a.linkedB} is single-valued (AB's {@code A[1] role
   * owner -- B[1] role linkedB}), {@code .cs} is collection-valued (BC's {@code B[1] role ownerB
   * -- C[*] role cs}) -- the same chained-navigation shape as {@code Demo.use}'s real {@code
   * self.department.employee}. Previously refused outright by {@code populationOf}'s single-hop-
   * only restriction; now resolved via {@link ExpressionTranslator#navigationHop}'s general
   * recursive form, reached through {@link ExpressionTranslator#collectionSize}'s own unconditional
   * delegation to {@code populationOf}. Full pipeline through real Z3, discriminating a genuine
   * SAT/UNSAT pair on how many C's are actually linked.
   */
  @Test
  public void chainedTwoHopNavigationDiscriminatesOnTheRealLinkedCount() throws Exception {
    assertEquals(SolverOutcome.SAT, solveChainedSize(true));
    assertEquals(SolverOutcome.UNSAT, solveChainedSize(false));
  }

  private static SolverOutcome solveChainedSize(boolean onlyOneCLinked) throws Exception {
    MModel model = compileSizeScope();
    MClassInvariant inv = findInvariant(model, "chainedTooDeep");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots as = ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    ObjectSlots bs = ObjectSlotEncoder.encode(script, List.of(new ClassScope("B", 1, 1))).get("B");
    ObjectSlots cs = ObjectSlotEncoder.encode(script, List.of(new ClassScope("C", 2, 2))).get("C");
    AssociationLinks ab =
        AssociationLinkEncoder.encode(
            script, "AB", as, new Multiplicity(1, 1), bs, new Multiplicity(1, 1),
            new AssociationScope("AB", 0, -1));
    AssociationLinks bc =
        AssociationLinkEncoder.encode(
            script, "BC", bs, new Multiplicity(0, -1), cs, new Multiplicity(0, -1),
            new AssociationScope("BC", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("a", new VariableBinding("A", 0)),
            Map.of(),
            Map.of(),
            Map.of("A", as, "B", bs, "C", cs),
            Map.of("AB", ab, "BC", bc));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("A_0_exists"));
    script.assertThat(Smt.sym("B_0_exists"));
    script.assertThat(Smt.sym("C_0_exists"));
    script.assertThat(Smt.sym("C_1_exists"));
    script.assertThat(Smt.sym(ab.linkNames()[0][0]));
    script.assertThat(Smt.sym(bc.linkNames()[0][0]));
    if (onlyOneCLinked) {
      script.assertThat(Smt.not(Smt.sym(bc.linkNames()[0][1])));
    } else {
      script.assertThat(Smt.sym(bc.linkNames()[0][1]));
    }
    script.assertThat(translated);

    return solve(script).outcome();
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
        thrown.getMessage().contains("chained, collection-valued association navigation"));
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
        thrown.getMessage().contains("chained, collection-valued association navigation"));
  }

  /**
   * {@code String->size()} already has its own dedicated meaning (character count) and must not be
   * confused with collection cardinality -- it never reaches an {@code ExpNavigation} receiver at
   * all, so it fails closed the same way any other non-navigation receiver does.
   */
  /**
   * 2026-08-29: {@code String.size()} over a configured-candidate string is now SUPPORTED -- the
   * configured spellings are the content space, so the size enumerates their lengths (this
   * superseded the old TIER_3 refusal pin for this shape; the dispatch still never confuses a
   * String receiver with the collection path, which is what the original test guarded).
   */
  @Test
  public void stringSizeEnumeratesTheConfiguredSpellings() throws Exception {
    MModel model = compileSizeScope();
    MClassInvariant inv = findInvariant(model, "stringSizeNotConfused");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    AttributeDomain nameDomain =
        new AttributeDomain("A", "name", null, List.of("abc", "de"), null, null);
    AttributeValues name =
        AttributeEncoder.encode(script, objects, "name", AttributeType.STRING, nameDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of("a", new VariableBinding("A", 0)),
            Map.of("A.name", name),
            Map.of("A.name", nameDomain),
            Map.of("A", objects),
            Map.of());

    TranslatedExpression translated =
        ExpressionTranslator.translate(inv.bodyExpression(), context, TranslationMode.UNCERTAIN);

    assertEquals("(= (ite (= A_0_name 0) 3 2) 3)", translated.value().toSmtLib());
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
        thrown.getMessage().contains("chained, collection-valued association navigation"));
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
