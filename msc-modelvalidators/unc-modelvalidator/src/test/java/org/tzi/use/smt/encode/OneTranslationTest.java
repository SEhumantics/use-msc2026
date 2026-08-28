package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Parser-backed regression coverage for {@code X.allInstances()->one(body)}.
 *
 * <p>A real, non-obvious configuration-semantics trap found and worked around while writing this
 * suite, recorded so it doesn't cost the next person the same debugging session: {@code
 * ClassName_attr = Set{v1,...,vN}} against N NAMED objects is NOT positional per-object pinning --
 * it is a SHARED candidate domain each object independently draws from (with repetition allowed).
 * A first attempt at these tests assumed positional pinning, silently let the solver pick a
 * DIFFERENT witness than intended, and produced a false "it's broken" signal that took a direct
 * SMT-LIB probe (confirming the emitted formula is correct) plus a direct witness inspection
 * (confirming the config layer, not this feature, was the source of the surprise) to resolve.
 * These tests sidestep it entirely by asserting each object's score directly via {@code
 * script.assertThat}, the same way {@code LetTranslationTest}'s CompanyER round trip does.
 */
public class OneTranslationTest {

  @Test
  public void exactlyOneMatchIsSatisfiable() throws Exception {
    assertEquals(SolverOutcome.SAT, solveWithScores(20, 5, 3));
  }

  @Test
  public void twoMatchesIsUnsatisfiableWhenEnforced() throws Exception {
    assertEquals(SolverOutcome.UNSAT, solveWithScores(20, 15, 3));
  }

  @Test
  public void zeroMatchesIsUnsatisfiableWhenEnforced() throws Exception {
    assertEquals(SolverOutcome.UNSAT, solveWithScores(5, 5, 3));
  }

