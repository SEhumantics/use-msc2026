package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * A multi-variable context invariant ({@code context e1, e2 : Employee inv ...}) is the SAME
 * implicit quantifier the single-variable case already encodes, taken over the CARTESIAN PRODUCT of
 * the polymorphic instance range -- which is how USE itself expands it, and what {@link
 * org.tzi.use.smt.verify.InvariantReEvaluator} (the independent oracle) has already done since
 * Milestone 4.5. The encoder refused it; these tests are what makes the encoder catch up.
 *
 * <p><b>Why each test here is shaped the way it is.</b> A generalisation to n variables has exactly
 * two plausible ways of being quietly wrong, and a naive "it now solves" test passes under both of
 * them:
 *
 * <ul>
 *   <li>pairing slot i with slot i only (a diagonal, not a product) -- caught by {@link
 *       #twoContextVariablesCoverOffDiagonalPairsNotJustTheDiagonal}, whose only violation lives
 *       strictly off the diagonal;
 *   <li>guarding only ONE of the n variables with its existence flag -- caught by {@link
 *       #everyContextVariableCarriesItsOwnExistenceGuard}, whose violating pair mixes an existing
 *       slot with a non-existing one in BOTH orders, so guarding only the first variable and
 *       guarding only the last are each caught.
 * </ul>
 */
public class MultiVariableContextInvariantTest {

  /**
   * The single-variable encoding is what every translation test through Milestone 4.7 exercises.
   * Generalising the quantifier must leave its emitted script BYTE-IDENTICAL, not merely
   * equisatisfiable: this golden text was captured from the pre-generalisation encoder and is
   * asserted verbatim. {@code Smt.and}/{@code Smt.or} of a singleton already collapse to the term
   * itself, so an n-ary guard built as a conjunction over one variable emits the bare existence
   * flag exactly as before -- this test is what turns that reasoning into evidence.
   */
  private static final String GOLDEN_SINGLE_VARIABLE_SCRIPT =
      """
      (set-logic QF_LIA)
      (declare-const Person_0 Int)
      (declare-const Person_0_exists Bool)
      (declare-const Person_1 Int)
      (declare-const Person_1_exists Bool)
      (declare-const Person_0_n Int)
      (declare-const Person_1_n Int)
      (declare-const def_uncertain_Person__single Bool)
      (declare-const val_uncertain_Person__single Bool)
      (assert (>= (+ (ite Person_0_exists 1 0) (ite Person_1_exists 1 0)) 0))
      (assert (<= (+ (ite Person_0_exists 1 0) (ite Person_1_exists 1 0)) 2))
      (assert (=> Person_1_exists Person_0_exists))
      (assert (=> Person_0_exists (and (>= Person_0_n 0) (<= Person_0_n 3))))
      (assert (=> Person_1_exists (and (>= Person_1_n 0) (<= Person_1_n 3))))
      (assert (= def_uncertain_Person__single (or (or (and Person_0_exists (and true true) (not (> Person_0_n 0))) (and Person_1_exists (and true true) (not (> Person_1_n 0)))) (and (=> Person_0_exists (and true true)) (=> Person_1_exists (and true true))))))
      (assert (= val_uncertain_Person__single (and (=> Person_0_exists (> Person_0_n 0)) (=> Person_1_exists (> Person_1_n 0)))))
      (check-sat)
      (get-model)\
      """;

  @Test
  public void theSingleContextVariableEmissionIsByteIdenticalToThePreGeneralisationEncoder() {
    MModel model =
        compile(
            """
            model Golden
            class Person
            attributes
              n : Integer
            end
            constraints
            context p : Person inv single:
              p.n > 0
            """);
    SmtScript script = new SmtScript("QF_LIA");
    TranslationContext context = personContext(script, 0, 2, null);
    InvariantAssembler.reify(
        script, invariant(model, "single"), context, TranslationMode.UNCERTAIN);

    assertEquals(GOLDEN_SINGLE_VARIABLE_SCRIPT, script.toSmtLib());
  }

  /**
   * Two existing objects that share the one available {@code n} value violate {@code p1 <> p2
   * implies p1.n <> p2.n} -- but ONLY at the pairs (0,1) and (1,0). Every diagonal pair satisfies
   * the body vacuously, so an encoder that quantified one variable and reused its slot for the
   * other would report the invariant TRUE here. The invariant must be definitely FALSE.
   */
  @Test
  public void twoContextVariablesCoverOffDiagonalPairsNotJustTheDiagonal() {
    MModel model = compile(pairwiseModel());

    Setup mustNotHold = setUp(model, "pairwise", 2, 2, List.of("5"));
    mustNotHold.script().assertThat(mustNotHold.classification().trueTerm());
    assertEquals(
        "the only violating pairs are off the diagonal, so a diagonal-only quantifier would call"
            + " this invariant true",
        SolverOutcome.UNSAT,
        solve(mustNotHold.script()));

    Setup isDefinitelyFalse = setUp(model, "pairwise", 2, 2, List.of("5"));
    isDefinitelyFalse.script().assertThat(isDefinitelyFalse.classification().falseTerm());
    assertEquals(SolverOutcome.SAT, solve(isDefinitelyFalse.script()));
  }

  /**
   * Only slot 0 exists. Both violating pairs, (0,1) and (1,0), touch the non-existing slot 1, so
   * the invariant is vacuously TRUE. Guarding only the first context variable leaves (0,1) firing;
   * guarding only the last leaves (1,0) firing; either way this test fails.
   *
   * <p>The body is deliberately {@code p1 <> p2 implies false} and not the attribute comparison the
   * other tests use. A non-existing slot's attribute carries NO domain constraint -- the encoder
   * emits the domain as {@code (=> exists domain)} -- so with an attribute body a wrongly-guarded
   * pair could still be satisfied by handing the dead slot a convenient value, and only half of
   * this test would bite. With a structural body the dead pair's violation is unconditional, so
   * both halves do. The third assertion keeps the other two honest: with BOTH slots alive the very
   * same invariant IS decisively false, so what the first two read is the existence guard and not a
   * body that can never fire.
   */
  @Test
  public void everyContextVariableCarriesItsOwnExistenceGuard() {
    MModel model = compile(pairwiseModel());

    Setup holds = setUp(model, "structural", 1, 2, List.of("5"));
    holds.script().assertThat(Smt.not(Smt.sym("Person_1_exists")));
    holds.script().assertThat(holds.classification().trueTerm());
    assertEquals(
        "a pair touching a non-existing slot must not be able to falsify the invariant",
        SolverOutcome.SAT,
        solve(holds.script()));

    Setup cannotBeFalse = setUp(model, "structural", 1, 2, List.of("5"));
    cannotBeFalse.script().assertThat(Smt.not(Smt.sym("Person_1_exists")));
    cannotBeFalse.script().assertThat(cannotBeFalse.classification().falseTerm());
    assertEquals(SolverOutcome.UNSAT, solve(cannotBeFalse.script()));

    Setup bothAlive = setUp(model, "structural", 2, 2, List.of("5"));
    bothAlive.script().assertThat(bothAlive.classification().falseTerm());
    assertEquals(SolverOutcome.SAT, solve(bothAlive.script()));
  }

  /**
   * Three context variables are three nested quantifiers, not two plus a repeat. The body is
   * violated only by a triple of three PAIRWISE DISTINCT slots, so it is FALSE with three existing
   * objects and TRUE with only two -- a distinction no two-variable product can make.
   */
  @Test
  public void threeContextVariablesNestToTheFullProduct() {
    MModel model =
        compile(
            """
            model Triple
            class Person
            attributes
              n : Integer
            end
            constraints
            context a, b, c : Person inv triple:
              (a <> b and b <> c and a <> c) implies false
            """);

    Setup withThree = setUp(model, "triple", 3, 3, List.of("5"));
    withThree.script().assertThat(withThree.classification().trueTerm());
    assertEquals(
        "three distinct existing slots form a violating triple",
        SolverOutcome.UNSAT,
        solve(withThree.script()));

    Setup withTwo = setUp(model, "triple", 2, 3, List.of("5"));
    withTwo.script().assertThat(Smt.not(Smt.sym("Person_2_exists")));
    withTwo.script().assertThat(withTwo.classification().trueTerm());
    assertEquals(
        "no triple of pairwise-distinct EXISTING slots exists, so the invariant holds",
        SolverOutcome.SAT,
        solve(withTwo.script()));
  }

  /**
   * The existential dual: {@code existential inv} over two context variables needs ONE existing,
   * off-diagonal pair satisfying the body, and USE expands it over the same product.
   */
  @Test
  public void anExistentialInvariantOverTwoContextVariablesNeedsOneOffDiagonalWitness() {
    MModel model =
        compile(
            """
            model Ex
            class Person
            attributes
              n : Integer
            end
            constraints
            context p1, p2 : Person existential inv differ:
              p1 <> p2 and p1.n <> p2.n
            """);

    Setup pair = setUp(model, "differ", 2, 2, List.of("5", "7"));
    pair.script().assertThat(pair.classification().trueTerm());
    assertEquals(SolverOutcome.SAT, solve(pair.script()));

    Setup lone = setUp(model, "differ", 1, 2, List.of("5", "7"));
    lone.script().assertThat(Smt.not(Smt.sym("Person_1_exists")));
    lone.script().assertThat(lone.classification().trueTerm());
    assertEquals(
        "a single existing object cannot form a distinct pair, so the existential is false",
        SolverOutcome.UNSAT,
        solve(lone.script()));
  }

  /**
   * The corpus shape itself, verbatim from {@code examples/NQueens/NQueens.use}: the refusal that
   * named the context variables must be gone, in BOTH translation modes.
   */
  @Test
  public void theCorpusPairwiseDistinctnessShapeIsNoLongerRefused() {
    MModel model =
        compile(
            """
            model NQueensShape
            class Row
            attributes
              idx : Integer
            end
            constraints
            context r1,r2:Row inv distinctRowIdx:
              r1<>r2 implies r1.idx<>r2.idx
            """);
    for (TranslationMode mode : TranslationMode.values()) {
      SmtScript script = new SmtScript("QF_LIA");
      ObjectSlots slots =
          ObjectSlotEncoder.encode(script, List.of(new ClassScope("Row", 2, 2))).get("Row");
      AttributeDomain domain =
          new AttributeDomain(
              "Row", "idx", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(1));
      AttributeValues values =
          AttributeEncoder.encode(script, slots, "idx", AttributeType.INTEGER, domain);
      TranslationContext context =
          new TranslationContext(
              Map.of(),
              Map.of("Row.idx", values),
              Map.of("Row.idx", domain),
              Map.of("Row", slots),
              Map.of());
      InvariantClassification distinct =
          InvariantAssembler.reify(script, invariant(model, "distinctRowIdx"), context, mode);
      assertTrue(script.declaredNames().contains(distinct.definedName()));
      script.assertThat(distinct.trueTerm());
      assertEquals(mode.toString(), SolverOutcome.SAT, solve(script));
    }
  }

  /** One built script plus the invariant reified into it, so a test can assert its own goal. */
  private record Setup(SmtScript script, InvariantClassification classification) {}

  private static Setup setUp(
      MModel model, String invariantName, int min, int capacity, List<String> values) {
    SmtScript script = new SmtScript("QF_LIA");
    TranslationContext context = personContext(script, min, capacity, values);
    return new Setup(
        script,
        InvariantAssembler.reify(
            script, invariant(model, invariantName), context, TranslationMode.UNCERTAIN));
  }

  private static String pairwiseModel() {
    return """
    model Pairwise
    class Person
    attributes
      n : Integer
    end
    constraints
    context p1, p2 : Person inv pairwise:
      p1 <> p2 implies p1.n <> p2.n
    context p1, p2 : Person inv structural:
      p1 <> p2 implies false
    """;
  }

  /**
   * {@code capacity} object slots for {@code Person}, at least {@code min} of them existing, and
   * {@code n} drawn either from an explicit value list or from the range 0..3.
   */
  private static TranslationContext personContext(
      SmtScript script, int min, int capacity, List<String> values) {
    ObjectSlots slots =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Person", min, capacity)))
            .get("Person");
    AttributeDomain domain =
        values == null
            ? new AttributeDomain(
                "Person", "n", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(3))
            : new AttributeDomain("Person", "n", null, values, null, null);
    AttributeValues attributeValues =
        AttributeEncoder.encode(script, slots, "n", AttributeType.INTEGER, domain);
    return new TranslationContext(
        Map.of(),
        Map.of("Person.n", attributeValues),
        Map.of("Person.n", domain),
        Map.of("Person", slots),
        Map.of());
  }

  private static SolverOutcome solve(SmtScript script) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
        .run(script.toSmtLib())
        .outcome();
  }

  private static MClassInvariant invariant(MModel model, String name) {
    return model.classInvariants(true).stream()
        .filter(candidate -> candidate.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no invariant " + name));
  }

  private static MModel compile(String source) {
    MModel model =
        USECompiler.compileSpecification(
            source, "test", new PrintWriter(System.err), new ModelFactory());
    if (model == null) {
      throw new AssertionError("model did not compile");
    }
    return model;
  }
}
