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

/**
 * The OCL Real type.
 *
 * @author  Mark Richters
 */
public final class RealType extends BasicType {

    RealType() {
        super("Real");
    }
    
    @Override
    public boolean isKindOfNumber(VoidHandling h) {
    	return true;
    }
    
    @Override
    public boolean isTypeOfReal() {
    	return true;
    }
    
    @Override
    public boolean isKindOfReal(VoidHandling h) {
    	return true;
    }
    /** A certain real is an uncertain one with an uncertainty of zero. */
    @Override
    public boolean isKindOfUReal(VoidHandling h) {
    	return true;
    }
    
    /** 
     * Returns true if this type is a subtype of <code>t</code>. 
     */
    public boolean conformsTo(Type t) {
        return equals(t) || t.isTypeOfUReal() || t.isTypeOfOclAny();
    }

    /** 
     * Returns the set of all supertypes (including this type).
     */
    public Set<Type> allSupertypes() {
        Set<Type> res = new HashSet<Type>(2);
        res.add(TypeFactory.mkOclAny());
        res.add(this);
        res.add(TypeFactory.mkUReal());
        return res;
    }
}
