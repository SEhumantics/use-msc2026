package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.*;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

public class FragmentCheckerTest {

  @Test
  public void allNineLibraryInvariantsAssembleAndAreJointlySatisfiable() throws Exception {
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

    TranslationContext ctx =
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

    FragmentChecker.Result result =
        FragmentChecker.check(model.classInvariants().stream().toList(), ctx);

    assertEquals(9, result.ledger().entries().size());
    assertTrue("expected all 9 supported: " + result.ledger(), result.ledger().allSupported());
    result.ledger().requireAllSupported();

    for (SmtTerm term : result.assembled().values()) {
      script.assertThat(term);
    }

    assertEquals(SolverOutcome.SAT, solve(script).outcome());

    Map<String, Set<TranslationMode>> bothModes = new LinkedHashMap<>();
    model
        .classInvariants()
        .forEach(
            invariant ->
                bothModes.put(
                    invariant.qualifiedName(),
                    Set.of(TranslationMode.NOMINAL, TranslationMode.UNCERTAIN)));
    FragmentChecker.ReifiedResult reified =
        FragmentChecker.checkAndReify(
            model.classInvariants().stream().toList(), bothModes, ctx, script);
    assertEquals(18, reified.ledger().entries().size());
    reified.ledger().requireAllSupported();
    List<SmtTerm> differences = new java.util.ArrayList<>();
    for (MClassInvariant invariant : model.classInvariants()) {
      InvariantClassification nominal =
          reified
              .classifications()
              .get(
                  new FragmentChecker.ClassificationKey(
                      invariant.qualifiedName(), TranslationMode.NOMINAL));
      InvariantClassification uncertain =
          reified
              .classifications()
              .get(
                  new FragmentChecker.ClassificationKey(
                      invariant.qualifiedName(), TranslationMode.UNCERTAIN));
      differences.add(Smt.not(Smt.eq(nominal.defined(), uncertain.defined())));
      differences.add(Smt.not(Smt.eq(nominal.value(), uncertain.value())));
    }
    script.assertThat(Smt.or(differences));
    assertEquals("crisp Library modes must coincide", SolverOutcome.UNSAT, solve(script).outcome());
  }

  @Test
  public void anUnsupportedInvariantIsCaughtIndividuallyAndFailsClosed() throws Exception {
    MModel model = compileLibrary();
    MClassInvariant noDoubleBorrowings =
        model.classInvariants().stream()
            .filter(inv -> inv.name().equals("noDoubleBorrowings"))
            .findFirst()
            .orElseThrow();
    MClassInvariant yearPlausible =
        model.classInvariants().stream()
            .filter(inv -> inv.name().equals("yearPlausible"))
            .findFirst()
            .orElseThrow();

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots books =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 1, 1))).get("Book");
    AttributeDomain yearDomain =
        new AttributeDomain("Book", "year", null, List.of(), new BigDecimal("1455"), null);
    AttributeValues yearValues =
        AttributeEncoder.encode(script, books, "year", AttributeType.INTEGER, yearDomain);

    // Deliberately omit User/Copy slots and Borrows/BelongsTo links: noDoubleBorrowings needs
    // them and must be caught as unsupported, while yearPlausible (needs only Book) succeeds.
    TranslationContext ctx =
        new TranslationContext(
            Map.of(),
            Map.of("Book.year", yearValues),
            Map.of("Book.year", yearDomain),
            Map.of("Book", books),
            Map.of());

    FragmentChecker.Result result =
        FragmentChecker.check(List.of(yearPlausible, noDoubleBorrowings), ctx);

    assertEquals(2, result.ledger().entries().size());
    assertTrue(result.ledger().entries().get(0).supported());
    assertTrue(!result.ledger().entries().get(1).supported());
    assertThrows(SmtTranslationException.class, () -> result.ledger().requireAllSupported());
  }

  @Test
  public void unsupportedSyntaxIsReportedForEveryRequestedModeBeforeSolving() {
    MModel model =
        compile(
            """
            model Unsupported
            class Sample
            end
            constraints
            context self : Sample inv Conditional: if true then true else false endif
            """,
            "Unsupported");
    MClassInvariant invariant = model.classInvariants().iterator().next();
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots slots =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Sample", 1, 1))).get("Sample");
    TranslationContext context =
        new TranslationContext(
            Map.of(), Map.of(), Map.of(), Map.of("Sample", slots), Map.of());

    FragmentChecker.ReifiedResult result =
        FragmentChecker.checkAndReify(
            List.of(invariant),
            Map.of(
                invariant.qualifiedName(),
                Set.of(TranslationMode.NOMINAL, TranslationMode.UNCERTAIN)),
            context,
            script);

    assertEquals(2, result.ledger().entries().size());
    assertEquals(
        Set.of(TranslationMode.NOMINAL, TranslationMode.UNCERTAIN),
        result.ledger().entries().stream()
            .map(InvariantCoverage::mode)
            .collect(java.util.stream.Collectors.toSet()));
    assertTrue(result.ledger().entries().stream().noneMatch(InvariantCoverage::supported));
    SmtTranslationException exception =
        assertThrows(SmtTranslationException.class, result.ledger()::requireAllSupported);
    assertTrue(exception.getMessage().contains("[NOMINAL]"));
    assertTrue(exception.getMessage().contains("[UNCERTAIN]"));
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

  private static MModel compile(String source, String name) {
    MModel model =
        USECompiler.compileSpecification(
            source, name, new PrintWriter(System.err), new ModelFactory());
    if (model == null) throw new AssertionError("model did not compile: " + name);
    return model;
  }

  private static SolverResult solve(SmtScript script) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
  }
}
