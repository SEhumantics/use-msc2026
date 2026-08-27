package org.tzi.use.smt.encode;

public enum AttributeType {
  STRING,
  INTEGER,
  REAL,
  UREAL,
  UINTEGER,
  UBOOLEAN,
  BOOLEAN;

  /**
   * True for a U-type family encoded as a REPRESENTATIVE plus an UNCERTAINTY, per {@code
   * THESIS_SMT_MODEL_FINDER_PLAN.md} 7.2. The two families differ only in the representative's SMT
   * sort -- {@code UREAL} on Real, {@code UINTEGER} on Int -- which is why the uncertainty half and
   * the threshold boundary are shared rather than duplicated.
   *
   * <p>{@code UBOOLEAN} is deliberately NOT one of them: {@code
   * archive2/robust_utype_model_finding_proposal.md} canonicalises a UBoolean to a SINGLE truth
   * probability, so it has no second component to pair, no scenario-quantifiable measurement
   * quality, and no normal-CDF boundary. Use {@link #isUncertain()} for "is this a U-type at all".
   */
  public boolean isPairedUType() {
    return this == UREAL || this == UINTEGER;
  }

  /**
   * True for every U-type family this encoder knows, paired or not. A bare access to any of them is
   * refused outside a supported projection, and none of them decodes through the crisp {@code
   * SmtValueDecoder#decode} path.
   */
  public boolean isUncertain() {
    return isPairedUType() || this == UBOOLEAN;
  }
}
