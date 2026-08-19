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

package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.Type;

/**
 * Abstract base of the uncertain values.
 *
 * <p>Its reason to exist is {@link #uEquals(Value)}: comparing two uncertain values does not yield
 * a {@code boolean} but a <em>degree</em> of equality, so the uncertain types cannot reuse
 * {@link Value}'s crisp equality contract.
 *
 * @author Víctor M. Ortiz
 * @see Value
 */
public abstract class UncertainValue extends Value {

    protected UncertainValue(Type t) {
        super(t);
    }

    /**
     * Compare this value with another, with uncertainty.
     *
     * @param other value to compare against
     * @return the degree to which the two are equal
     */
    public abstract UncertainBooleanValue uEquals(Value other);

    /**
     * The complement of {@link #uEquals(Value)}.
     *
     * @param other value to compare against
     * @return the degree to which the two differ
     */
    public UncertainBooleanValue uDistinct(Value other) {
        UncertainBooleanValue equals = uEquals(other);
        UncertainBooleanValue distinct;

        distinct = equals.not();

        return distinct;
    }
}
