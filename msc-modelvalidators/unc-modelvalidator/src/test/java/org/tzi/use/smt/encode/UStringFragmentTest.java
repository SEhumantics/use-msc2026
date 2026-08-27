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
import org.tzi.use.uncertainty.datatypes.UString;

/**
 * The load-bearing claim of the {@code UString} slice, which is the same claim the {@code UBoolean}
 * slice had to make: the source's rule is NONLINEAR, this project pins {@code QF_LIRA}, and the
 * emitted script stays inside it anyway.
 *
 * <p>{@code archive2/robust_utype_model_finding_proposal.md} fixes UString-to-UString equality as
 * "let {@code b = c_s * c_r}; the evaluator returns {@code b} when the representative spellings
 * match and {@code 1 - b} otherwise" -- a PRODUCT of two confidences. Emitting that as a symbolic
 * multiplication of two solver-chosen reals would be outside {@code QF_LIRA}, and widening the
 * logic to make it fit would change solver behaviour across the whole project. The proposal's own
 * mitigation is used instead ("in the default bounded encoding, configured spellings become a
 * finite Z3 enumeration"), and the confidences are finite configured choices for exactly the reason
 * {@code UBoolean}'s are, so every product is computed at TRANSLATION time by USE's own {@code
 * UString} and the solver is only ever asked WHICH spelling and WHICH confidence the snapshot
 * carries.
 *
 * <p><b>The structural scan is the only authority.</b> Z3 does not enforce the declared logic --
 * {@code (set-logic QF_LIRA) (declare-const p Real) (assert (<= (* p p) 1.0)) (assert (>= p 0.5))
 * (check-sat)} answers {@code sat} against the pinned binary -- so a passing solver run proves
 * nothing about linearity. {@link #theProductOfTwoConfidencesEmitsNoArithmeticOperatorAtAll} scans
 * the ENTIRE emitted script, declarations and guards included, and it alone establishes
 * conformance.
 */
public class UStringFragmentTest {

  /** Any SMT arithmetic operator, as a whole token inside an s-expression head position. */
  private static final Pattern ARITHMETIC =
      Pattern.compile("\\((\\*|\\+|-|/|div|mod|abs|to_real|to_int)\\s");

  @Test
  public void theProductOfTwoConfidencesEmitsNoArithmeticOperatorAtAll() throws Exception {
    SmtScript script = new SmtScript("QF_LIRA");
    Emitted emitted = emit(script, "self.id = self.witness", 0.55, twoChoices());

    assertFalse(
        "the c_s * c_r rule must not reach the solver as arithmetic; emitted: "
            + emitted.invariantTerm(),
        ARITHMETIC.matcher(emitted.invariantTerm()).find());
    Matcher inScript = ARITHMETIC.matcher(emitted.wholeScript());
    assertFalse(
        "no arithmetic operator may appear ANYWHERE in a UString script -- declarations, the"
            + " spelling-index guard and the [0,1] confidence guard included; emitted:\n"
            + emitted.wholeScript(),
        inScript.find());
    assertTrue(
        "the emitted term must be built from equalities between a spelling/confidence symbol and a"
            + " literal, and nothing else: "
            + emitted.invariantTerm(),
        emitted.invariantTerm().matches("[-()=>\\s0-9.a-zA-Z_]*"));
    assertTrue(
        "both slots' spelling AND confidence symbols must actually be enumerated, or this test is"
            + " measuring an unexercised path: "
            + emitted.invariantTerm(),
        emitted.invariantTerm().contains("Camera_0_id_value")
            && emitted.invariantTerm().contains("Camera_0_id_confidence")
            && emitted.invariantTerm().contains("Camera_0_witness_value")
            && emitted.invariantTerm().contains("Camera_0_witness_confidence"));
  }

  /** The empirical half: the complete script is well-sorted and genuinely answerable. */
  @Test
  public void thePinnedSolverAcceptsTheWholeUStringScriptUnderALiteralQfLiraLogic()
      throws Exception {
    SmtScript script = new SmtScript("QF_LIRA");
    Emitted emitted = emit(script, "self.id = self.witness", 0.55, twoChoices());

    assertTrue(emitted.wholeScript().contains("(set-logic QF_LIRA)"));
    SolverResult result =
        new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
            .run(emitted.wholeScript());
    assertEquals(
        "the UString script must be well-sorted and answerable: " + result.rawOutput(),
        SolverOutcome.SAT,
        result.outcome());
  }

