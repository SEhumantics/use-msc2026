package org.tzi.use.uml.ocl.type;
import java.util.*;
public final class UStringType extends UncertainType {
    UStringType() { super("UString"); }
    @Override public boolean isTypeOfUString() { return true; }
    @Override public boolean isKindOfUString(VoidHandling h) { return true; }
    @Override public boolean conformsTo(Type t) { return t.isTypeOfUString() || t.isTypeOfOclAny(); }
    @Override public Set<Type> allSupertypes() { return new HashSet<>(Arrays.asList(this, TypeFactory.mkOclAny())); }
}
