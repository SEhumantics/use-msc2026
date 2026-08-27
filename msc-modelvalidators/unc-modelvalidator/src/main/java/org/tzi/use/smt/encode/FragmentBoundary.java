package org.tzi.use.smt.encode;

/**
 * WHICH supported-fragment boundary a refusal hit.
 *
 * <p>Before Milestone 4.7 a refusal carried only free text. That is enough to tell a human what
 * went wrong and useless as evidence: the ledger could not answer "how much of Tier 2 is missing?"
 * or "was this a crisp gap or a deliberate U-type exclusion?" without reading prose. The boundary
 * is therefore recorded ALONGSIDE that text, never instead of it -- {@code FragmentChecker} keeps
 * the original message, which still names the construct and the invariant exactly as Milestones
 * 4.3-4.6 left it.
 *
 * <p>The constants are not a taxonomy invented here. The crisp tiers are {@code
 * THESIS_SMT_MODEL_FINDER_PLAN.md} 7.1's, which orders the crisp syntax BY CORPUS FREQUENCY across
 * the 33 examples rather than by taste; the U-type constants are 7.2's core plus its five named
 * exclusions. A construct that appears in no tier at all is {@link #BEYOND_FIRST_FRAGMENT}, which
 * 7.1 spells out as "out of the required first fragment".
 *
 * <p>{@link #ENCODING_SCOPE} is the one constant that is NOT a fragment boundary: it means the OCL
 * itself was fine but the configured encoding registered no symbol for an element the expression
 * needs (no slots for a class, no domain for an attribute). Recording it separately keeps a
 * configuration gap from being miscounted as a missing language feature -- which would overstate
 * how much of the fragment is unimplemented.
 */
public enum FragmentBoundary {

  /**
   * 7.1 Tier 1, present in at least 12 of the 33 corpus examples: class instantiation and bounded
   * slots; attribute declaration/typing; binary associations; multiplicity ranges; predefined
   * links; Boolean literals and operators; {@code forAll}; attribute access; invariant
   * transformation; object-diagram reconstruction; trivial SAT/UNSAT dispatch.
   */
  TIER_1("THESIS_SMT_MODEL_FINDER_PLAN.md 7.1 Tier 1 (in >=12 corpus examples)"),

  /**
   * 7.1 Tier 2: navigation over regular associations; {@code exists}; integer arithmetic and
   * comparisons; string literals and equality; {@code allInstances}; type tests and casts; single
   * and multiple inheritance; abstract classes; {@code oclUndefined}; object reference equality.
   */
  TIER_2("THESIS_SMT_MODEL_FINDER_PLAN.md 7.1 Tier 2"),

  /**
   * 7.1 Tier 3: association classes; self-referential and derived associations; {@code subsets},
   * {@code redefines}, union associations; aggregation/composition cycle-freeness and forbidden
   * sharing; {@code select}, {@code collect}, {@code closure}, {@code isUnique}, {@code one},
   * {@code let}, {@code iterate}; enumerations; set literals.
   */
  TIER_3("THESIS_SMT_MODEL_FINDER_PLAN.md 7.1 Tier 3"),

  /**
   * 7.1 "out of the required first fragment": operation pre/postcondition contracts, recursive
   * operations, classifying-terms scrolling, partial-solution completion -- and, by the same rule,
   * any construct 7.1 lists in no tier at all.
   */
  BEYOND_FIRST_FRAGMENT("THESIS_SMT_MODEL_FINDER_PLAN.md 7.1 out of the required first fragment"),

  /**
   * Inside 7.2's U-type core, but not in the shape this translation slice implements. 7.2 bounds
   * the core to {@code UReal} (representative plus fixed/finite uncertainty, affine arithmetic,
   * comparison against ONE exact operand, {@code toBooleanC} thresholds), {@code UInteger}, {@code
   * UBoolean} and {@code UString}. Only the {@code UReal} threshold family is implemented today, so
   * a bare {@code UReal} access or a {@code UInteger}/{@code UBoolean}/{@code UString} literal is a
   * gap INSIDE the core, not an exclusion from it -- a distinction that matters, because the two
   * have opposite implications for whether the gap will ever close.
   */
  UTYPE_CORE("THESIS_SMT_MODEL_FINDER_PLAN.md 7.2 U-type core, shape not yet implemented"),

  /** 7.2 excluded: general uncertain-versus-uncertain numeric comparison. */
  UTYPE_UNCERTAIN_VERSUS_UNCERTAIN(
      "THESIS_SMT_MODEL_FINDER_PLAN.md 7.2 excluded: general uncertain-versus-uncertain numeric"
          + " comparison"),

  /** 7.2 excluded: unrestricted strings. */
  UTYPE_UNRESTRICTED_STRING("THESIS_SMT_MODEL_FINDER_PLAN.md 7.2 excluded: unrestricted strings"),

  /** 7.2 excluded: nonlinear and transcendental operations. */
  UTYPE_NONLINEAR_OR_TRANSCENDENTAL(
      "THESIS_SMT_MODEL_FINDER_PLAN.md 7.2 excluded: nonlinear and transcendental operations"),

  /** 7.2 excluded: arbitrary collections of U-values. */
  UTYPE_VALUE_COLLECTION(
      "THESIS_SMT_MODEL_FINDER_PLAN.md 7.2 excluded: arbitrary collections of U-values"),

  /** 7.2 excluded: {@code SBoolean}. */
  UTYPE_SBOOLEAN("THESIS_SMT_MODEL_FINDER_PLAN.md 7.2 excluded: SBoolean"),

  /** 7.2 excluded: behavioural OCL. */
  UTYPE_BEHAVIOURAL_OCL("THESIS_SMT_MODEL_FINDER_PLAN.md 7.2 excluded: behavioural OCL"),

  /**
   * Not a fragment boundary: the configuration registered no encoding for a model element the
   * expression names. Widening the fragment would not help; configuring the missing scope would.
   */
  ENCODING_SCOPE(
      "outside 7.1/7.2: the configured encoding registered no symbol for this model element");

  private final String citation;

  FragmentBoundary(String citation) {
    this.citation = citation;
  }

  /** The source sentence this boundary is taken from, for reports and failure messages. */
  public String citation() {
    return citation;
  }

  /** True for the four 7.1 crisp-syntax boundaries. */
  public boolean isCrispTier() {
    return this == TIER_1 || this == TIER_2 || this == TIER_3 || this == BEYOND_FIRST_FRAGMENT;
  }

  /** True for 7.2's U-type core and each of its named exclusions. */
  public boolean isUTypeBoundary() {
    return name().startsWith("UTYPE_");
  }
}
