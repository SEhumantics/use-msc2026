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
 * Abstract base of the uncertain boolean values, {@link UBooleanValue} and {@link SBooleanValue}.
 *
 * <p>Declares {@link #not()} so that {@link UncertainValue#uDistinct(Value)} can be written once
 * against the degree rather than once per uncertain boolean representation.
 */
public abstract class UncertainBooleanValue extends UncertainValue {

    protected UncertainBooleanValue(Type t) {
        super(t);
    }

    public abstract UncertainBooleanValue not();
}
