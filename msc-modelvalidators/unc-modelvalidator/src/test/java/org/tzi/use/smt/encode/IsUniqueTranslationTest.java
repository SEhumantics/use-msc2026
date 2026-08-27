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
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.*;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * {@code isUnique} over the two evidence-grounded population sources named by
 * THESIS_SMT_MODEL_FINDER_PLAN.md 7.1's Tier 3 list and confirmed as the sole remaining refusal
 * blocking Sudoku/Sudoku-UNSAT: {@code X.allInstances()->isUnique(body)} (Shape 1, real invariants
 * {@code Column::columnIndexUnique}/{@code Row::rowIndexUnique}) and {@code
 * self.<single-hop, collection-valued association end>->isUnique(body)} (Shape 2, real invariants
 * {@code Row::uniqueValuesRow}/{@code Column::uniqueValuesColumn}/{@code Square::uniqueValuesSquare}).
 *
 * <p>Real USE isUnique semantics, established by executing the real evaluator (not inferred): the
 * result is ALWAYS a defined Boolean for these two source shapes -- never undefined, regardless of
 * how many population members have an undefined body -- and duplicate detection uses USE's own
 * TOTAL equality rule, where two undefined body values collide with EACH OTHER (never with a
 * defined value). That is exactly {@link ExpressionTranslator}'s existing {@code useEquality}
 * helper (already used for {@code =}/{@code <>}), reused here rather than re-derived.
 */
public class IsUniqueTranslationTest {

  // ---------------------------------------------------------------------
  // Shape 1: X.allInstances()->isUnique(body) -- Column::columnIndexUnique
  // ---------------------------------------------------------------------

