package org.tzi.use.smt.encode;

import org.tzi.use.smt.config.TranslationMode;

public record InvariantCoverage(
    String invariantName, TranslationMode mode, boolean supported, String reason) {
  public InvariantCoverage(String invariantName, boolean supported, String reason) {
    this(invariantName, TranslationMode.UNCERTAIN, supported, reason);
  }
}
