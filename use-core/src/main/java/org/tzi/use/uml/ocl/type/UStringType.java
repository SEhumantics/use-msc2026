/*
 * USE - UML based specification environment
 * Copyright (C) 1999-2004 Mark Richters, University of Bremen
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.tzi.use.uml.ocl.type;

import java.util.HashSet;
import java.util.Set;

public class UStringType extends UncertainType {

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
    public boolean conformsTo(Type other) {
        return equals(other) || other.isTypeOfOclAny();
    }

    /**
     * B7 / ledger M-21 — the self-entry is {@code this}, not {@code TypeFactory.mkUString()}.
     *
     * <p>The fork used the two interchangeably across the five uncertain types: {@code URealType}
     * and {@code UIntegerType} added {@code this}, while {@code UBooleanType}, {@code UStringType}
     * and {@code SBooleanType} added the factory singleton. For the singletons the factory hands out
     * those are the same object, so the inconsistency is invisible — until a type is constructed
     * directly, which {@code TypeTest} does at {@code :380-403}. For such an instance
     * {@code this != mkUString()}, and its own supertype set did not contain it.
     *
     * <p>Unified on {@code this}, because "the set of types this one conforms to" containing
     * <em>this</em> one is what every other {@code allSupertypes()} in the tree means, and because a
     * type that is not among its own supertypes fails {@code conformsTo} against itself.
     *
     * <p><strong>Declared consequence.</strong> {@code SET} — {@code allSupertypes()} contents, for
     * directly-constructed instances only. Decided by the user on 2026-08-17 (B7);
     * {@code docs/port2/b7-fix-plan.md} section 2 M-21.
     */
    @Override
    public Set<? extends Type> allSupertypes() {
        Set<Type> res = new HashSet<Type>(2);
        res.add(this);
        res.add(TypeFactory.mkOclAny());
        return res;
    }
}