  @Test
  public void columnIndexUniqueOnTheRealAstRejectsTwoColumnsSharingAnIndex() throws Exception {
    MModel model = compileSudoku();
    MClassInvariant inv = findInvariant(model, "columnIndexUnique");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots columns =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Column", 2, 2))).get("Column");
    AttributeDomain indexDomain = new AttributeDomain("Column", "index", null, List.of(), null, null);
    AttributeValues indexValues =
        AttributeEncoder.encode(script, columns, "index", AttributeType.INTEGER, indexDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of(),
            Map.of("Column.index", indexValues),
            Map.of("Column.index", indexDomain),
            Map.of("Column", columns),
            Map.of());
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Column_0_exists"));
    script.assertThat(Smt.sym("Column_1_exists"));
    script.assertThat(
        Smt.eq(Smt.sym(indexValues.valueNames().get(0)), Smt.sym(indexValues.valueNames().get(1))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  @Test
  public void columnIndexUniqueOnTheRealAstIsSatWithDistinctIndices() throws Exception {
    MModel model = compileSudoku();
    MClassInvariant inv = findInvariant(model, "columnIndexUnique");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots columns =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Column", 2, 2))).get("Column");
    AttributeDomain indexDomain = new AttributeDomain("Column", "index", null, List.of(), null, null);
    AttributeValues indexValues =
        AttributeEncoder.encode(script, columns, "index", AttributeType.INTEGER, indexDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of(),
            Map.of("Column.index", indexValues),
            Map.of("Column.index", indexDomain),
            Map.of("Column", columns),
            Map.of());
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Column_0_exists"));
    script.assertThat(Smt.sym("Column_1_exists"));
    script.assertThat(
        Smt.eq(Smt.sym(indexValues.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(1))));
    script.assertThat(
        Smt.eq(Smt.sym(indexValues.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(2))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  /** Proves the existence guard is load-bearing: a duplicate on a non-existent slot is harmless. */
  @Test
  public void columnIndexUniqueIsVacuouslySatisfiedWhenTheSecondSlotDoesNotExist() throws Exception {
    MModel model = compileSudoku();
    MClassInvariant inv = findInvariant(model, "columnIndexUnique");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots columns =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Column", 1, 2))).get("Column");
    AttributeDomain indexDomain = new AttributeDomain("Column", "index", null, List.of(), null, null);
    AttributeValues indexValues =
        AttributeEncoder.encode(script, columns, "index", AttributeType.INTEGER, indexDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of(),
            Map.of("Column.index", indexValues),
            Map.of("Column.index", indexDomain),
            Map.of("Column", columns),
            Map.of());
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Column_0_exists"));
    script.assertThat(Smt.not(Smt.sym("Column_1_exists")));
    script.assertThat(
        Smt.eq(Smt.sym(indexValues.valueNames().get(0)), Smt.sym(indexValues.valueNames().get(1))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  // ---------------------------------------------------------------------
  // Shape 2: self.<role>->isUnique(body) -- Row::uniqueValuesRow
  // ---------------------------------------------------------------------

  /**
   * Adversarial: ALL THREE Field slots are genuinely linked into the row (a full population), and
   * the colliding pair is the NON-ADJACENT one (slot 0 and slot 2, with slot 1 in between carrying
   * a distinct value). An implementation that only checked ADJACENT pairs (0,1) and (1,2) would see
   * 7&lt;&gt;8 and 8&lt;&gt;7 and wrongly report SAT; only a genuine all-pairs check catches (0,2).
   */
  @Test
  public void uniqueValuesRowOnTheRealAstRejectsANonAdjacentDuplicatePairAcrossAFullPopulation()
      throws Exception {
    MModel model = compileSudoku();
    MClassInvariant inv = findInvariant(model, "uniqueValuesRow");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots rows = ObjectSlotEncoder.encode(script, List.of(new ClassScope("Row", 1, 1))).get("Row");
    ObjectSlots fields =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Field", 3, 3))).get("Field");
    AssociationLinks rowFields =
        AssociationLinkEncoder.encode(
            script,
            "RowFields",
            rows,
            new Multiplicity(0, -1),
            fields,
            new Multiplicity(0, -1),
            new AssociationScope("RowFields", 0, -1));
    AttributeDomain valueDomain = new AttributeDomain("Field", "value", null, List.of(), null, null);
    AttributeValues valueValues =
        AttributeEncoder.encode(script, fields, "value", AttributeType.INTEGER, valueDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("self", new VariableBinding("Row", 0)),
            Map.of("Field.value", valueValues),
            Map.of("Field.value", valueDomain),
            Map.of("Row", rows, "Field", fields),
            Map.of("RowFields", rowFields));
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Row_0_exists"));
    script.assertThat(Smt.sym("Field_0_exists"));
    script.assertThat(Smt.sym("Field_1_exists"));
    script.assertThat(Smt.sym("Field_2_exists"));
    // All three fields genuinely belong to this row -- a full population, no decoys.
    script.assertThat(Smt.sym(rowFields.linkNames()[0][0]));
    script.assertThat(Smt.sym(rowFields.linkNames()[0][1]));
    script.assertThat(Smt.sym(rowFields.linkNames()[0][2]));
    script.assertThat(Smt.eq(Smt.sym(valueValues.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(7))));
    script.assertThat(Smt.eq(Smt.sym(valueValues.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(8))));
    script.assertThat(Smt.eq(Smt.sym(valueValues.valueNames().get(2)), Smt.intLit(BigInteger.valueOf(7))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  @Test
  public void uniqueValuesRowOnTheRealAstIsSatWhenAllLinkedFieldsHaveDistinctValues()
      throws Exception {
    MModel model = compileSudoku();
    MClassInvariant inv = findInvariant(model, "uniqueValuesRow");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots rows = ObjectSlotEncoder.encode(script, List.of(new ClassScope("Row", 1, 1))).get("Row");
    ObjectSlots fields =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Field", 3, 3))).get("Field");
    AssociationLinks rowFields =
        AssociationLinkEncoder.encode(
            script,
            "RowFields",
            rows,
            new Multiplicity(0, -1),
            fields,
            new Multiplicity(0, -1),
            new AssociationScope("RowFields", 0, -1));
    AttributeDomain valueDomain = new AttributeDomain("Field", "value", null, List.of(), null, null);
    AttributeValues valueValues =
        AttributeEncoder.encode(script, fields, "value", AttributeType.INTEGER, valueDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("self", new VariableBinding("Row", 0)),
            Map.of("Field.value", valueValues),
            Map.of("Field.value", valueDomain),
            Map.of("Row", rows, "Field", fields),
            Map.of("RowFields", rowFields));
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Row_0_exists"));
    script.assertThat(Smt.sym("Field_0_exists"));
    script.assertThat(Smt.sym("Field_1_exists"));
    script.assertThat(Smt.sym("Field_2_exists"));
    script.assertThat(Smt.sym(rowFields.linkNames()[0][0]));
    script.assertThat(Smt.sym(rowFields.linkNames()[0][1]));
    script.assertThat(Smt.sym(rowFields.linkNames()[0][2]));
    script.assertThat(Smt.eq(Smt.sym(valueValues.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(7))));
    script.assertThat(Smt.eq(Smt.sym(valueValues.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(8))));
    script.assertThat(Smt.eq(Smt.sym(valueValues.valueNames().get(2)), Smt.intLit(BigInteger.valueOf(9))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  /**
   * Adversarial the other direction: a NON-FULL population. Slot 1 EXISTS as a Field but is NOT
   * linked into this row (a decoy, e.g. it belongs to some other row) and carries a value that
   * duplicates slot 0's -- while the two fields that actually ARE members (slots 0 and 2) have
   * distinct values. Only correct if Shape 2's membership resolution is a genuine "for every k
   * where linkTerm holds" rather than something that iterates every destination slot regardless of
   * whether it is actually linked to this row.
   */
  @Test
  public void uniqueValuesRowOnTheRealAstIgnoresAnUnlinkedDecoyFieldThatWouldOtherwiseCollide()
      throws Exception {
    MModel model = compileSudoku();
    MClassInvariant inv = findInvariant(model, "uniqueValuesRow");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots rows = ObjectSlotEncoder.encode(script, List.of(new ClassScope("Row", 1, 1))).get("Row");
    ObjectSlots fields =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Field", 3, 3))).get("Field");
    AssociationLinks rowFields =
        AssociationLinkEncoder.encode(
            script,
            "RowFields",
            rows,
            new Multiplicity(0, -1),
            fields,
            new Multiplicity(0, -1),
            new AssociationScope("RowFields", 0, -1));
    AttributeDomain valueDomain = new AttributeDomain("Field", "value", null, List.of(), null, null);
    AttributeValues valueValues =
        AttributeEncoder.encode(script, fields, "value", AttributeType.INTEGER, valueDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("self", new VariableBinding("Row", 0)),
            Map.of("Field.value", valueValues),
            Map.of("Field.value", valueDomain),
            Map.of("Row", rows, "Field", fields),
            Map.of("RowFields", rowFields));
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Row_0_exists"));
    script.assertThat(Smt.sym("Field_0_exists"));
    script.assertThat(Smt.sym("Field_1_exists"));
    script.assertThat(Smt.sym("Field_2_exists"));
    script.assertThat(Smt.sym(rowFields.linkNames()[0][0]));
    // Slot 1 exists but is NOT linked into this row -- a decoy.
    script.assertThat(Smt.not(Smt.sym(rowFields.linkNames()[0][1])));
    script.assertThat(Smt.sym(rowFields.linkNames()[0][2]));
    script.assertThat(Smt.eq(Smt.sym(valueValues.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(5))));
    // Decoy's value duplicates slot 0's -- must NOT matter, since it is not a member.
    script.assertThat(Smt.eq(Smt.sym(valueValues.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(5))));
    script.assertThat(Smt.eq(Smt.sym(valueValues.valueNames().get(2)), Smt.intLit(BigInteger.valueOf(9))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  // ---------------------------------------------------------------------
  // Scope boundary: fail closed on anything outside the two named shapes
  // ---------------------------------------------------------------------

  @Test
  public void isUniqueOverAChainedTwoHopNavigationFailsClosed() throws Exception {
    MModel model = compileScopeFixture();
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
  public void isUniqueOverASelectFilteredSourceFailsClosed() throws Exception {
    MModel model = compileScopeFixture();
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
        thrown.getMessage().contains("range other than X.allInstances()"));
  }

  @Test
  public void isUniqueWithAnUnsupportedBodyShapeFailsClosed() throws Exception {
    MModel model = compileScopeFixture();
    MClassInvariant inv = findInvariant(model, "unsupportedBodyShape");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots bs = ObjectSlotEncoder.encode(script, List.of(new ClassScope("B", 1, 1))).get("B");
    ObjectSlots cs = ObjectSlotEncoder.encode(script, List.of(new ClassScope("C", 1, 1))).get("C");
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

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () -> ExpressionTranslator.translate(inv.bodyExpression(), ctx));

    assertEquals(FragmentBoundary.TIER_3, thrown.boundary());
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("size"));
  }

  private static MModel compileSudoku() throws Exception {
    Path file = Path.of("../benchmark/examples/Sudoku/Sudoku.use");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/Sudoku/Sudoku.use");
    }
    String source = Files.readString(file);
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "Sudoku", err, factory);
    err.flush();
    return model;
  }

  private static MModel compileScopeFixture() {
    String source =
        """
        model IsUniqueScope
        class A
        attributes
          dummy : Integer
        end
        class B
        attributes
          dummy : Integer
        end
        class C
        attributes
          val : Integer
        end
        association AB between
          A[1] role owner
          B[1] role linkedB
        end
        association BC between
          B[1] role ownerB
          C[*] role cs
        end
        constraints
        context a: A inv chainedTooDeep:
          a.linkedB.cs->isUnique(val)
        context b: B inv filteredSource:
          b.cs->select(val > 0)->isUnique(val)
        context b: B inv unsupportedBodyShape:
          b.cs->isUnique(b.cs->size())
        """;
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "IsUniqueScope", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("scope fixture model did not compile:\n" + source);
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
