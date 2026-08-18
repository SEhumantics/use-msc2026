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

/**
 * Abstract base of the uncertain basic types (`UReal`, `UInteger`, `UBoolean`,
 * `UString`, `SBoolean`).
 *
 * <p>Carries no behaviour. It exists purely as an {@code instanceof} tag, which is how the
 * uncertainty-aware operations ask "is this operand uncertain at all?" without enumerating the
 * five leaves.
 *
 * @author Víctor Manuel Ortiz Guardeño
 */
public abstract class UncertainType extends BasicType {

    protected UncertainType(String t) {
        super(t);
    }
}
