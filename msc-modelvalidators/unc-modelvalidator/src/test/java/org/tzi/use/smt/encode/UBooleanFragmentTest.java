package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.smt.solver.SolverResult;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * The load-bearing claim of the {@code UBoolean} slice: the source's rules are NONLINEAR, this
 * project pins {@code QF_LIRA}, and the emitted script stays inside it anyway.
 *
 * <p>Three of the four UBoolean rules multiply two probabilities ({@code p1 and p2 = p1*p2}, {@code
 * or} and {@code implies} expand to products too). Emitting a symbolic product of two solver-chosen
 * variables would be outside {@code QF_LIRA}, and quietly widening the logic to make a test pass
 * would change solver behaviour across the WHOLE project. The proposal's own mitigation is used
 * instead -- "Version 1 makes base confidence values constants or finite configured choices to
 * control nonlinear search" -- and this test is what holds the encoding to it, from two independent
 * directions: STRUCTURALLY, that no arithmetic operator is emitted at all, and EMPIRICALLY, that
 * the pinned solver accepts the complete script under a literal {@code (set-logic QF_LIRA)}.
 *
 * <p>The rest of the file is the fail-closed half. Every shape that would need a product of two
 * solver variables, and every UBoolean operation the source's rule set does not define, is refused
 * with an accurate {@link FragmentBoundary} rather than approximated.
 */
public class UBooleanFragmentTest {

  /** Any SMT arithmetic operator, as a whole token inside an s-expression head position. */
  private static final Pattern ARITHMETIC =
      Pattern.compile("\\((\\*|\\+|-|/|div|mod|abs|to_real|to_int)\\s");

  @Test
  public void theComposedProductRuleEmitsNoArithmeticOperatorAtAll() throws Exception {
    SmtScript script = new SmtScript("QF_LIRA");
    Emitted emitted = emit(script, "self.hitsTarget and self.isClear", 0.81, twoChoices());

    assertFalse(
        "a product rule must not reach the solver as arithmetic; emitted: "
            + emitted.invariantTerm(),
        ARITHMETIC.matcher(emitted.invariantTerm()).find());
    // Scanned over the WHOLE script, not just the invariant term. That distinction was found by
    // adversarial mutation and matters: injecting a genuinely nonlinear DOMAIN GUARD -- (<= (* p p)
    // 1.0) in place of (<= p 1.0) -- left the invariant term untouched and every test green,
    // because the pinned solver does not police the declared logic (see
    // thePinnedSolverAcceptsTheWholeComposedScriptUnderALiteralQfLiraLogic). This assertion is
    // therefore the ONLY thing standing between a nonlinear term and the emitted script.
    Matcher inScript = ARITHMETIC.matcher(emitted.wholeScript());
    assertFalse(
        "no arithmetic operator may appear ANYWHERE in a UBoolean script -- declarations, domain"
            + " guards and the [0,1] type guard included; emitted:\n"
            + emitted.wholeScript(),
        inScript.find());
    assertTrue(
        "the emitted term must be built from equalities between a probability symbol and a"
            + " rational literal, and nothing else: "
            + emitted.invariantTerm(),
        emitted.invariantTerm().matches("[-()=>\\s0-9.a-zA-Z_]*"));
    assertTrue(
        "the four configured combinations must actually be enumerated, or this test is measuring"
            + " an unexercised path: "
            + emitted.invariantTerm(),
        emitted.invariantTerm().contains("Detection_0_hitsTarget_probability")
            && emitted.invariantTerm().contains("Detection_0_isClear_probability"));
  }

