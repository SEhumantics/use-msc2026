package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.finder.BoundedCompletenessQualification;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtSort;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.smt.solver.SolverResult;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Correctness obligation 1 (type and definedness preservation), 7 of the proposal: "every accepted
 * expression produces the intended Z3 sort together with an explicit definedness condition."
 *
 * <p>The SORT half is checked by the real solver rather than by a Java-side model of sorts. {@code
 * SmtTerm} is deliberately untyped text, so a Java assertion about sorts would only be checking a
 * second implementation of the same guess; Z3 rejects an ill-sorted {@code (assert ...)} outright,
 * which makes it the actual authority on whether the emitted term has the intended sort. The
 * DEFINEDNESS half is checked structurally, because "explicit" has a precise meaning here: the term
 * must not be the constant {@code true} wherever the source expression really can be undefined.
 *
 * <p>Also carries 7.3's degeneracy assertion: on a crisp-only model the two translation modes must
 * produce IDENTICAL terms, not merely equisatisfiable ones -- that is what "the same code path
 * serves it" means, and it is asserted here rather than special-cased in the translator.
 */
public class TypeAndDefinednessPreservationTest {

  /**
   * Every invariant of the real Library model, reified in BOTH modes, is accepted by the pinned
   * solver as a well-sorted Boolean. An ill-sorted term makes Z3 emit an error instead of a
   * verdict, which {@code SolverProcess} classifies as MALFORMED.
   */
  @Test
  public void everyAcceptedExpressionIsWellSortedBooleanToTheRealSolver() throws Exception {
    MModel model = compileLibrary();
    SmtScript script = new SmtScript("QF_UFLIRA");
    TranslationContext context = libraryContext(script);

    int reified = 0;
    for (MClassInvariant invariant : model.classInvariants(true)) {
      for (TranslationMode mode : TranslationMode.values()) {
        InvariantClassification classification =
            InvariantAssembler.reify(script, invariant, context, mode);
        // Each of the three classifications must itself be a well-sorted Boolean term.
        script.declareConst("probe_" + classification.definedName(), SmtSort.BOOL);
        script.assertThat(
            Smt.eq(
                Smt.sym("probe_" + classification.definedName()),
                Smt.or(
                    List.of(
                        classification.trueTerm(),
                        classification.falseTerm(),
                        classification.undefinedTerm()))));
        reified++;
      }
    }
    assertEquals("nine Library invariants in two modes", 18, reified);

    SolverResult result = solve(script);
    assertNotEquals(
        "an ill-sorted term would make Z3 emit an error instead of a verdict: "
            + result.rawOutput(),
        SolverOutcome.MALFORMED,
        result.outcome());
    assertEquals(SolverOutcome.SAT, result.outcome());
  }

  /**
   * "Explicit definedness condition" has teeth only if it is a real term. A total operation over
   * always-defined operands legitimately yields the constant {@code true}; an expression whose
   * source really can be undefined must not.
   */
  @Test
  public void definednessIsAnExplicitTermAndNotConstantTrueWhereTheSourceCanBeUndefined()
      throws Exception {
    MModel model = compileLibrary();
    SmtScript script = new SmtScript("QF_UFLIRA");
    TranslationContext context = libraryContext(script);

    // noDoubleBorrowings quantifies over links that may be absent, so its definedness is a term
    // over the link grid, never the constant true.
    TranslatedExpression quantified =
        InvariantAssembler.classify(
            invariant(model, "User::noDoubleBorrowings"), context, TranslationMode.UNCERTAIN);
    assertNotNull(quantified.defined());
    assertNotEquals(
        "an existence-guarded quantifier cannot be unconditionally defined",
        Smt.bool(true),
        quantified.defined());

    // A crisp attribute comparison over always-populated slots legitimately IS unconditionally
    // defined; asserting otherwise would be asserting a defect.
    TranslatedExpression total =
        InvariantAssembler.classify(
            invariant(model, "Book::yearPlausible"), context, TranslationMode.UNCERTAIN);
    assertNotNull(total.defined());
  }

  /** The pair is structural: a translated expression cannot exist without its definedness half. */
  @Test
  public void atranslatedExpressionCannotExistWithoutADefinednessTerm() {
    assertThrows(
        IllegalArgumentException.class, () -> new TranslatedExpression(null, Smt.bool(true)));
    assertThrows(
        IllegalArgumentException.class, () -> new TranslatedExpression(Smt.bool(true), null));
  }

