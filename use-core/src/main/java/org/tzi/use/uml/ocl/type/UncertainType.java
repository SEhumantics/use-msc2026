package org.tzi.use.uml.ocl.type;

/** Common base for the native uncertainty built-in types. */
public abstract class UncertainType extends BasicType {
    protected UncertainType(String name) { super(name); }
    @Override public boolean isKindOfUncertain(VoidHandling h) { return true; }
}
