package org.tzi.use.uml.ocl.type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The type of a binary subjective opinion: belief, disbelief, uncertainty and a
 * base rate.
 *
 * <p>It is the top of the Boolean side of the uncertain lattice --
 * <code>Boolean</code> and <code>UBoolean</code> are kind of
 * <code>SBoolean</code> -- but it is not itself kind of <code>UBoolean</code>,
 * so a query that yields an opinion is rejected by the uncertainty-aware
 * collection operations rather than failing in the evaluator.
 */
public final class SBooleanType extends UncertainType {

    SBooleanType() {
        super("SBoolean");
    }

    @Override
    public boolean isTypeOfSBoolean() {
        return true;
    }

    @Override
    public boolean isKindOfSBoolean(VoidHandling h) {
        return true;
    }

    @Override
    public boolean conformsTo(Type t) {
        return t.isTypeOfSBoolean() || t.isTypeOfOclAny();
    }

    @Override
    public Set<Type> allSupertypes() {
        return new HashSet<>(Arrays.asList(this, TypeFactory.mkOclAny()));
    }
}
