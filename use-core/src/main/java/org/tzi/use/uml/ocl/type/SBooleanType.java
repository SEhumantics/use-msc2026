package org.tzi.use.uml.ocl.type;
import java.util.*;
public final class SBooleanType extends UncertainType {
    SBooleanType() { super("SBoolean"); }
    @Override public boolean isTypeOfSBoolean() { return true; }
    @Override public boolean isKindOfSBoolean(VoidHandling h) { return true; }
    @Override public boolean conformsTo(Type t) { return t.isTypeOfSBoolean() || t.isTypeOfOclAny(); }
    @Override public Set<Type> allSupertypes() { return new HashSet<>(Arrays.asList(this, TypeFactory.mkOclAny())); }
}
