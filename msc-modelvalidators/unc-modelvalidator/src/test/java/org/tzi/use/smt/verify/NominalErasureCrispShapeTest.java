package org.tzi.use.smt.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * The nominal oracle on PROVABLY CRISP bodies -- the family it used to refuse outright.
 *
 * <p>{@code NominalErasureEvaluator}'s contract is "a crisp expression erases to itself", and its
 * crispness test used to be a nine-class {@code instanceof} whitelist whose terminal answer for
 * everything else was "uncertain". A crisp {@code let}, a crisp {@code if} and a crisp collection
 * literal are all outside that whitelist, so all three were routed into the erasure dispatch --
 * which has no rule for them -- and came back as {@link NominalErasureUnsupportedException}. The
 * collection-literal message was the blunt one: "operator includes over an uncertain operand" for
 * {@code Set{1,2}->includes(1)}, in which nothing is uncertain and nothing is even attribute-fed.
 *
 * <p>That is not a cosmetic refusal. Every {@code nominal} atom -- so every D1/D2/D3 diagnostic row
 * and every {@code fragile(j)} witness -- runs through this oracle, and it is checked AFTER the
 * solve ({@code SmtModelFinder.checkedVerdicts}), so the refusal costs a completed Z3 solve as
 * well as the evidence. Each shape below is therefore driven end-to-end through {@code
 * SmtModelFinder.find()} with a real solve, and the erased verdict is independently re-verified
 * against USE's OWN evaluator run over the very same reconstructed snapshot -- which is a
 * legitimate oracle here precisely because the body is crisp, so nominal and U-aware evaluation
 * must coincide.
 *
 * <p>The last test is the guard on the other side: inverting a fail-closed default is only safe if
 * the "crisp" answer is genuinely proved, so a real U-typed {@code let} must NOT be quietly waved
 * through to the U-aware evaluator. It is erased -- through the binding -- and the erased reading
 * disagrees with the U-aware one exactly where {@code fragile} says it should.
 */
public class NominalErasureCrispShapeTest {

  /**
   * Four Boolean bodies over the same crisp Integer attribute. {@code Control} is inside the old
   * whitelist and always worked; the other three are the shapes it excluded. {@code n = 5} makes
   * all four TRUE, so a wrong answer cannot hide behind a coincidence of outcome -- only a REFUSAL
   * or a flipped verdict can distinguish them.
   */
  private static final String CRISP_MODEL =
      """
      model CrispShapes
      class C
      attributes
        n : Integer
      end
      constraints
      context self : C inv Control:
        self.n > 0
      context self : C inv CrispLet:
        let k : Integer = self.n in k > 0
      context self : C inv CrispIf:
        if self.n > 0 then true else false endif
      context self : C inv CrispLiteral:
        Set{1,2}->includes(1)
      """;

  /**
   * The U-typed {@code let} the SMT NOMINAL arm was deliberately extended to translate ({@code
   * UTypeLetTest}): the binding carries BOTH components of the paired encoding, and the erasure has
   * to carry the representative through it. 0.31 clears 0.30 crisply while the 95% confidence
   * reading does not, which is exactly a {@code fragile} witness.
   */
  private static final String UTYPE_LET_MODEL =
      """
      model UTypeLetErasure
      class C
      attributes
        speed : UReal
      end
      constraints
      context self : C inv LetThreshold:
        let u : UReal = self.speed in (u > 0.30).toBooleanC(0.95)
      """;

  @Test
  public void aCrispLetIsErasedToItselfInsteadOfRefusedAsAnExpLetShape() throws Exception {
    assertNominalAgreesWithUse("CrispLet");
  }

  @Test
  public void aCrispIfIsErasedToItselfInsteadOfRefusedAsAnExpIfShape() throws Exception {
    assertNominalAgreesWithUse("CrispIf");
  }

  /**
   * The most misleading of the three: nothing in {@code Set{1,2}->includes(1)} is uncertain, and it
   * was refused with a message that said an operand was.
   */
  @Test
  public void aCrispCollectionLiteralIsErasedToItselfInsteadOfCalledAnUncertainOperand()
      throws Exception {
    assertNominalAgreesWithUse("CrispLiteral");
  }

  /** The control: this one was never refused, and must stay exactly as it was. */
  @Test
  public void theCrispControlShapeIsUnchanged() throws Exception {
    assertNominalAgreesWithUse("Control");
  }