  /**
   * 7.3's degenerate case, asserted rather than special-cased: Library is crisp-only, so NOMINAL
   * and UNCERTAIN must produce the very same terms -- same code path, not two implementations that
   * happen to agree.
   */
  @Test
  public void aCrispOnlyModelTranslatesIdenticallyInBothModes() throws Exception {
    MModel model = compileLibrary();
    SmtScript script = new SmtScript("QF_UFLIRA");
    TranslationContext context = libraryContext(script);

    List<String> differing = new ArrayList<>();
    for (MClassInvariant invariant : model.classInvariants(true)) {
      TranslatedExpression nominal =
          InvariantAssembler.classify(invariant, context, TranslationMode.NOMINAL);
      TranslatedExpression uncertain =
          InvariantAssembler.classify(invariant, context, TranslationMode.UNCERTAIN);
      if (!nominal.equals(uncertain)) {
        differing.add(invariant.qualifiedName());
      }
    }
    assertEquals(
        "a crisp-only model is the degenerate case where the two modes COINCIDE",
        List.of(),
        differing);
  }

  /**
   * The numerical policy a refutation is qualified by must describe the encoding that actually ran.
   * Stating a width in prose and bisecting to a different one would make the qualification a
   * decoration.
   */
  @Test
  public void theDeclaredNumericalPolicyMatchesTheEncodingItDescribes() {
    assertEquals(
        "the declared quantile-enclosure width must be the width URealThresholdBoundary bisects to",
        URealThresholdBoundary.MAX_WIDTH,
        BoundedCompletenessQualification.QUANTILE_ENCLOSURE_WIDTH,
        0.0);
    assertTrue(
        BoundedCompletenessQualification.NUMERICAL_POLICY,
        BoundedCompletenessQualification.NUMERICAL_POLICY.contains("outward"));
  }

  private static MClassInvariant invariant(MModel model, String qualifiedName) {
    return model.classInvariants(true).stream()
        .filter(candidate -> candidate.qualifiedName().equals(qualifiedName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no invariant " + qualifiedName));
  }

  /** The same hand-built Library encoding {@code FragmentCheckerTest} already uses. */
  private static TranslationContext libraryContext(SmtScript script) {
    ObjectSlots users =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("User", 1, 2))).get("User");
    ObjectSlots copies =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Copy", 1, 2))).get("Copy");
    ObjectSlots books =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 1, 2))).get("Book");

    AttributeDomain nameDomain =
        new AttributeDomain("User", "name", null, List.of("Ada", "Bob"), null, null);
    AttributeDomain addressDomain =
        new AttributeDomain("User", "address", null, List.of("NY", "CA"), null, null);
    AttributeDomain sigDomain =
        new AttributeDomain("Copy", "signature", null, List.of("DBS42", "NW21"), null, null);
    AttributeDomain returnsDomain =
        new AttributeDomain(
            "Copy", "numReturns", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(2));
    AttributeDomain titleDomain =
        new AttributeDomain(
            "Book", "title", null, List.of("DBforDummies", "IntrotoAI"), null, null);
    AttributeDomain authDomain =
        new AttributeDomain("Book", "auth", null, List.of("Ada", "Cyd"), null, null);
    AttributeDomain yearDomain =
        new AttributeDomain(
            "Book", "year", null, List.of(), new BigDecimal("1455"), new BigDecimal("2020"));

    Map<String, AttributeValues> values =
        Map.of(
            "User.name",
                AttributeEncoder.encode(script, users, "name", AttributeType.STRING, nameDomain),
            "User.address",
                AttributeEncoder.encode(
                    script, users, "address", AttributeType.STRING, addressDomain),
            "Copy.signature",
                AttributeEncoder.encode(
                    script, copies, "signature", AttributeType.STRING, sigDomain),
            "Copy.numReturns",
                AttributeEncoder.encode(
                    script, copies, "numReturns", AttributeType.INTEGER, returnsDomain),
            "Book.title",
                AttributeEncoder.encode(script, books, "title", AttributeType.STRING, titleDomain),
            "Book.auth",
                AttributeEncoder.encode(script, books, "auth", AttributeType.STRING, authDomain),
            "Book.year",
                AttributeEncoder.encode(script, books, "year", AttributeType.INTEGER, yearDomain));

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

    return new TranslationContext(
        Map.of(),
        values,
        Map.of(
            "User.name", nameDomain,
            "User.address", addressDomain,
            "Copy.signature", sigDomain,
            "Copy.numReturns", returnsDomain,
            "Book.title", titleDomain,
            "Book.auth", authDomain,
            "Book.year", yearDomain),
        Map.of("User", users, "Copy", copies, "Book", books),
        Map.of("Borrows", borrows, "BelongsTo", belongsTo));
  }

  private static SolverResult solve(SmtScript script) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
  }

  private static MModel compileLibrary() throws Exception {
    Path file = Path.of("../benchmark/examples/Library/Library.use");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/Library/Library.use");
    }
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(
            Files.readString(file), "Library", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("Library did not compile");
    }
    return model;
  }
}
