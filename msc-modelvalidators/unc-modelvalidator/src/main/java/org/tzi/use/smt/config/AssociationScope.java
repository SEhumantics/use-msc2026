package org.tzi.use.smt.config;

/** Inclusive link-count bounds for one UML association. */
public record AssociationScope(String associationName, int min, int max) {
}
