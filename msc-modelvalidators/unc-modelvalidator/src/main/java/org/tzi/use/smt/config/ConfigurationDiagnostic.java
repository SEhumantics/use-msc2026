package org.tzi.use.smt.config;

/** A non-fatal compatibility diagnostic retained until model-aware validation can decide it. */
public record ConfigurationDiagnostic(String key, String message) {
}
