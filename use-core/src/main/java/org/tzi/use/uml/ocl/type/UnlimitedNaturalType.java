/*
 * USE - UML based specification environment
 * Copyright (C) 1999-2010 Mark Richters, University of Bremen
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

/**
 * The OCL type UnlimitedNatural
 * @author Lars Hamann
 * @since 3.1
 */
public class UnlimitedNaturalType extends BasicType {
	UnlimitedNaturalType() {
        super("UnlimitedNatural");
    }
    
    @Override
	public boolean isKindOfNumber(VoidHandling h) {
    	return true;
    }
    
    @Override
	public boolean isKindOfInteger(VoidHandling h) {
    	return true;
    }
    
    @Override
	public boolean isKindOfUnlimitedNatural(VoidHandling h) {
    	return true;
    }
    
    @Override
	public boolean isTypeOfUnlimitedNatural() {
    	return true;
    }
    
    /** 
     * Returns true if this type is a subtype of <code>t</code>. 
     */
    @Override
	public boolean conformsTo(Type t) {
        return !t.isTypeOfVoidType() && (t.isKindOfNumber(VoidHandling.EXCLUDE_VOID) || t.isTypeOfOclAny());
    }

    /**
     * Returns the set of all supertypes (including this type).
     *
     * @implNote Deliberately does <b>not</b> add {@code UInteger} or {@code UReal}, even though
     *     {@link #conformsTo} answers {@code true} for both (inherited from the "kind of number"
     *     predicate rule this type shares with {@link IntegerType}). This reproduces a historical
     *     lattice inconsistency (B11) bit-for-bit rather than fixing it: {@code
     *     UnlimitedNatural.conformsTo(UInteger)} is {@code true} but {@code
     *     UnlimitedNatural.getLeastCommonSupertype(UInteger)} returns {@code OclAny}, not {@code
     *     UInteger}, and the two answers disagree with what {@link
     *     UniqueLeastCommonSupertypeDeterminator#calculateFor} reports for the same pair. Do not
     *     "complete" this set to match {@code conformsTo} without re-opening B11 -- it is the same
     *     shape as a pre-existing upstream defect ({@code Integer.conformsTo(UnlimitedNatural)} is
     *     true while {@code UnlimitedNatural} is absent from {@code Integer.allSupertypes()}), so
     *     widening this one alone would not even make the lattice self-consistent.
     * @see "docs/port2/spec-parts/11-types.md &sect;3 B11 -- deviation ledger (adaptation-policy.md row T-15, specification.md &sect;9 row 11)"
     */
    @Override
	public Set<Type> allSupertypes() {
        Set<Type> res = new HashSet<Type>(4);
        res.add(TypeFactory.mkOclAny());
        res.add(TypeFactory.mkReal());
        res.add(TypeFactory.mkInteger());
        res.add(this);
        return res;
    }
}
