package org.tzi.use.uml.ocl.type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The type of a string value carrying a confidence in its spelling.
 *
 * <p><code>String</code> is kind of <code>UString</code>, so an ordinary string
 * literal can be used wherever an uncertain one is expected.
 */
public final class UStringType extends UncertainType {

    UStringType() {
        super("UString");
    }

    @Override
    public boolean isTypeOfUString() {
        return true;
    }

    @Override
    public boolean isKindOfUString(VoidHandling h) {
        return true;
    }

    @Override
    public boolean conformsTo(Type t) {
        return t.isTypeOfUString() || t.isTypeOfOclAny();
    }

    @Override
    public Set<Type> allSupertypes() {
        return new HashSet<>(Arrays.asList(this, TypeFactory.mkOclAny()));
    }
}
