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
     * Returns the set of types this type conforms to, including itself.
     *
     * @implNote The fork added {@code TypeFactory.mkUBoolean()} here instead of {@code this};
     *     harmless for the factory singleton but wrong for a directly-constructed instance (as
     *     {@code TypeTest} does), whose own supertype set then would not contain it. This adds
     *     {@code this}, matching what every other {@code allSupertypes()} in the tree means.
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-21 &mdash; deviation ledger (decided 2026-08-17)"
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
