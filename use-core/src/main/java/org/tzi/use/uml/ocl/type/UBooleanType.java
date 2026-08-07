package org.tzi.use.uml.ocl.type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The type of a Boolean proposition held with a probability.
 *
 * <p><code>UBoolean</code> is kind of <code>SBoolean</code>: an uncertain
 * Boolean embeds into a subjective opinion as a dogmatic one. The historical
 * fork stated that relation through an intermediate
 * <code>UncertainBooleanType</code> that carried no other behaviour; it is
 * stated directly here instead.
 */
public final class UBooleanType extends UncertainType {

    UBooleanType() {
        super("UBoolean");
    }

    @Override
    public boolean isTypeOfUBoolean() {
        return true;
    }

    @Override
    public boolean isKindOfUBoolean(VoidHandling h) {
        return true;
    }

    @Override
    public boolean isKindOfSBoolean(VoidHandling h) {
        return true;
    }

    @Override
    public boolean conformsTo(Type t) {
        return t.isTypeOfUBoolean() || t.isTypeOfSBoolean() || t.isTypeOfOclAny();
    }

    @Override
    public Set<Type> allSupertypes() {
        return new HashSet<>(Arrays.asList(this, TypeFactory.mkSBoolean(), TypeFactory.mkOclAny()));
    }
}