  /**
   * The two rules, measured against the real datatype rather than quoted from the proposal, so the
   * encoding's translation-time arithmetic has a source of truth that is not itself.
   */
  @Test
  public void theEvaluatorsOwnNumbersAreTheOnesTheProposalStates() {
    assertEquals(
        "exact-string match yields c_s",
        0.70,
        new UString("ALLY-7", 0.70).uEquals(new UString("ALLY-7", 1.0)).getC(),
        0.0);
    assertEquals(
        "exact-string mismatch yields 1 - c_s, computed by USE and NOT rounded here",
        1.0 - 0.70,
        new UString("ALLY-7", 0.70).uEquals(new UString("ALLY-8", 1.0)).getC(),
        0.0);
    assertEquals(
        "two UStrings, matching spellings: b = c_s * c_r",
        0.70 * 0.85,
        new UString("ALLY-7", 0.70).uEquals(new UString("ALLY-7", 0.85)).getC(),
        0.0);
    assertEquals(
        "two UStrings, differing spellings: 1 - b",
        1.0 - 0.70 * 0.85,
        new UString("ALLY-7", 0.70).uEquals(new UString("ALLY-8", 0.85)).getC(),
        0.0);
  }

  /**
   * {@code <>} is supported because it is DERIVED, not because it is convenient: USE computes it as
   * {@code UncertainValue.uDistinct}, which is literally {@code uEquals(other).not()}, and {@code
   * not(p) = 1 - p} is one of the four rules the proposal gives. Nothing new is assumed.
   */
  @Test
  public void inequalityIsTheComplementUseItselfComputes() throws Exception {
    assertEquals(
        1.0 - 0.70,
        new UString("ALLY-7", 0.70).uEquals(new UString("ALLY-7", 1.0)).not().getC(),
        0.0);

    SmtScript script = new SmtScript("QF_LIRA");
    Emitted emitted = emit(script, "self.id <> 'ALLY-7'", 0.25, twoChoices());
    assertFalse(ARITHMETIC.matcher(emitted.wholeScript()).find());
  }

