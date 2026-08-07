package org.tzi.use.uml.ocl.type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The type of an integer value carrying an absolute uncertainty.
 *
 * <p><code>UInteger</code> is kind of <code>UReal</code>, mirroring
 * <code>Integer</code> being kind of <code>Real</code>. Mixed arithmetic
 * resolves through the least common supertype, so a <code>UInteger</code> and a
 * <code>Real</code> meet at <code>UReal</code>.
 */
public final class UIntegerType extends UncertainType {

    UIntegerType() {
        super("UInteger");
    }

    @Override
    public boolean isTypeOfUInteger() {
        return true;
    }

    @Override
    public boolean isKindOfUInteger(VoidHandling h) {
        return true;
    }

    @Override
    public boolean isKindOfUReal(VoidHandling h) {
        return true;
    }

    @Override
    public boolean isKindOfNumber(VoidHandling h) {
        return true;
    }

    @Override
    public boolean conformsTo(Type t) {
        return t.isTypeOfUInteger() || t.isTypeOfUReal() || t.isTypeOfOclAny();
    }

    @Override
    public Set<Type> allSupertypes() {
        return new HashSet<>(Arrays.asList(this, TypeFactory.mkUReal(), TypeFactory.mkOclAny()));
    }
}
