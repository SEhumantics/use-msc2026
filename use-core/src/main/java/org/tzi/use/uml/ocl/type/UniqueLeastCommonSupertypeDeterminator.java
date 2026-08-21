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

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Determines the unique least common super-type for a set of types.
 * @author Fabian
 *
 */
public class UniqueLeastCommonSupertypeDeterminator {

	/**
	 * @implNote {@code IntegerType} and {@code UnlimitedNaturalType} both declare
	 *     {@code conformsTo} as "is a kind of number", making them mutually conformant -- a
	 *     pre-existing defect in plain USE 7.5.0, not introduced by any uncertainty extension. The
	 *     third step below has no fixpoint for a mutually-conformant pair: the winner is whichever
	 *     one the {@code allCommonSuperTypes} iterator yields last. With a {@code HashSet}, ordered
	 *     by {@code BasicType.hashCode()} (the JVM's lazily-assigned identity hash of the type's
	 *     {@code Class} object), that made {@code calculateFor} order-dependent across JVM runs --
	 *     confirmed reproducible with the same binary, varying only which classes had their identity
	 *     hash requested first (docs/port2/adaptation/01-types.md &sect;C-08). Iterating a
	 *     {@code TreeSet} ordered by {@code toString()} instead makes every step's iteration order a
	 *     function of the types themselves, not of JVM-internal state, so the same input set now
	 *     always produces the same result. This does not resolve which of the two mutually-conformant
	 *     types is "correct" -- that is docs/port2/adaptation/01-types.md &sect;C-08's still-open
	 *     decision B11b -- only that the answer is reproducible.
	 */
	public Type calculateFor(Set<Type> types) {
		if (types.isEmpty())
			return TypeFactory.mkVoidType();

		if (types.size() == 1) {
			return types.iterator().next();
		}

		//TODO:  The first two steps can be optimized

		// First step: Determine the set of common super-types of all elements
    	Set<Type> allSuperTypes = new TreeSet<Type>(Comparator.comparing(Type::toString));
    	for(Type t : types) {
			if (t.isVoidOrElementTypeIsVoid())
				allSuperTypes.add(t);
			else
				allSuperTypes.addAll(t.allSupertypes());
		}

    	// Second step: Select those that are common to all others
    	Set<Type> allCommonSuperTypes = new TreeSet<Type>(Comparator.comparing(Type::toString));
    	for (Type t : allSuperTypes) {
    		if (typeIsSupertypeOfAll(t,types))
    			allCommonSuperTypes.add(t);
    	}

		// Third step: Find the most specific-one that is comparable to all others
    	Type result = null;
    	for (Type t : allCommonSuperTypes) {
			if (typeIsComparableToAll(t,allCommonSuperTypes)) {
				if (result == null) {
					result = t;
				}
				else if (t.conformsTo(result)) {
					result = t;
				}
			}
		}
		return result;
	}
	
	/**
     * Determines whether t is either sub-type or super-type of each element in allSuperTypes.
	 * @param t
	 * @param allSuperTypes
	 * @return
	 */
	private boolean typeIsComparableToAll(Type t, Set<Type> allSuperTypes) {
		for (Type t1 : allSuperTypes) {
			if (! (t1.conformsTo(t) || t.conformsTo(t1)))  
				return false;
		}
		return true;
	}

	/**
	 * Determines whether t is a super-type of each element in types.
	 * @param t
	 * @param types
	 * @return
	 */
	private boolean typeIsSupertypeOfAll(Type t, Set<Type> types) {
		for (Type t1 : types) {
			if (! t1.conformsTo(t)) {
				return false;
			}
		}
		return true;
	}
}
