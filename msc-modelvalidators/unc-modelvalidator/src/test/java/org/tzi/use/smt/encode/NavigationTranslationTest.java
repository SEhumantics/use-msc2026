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

public class NavigationTranslationTest {

  @Test
  public void noDoubleBorrowingsOnTheRealAstRejectsTwoCopiesOfTheSameBook() throws Exception {
    MModel model = compileLibrary();
    MClassInvariant inv = findInvariant(model, "noDoubleBorrowings");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots users =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("User", 1, 1))).get("User");
    ObjectSlots copies =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Copy", 2, 2))).get("Copy");
    ObjectSlots books =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 1, 2))).get("Book");

    AssociationLinks borrows =
        AssociationLinkEncoder.encode(
            script,
            "Borrows",
            copies,
            new Multiplicity(0, -1),
            users,
            new Multiplicity(0, 1),
            new AssociationScope("Borrows", 0, -1));
    AssociationLinks belongsTo =
        AssociationLinkEncoder.encode(
            script,
            "BelongsTo",
            copies,
            new Multiplicity(0, -1),
            books,
            new Multiplicity(1, 1),
            new AssociationScope("BelongsTo", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("u", new VariableBinding("User", 0)),
            Map.of(),
            Map.of(),
            Map.of("User", users, "Copy", copies, "Book", books),
            Map.of("Borrows", borrows, "BelongsTo", belongsTo));
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("User_0_exists"));
    script.assertThat(Smt.sym("Copy_0_exists"));
    script.assertThat(Smt.sym("Copy_1_exists"));
    script.assertThat(Smt.sym(borrows.linkNames()[0][0]));
    script.assertThat(Smt.sym(borrows.linkNames()[1][0]));
    script.assertThat(Smt.sym(belongsTo.linkNames()[0][0]));
    script.assertThat(Smt.sym(belongsTo.linkNames()[1][0]));
    script.assertThat(translated);

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  @Test
  public void noDoubleBorrowingsOnTheRealAstAllowsTwoCopiesOfDifferentBooks() throws Exception {
    MModel model = compileLibrary();
    MClassInvariant inv = findInvariant(model, "noDoubleBorrowings");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots users =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("User", 1, 1))).get("User");
    ObjectSlots copies =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Copy", 2, 2))).get("Copy");
    ObjectSlots books =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 2, 2))).get("Book");

    AssociationLinks borrows =
        AssociationLinkEncoder.encode(
            script,
            "Borrows",
            copies,
            new Multiplicity(0, -1),
            users,
            new Multiplicity(0, 1),
            new AssociationScope("Borrows", 0, -1));
    AssociationLinks belongsTo =
        AssociationLinkEncoder.encode(
            script,
            "BelongsTo",
            copies,
            new Multiplicity(0, -1),
            books,
            new Multiplicity(1, 1),
            new AssociationScope("BelongsTo", 0, -1));

    TranslationContext ctx =
        new TranslationContext(
            Map.of("u", new VariableBinding("User", 0)),
            Map.of(),
            Map.of(),
            Map.of("User", users, "Copy", copies, "Book", books),
            Map.of("Borrows", borrows, "BelongsTo", belongsTo));
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("User_0_exists"));
    script.assertThat(Smt.sym("Copy_0_exists"));
    script.assertThat(Smt.sym("Copy_1_exists"));
    script.assertThat(Smt.sym(borrows.linkNames()[0][0]));
    script.assertThat(Smt.sym(borrows.linkNames()[1][0]));
    script.assertThat(Smt.sym(belongsTo.linkNames()[0][0]));
    script.assertThat(Smt.sym(belongsTo.linkNames()[1][1]));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  /**
   * Sudoku's real {@code givenR1C3}: {@code (self.row.index = 1 and self.column.index = 3) implies
   * self.value = 6} -- one of the two real corpus invariant bodies this feature targets ({@code
   * THESIS_SMT_MODEL_FINDER_PLAN.md} 7.1's "attribute access" combined with "navigation over
   * regular associations"). {@code self.row} and {@code self.column} are both single-valued
   * navigations (Row[1]/Column[1] ends), so {@code .index} after either is exactly the "one
   * navigation hop then one attribute" shape.
   *
   * <p>Adversarial by construction: Row has TWO slots, and the fixture wires {@code self.row}'s
   * link to slot 1 (index = 1, satisfying the clue) while slot 0 carries a DECOY index (99) that
   * would satisfy nothing. A translation that read the wrong slot -- e.g. always slot 0, or the
   * wrong end of the association -- would see {@code self.row.index = 99}, make the antecedent
   * false, and turn the whole implication vacuously true regardless of {@code self.value}. Pinning
   * {@code self.value = 7} (violating the real consequent) makes the CORRECT translation UNSAT and
   * a "wrong slot" translation SAT, so this is a real correctness check, not just a "does it throw"
   * check.
   */
  @Test
  public void givenR1C3OnTheRealAstIsUnsatWhenTheLinkedRowsIndexForcesTheClueAndValueDisagrees()
      throws Exception {
    MModel model = compileSudoku();
    MClassInvariant inv = findInvariant(model, "givenR1C3");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots fields =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Field", 1, 1))).get("Field");
    ObjectSlots rows =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Row", 2, 2))).get("Row");
    ObjectSlots columns =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Column", 1, 1))).get("Column");

    AssociationLinks rowFields =
        AssociationLinkEncoder.encode(
            script,
            "RowFields",
            rows,
            new Multiplicity(0, -1),
            fields,
            new Multiplicity(0, -1),
            new AssociationScope("RowFields", 0, -1));
    AssociationLinks columnFields =
        AssociationLinkEncoder.encode(
            script,
            "ColumnFields",
            columns,
            new Multiplicity(0, -1),
            fields,
            new Multiplicity(0, -1),
            new AssociationScope("ColumnFields", 0, -1));

    AttributeDomain rowIndexDomain =
        new AttributeDomain("Row", "index", null, List.of(), null, null);
    AttributeValues rowIndex =
        AttributeEncoder.encode(script, rows, "index", AttributeType.INTEGER, rowIndexDomain);
    AttributeDomain columnIndexDomain =
        new AttributeDomain("Column", "index", null, List.of(), null, null);
    AttributeValues columnIndex =
        AttributeEncoder.encode(script, columns, "index", AttributeType.INTEGER, columnIndexDomain);
    AttributeDomain fieldValueDomain =
        new AttributeDomain("Field", "value", null, List.of(), null, null);
    AttributeValues fieldValue =
        AttributeEncoder.encode(script, fields, "value", AttributeType.INTEGER, fieldValueDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("self", new VariableBinding("Field", 0)),
            Map.of("Row.index", rowIndex, "Column.index", columnIndex, "Field.value", fieldValue),
            Map.of(
                "Row.index", rowIndexDomain,
                "Column.index", columnIndexDomain,
                "Field.value", fieldValueDomain),
            Map.of("Field", fields, "Row", rows, "Column", columns),
            Map.of("RowFields", rowFields, "ColumnFields", columnFields));
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Field_0_exists"));
    // self.row links to Row slot 1, NOT slot 0 -- the decoy.
    script.assertThat(Smt.not(Smt.sym(rowFields.linkNames()[0][0])));
    script.assertThat(Smt.sym(rowFields.linkNames()[1][0]));
    script.assertThat(
        Smt.eq(Smt.sym(rowIndex.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(99))));
    script.assertThat(
        Smt.eq(Smt.sym(rowIndex.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(1))));
    script.assertThat(Smt.sym(columnFields.linkNames()[0][0]));
    script.assertThat(
        Smt.eq(Smt.sym(columnIndex.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(3))));
    // Violates the real consequent (self.value = 6) -- correct only if the antecedent genuinely
    // reads Row slot 1's index (1), not slot 0's decoy (99).
    script.assertThat(
        Smt.eq(Smt.sym(fieldValue.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(7))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  /** Same fixture, consistent state: the linked row/column really do give the clue's coordinate. */
  @Test
  public void givenR1C3OnTheRealAstIsSatWhenTheLinkedValueMatchesTheClue() throws Exception {
    MModel model = compileSudoku();
    MClassInvariant inv = findInvariant(model, "givenR1C3");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots fields =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Field", 1, 1))).get("Field");
    ObjectSlots rows =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Row", 2, 2))).get("Row");
    ObjectSlots columns =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Column", 1, 1))).get("Column");

    AssociationLinks rowFields =
        AssociationLinkEncoder.encode(
            script,
            "RowFields",
            rows,
            new Multiplicity(0, -1),
            fields,
            new Multiplicity(0, -1),
            new AssociationScope("RowFields", 0, -1));
    AssociationLinks columnFields =
        AssociationLinkEncoder.encode(
            script,
            "ColumnFields",
            columns,
            new Multiplicity(0, -1),
            fields,
            new Multiplicity(0, -1),
            new AssociationScope("ColumnFields", 0, -1));

    AttributeDomain rowIndexDomain =
        new AttributeDomain("Row", "index", null, List.of(), null, null);
    AttributeValues rowIndex =
        AttributeEncoder.encode(script, rows, "index", AttributeType.INTEGER, rowIndexDomain);
    AttributeDomain columnIndexDomain =
        new AttributeDomain("Column", "index", null, List.of(), null, null);
    AttributeValues columnIndex =
        AttributeEncoder.encode(script, columns, "index", AttributeType.INTEGER, columnIndexDomain);
    AttributeDomain fieldValueDomain =
        new AttributeDomain("Field", "value", null, List.of(), null, null);
    AttributeValues fieldValue =
        AttributeEncoder.encode(script, fields, "value", AttributeType.INTEGER, fieldValueDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("self", new VariableBinding("Field", 0)),
            Map.of("Row.index", rowIndex, "Column.index", columnIndex, "Field.value", fieldValue),
            Map.of(
                "Row.index", rowIndexDomain,
                "Column.index", columnIndexDomain,
                "Field.value", fieldValueDomain),
            Map.of("Field", fields, "Row", rows, "Column", columns),
            Map.of("RowFields", rowFields, "ColumnFields", columnFields));
    var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Field_0_exists"));
    script.assertThat(Smt.not(Smt.sym(rowFields.linkNames()[0][0])));
    script.assertThat(Smt.sym(rowFields.linkNames()[1][0]));
    script.assertThat(
        Smt.eq(Smt.sym(rowIndex.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(99))));
    script.assertThat(
        Smt.eq(Smt.sym(rowIndex.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(1))));
    script.assertThat(Smt.sym(columnFields.linkNames()[0][0]));
    script.assertThat(
        Smt.eq(Smt.sym(columnIndex.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(3))));
    script.assertThat(
        Smt.eq(Smt.sym(fieldValue.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(6))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  /**
   * Scope boundary: {@code a.b.c.val} chains TWO navigation hops before the attribute leaf ({@code
   * a.b.c}'s own receiver {@code a.b} is itself a navigation, not a bare variable). This is
   * deliberately refused rather than silently generalized into a recursive evaluator -- neither
   * real corpus invariant needs more than one hop.
   */
  @Test
  public void attributeAccessAfterMoreThanOneNavigationHopFailsClosed() throws Exception {
    MModel model = compileNavChain();
    MClassInvariant inv = findInvariant(model, "chainedTooDeep");

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    inv.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));

    assertEquals(FragmentBoundary.TIER_2, thrown.boundary());
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("more than one navigation hop"));
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

  private static MModel compileNavChain() throws Exception {
    String source =
        """
        model NavChain
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
          A[1] role a
          B[1] role b
        end
        association BC between
          B[1] role bb
          C[1] role c
        end
        constraints
        context a:A inv chainedTooDeep:
          a.b.c.val = 1
        """;
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "NavChain", err, factory);
    err.flush();
    return model;
  }

  private static MModel compileLibrary() throws Exception {
    Path file = Path.of("../benchmark/examples/Library/Library.use");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/Library/Library.use");
    }
    String source = Files.readString(file);
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "Library", err, factory);
    err.flush();
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