  @Test
  public void moreThanOneLoopVariableIsRefused() throws Exception {
    MModel model = compileFixture("Item.allInstances()->one(x, y | x.score > y.score)");
    MClassInvariant invariant = findInvariant(model, "probe");
    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    invariant.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("more than one loop variable"));
  }

  /**
   * {@code X.allInstances()->select(pred)->one(body)} -- {@code one()} previously only recognized a
   * bare {@code X.allInstances()} range, hand-rolling its own {@link PolymorphicRange#slotsOf} loop
   * instead of going through {@link ExpressionTranslator#populationOf}; generalized to reuse it, the
   * same {@code select()}/navigation-range widening {@code forAll}/{@code exists}/{@code isUnique}/
   * {@code size()} already received. Both configs mark the {@code one()}-based invariant ACTIVE,
   * paired with an active, deterministic score-forcing setup, so a wrong count (e.g. the select()
   * filter silently not applied, letting an out-of-category Item count too) manufactures a genuine
   * SAT/UNSAT contradiction rather than a value merely observed after the fact.
   */
  @Test
  public void oneOverASelectFilteredAllInstancesCountsOnlyTheFilteredCandidates() throws Exception {
    MModel model =
        compileFixture("Item.allInstances()->select(category = 1)->one(x | x.score > 10)");

    // Category-1 members: scores 20, 5 -> exactly one > 10. Category-2 member (score 20, ignored
    // by the filter) must NOT count, or this would wrongly become two matches -> UNSAT.
    assertEquals(SolverOutcome.SAT, solveWithCategorizedScores(20, 1, 5, 1, 20, 2));

    // Category-1 members: scores 20, 20 -> two matches, genuinely UNSAT under one().
    assertEquals(SolverOutcome.UNSAT, solveWithCategorizedScores(20, 1, 20, 1, 5, 2));
  }

  /**
   * {@code self.<role>->one(body)} -- the single-hop, collection-valued association-navigation
   * range shape, the other range {@link ExpressionTranslator#populationOf} supports alongside
   * {@code select()}.
   */
  @Test
  public void oneOverACollectionValuedNavigationCountsOnlyTheLinkedCandidates() throws Exception {
    MModel model = compileNavigationFixture();

    // Both linked Items score above 10 -> two matches -> UNSAT under one().
    assertEquals(SolverOutcome.UNSAT, solveWithLinkedScores(20, 20, true, true));
    // Only one linked Item scores above 10 -> exactly one match -> SAT.
    assertEquals(SolverOutcome.SAT, solveWithLinkedScores(20, 5, true, true));
    // Both Items score above 10, but only one is actually LINKED -- the unlinked one must not
    // count, or this would wrongly become two matches -> genuinely SAT, not accidentally UNSAT.
    assertEquals(SolverOutcome.SAT, solveWithLinkedScores(20, 20, true, false));
  }

  @Test
  public void fullRoundTripSingleCandidateConfirmedByUseEvaluator() throws Exception {
    MModel model = compileFixture("Item.allInstances()->one(x | x.score > 10)");
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Item_min = 1
            Item_max = 1
            Item_score = Set{20}
            Item_probe = active
            """);
    org.tzi.use.smt.finder.ModelFinderResult result =
        org.tzi.use.smt.finder.SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "one candidate scoring above 10 out of one total is exactly one match",
        result.allActiveInvariantsHold());
  }

  private static SolverOutcome solveWithScores(int s0, int s1, int s2) throws Exception {
    MModel model = compileFixture("Item.allInstances()->one(x | x.score > 10)");
    MClassInvariant invariant = findInvariant(model, "probe");
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Item", 3, 3))).get("Item");
    AttributeDomain scoreDomain = new AttributeDomain("Item", "score", null, List.of(), null, null);
    AttributeValues score =
        AttributeEncoder.encode(script, objects, "score", AttributeType.INTEGER, scoreDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of("i", new VariableBinding("Item", 0)),
            Map.of("Item.score", score),
            Map.of("Item.score", scoreDomain),
            Map.of("Item", objects),
            Map.of());
    script.assertThat(Smt.eq(Smt.sym(score.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(s0))));
    script.assertThat(Smt.eq(Smt.sym(score.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(s1))));
    script.assertThat(Smt.eq(Smt.sym(score.valueNames().get(2)), Smt.intLit(BigInteger.valueOf(s2))));
    TranslatedExpression translated =
        ExpressionTranslator.translate(invariant.bodyExpression(), context, TranslationMode.UNCERTAIN);
    script.assertThat(translated.trueTerm());
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
        .run(script.toSmtLib())
        .outcome();
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("one", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static SolverOutcome solveWithCategorizedScores(
      int s0, int cat0, int s1, int cat1, int s2, int cat2) throws Exception {
    MModel model =
        compileFixture("Item.allInstances()->select(category = 1)->one(x | x.score > 10)");
    MClassInvariant invariant = findInvariant(model, "probe");
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Item", 3, 3))).get("Item");
    AttributeDomain scoreDomain = new AttributeDomain("Item", "score", null, List.of(), null, null);
    AttributeValues score =
        AttributeEncoder.encode(script, objects, "score", AttributeType.INTEGER, scoreDomain);
    AttributeDomain categoryDomain =
        new AttributeDomain("Item", "category", null, List.of(), null, null);
    AttributeValues category =
        AttributeEncoder.encode(script, objects, "category", AttributeType.INTEGER, categoryDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of("i", new VariableBinding("Item", 0)),
            Map.of("Item.score", score, "Item.category", category),
            Map.of("Item.score", scoreDomain, "Item.category", categoryDomain),
            Map.of("Item", objects),
            Map.of());
    script.assertThat(Smt.eq(Smt.sym(score.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(s0))));
    script.assertThat(Smt.eq(Smt.sym(score.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(s1))));
    script.assertThat(Smt.eq(Smt.sym(score.valueNames().get(2)), Smt.intLit(BigInteger.valueOf(s2))));
    script.assertThat(
        Smt.eq(Smt.sym(category.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(cat0))));
    script.assertThat(
        Smt.eq(Smt.sym(category.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(cat1))));
    script.assertThat(
        Smt.eq(Smt.sym(category.valueNames().get(2)), Smt.intLit(BigInteger.valueOf(cat2))));
    TranslatedExpression translated =
        ExpressionTranslator.translate(invariant.bodyExpression(), context, TranslationMode.UNCERTAIN);
    script.assertThat(translated.trueTerm());
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
        .run(script.toSmtLib())
        .outcome();
  }

  private static SolverOutcome solveWithLinkedScores(
      int s0, int s1, boolean link0, boolean link1) throws Exception {
    MModel model = compileNavigationFixture();
    MClassInvariant invariant = findInvariant(model, "probe");
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots containers =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Container", 1, 1))).get("Container");
    ObjectSlots items =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Item", 2, 2))).get("Item");
    AttributeDomain scoreDomain = new AttributeDomain("Item", "score", null, List.of(), null, null);
    AttributeValues score =
        AttributeEncoder.encode(script, items, "score", AttributeType.INTEGER, scoreDomain);
    AssociationLinks links =
        AssociationLinkEncoder.encode(
            script,
            "Holds",
            containers,
            new Multiplicity(0, 1),
            items,
            new Multiplicity(0, -1),
            new AssociationScope("Holds", 0, -1));
    TranslationContext context =
        new TranslationContext(
            Map.of("c", new VariableBinding("Container", 0)),
            Map.of("Item.score", score),
            Map.of("Item.score", scoreDomain),
            Map.of("Container", containers, "Item", items),
            Map.of("Holds", links));
    script.assertThat(Smt.sym("Container_0_exists"));
    script.assertThat(Smt.sym("Item_0_exists"));
    script.assertThat(Smt.sym("Item_1_exists"));
    script.assertThat(Smt.eq(Smt.sym(score.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(s0))));
    script.assertThat(Smt.eq(Smt.sym(score.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(s1))));
    SmtTerm link0Term = Smt.sym(links.linkNames()[0][0]);
    SmtTerm link1Term = Smt.sym(links.linkNames()[0][1]);
    script.assertThat(link0 ? link0Term : Smt.not(link0Term));
    script.assertThat(link1 ? link1Term : Smt.not(link1Term));
    TranslatedExpression translated =
        ExpressionTranslator.translate(invariant.bodyExpression(), context, TranslationMode.UNCERTAIN);
    script.assertThat(translated.trueTerm());
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
        .run(script.toSmtLib())
        .outcome();
  }

  private static MModel compileFixture(String invariantBody) throws Exception {
    String source =
        """
        model OneScope
        class Item
        attributes
          score : Integer
          category : Integer
        end
        constraints
        context i : Item inv probe:
          %s
        """
            .formatted(invariantBody);
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "OneScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("OneScope fixture model did not compile: " + invariantBody);
    }
    return model;
  }

  private static MModel compileNavigationFixture() throws Exception {
    String source =
        """
        model OneNavigationScope
        class Container
        end
        class Item
        attributes
          score : Integer
        end
        association Holds between
          Container[0..1] role holder
          Item[0..*] role items
        end
        constraints
        context c : Container inv probe:
          c.items->one(x | x.score > 10)
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "OneNavigationScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("OneNavigationScope fixture model did not compile");
    }
    return model;
  }

  private static MClassInvariant findInvariant(MModel model, String name) {
    for (MClassInvariant invariant : model.classInvariants()) {
      if (invariant.name().equals(name)) {
        return invariant;
      }
    }
    throw new IllegalStateException("invariant not found: " + name);
  }
}
