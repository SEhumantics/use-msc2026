package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Milestone 4.7a, Part 1. The ledger already split support per (invariant, mode); what it could not
 * say was WHICH boundary a refusal hit, because {@code reason} is free text. These tests hold every
 * refusal to a classified {@link FragmentBoundary} drawn from {@code
 * THESIS_SMT_MODEL_FINDER_PLAN.md} 7.1 (crisp tiers, fixed by corpus frequency) and 7.2 (the U-type
 * core and its named exclusions), and hold the ledger to accounting for every (invariant, mode)
 * pair a query requires BEFORE any solver call.
 */
public class FragmentLedgerBoundaryTest {

  /**
   * One battery, one invariant per boundary. Every body here is a WELL-TYPED Boolean invariant,
   * which is a real constraint on what can be tested this way and worth recording: USE rejects an
   * invariant whose static type is {@code UBoolean} (7.3), so {@code self.speed = self.other} does
   * not compile at all and the translator's bare-UReal-access refusal is unreachable from a parsed
   * model. The {@code UReal} literal comparison is the reachable U-type-core shape.
   *
   * <p>{@code BeyondFirstFragmentConditional} was originally a plain {@code if true then true
   * else false endif} -- once visitIf() started supporting matching-type branches, that exact
   * shape stopped refusing at all, so it was changed to a type-mismatched if-then-else (Integer
   * vs Real), which still hits {@code BEYOND_FIRST_FRAGMENT} via visitIf()'s own type guard,
   * preserving this fixture's role without depending on if-then-else staying wholly unsupported.
   */
  private static final String BOUNDARY_MODEL =
      """
      model Boundaries
      enum Color { red, green }
      class Sample
      attributes
        n : Integer
        speed : UReal
        other : UReal
      end
      constraints
      context self : Sample inv Tier1ForAllTwoVariables:
        Sample.allInstances()->forAll(x, y | x.n > 0)
      context self : Sample inv Tier2ExistsOneVariable:
        Sample.allInstances()->exists(x | x.n > 0)
      context self : Sample inv Tier2TypeTest:
        self.oclAsType(Sample).oclIsTypeOf(Sample)
      context self : Sample inv Tier3Enumeration:
        Color::red = Color::green
      context self : Sample inv Tier3SetLiteral:
        Set{1,2} = Set{1}
      context self : Sample inv BeyondFirstFragmentConditional:
        (if true then 1 else 1.5 endif) > 0
      context self : Sample inv UTypeCoreLiteral:
        (UReal(0.5, 0.1) > 0.30).toBooleanC(0.95)
      context self : Sample inv UTypeUncertainVersusUncertain:
        (self.speed > self.other).toBooleanC(0.95)
      """;

  /**
   * Every refusal in the battery lands on the tier or U-type exclusion its construct belongs to.
   * The expectations are read off 7.1/7.2 directly, not off the implementation.
   */
  @Test
  public void everyRefusalIsClassifiedAtItsTierOrUTypeBoundary() {
    Map<String, FragmentBoundary> expected = new LinkedHashMap<>();
    expected.put("Sample::Tier1ForAllTwoVariables", FragmentBoundary.TIER_1);
    expected.put("Sample::Tier2ExistsOneVariable", FragmentBoundary.TIER_2);
    expected.put("Sample::Tier2TypeTest", FragmentBoundary.TIER_2);
    expected.put("Sample::Tier3Enumeration", FragmentBoundary.TIER_3);
    expected.put("Sample::Tier3SetLiteral", FragmentBoundary.TIER_3);
    expected.put("Sample::BeyondFirstFragmentConditional", FragmentBoundary.BEYOND_FIRST_FRAGMENT);
    expected.put("Sample::UTypeCoreLiteral", FragmentBoundary.UTYPE_CORE);
    expected.put(
        "Sample::UTypeUncertainVersusUncertain", FragmentBoundary.UTYPE_UNCERTAIN_VERSUS_UNCERTAIN);

    FragmentCoverageLedger ledger = boundaryLedger(TranslationMode.UNCERTAIN);

    Map<String, FragmentBoundary> observed = new LinkedHashMap<>();
    for (InvariantCoverage entry : ledger.entries()) {
      assertFalse("every invariant in this battery is unsupported", entry.supported());
      observed.put(entry.invariantName(), entry.boundary());
    }
    assertEquals(expected, observed);
  }

