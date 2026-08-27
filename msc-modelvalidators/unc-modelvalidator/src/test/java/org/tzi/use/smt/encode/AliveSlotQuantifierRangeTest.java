package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
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
 * The proposal's proof obligation "bounded quantifiers ranging over exactly the alive object
 * slots", made executable.
 *
 * <p>Bounded model finding declares a FIXED number of candidate object slots and lets the solver
 * decide which of them exist. An invariant's implicit context quantifier must therefore range over
 * the alive ones and ONLY the alive ones: a dead slot that could falsify an invariant would make a
 * satisfiable model look unsatisfiable, and a dead slot silently exempted from a quantifier that
 * should have covered it would deliver a witness that is not one.
 *
 * <p>Each check is a Z3 ENTAILMENT rather than a single satisfying assignment: the negation of the
 * required property is asserted alongside the pinned symbols, and UNSAT is the proof. Asking merely
 * for SAT would only show the property CAN hold, not that it must.
 */
public class AliveSlotQuantifierRangeTest {

  private static final String MODEL =
      """
      model Alive
      class A
      attributes
        n : Integer
      end
      constraints
      context self : A inv Positive: self.n > 0
      context self : A existential inv SomePositive: self.n > 0
      """;

  /**
   * With every slot dead, the universal invariant is vacuously TRUE and the existential one is
   * FALSE -- OCL's own empty-range rules, and the sharpest statement that dead slots contribute
   * nothing at all.
   */
  @Test
  public void anEmptyAliveRangeIsVacuouslyTrueUniversallyAndFalseExistentially() {
    Fixture fixture = fixture();

    assertEquals(
        "a universal invariant over an empty alive range must be TRUE",
        SolverOutcome.UNSAT,
        fixture.refute(
            List.of(dead(0), dead(1)), Smt.not(fixture.classification("A::Positive").trueTerm())));

    assertEquals(
        "an existential invariant over an empty alive range must be FALSE",
        SolverOutcome.UNSAT,
        fixture.refute(
            List.of(dead(0), dead(1)),
            Smt.not(fixture.classification("A::SomePositive").falseTerm())));
  }

  /**
   * The decisive half: slot 1 carries a violating value but is NOT alive, so the invariant must
   * still be true. A quantifier that ranged over the declared capacity instead of the alive slots
   * would report a counterexample here.
   */
  @Test
  public void aDeadSlotsViolatingAttributeValueCannotFalsifyTheInvariant() {
    Fixture fixture = fixture();
    assertEquals(
        "a dead slot's value is outside the quantifier's range",
        SolverOutcome.UNSAT,
        fixture.refute(
            List.of(alive(0), value(0, 1), dead(1), value(1, -1)),
            Smt.not(fixture.classification("A::Positive").trueTerm())));
  }

  /**
   * The same violating value on the same slot, with only its existence flipped, MUST falsify the
   * invariant. Without this the previous test would also pass an encoding that ignored the
   * attribute entirely.
   */
  @Test
  public void thatSameViolatingValueDoesFalsifyTheInvariantOnceItsSlotIsAlive() {
    Fixture fixture = fixture();
    assertEquals(
        "an alive slot's violating value must make the invariant defined-FALSE",
        SolverOutcome.UNSAT,
        fixture.refute(
            List.of(alive(0), value(0, 1), alive(1), value(1, -1)),
            Smt.not(fixture.classification("A::Positive").falseTerm())));
  }

  /**
   * The polymorphic range is exactly the declared capacity of the class and its subclasses -- no
   * more slots than were configured, and no fewer.
   */
  @Test
  public void theCandidateRangeIsExactlyTheConfiguredCapacity() {
    Fixture fixture = fixture();
    assertEquals(2, PolymorphicRange.slotsOf(fixture.model.getClass("A"), fixture.context).size());
  }

  private static SmtTerm alive(int slot) {
    return Smt.sym("A_" + slot + "_exists");
  }

  private static SmtTerm dead(int slot) {
    return Smt.not(alive(slot));
  }

  private static SmtTerm value(int slot, int n) {
    return Smt.eq(Smt.sym("A_" + slot + "_n"), Smt.intLit(BigInteger.valueOf(n)));
  }

  /**
   * One encoding recipe, replayed into a FRESH script per check.
   *
   * <p>Replaying rather than reusing matters: pinning slot 1 dead in one check must not leak into
   * the next. The reified {@code def}/{@code val} symbol names are deterministic (see {@link
   * InvariantAssembler#reify}), so a classification term taken from one script names exactly the
   * same symbols in another built the same way.
   */
  private static final class Fixture {
    private final MModel model;
    private final TranslationContext context;
    private final Map<String, InvariantClassification> classifications;

    private Fixture() {
      this.model = compile();
      SmtScript script = new SmtScript("QF_LIA");
      this.context = encode(script);
      this.classifications = reifyAll(script, model, context);
    }

    InvariantClassification classification(String qualifiedName) {
      InvariantClassification classification = classifications.get(qualifiedName);
      if (classification == null) {
        throw new AssertionError("no classification for " + qualifiedName);
      }
      return classification;
    }

    /** UNSAT here is a proof that the property holds under the pinned symbols. */
    SolverOutcome refute(List<SmtTerm> pinned, SmtTerm negatedProperty) {
      SmtScript fresh = new SmtScript("QF_LIA");
      TranslationContext freshContext = encode(fresh);
      reifyAll(fresh, model, freshContext);
      pinned.forEach(fresh::assertThat);
      fresh.assertThat(negatedProperty);
      return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
          .run(fresh.toSmtLib())
          .outcome();
    }
  }

  private static Map<String, InvariantClassification> reifyAll(
      SmtScript script, MModel model, TranslationContext context) {
    Map<String, InvariantClassification> reified = new LinkedHashMap<>();
    for (MClassInvariant invariant : model.classInvariants(true)) {
      reified.put(
          invariant.qualifiedName(),
          InvariantAssembler.reify(script, invariant, context, TranslationMode.UNCERTAIN));
    }
    return reified;
  }

  private static Fixture fixture() {
    return new Fixture();
  }

  private static TranslationContext encode(SmtScript script) {
    ObjectSlots slots =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 0, 2))).get("A");
    AttributeDomain domain =
        new AttributeDomain(
            "A", "n", null, List.of(), BigDecimal.valueOf(-1), BigDecimal.valueOf(1));
    AttributeValues values =
        AttributeEncoder.encode(script, slots, "n", AttributeType.INTEGER, domain);
    return new TranslationContext(
        Map.of(), Map.of("A.n", values), Map.of("A.n", domain), Map.of("A", slots), Map.of());
  }

  private static MModel compile() {
    MModel model =
        USECompiler.compileSpecification(
            MODEL, "Alive", new PrintWriter(System.err), new ModelFactory());
    if (model == null) {
      throw new AssertionError("model did not compile");
    }
    return model;
  }
}
