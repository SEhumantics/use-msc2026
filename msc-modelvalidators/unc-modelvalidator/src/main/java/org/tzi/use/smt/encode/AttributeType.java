package org.tzi.use.smt.encode;

public enum AttributeType {
  STRING,
  INTEGER,
  REAL,
  UREAL,
  UINTEGER,
  BOOLEAN;

  /**
   * True for a U-type family encoded as a REPRESENTATIVE plus an UNCERTAINTY, per {@code
   * THESIS_SMT_MODEL_FINDER_PLAN.md} 7.2. The two families differ only in the representative's SMT
   * sort -- {@code UREAL} on Real, {@code UINTEGER} on Int -- which is why the uncertainty half and
   * the threshold boundary are shared rather than duplicated.
   */
  public boolean isUType() {
    return this == UREAL || this == UINTEGER;
  }
}
