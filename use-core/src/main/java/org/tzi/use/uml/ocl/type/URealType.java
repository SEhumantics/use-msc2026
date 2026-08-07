package org.tzi.use.uml.ocl.type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The type of a real value carrying an absolute uncertainty.
 *
 * <p><code>Real</code> and <code>Integer</code> are kind of <code>UReal</code>,
 * which is what lets a certain number take part in an uncertain expression.
 */
public final class URealType extends UncertainType {

    URealType() {
        super("UReal");
    }

    @Override
    public boolean isTypeOfUReal() {
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
        return t.isTypeOfUReal() || t.isTypeOfOclAny();
    }

    @Override
    public Set<Type> allSupertypes() {
        return new HashSet<>(Arrays.asList(this, TypeFactory.mkOclAny()));
    }
}