  /** The classification is recorded per MODE, exactly as support already is. */
  @Test
  public void theBoundaryIsRecordedSeparatelyForNominalAndUncertainTranslation() {
    FragmentCoverageLedger ledger = boundaryLedger(TranslationMode.NOMINAL);
    for (InvariantCoverage entry : ledger.entries()) {
      assertEquals(TranslationMode.NOMINAL, entry.mode());
      assertNotNull("a refusal without a boundary is not evidence", entry.boundary());
    }
    assertEquals(
        List.of("Sample::UTypeUncertainVersusUncertain"),
        ledger.entriesFor(FragmentBoundary.UTYPE_UNCERTAIN_VERSUS_UNCERTAIN).stream()
            .map(InvariantCoverage::invariantName)
            .toList());
    assertTrue(
        "the U-type exclusions must be visible as a group",
        ledger.boundariesHit().contains(FragmentBoundary.UTYPE_CORE));
  }

  /** Every boundary is either a 7.1 crisp tier or a 7.2 U-type boundary -- never neither. */
  @Test
  public void everyBoundaryIsEitherACrispTierOrAUTypeBoundaryOrAnEncodingScope() {
    for (FragmentBoundary boundary : FragmentBoundary.values()) {
      int kinds =
          (boundary.isCrispTier() ? 1 : 0)
              + (boundary.isUTypeBoundary() ? 1 : 0)
              + (boundary == FragmentBoundary.ENCODING_SCOPE ? 1 : 0);
      assertEquals("exactly one kind for " + boundary, 1, kinds);
      assertFalse("every boundary cites its source", boundary.citation().isBlank());
    }
  }

  /**
   * Milestones 4.3-4.6 built messages that name the construct and the invariant. The boundary is
   * ADDITIVE evidence; it must not have displaced any of that.
   */
  @Test
  public void theFailClosedMessageStillNamesTheConstructTheInvariantAndTheModeAndAddsTheBoundary() {
    FragmentCoverageLedger ledger = boundaryLedger(TranslationMode.UNCERTAIN);
    SmtTranslationException failure =
        assertThrows(SmtTranslationException.class, ledger::requireAllSupported);
    String message = failure.getMessage();

    assertTrue(message, message.contains("Sample::Tier3SetLiteral"));
    assertTrue(message, message.contains("[UNCERTAIN]"));
    assertTrue("the construct must still be named", message.contains("Set literal"));
    // Tier2TypeTest's body changed from `self.oclIsTypeOf(Sample)` (now genuinely supported --
    // see isTypeCheck's own javadoc) to `self.oclAsType(Sample).oclIsTypeOf(Sample)`, refused by
    // variableNameOf because the receiver is no longer a bare variable, not by isTypeCheck's own
    // logic -- so its message no longer says "isTypeOf" by name; it says why the RECEIVER is
    // unsupported instead, which is now the real, accurate reason.
    assertTrue(
        "the construct must still be named",
        message.contains("attribute access on a non-variable receiver"));
    // The message text changed since this assertion was first written: `self.speed > self.other`
    // now reaches uTypeSymmetricThreshold (both operands are UReal attributes), which names the
    // REAL reason for the refusal (uncertainty not provably equal) instead of the old, less
    // precise "not a crisp numeric literal" -- a genuine improvement in the located reason, not a
    // weakened assertion; the boundary itself (asserted below) is unchanged.
    assertTrue(
        "the located reason must survive",
        message.contains("uncertain-vs-uncertain comparison whose two operands' uncertainty"));
    assertTrue(message, message.contains(FragmentBoundary.TIER_3.name()));
    assertTrue(message, message.contains(FragmentBoundary.UTYPE_UNCERTAIN_VERSUS_UNCERTAIN.name()));
  }

  /**
   * "Every active invariant referenced directly or by an aggregate is accounted for before
   * solving": a requirement with no ledger entry at all is a silent hole, and fails closed.
   */
  @Test
  public void aRequiredInvariantModePairWithNoLedgerEntryFailsClosed() {
    MModel model = compile(BOUNDARY_MODEL, "Boundaries");
    Map<String, Set<TranslationMode>> requirements = new LinkedHashMap<>();
    for (MClassInvariant invariant : model.classInvariants(true)) {
      requirements.put(invariant.qualifiedName(), Set.of(TranslationMode.UNCERTAIN));
    }
    requirements.put("Sample::NeverChecked", Set.of(TranslationMode.UNCERTAIN));

    FragmentCoverageLedger ledger = boundaryLedger(TranslationMode.UNCERTAIN);

    SmtTranslationException failure =
        assertThrows(SmtTranslationException.class, () -> ledger.requireAccountedFor(requirements));
    assertTrue(failure.getMessage(), failure.getMessage().contains("Sample::NeverChecked"));
    assertTrue(failure.getMessage(), failure.getMessage().contains("UNCERTAIN"));
  }

