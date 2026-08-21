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

public class UBooleanType extends UncertainBooleanType {

    UBooleanType() {
        super("UBoolean");
    }

    @Override
    public boolean isKindOfOclAny(VoidHandling h) {
        return true;
    }

    @Override
    public boolean isTypeOfUBoolean() {
        return true;
    }

    @Override
    public boolean isKindOfUBoolean(VoidHandling h) {
        return true;
    }

    /**
     * @implNote Unconditionally {@code true} — not a defect, though it looks surprising in isolation
     *     (verified: docs/port2/spec-parts/19-open-questions.md Q2). {@code UBoolean} declares
     *     {@link #conformsTo} to {@code SBoolean} below, so by the standing meaning of
     *     "{@code isKindOf}" in this hierarchy (this type or a subtype of it), returning anything
     *     other than {@code true} here would contradict that. The practical consequence: any
     *     {@code StandardOperationsSBoolean} operation whose {@code matches()} gates on
     *     {@code isKindOfSBoolean} — {@code min}, {@code max}, the fusion family, {@code discount},
     *     etc. — is reachable on a {@code UBoolean} receiver too, since {@code UBoolean} has no
     *     operations of its own in that set.
     *     {@link org.tzi.use.uml.ocl.value.SBooleanValue#valueOf(org.tzi.use.uml.ocl.value.Value)}
     *     widens the actual {@code UBooleanValue} argument into an equivalent {@code SBoolean}
     *     opinion before use, so
     *     this is a real, correctly-coercing dispatch, not a crash risk. Plain crisp {@code Boolean}
     *     is not affected: {@link BooleanType} does not override this method, so it inherits
     *     {@code false} from {@link TypeImpl} and must first conform to {@code UBoolean} (a
     *     compile-time widening, not this method) before any of this applies.
     */
    @Override
    public boolean isKindOfSBoolean(VoidHandling h) {
        return true;
    }

    @Override
    public boolean conformsTo(Type other) {
        return other.equals(this) || other.isTypeOfOclAny() || other.isTypeOfSBoolean();
    }

    /**
     * B7 / ledger M-21 — the self-entry is {@code this}, not {@code TypeFactory.mkUBoolean()}.
     *
     * <p>The fork used the two interchangeably across the five uncertain types: {@code URealType}
     * and {@code UIntegerType} added {@code this}, while {@code UBooleanType}, {@code UStringType}
     * and {@code SBooleanType} added the factory singleton. For the singletons the factory hands out
     * those are the same object, so the inconsistency is invisible — until a type is constructed
     * directly, which {@code TypeTest} does at {@code :380-403}. For such an instance
     * {@code this != mkUBoolean()}, and its own supertype set did not contain it.
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
        Set<Type> res = new HashSet<Type>(3);
        res.add(this);
        res.add(TypeFactory.mkOclAny());
        res.add(TypeFactory.mkSBoolean());
        return res;
    }
}
