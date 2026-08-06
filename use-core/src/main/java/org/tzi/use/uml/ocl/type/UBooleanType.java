package org.tzi.use.uml.ocl.type;
import java.util.*;
public final class UBooleanType extends UncertainType {
    UBooleanType() { super("UBoolean"); }
    @Override public boolean isTypeOfUBoolean() { return true; }
    @Override public boolean isKindOfUBoolean(VoidHandling h) { return true; }
    @Override public boolean isKindOfSBoolean(VoidHandling h) { return true; }
    @Override public boolean conformsTo(Type t) { return t.isTypeOfUBoolean() || t.isTypeOfSBoolean() || t.isTypeOfOclAny(); }
    @Override public Set<Type> allSupertypes() { return new HashSet<>(Arrays.asList(this, TypeFactory.mkSBoolean(), TypeFactory.mkOclAny())); }
}
