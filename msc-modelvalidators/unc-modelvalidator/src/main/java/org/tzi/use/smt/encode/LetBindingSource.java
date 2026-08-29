package org.tzi.use.smt.encode;

import org.tzi.use.smt.config.AttributeDomain;

/**
 * The SOURCE of a U-type let binding whose consumer enumerates configured candidates: the bare
 * attribute access a {@code UString}/{@code UBoolean} let initialized from, carried on the
 * translator's {@code LocalBinding} so {@link UBooleanProbability}'s case enumeration can read
 * the same configured candidate lists (spellings/confidences/probabilities) and the same source
 * symbols it would read for the attribute itself -- the SMT let makes the alias and the source
 * interchangeable, and the alias key keeps the read-once aliasing rule per let variable.
 *
 * <p>{@code firstDomain} is the UBoolean probability domain or the UString spelling domain;
 * {@code secondDomain} is the UString confidence domain, null for UBoolean.
 */
public record LetBindingSource(
    String aliasKey,
    VariableBinding source,
    AttributeValues values,
    AttributeDomain firstDomain,
    AttributeDomain secondDomain) {}
