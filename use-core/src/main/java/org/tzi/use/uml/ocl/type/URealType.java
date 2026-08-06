package org.tzi.use.uml.ocl.type;
import java.util.*;
public final class URealType extends UncertainType {
    URealType() { super("UReal"); }
    @Override public boolean isTypeOfUReal() { return true; }
    @Override public boolean isKindOfUReal(VoidHandling h) { return true; }
    @Override public boolean isKindOfNumber(VoidHandling h) { return true; }
    @Override public boolean conformsTo(Type t) { return t.isTypeOfUReal() || t.isTypeOfOclAny(); }
    @Override public Set<Type> allSupertypes() { return new HashSet<>(Arrays.asList(this, TypeFactory.mkOclAny())); }
}
