package org.tzi.use.uml.ocl.type;

/**
 * Common base for the native uncertainty built-in types.
 *
 * <p>The uncertain types are basic types like <code>Integer</code> or
 * <code>String</code>: they are interned singletons handed out by
 * {@link TypeFactory} and they carry no structure of their own.
 */
public abstract class UncertainType extends BasicType {

    protected UncertainType(String name) {
        super(name);
    }

    @Override
    public boolean isKindOfUncertain(VoidHandling h) {
        return true;
    }
}