  /** The same check passes when the ledger really does cover every requested pair. */
  @Test
  public void aLedgerCoveringEveryRequestedPairIsAccountedFor() {
    MModel model = compile(BOUNDARY_MODEL, "Boundaries");
    Map<String, Set<TranslationMode>> requirements = new LinkedHashMap<>();
    for (MClassInvariant invariant : model.classInvariants(true)) {
      requirements.put(invariant.qualifiedName(), Set.of(TranslationMode.UNCERTAIN));
    }
    boundaryLedger(TranslationMode.UNCERTAIN).requireAccountedFor(requirements);
  }

  /** A supported entry carries no boundary at all: a boundary means a refusal. */
  @Test
  public void aSupportedEntryCarriesNoBoundary() {
    InvariantCoverage supported =
        new InvariantCoverage("A::ok", TranslationMode.UNCERTAIN, true, null, null);
    assertNull(supported.boundary());
    assertThrows(
        IllegalArgumentException.class,
        () -> new InvariantCoverage("A::bad", TranslationMode.UNCERTAIN, false, null, "why"));
  }

  /** The classification travels on the refusal itself, so the ledger merely records it. */
  @Test
  public void theTranslationExceptionItselfCarriesTheBoundary() {
    MModel model = compile(BOUNDARY_MODEL, "Boundaries");
    MClassInvariant setLiteral = invariant(model, "Sample::Tier3SetLiteral");
    SmtTranslationException failure =
        assertThrows(
            SmtTranslationException.class,
            () ->
                InvariantAssembler.assemble(
                    setLiteral, boundaryContext(new SmtScript("QF_UFLIRA"))));
    assertEquals(FragmentBoundary.TIER_3, failure.boundary());
  }

  private static FragmentCoverageLedger boundaryLedger(TranslationMode mode) {
    MModel model = compile(BOUNDARY_MODEL, "Boundaries");
    SmtScript script = new SmtScript("QF_UFLIRA");
    TranslationContext context = boundaryContext(script);
    Map<String, Set<TranslationMode>> requirements = new LinkedHashMap<>();
    List<MClassInvariant> invariants = model.classInvariants(true).stream().toList();
    invariants.forEach(invariant -> requirements.put(invariant.qualifiedName(), Set.of(mode)));
    return FragmentChecker.checkAndReify(invariants, requirements, context, script).ledger();
  }

  private static TranslationContext boundaryContext(SmtScript script) {
    ObjectSlots slots =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Sample", 1, 1))).get("Sample");
    AttributeDomain nDomain =
        new AttributeDomain("Sample", "n", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(4));
    AttributeValues nValues =
        AttributeEncoder.encode(script, slots, "n", AttributeType.INTEGER, nDomain);
    // Both speed and other get a RANGE (not a singleton) uncertainty domain deliberately: this is
    // what keeps UTypeUncertainVersusUncertain (self.speed > self.other) refused under
    // UTYPE_UNCERTAIN_VERSUS_UNCERTAIN rather than translated by uTypeSymmetricThreshold's
    // equal-proven-singleton-uncertainty shape -- a range can't be proven equal to another range,
    // so this fixture still genuinely exercises the general refusal it is named for.
    Map<String, AttributeDomain> domains = new LinkedHashMap<>();
    domains.put("Sample.n", nDomain);
    AttributeValues speed = uReal(script, slots, "speed", domains);
    AttributeValues other = uReal(script, slots, "other", domains);
    return new TranslationContext(
        Map.of(),
        Map.of("Sample.n", nValues, "Sample.speed", speed, "Sample.other", other),
        domains,
        Map.of("Sample", slots),
        Map.of());
  }

  private static AttributeValues uReal(
      SmtScript script, ObjectSlots slots, String name, Map<String, AttributeDomain> domains) {
    AttributeDomain value =
        new AttributeDomain("Sample", name, "value", List.of(), BigDecimal.ZERO, BigDecimal.ONE);
    AttributeDomain uncertainty =
        new AttributeDomain(
            "Sample", name, "uncertainty", List.of(), BigDecimal.ZERO, BigDecimal.ONE);
    domains.put("Sample." + name + ".value", value);
    domains.put("Sample." + name + ".uncertainty", uncertainty);
    return AttributeEncoder.encodeUType(
        script, slots, name, AttributeType.UREAL, value, uncertainty);
  }

  private static MClassInvariant invariant(MModel model, String qualifiedName) {
    return model.classInvariants(true).stream()
        .filter(candidate -> candidate.qualifiedName().equals(qualifiedName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no invariant " + qualifiedName));
  }

  private static MModel compile(String source, String name) {
    MModel model =
        USECompiler.compileSpecification(
            source, name, new PrintWriter(System.err), new ModelFactory());
    if (model == null) {
      throw new AssertionError("model did not compile: " + name);
    }
    return model;
  }
}
