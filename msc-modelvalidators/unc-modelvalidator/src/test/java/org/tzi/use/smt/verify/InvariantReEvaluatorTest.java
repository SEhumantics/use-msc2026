package org.tzi.use.smt.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
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
import org.tzi.use.smt.encode.*;
import org.tzi.use.smt.reconstruct.SystemStateReconstructor;
import org.tzi.use.smt.solver.*;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;

public class InvariantReEvaluatorTest {

  @Test
  public void aValidLibraryWitnessReEvaluatesAllNineInvariantsAsTrue() throws Exception {
    MModel model = compileLibrary();
    SmtScript script = new SmtScript("QF_LIA");

    ObjectSlots users =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("User", 1, 1))).get("User");
    ObjectSlots copies =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Copy", 1, 1))).get("Copy");
    ObjectSlots books =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 1, 1))).get("Book");

    AttributeDomain nameDomain =
        new AttributeDomain("User", "name", null, List.of("Ada"), null, null);
    AttributeDomain addressDomain =
        new AttributeDomain("User", "address", null, List.of("NY"), null, null);
    AttributeDomain sigDomain =
        new AttributeDomain("Copy", "signature", null, List.of("DBS42"), null, null);
    AttributeDomain titleDomain =
        new AttributeDomain("Book", "title", null, List.of("DBforDummies"), null, null);
    AttributeDomain authDomain =
        new AttributeDomain("Book", "auth", null, List.of("Ada"), null, null);
    AttributeDomain yearDomain =
        new AttributeDomain("Book", "year", null, List.of(), new BigDecimal("1455"), null);

    AttributeValues nameValues =
        AttributeEncoder.encode(script, users, "name", AttributeType.STRING, nameDomain);
    AttributeValues addressValues =
        AttributeEncoder.encode(script, users, "address", AttributeType.STRING, addressDomain);
    AttributeValues sigValues =
        AttributeEncoder.encode(script, copies, "signature", AttributeType.STRING, sigDomain);
    AttributeValues titleValues =
        AttributeEncoder.encode(script, books, "title", AttributeType.STRING, titleDomain);
    AttributeValues authValues =
        AttributeEncoder.encode(script, books, "auth", AttributeType.STRING, authDomain);
    AttributeValues yearValues =
        AttributeEncoder.encode(script, books, "year", AttributeType.INTEGER, yearDomain);

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

    script.assertThat(Smt.sym("User_0_exists"));
    script.assertThat(Smt.sym("Copy_0_exists"));
    script.assertThat(Smt.sym("Book_0_exists"));
    script.assertThat(Smt.eq(Smt.sym(nameValues.valueNames().get(0)), Smt.intLit(BigInteger.ZERO)));
    script.assertThat(
        Smt.eq(Smt.sym(addressValues.valueNames().get(0)), Smt.intLit(BigInteger.ZERO)));
    script.assertThat(Smt.eq(Smt.sym(sigValues.valueNames().get(0)), Smt.intLit(BigInteger.ZERO)));
    script.assertThat(
        Smt.eq(Smt.sym(titleValues.valueNames().get(0)), Smt.intLit(BigInteger.ZERO)));
    script.assertThat(Smt.eq(Smt.sym(authValues.valueNames().get(0)), Smt.intLit(BigInteger.ZERO)));
    script.assertThat(
        Smt.eq(Smt.sym(yearValues.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(1995))));
    script.assertThat(Smt.sym(borrows.linkNames()[0][0]));
    script.assertThat(Smt.sym(belongsTo.linkNames()[0][0]));

    MSystem system =
        reconstruct(
            model,
            script,
            users,
            copies,
            books,
            nameValues,
            addressValues,
            sigValues,
            titleValues,
            authValues,
            yearValues,
            nameDomain,
            addressDomain,
            sigDomain,
            titleDomain,
            authDomain,
            yearDomain,
            borrows,
            belongsTo);

    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, system);

    assertEquals(9, verdicts.size());
    for (InvariantVerdict verdict : verdicts) {
      assertTrue(
          verdict.invariantName() + " must hold in a genuinely valid witness", verdict.holds());
    }
  }

  /**
   * Proves the re-evaluator genuinely discriminates true from false, not just always-true: two
   * Users share one name (violating nameIsKey); no Copy/Book objects exist at all, so every
   * Book/Copy-context invariant holds vacuously (forAll over allInstances() = {}) and
   * nameAddressFormatOk holds for both -- isolating nameIsKey as the only invariant that can fail.
   */
  @Test
  public void twoUsersSharingANameReEvaluatesNameIsKeyAsFalseAndNothingElse() throws Exception {
    MModel model = compileLibrary();
    SmtScript script = new SmtScript("QF_LIA");

    ObjectSlots users =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("User", 2, 2))).get("User");
    AttributeDomain nameDomain =
        new AttributeDomain("User", "name", null, List.of("Ada"), null, null);
    AttributeDomain addressDomain =
        new AttributeDomain("User", "address", null, List.of("NY", "LA"), null, null);
    AttributeValues nameValues =
        AttributeEncoder.encode(script, users, "name", AttributeType.STRING, nameDomain);
    AttributeValues addressValues =
        AttributeEncoder.encode(script, users, "address", AttributeType.STRING, addressDomain);

    script.assertThat(Smt.sym("User_0_exists"));
    script.assertThat(Smt.sym("User_1_exists"));
    for (int i = 0; i < 2; i++) {
      script.assertThat(
          Smt.eq(Smt.sym(nameValues.valueNames().get(i)), Smt.intLit(BigInteger.ZERO)));
      script.assertThat(
          Smt.eq(Smt.sym(addressValues.valueNames().get(i)), Smt.intLit(BigInteger.valueOf(i))));
    }

    SolverResult result =
        new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
    assertEquals(SolverOutcome.SAT, result.outcome());
    Map<String, SmtValue> modelValues = SmtModelParser.parse(result.modelText());

    TranslationContext context =
        new TranslationContext(
            Map.of(),
            Map.of("User.name", nameValues, "User.address", addressValues),
            Map.of("User.name", nameDomain, "User.address", addressDomain),
            Map.of("User", users),
            Map.of());

    MSystem system = SystemStateReconstructor.reconstruct(model, context, modelValues);
    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, system);

    assertEquals(9, verdicts.size());
    long failing = verdicts.stream().filter(v -> !v.holds()).count();
    assertEquals("exactly nameIsKey must fail", 1, failing);
    assertTrue(
        verdicts.stream().anyMatch(v -> !v.holds() && v.invariantName().contains("nameIsKey")));
  }

  private static MSystem reconstruct(
      MModel model,
      SmtScript script,
      ObjectSlots users,
      ObjectSlots copies,
      ObjectSlots books,
      AttributeValues nameValues,
      AttributeValues addressValues,
      AttributeValues sigValues,
      AttributeValues titleValues,
      AttributeValues authValues,
      AttributeValues yearValues,
      AttributeDomain nameDomain,
      AttributeDomain addressDomain,
      AttributeDomain sigDomain,
      AttributeDomain titleDomain,
      AttributeDomain authDomain,
      AttributeDomain yearDomain,
      AssociationLinks borrows,
      AssociationLinks belongsTo)
      throws Exception {
    SolverResult result =
        new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
    assertEquals(SolverOutcome.SAT, result.outcome());
    Map<String, SmtValue> modelValues = SmtModelParser.parse(result.modelText());

    TranslationContext context =
        new TranslationContext(
            Map.of(),
            Map.of(
                "User.name",
                nameValues,
                "User.address",
                addressValues,
                "Copy.signature",
                sigValues,
                "Book.title",
                titleValues,
                "Book.auth",
                authValues,
                "Book.year",
                yearValues),
            Map.of(
                "User.name",
                nameDomain,
                "User.address",
                addressDomain,
                "Copy.signature",
                sigDomain,
                "Book.title",
                titleDomain,
                "Book.auth",
                authDomain,
                "Book.year",
                yearDomain),
            Map.of("User", users, "Copy", copies, "Book", books),
            Map.of("Borrows", borrows, "BelongsTo", belongsTo));

    return SystemStateReconstructor.reconstruct(model, context, modelValues);
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
}
