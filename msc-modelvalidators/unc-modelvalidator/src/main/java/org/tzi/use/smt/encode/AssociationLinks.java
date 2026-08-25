package org.tzi.use.smt.encode;

/** The link-boolean grid for one binary association. */
public record AssociationLinks(
    String associationName, ObjectSlots aEnd, ObjectSlots bEnd, String[][] linkNames) {}