  /**
   * The empirical half: the pinned solver answers the COMPLETE script -- declarations, domain
   * guards, {@code [0,1]} type guard and the composed invariant -- under a literal {@code
   * (set-logic QF_LIRA)}, and answers it {@code SAT} rather than rejecting it as ill-sorted.
   *
   * <p><b>What this does NOT prove, measured rather than assumed.</b> It does not prove the script
   * is inside {@code QF_LIRA}. Z3 does not enforce the declared logic: asked the three-line script
   * {@code (set-logic QF_LIRA) (declare-const p Real) (assert (<= (* p p) 1.0)) (assert (>= p 0.5))
   * (check-sat)}, the pinned Z3 5.1.0 answers {@code sat}, not an error. Conformance to the pinned
   * logic is therefore established by {@link #theComposedProductRuleEmitsNoArithmeticOperatorAtAll}
   * scanning the emitted text, and by that alone; this test's job is the narrower one of showing
   * the script is well-sorted and genuinely answerable.
   */
  @Test
  public void thePinnedSolverAcceptsTheWholeComposedScriptUnderALiteralQfLiraLogic()
      throws Exception {
    SmtScript script = new SmtScript("QF_LIRA");
    Emitted emitted = emit(script, "self.hitsTarget and self.isClear", 0.81, twoChoices());

    assertTrue(emitted.wholeScript().contains("(set-logic QF_LIRA)"));
    SolverResult result =
        new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
            .run(emitted.wholeScript());
    assertEquals(
        "the composed UBoolean script must be well-sorted and answerable: " + result.rawOutput(),
        SolverOutcome.SAT,
        result.outcome());
  }