  /**
   * A UString attribute with no finite configured spelling enumeration is precisely 7.2's excluded
   * "unrestricted strings": the proposal's default bounded encoding is defined only where
   * "configured spellings become a finite Z3 enumeration". It fails closed under that boundary --
   * which is the first genuine throw site {@link FragmentBoundary#UTYPE_UNRESTRICTED_STRING} has
   * ever had.
   */
  @Test
  public void aUStringWithNoFiniteConfiguredSpellingFailsClosedAsUnrestricted() throws Exception {
    Map<String, AttributeDomain[]> unbounded = new LinkedHashMap<>();
    unbounded.put(
        "id",
        new AttributeDomain[] {
          new AttributeDomain("Camera", "id", "value", List.of(), null, null),
          new AttributeDomain("Camera", "id", "confidence", List.of("0.7"), null, null)
        });
    unbounded.put("witness", spellingAndConfidence("witness"));

    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () -> emit(new SmtScript("QF_LIRA"), "self.id = 'ALLY-7'", 0.5, unbounded));
    assertEquals(FragmentBoundary.UTYPE_UNRESTRICTED_STRING, refusal.boundary());
    assertTrue(refusal.getMessage(), refusal.getMessage().contains("finite"));
  }

  /**
   * A string-OPERATION chain is 7.2's other excluded UString shape -- the proposal's own scope
   * table gives "regex and unrestricted string-operation chains" as what is outside version 1. USE
   * types all of these, so they really do reach the lowering and really must be refused there.
   */
  @Test
  public void unrestrictedStringOperationChainsFailClosed() throws Exception {
    for (String body :
        List.of(
            "self.id.toUpperCase() = 'ALLY-7'",
            "self.id + self.witness = 'ALLY-7'",
            "self.id.substring(1,2) = 'AL'")) {
      SmtTranslationException refusal =
          assertThrows(
              "'" + body + "' must fail closed",
              SmtTranslationException.class,
              () -> emit(new SmtScript("QF_LIRA"), body, 0.5, twoChoices()));
      assertEquals(
          "'" + body + "'", FragmentBoundary.UTYPE_UNRESTRICTED_STRING, refusal.boundary());
    }
  }

  /**
   * ORDERED comparison of two UStrings types as a UBoolean in USE, so it reaches the lowering, but
   * the proposal gives rules for equality only -- its UString scope row is "equality/inequality
   * against an exact string and between two configured UStrings using the c1c2 rule". An ordering
   * is a string operation outside that, so it fails closed under the unrestricted-string boundary.
   */
  @Test
  public void orderedUStringComparisonFailsClosed() throws Exception {
    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () -> emit(new SmtScript("QF_LIRA"), "self.id < self.witness", 0.5, twoChoices()));
    assertEquals(FragmentBoundary.UTYPE_UNRESTRICTED_STRING, refusal.boundary());
  }

  /**
   * {@code uEqualsIgnoreCase} is NOT derivable from the proposal's rules -- USE computes it as
   * {@code uToUpperCase().uEquals(uToUpperCase())}, which decides a DIFFERENT spelling relation
   * from the {@code s = r} the rules are written over, and the two genuinely disagree. It is
   * refused, and the reason it never needs a throw site of its own is recorded here rather than
   * assumed: USE registers no such OCL operation on {@code UString} at all, so the type checker
   * rejects it before any translation runs.
   */
  @Test
  public void equalsIgnoreCaseIsRefusedByTheTypeCheckerAndWouldDisagreeAnyway() {
    assertEquals(
        "case-folded equality answers 0.70 where the rules' own s = r answers 1 - 0.70",
        0.70,
        new UString("ALLY-7", 0.70).uEqualsIgnoreCase(new UString("ally-7", 1.0)).getC(),
        0.0);
    assertEquals(
        1.0 - 0.70, new UString("ALLY-7", 0.70).uEquals(new UString("ally-7", 1.0)).getC(), 0.0);

    String source =
        """
        model Camera
        class Camera
        attributes
          id : UString
        end
        constraints
        context self : Camera inv X:
          (self.id.equalsIgnoreCase('x')).toBooleanC(0.5)
        """;
    java.io.StringWriter errors = new java.io.StringWriter();
    MModel model =
        USECompiler.compileSpecification(
            source, "Camera", new PrintWriter(errors), new ModelFactory());
    assertEquals("USE registers no equalsIgnoreCase on UString", null, model);
    assertTrue(
        errors.toString(),
        errors.toString().contains("Undefined operation `UString.equalsIgnoreCase(String)'"));
  }

  /**
   * The aliasing refusal, and the measurement that justifies it -- exactly the shape the {@code
   * UBoolean} slice refused, for exactly the same reason. {@code UString.uEquals} carries a
   * reference-identity fast path, {@code double conf = (this == u) ? 1.0 : calculateConf(u)}, so
   * two reads of one slot do NOT follow the {@code c_s * c_r} rule at all.
   */
  @Test
  public void readingOneSlotTwiceFailsClosedAndTheEvaluatorShowsWhy() throws Exception {
    UString aliased = new UString("ALLY-7", 0.70);
    assertEquals(
        "USE's own uEquals returns certainty, not c * c, for two references to one value",
        1.0,
        aliased.uEquals(aliased).getC(),
        0.0);
    assertEquals(
        "the source's independent rule would say c * c",
        0.70 * 0.70,
        new UString("ALLY-7", 0.70).uEquals(new UString("ALLY-7", 0.70)).getC(),
        0.0);

    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () -> emit(new SmtScript("QF_LIRA"), "self.id = self.id", 0.6, twoChoices()));
    assertEquals(FragmentBoundary.UTYPE_CORE, refusal.boundary());
    assertTrue(refusal.getMessage(), refusal.getMessage().contains("more than once"));
  }

  /** A {@code UString} LITERAL operand stays refused, exactly as {@code visitConstUString} does. */
  @Test
  public void aUStringLiteralOperandFailsClosed() throws Exception {
    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () ->
                emit(
                    new SmtScript("QF_LIRA"),
                    "self.id = UString('ALLY-7', 0.9)",
                    0.5,
                    twoChoices()));
    assertEquals(FragmentBoundary.UTYPE_CORE, refusal.boundary());
  }

  /**
   * A bare UString attribute access outside a supported projection is refused INSIDE the U-type
   * core, not silently decoded through the crisp string-index path it shares an SMT sort with.
   *
   * <p>The shape is reachable and is not contrived: USE types {@code x <> oclUndefined(UString)} as
   * a plain {@code Boolean} (its {@code someOfThemIsUndefined} arm), so the invariant compiles
   * without any confidence projection and the attribute really does arrive in a crisp value
   * position.
   */
  @Test
  public void aBareUStringAccessFailsClosedInsideTheCore() throws Exception {
    SmtTranslationException refusal =
        assertThrows(
            SmtTranslationException.class,
            () -> emitCrisp("self.id <> oclUndefined(UString)", twoChoices()));
    assertEquals(FragmentBoundary.UTYPE_CORE, refusal.boundary());
    assertTrue(refusal.getMessage(), refusal.getMessage().contains("UString"));
  }

  /**
   * The nominal arm refuses exactly where the independent oracle refuses, and where it does NOT
   * refuse it uses the oracle's rule rather than the confidence one. {@code
   * NominalErasureEvaluator} erases {@code UString(s,c)} to the representative spelling {@code s}
   * and then compares crisply, so the nominal encoding of {@code self.id = 'ALLY-7'} must be the
   * SPELLING relation and must not mention a confidence symbol at all.
   */
  @Test
  public void theNominalArmErasesToTheSpellingAndNotToTheConfidence() throws Exception {
    Emitted nominal =
        emit(
            new SmtScript("QF_LIRA"),
            "self.id = 'ALLY-7'",
            0.95,
            twoChoices(),
            TranslationMode.NOMINAL);
    assertTrue(
        "nominal erasure must decide the spelling: " + nominal.invariantTerm(),
        nominal.invariantTerm().contains("Camera_0_id_value"));
    assertFalse(
        "nominal erasure of a UString must not consult the confidence at all: "
            + nominal.invariantTerm(),
        nominal.invariantTerm().contains("Camera_0_id_confidence"));
  }

  // ---------------------------------------------------------------------------------------------

  private record Emitted(SmtScript script, SmtTerm term) {
    String invariantTerm() {
      return term.toSmtLib();
    }

    /** Declarations, guards and the asserted invariant -- everything emitted. */
    String wholeScript() {
      return script.toSmtLib();
    }
  }

  private static AttributeDomain[] spellingAndConfidence(String attribute) {
    return new AttributeDomain[] {
      new AttributeDomain("Camera", attribute, "value", List.of("ALLY-7", "ALLY-8"), null, null),
      new AttributeDomain("Camera", attribute, "confidence", List.of("0.7", "0.85"), null, null)
    };
  }

  private static Map<String, AttributeDomain[]> twoChoices() {
    Map<String, AttributeDomain[]> domains = new LinkedHashMap<>();
    domains.put("id", spellingAndConfidence("id"));
    domains.put("witness", spellingAndConfidence("witness"));
    return domains;
  }

  private static Emitted emit(
      SmtScript script, String body, double confidence, Map<String, AttributeDomain[]> domains)
      throws Exception {
    return emit(script, body, confidence, domains, TranslationMode.UNCERTAIN);
  }

  /** A body that is already crisp Boolean, so it carries no confidence projection at all. */
  private static Emitted emitCrisp(String body, Map<String, AttributeDomain[]> domains)
      throws Exception {
    return emit(
        new SmtScript("QF_LIRA"), body, domains, TranslationMode.UNCERTAIN, compileCrisp(body));
  }

  private static Emitted emit(
      SmtScript script,
      String body,
      double confidence,
      Map<String, AttributeDomain[]> domains,
      TranslationMode mode)
      throws Exception {
    return emit(script, body, domains, mode, compile(body, confidence));
  }

  private static Emitted emit(
      SmtScript script,
      String body,
      Map<String, AttributeDomain[]> domains,
      TranslationMode mode,
      MModel model)
      throws Exception {
    Map<String, ObjectSlots> slots =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Camera", 1, 1)));
    Map<String, AttributeValues> attributes = new LinkedHashMap<>();
    Map<String, AttributeDomain> registered = new LinkedHashMap<>();
    for (Map.Entry<String, AttributeDomain[]> entry : domains.entrySet()) {
      AttributeDomain spelling = entry.getValue()[0];
      AttributeDomain conf = entry.getValue()[1];
      attributes.put(
          "Camera." + entry.getKey(),
          AttributeEncoder.encodeUString(
              script, slots.get("Camera"), entry.getKey(), spelling, conf));
      registered.put("Camera." + entry.getKey(), spelling);
      registered.put("Camera." + entry.getKey() + ".value", spelling);
      registered.put("Camera." + entry.getKey() + ".confidence", conf);
    }
    TranslationContext context =
        new TranslationContext(Map.of(), attributes, registered, slots, Map.of());
    MClassInvariant invariant = model.classInvariants(true).iterator().next();
    SmtTerm term = InvariantAssembler.classify(invariant, context, mode).value();
    script.assertThat(term);
    return new Emitted(script, term);
  }

  private static MModel compileCrisp(String body) {
    return compileSource(
        """
        model Camera
        class Camera
        attributes
          id : UString
          witness : UString
        end
        constraints
        context self : Camera inv Projected:
          %s
        """
            .formatted(body));
  }

  private static MModel compile(String body, double confidence) {
    return compileSource(
        """
        model Camera
        class Camera
        attributes
          id : UString
          witness : UString
        end
        constraints
        context self : Camera inv Projected:
          (%s).toBooleanC(%s)
        """
            .formatted(body, BigDecimal.valueOf(confidence).toPlainString()));
  }

  private static MModel compileSource(String source) {
    MModel model =
        USECompiler.compileSpecification(
            source, "Camera", new PrintWriter(System.err), new ModelFactory());
    if (model == null) {
      throw new AssertionError("generated model did not compile:\n" + source);
    }
    return model;
  }
}
