package org.tzi.use.smt.config;

/** Inclusive object-count bounds for one UML class. */
public record ClassScope(String className, int min, int max) {}