  /**
   * The soundness guard on the inverted default. A U-typed {@code let} is NOT crisp, so it must not
   * be handed to the U-aware evaluator; it must go through the erasure, WITH the binding erased.
   *
   * <p>The discriminator is the whole point of {@code fragile}: with mu = 0.31 and sigma = 0.02 the
   * erased reading accepts (0.31 &gt; 0.30) while USE's own uncertainty-aware reading gives p =
   * 0.6915, far below the demanded 0.95, and rejects. Both halves are checked -- the solver's
   * witness, and USE's own two readings of that very snapshot -- so an erasure that silently
   * dropped the {@code let} and answered the U-aware question would report FALSE here and fail.
   */
  @Test
  public void aUTypedLetIsErasedThroughItsBindingAndStillDisagreesWithTheUAwareReading()
      throws Exception {
    MModel model = compile(UTYPE_LET_MODEL, "UTypeLetErasure");
    String invariant = "C::LetThreshold";

    ModelFinderResult fragile =
        SmtModelFinder.find(
            model,
            new AnalysisConfiguration(
                List.of(new ClassScope("C", 1, 1)),
                List.of(),
                List.of(
                    new AttributeDomain("C", "speed", "value", List.of("0.31"), null, null),
                    new AttributeDomain("C", "speed", "uncertainty", List.of("0.02"), null, null)),
                Set.of(invariant),
                QueryParser.parse(
                    "fragile(" + invariant + ")", ConfigurationVocabulary.fromModel(model)),
                Duration.ofSeconds(30),
                1));

    assertTrue("a fragile witness exists for a U-typed let binding", fragile.satisfiable());
    assertEquals(
        "the uncertainty-aware reading rejects 0.31 at 95% confidence",
        InvariantOutcome.FALSE,
        fragile.verdicts().get(0).outcome());
    assertEquals(
        "and the erasure accepts it, reading the representative THROUGH the let binding",
        InvariantOutcome.TRUE,
        fragile.nominalVerdicts().get(0).outcome());

    MObject object =
        fragile.system().state().objectsOfClass(model.getClass("C")).iterator().next();
    URealValue speed =
        (URealValue) object.state(fragile.system().state()).attributeValue("speed");
    assertEquals(0.31, speed.value(), 0.0);
    assertEquals(0.02, speed.uncertainty(), 0.0);

    // Independent of the solver AND of this oracle: USE's own evaluator on the same snapshot.
    assertEquals(
        "USE itself reads the U-aware body as FALSE, so the two readings really do differ",
        BooleanValue.FALSE,
        invariantOf(model, invariant)
            .bodyExpression()
            .eval(context(fragile.system(), object.value())));
    assertFalse(
        "the erased reading is not merely a copy of the U-aware one",
        InvariantOutcome.FALSE == fragile.nominalVerdicts().get(0).outcome());
  }

  /**
   * A U-typed {@code let} at a value position the erasure has NO rule for still fails closed. The
   * inverted default must not turn "I cannot prove this crisp" into "it is crisp": arithmetic over
   * a U-value is exactly the case the class refuses on purpose, and it must keep refusing.
   */
  @Test
  public void anErasureRuleThatDoesNotExistStillFailsClosed() throws Exception {
    MModel model =
        compile(
            """
            model UTypeLetArithmetic
            class C
            attributes
              speed : UReal
            end
            constraints
            context self : C inv LetSum:
              let u : UReal = self.speed in (u + u > 0.30).toBooleanC(0.95)
            """,
            "UTypeLetArithmetic");

    Session session = new Session();
    session.setSystem(new MSystem(model));
    UseSystemApi api = UseSystemApi.create(session);
    MObject object = api.createObjectEx(model.getClass("C"), "c1");
    api.setAttributeValueEx(
        object, model.getClass("C").attribute("speed", true), new URealValue(0.31, 0.02));

    NominalErasureUnsupportedException refused =
        org.junit.Assert.assertThrows(
            NominalErasureUnsupportedException.class,
            () ->
                NominalErasureEvaluator.eval(
                    invariantOf(model, "C::LetSum").bodyExpression(),
                    context(session.system(), object.value())));
    assertTrue(
        refused.getMessage(),
        refused.getMessage().contains("no nominal-erasure rule for the value expression"));
  }

  // ---------------------------------------------------------------------------------------------

  /**
   * Drives one crisp invariant end-to-end through a real solve as a {@code nominal ... is true}
   * query -- which is what makes the nominal oracle run at all -- and then re-derives the same
   * verdict from USE's own evaluator over the delivered snapshot. The body is crisp, so USE's
   * ordinary reading IS the nominal reading and is an independent oracle rather than a restatement.
   */
  private static void assertNominalAgreesWithUse(String invariantName) throws Exception {
    MModel model = compile(CRISP_MODEL, "CrispShapes");
    String qualified = "C::" + invariantName;

    ModelFinderResult result =
        SmtModelFinder.find(
            model,
            new AnalysisConfiguration(
                List.of(new ClassScope("C", 1, 1)),
                List.of(),
                List.of(new AttributeDomain("C", "n", null, List.of("5"), null, null)),
                Set.of(qualified),
                QueryParser.parse(
                    "nominal " + qualified + " is true",
                    ConfigurationVocabulary.fromModel(model)),
                Duration.ofSeconds(30),
                1));

    assertTrue(qualified + " has a witness", result.satisfiable());
    assertEquals(
        "the nominal oracle reports on " + qualified + " instead of refusing it",
        InvariantOutcome.TRUE,
        result.nominalVerdicts().get(0).outcome());

    MObject object = result.system().state().objectsOfClass(model.getClass("C")).iterator().next();
    assertEquals(
        IntegerValue.valueOf(5), object.state(result.system().state()).attributeValue("n"));
    assertEquals(
        "USE's own evaluator reads the same crisp body the same way",
        BooleanValue.TRUE,
        invariantOf(model, qualified)
            .bodyExpression()
            .eval(context(result.system(), object.value())));
  }

  private static MClassInvariant invariantOf(MModel model, String qualifiedName) {
    return model.classInvariants(true).stream()
        .filter(invariant -> invariant.qualifiedName().equals(qualifiedName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no invariant " + qualifiedName));
  }

  /** The same evaluation context {@code InvariantReEvaluator} builds, with {@code self} bound. */
  private static EvalContext context(MSystem system, Value self) {
    EvalContext ctx =
        new EvalContext(system.state(), system.state(), system.varBindings(), null, "");
    ctx.pushVarBinding("self", self);
    return ctx;
  }

  private static MModel compile(String source, String name) {
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("model did not compile: " + name);
    }
    return model;
  }
}