  /**
   * The refusal that keeps the mitigation honest. A UBoolean whose probability is derived from an
   * uncertain numeric COMPARISON is not a finite configured choice -- it is a normal-CDF function
   * of a solver-chosen representative -- so composing it with another probability really would need
   * a product of two solver variables. That must fail closed, not be approximated.
   */
  @Test
  public void composingWithAComparisonDerivedProbabilityFailsClosedAsNonlinear() throws Exception {
    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () ->
                emit(
                    new SmtScript("QF_LIRA"),
                    "(self.speed > 0.30) and self.hitsTarget",
                    0.5,
                    twoChoices()));
    assertEquals(FragmentBoundary.UTYPE_NONLINEAR_OR_TRANSCENDENTAL, refusal.boundary());
  }

  @Test
  public void xorAndEquivalentFailClosedBecauseTheSourceDoesNotDefineThem() throws Exception {
    // USE spells xor infix and equivalent as a method call; both are exercised as written.
    for (String body :
        List.of("self.hitsTarget xor self.isClear", "self.hitsTarget.equivalent(self.isClear)")) {
      SmtTranslationException refusal =
          assertThrows(
              "UBoolean '" + body + "' must fail closed",
              SmtTranslationException.class,
              () -> emit(new SmtScript("QF_LIRA"), body, 0.5, twoChoices()));
      assertEquals(FragmentBoundary.UTYPE_CORE, refusal.boundary());
      assertTrue(refusal.getMessage(), refusal.getMessage().contains("abs(p1 - p2)"));
    }
  }

  /**
   * The aliasing refusal, and the measurement that justifies it. USE's {@code UBoolean.and} has a
   * reference-identity fast path returning {@code p} rather than {@code p*p}, so two reads of one
   * attribute genuinely do not follow the source's product rule. The divergence is exhibited here
   * against the real datatype, so the refusal is evidence-backed rather than defensive.
   */
  @Test
  public void readingOneSlotTwiceFailsClosedAndTheEvaluatorShowsWhy() throws Exception {
    org.tzi.use.uncertainty.datatypes.UBoolean aliased =
        new org.tzi.use.uncertainty.datatypes.UBoolean(true, 0.5);
    assertEquals(
        "USE's own and() returns p, not p*p, for two references to one value",
        0.5,
        aliased.and(aliased).getC(),
        0.0);
    assertEquals(
        "the source's independent rule would say 0.25",
        0.25,
        new org.tzi.use.uncertainty.datatypes.UBoolean(true, 0.5)
            .and(new org.tzi.use.uncertainty.datatypes.UBoolean(true, 0.5))
            .getC(),
        0.0);

    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () ->
                emit(
                    new SmtScript("QF_LIRA"),
                    "self.hitsTarget and self.hitsTarget",
                    0.26,
                    twoChoices()));
    assertEquals(FragmentBoundary.UTYPE_CORE, refusal.boundary());
    assertTrue(refusal.getMessage(), refusal.getMessage().contains("more than once"));
  }

  /**
   * A confidence projection NESTED inside a UBoolean composition is refused. USE happily types
   * {@code <Boolean> and <UBoolean>} as a UBoolean -- it lifts the crisp operand to probability 0
   * or 1 -- so this really does reach the lowering, and lowering it would mean encoding the inner
   * threshold's own truth value as a probability. The rule set defines no such lifting, so it fails
   * closed rather than being invented here.
   */
  @Test
  public void aProjectionNestedInsideACompositionFailsClosed() throws Exception {
    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () ->
                emit(
                    new SmtScript("QF_LIRA"),
                    "self.hitsTarget.toBooleanC(0.5) and self.probe",
                    0.5,
                    twoChoices()));
    assertEquals(FragmentBoundary.UTYPE_CORE, refusal.boundary());
    assertTrue(
        refusal.getMessage(), refusal.getMessage().contains("UBoolean operator 'toBooleanC'"));
  }

  /** A confidence USE itself makes undefined is refused rather than encoded as anything. */
  @Test
  public void aConfidenceOutsideTheUnitIntervalFailsClosed() throws Exception {
    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () -> emit(new SmtScript("QF_LIRA"), "self.hitsTarget", 1.5, twoChoices()));
    assertEquals(FragmentBoundary.UTYPE_CORE, refusal.boundary());
    assertTrue(refusal.getMessage(), refusal.getMessage().contains("outside [0,1]"));
  }

  /**
   * A probability domain that is not a finite configured choice is exactly the case the proposal's
   * Version 1 mitigation excludes, so it fails closed as nonlinear -- naming the reason, not just
   * the symptom.
   */
  @Test
  public void aProbabilityWithNoFiniteConfiguredChoiceFailsClosedAsNonlinear() throws Exception {
    Map<String, AttributeDomain> unbounded = new LinkedHashMap<>();
    unbounded.put(
        "hitsTarget",
        new AttributeDomain(
            "Detection", "hitsTarget", "probability", List.of(), BigDecimal.ZERO, BigDecimal.ONE));
    unbounded.put(
        "isClear",
        new AttributeDomain("Detection", "isClear", "probability", List.of("0.9"), null, null));
    unbounded.put(
        "probe",
        new AttributeDomain("Detection", "probe", "probability", List.of("0.9"), null, null));

    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () -> emit(new SmtScript("QF_LIRA"), "self.hitsTarget", 0.5, unbounded));
    assertEquals(FragmentBoundary.UTYPE_NONLINEAR_OR_TRANSCENDENTAL, refusal.boundary());
    assertTrue(
        refusal.getMessage(), refusal.getMessage().contains("no finite configured probability"));
  }

  /**
   * The nominal arm refuses precisely where the independent oracle refuses. {@code
   * NominalErasureEvaluator}'s only UBoolean rule is "a STORED UBoolean probability uses {@code p
   * >= 0.5}"; it throws for a composed UBoolean expression. If the SMT nominal arm encoded one
   * anyway, a FRAGILE verdict could rest on a nominal reading nothing can independently confirm.
   */
  @Test
  public void theNominalArmRefusesExactlyWhereTheIndependentOracleRefuses() throws Exception {
    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () ->
                emit(
                    new SmtScript("QF_LIRA"),
                    "self.hitsTarget and self.isClear",
                    0.81,
                    twoChoices(),
                    TranslationMode.NOMINAL));
    assertEquals(FragmentBoundary.UTYPE_CORE, refusal.boundary());
    assertTrue(refusal.getMessage(), refusal.getMessage().contains("p >= 0.5"));

    // A STORED access is the one shape the oracle does define, so it must translate -- and at the
    // p >= 0.5 line, NOT at the invariant's own confidence.
    Emitted nominal =
        emit(
            new SmtScript("QF_LIRA"),
            "self.hitsTarget",
            0.95,
            twoChoices(),
            TranslationMode.NOMINAL);
    assertTrue(
        "nominal erasure must admit the 0.9 candidate and refuse the 0.2 one, which is the p >= 0.5"
            + " line and not the 0.95 the invariant asks for: "
            + nominal.invariantTerm(),
        nominal.invariantTerm().contains("0.9") && !nominal.invariantTerm().contains("0.2"));
  }

  /** The expansion cap fails closed rather than emitting a script whose size is a surprise. */
  @Test
  public void anExpansionBeyondTheCapFailsClosed() throws Exception {
    List<String> many = new java.util.ArrayList<>();
    for (int i = 0; i < 20; i++) {
      many.add(BigDecimal.valueOf(i).divide(BigDecimal.valueOf(100)).toPlainString());
    }
    Map<String, AttributeDomain> wide = new LinkedHashMap<>();
    for (String attribute : List.of("hitsTarget", "isClear", "probe")) {
      wide.put(
          attribute, new AttributeDomain("Detection", attribute, "probability", many, null, null));
    }
    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () ->
                emit(
                    new SmtScript("QF_LIRA"),
                    "(self.hitsTarget and self.isClear) and self.probe",
                    0.5,
                    wide));
    assertEquals(FragmentBoundary.UTYPE_NONLINEAR_OR_TRANSCENDENTAL, refusal.boundary());
    assertTrue(refusal.getMessage(), refusal.getMessage().contains("expansion cap"));
  }

  // ---------------------------------------------------------------------------------------------

  private record Emitted(SmtScript script, SmtTerm term) {
    String invariantTerm() {
      return term.toSmtLib();
    }

    /** Declarations, domain guards, type guard and the asserted invariant -- everything emitted. */
    String wholeScript() {
      return script.toSmtLib();
    }
  }

  private static Map<String, AttributeDomain> twoChoices() {
    Map<String, AttributeDomain> domains = new LinkedHashMap<>();
    for (String attribute : List.of("hitsTarget", "isClear", "probe")) {
      domains.put(
          attribute,
          new AttributeDomain(
              "Detection", attribute, "probability", List.of("0.2", "0.9"), null, null));
    }
    return domains;
  }

  private static Emitted emit(
      SmtScript script, String body, double confidence, Map<String, AttributeDomain> domains)
      throws Exception {
    return emit(script, body, confidence, domains, TranslationMode.UNCERTAIN);
  }

  private static Emitted emit(
      SmtScript script,
      String body,
      double confidence,
      Map<String, AttributeDomain> domains,
      TranslationMode mode)
      throws Exception {
    MModel model = compile(body, confidence);
    Map<String, ObjectSlots> slots =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Detection", 1, 1)));
    Map<String, AttributeValues> attributes = new LinkedHashMap<>();
    Map<String, AttributeDomain> registered = new LinkedHashMap<>();
    for (Map.Entry<String, AttributeDomain> entry : domains.entrySet()) {
      attributes.put(
          "Detection." + entry.getKey(),
          AttributeEncoder.encodeUBoolean(
              script, slots.get("Detection"), entry.getKey(), entry.getValue()));
      registered.put("Detection." + entry.getKey(), entry.getValue());
      registered.put("Detection." + entry.getKey() + ".probability", entry.getValue());
    }
    AttributeDomain speed =
        new AttributeDomain("Detection", "speed", "value", List.of("0.31"), null, null);
    AttributeDomain sigma =
        new AttributeDomain("Detection", "speed", "uncertainty", List.of("0.02"), null, null);
    attributes.put(
        "Detection.speed",
        AttributeEncoder.encodeUType(
            script, slots.get("Detection"), "speed", AttributeType.UREAL, speed, sigma));
    registered.put("Detection.speed", speed);
    registered.put("Detection.speed.value", speed);
    registered.put("Detection.speed.uncertainty", sigma);

    TranslationContext context =
        new TranslationContext(Map.of(), attributes, registered, slots, Map.of());
    MClassInvariant invariant = model.classInvariants(true).iterator().next();
    SmtTerm term = InvariantAssembler.classify(invariant, context, mode).value();
    script.assertThat(term);
    return new Emitted(script, term);
  }

  private static MModel compile(String body, double confidence) {
    String source =
        """
        model Detection
        class Detection
        attributes
          hitsTarget : UBoolean
          isClear : UBoolean
          probe : UBoolean
          speed : UReal
        end
        constraints
        context self : Detection inv Projected:
          (%s).toBooleanC(%s)
        """
            .formatted(body, BigDecimal.valueOf(confidence).toPlainString());
    MModel model =
        USECompiler.compileSpecification(
            source, "Detection", new PrintWriter(System.err), new ModelFactory());
    if (model == null) {
      throw new AssertionError("generated model did not compile:\n" + source);
    }
    return model;
  }
}
