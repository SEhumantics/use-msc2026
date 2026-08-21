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

package org.tzi.use.util;

/**
 * Some math utility functions
 * @author Lars Hamann
 *
 */
public class MathUtil {
	private MathUtil(){}
	
	/**
	 * Calculates the maximum of the given parameters
	 * @param value List of values to retrieve the maximum from
	 * @return The maximum value of all given values
	 */
	public static double max(double... value) {
		double max = Double.MIN_VALUE;
		
		for (double d : value) {
			max = Math.max(max, d);
		}
		
		return max;
	}
	
	/**
	 * Calculates the maximum of the given parameters
	 * @param useInt This parameter can be removed when Sun/Oracle fixes a <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6199075">bug</a> with parameter arrays.<br/>
	 * 				 The compiler wrongly determines max(double...) and max(int...) as ambiguous.
	 * @param value List of values to retrieve the maximum from
	 * @return The maximum value of all given values
	 */
	public static int max(boolean useInt, int... value) {
		int max = Integer.MIN_VALUE;
		
		for (int d : value) {
			max = Math.max(max, d);
		}
		
		return max;
	}
	
	/**
	 * Calculates the minimum of the given parameters
	 * @param value List of values to retrieve the minimum from
	 * @return The minimum value of all given values
	 */
	public static double min(double... value) {
		double min = Double.MAX_VALUE;
		
		for (double d : value) {
			min = Math.min(min, d);
		}
		
		return min;
	}
	
	/**
	 * Calculates the minimum of the given parameters
	 * @param useInt This parameter can be removed when Sun/Oracle fixes a <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6199075">bug</a> with parameter arrays.<br/>
	 * 				 The compiler wrongly determines max(double...) and max(int...) as ambiguous.
	 * @param value List of values to retrieve the minimum from
	 * @return The minimum value of all given values
	 */
	public static int min(boolean useInt, int... value) {
		int min = Integer.MAX_VALUE;
		
		for (int d : value) {
			min = Math.min(min, d);
		}
		
		return min;
	}

	/**
	 * Rounds {@code value} to {@code digits} decimal places.
	 *
	 * <p>Added by the uncertainty fork. The uncertain values use it to compare and print at a fixed
	 * precision so that accumulated double error does not make two equal quantities unequal.
	 *
	 * @implNote The fork computed this as {@code Math.round(value * Math.pow(10, digits)) / exp}.
	 *     {@code Math.round(double)} returns a {@code long} that saturates at {@code Long.MAX_VALUE};
	 *     every call site here passes {@code digits = 10}, so the saturation point is only about
	 *     {@code 9.2e8}, and above it every input collapses onto the same rounded value — since
	 *     {@code URealValue.equals} compares rounded values, two unequal {@code URealValue}s above
	 *     {@code 9.2e8} compared equal (a silently wrong answer, not an exception). Fixed with
	 *     {@code BigDecimal.valueOf(value).setScale(digits, mode)}, which has no such ceiling; {@code
	 *     valueOf} (not {@code new BigDecimal(double)}) is used deliberately so the exact binary
	 *     representation of {@code value} isn't rounded instead of its decimal value. The rounding
	 *     mode is {@code HALF_UP} for non-negative {@code value} and {@code HALF_DOWN} for negative,
	 *     which together reproduce {@code Math.round}'s actual half-toward-positive-infinity rule
	 *     ({@code Math.round(-2.5) == -2}, not {@code -3}); a single {@code HALF_UP} would silently
	 *     change rounding of negative halves, a population the corpus does reach. {@code NaN} and the
	 *     infinities have no {@code BigDecimal} representation and are returned unchanged (the fork
	 *     mapped them to {@code 0.0}, which is not a rounding of anything); this is reachable from OCL
	 *     via division by zero. No existing test or corpus entry reaches the {@code 9.2e8} magnitude,
	 *     so the fix is verified by a purpose-built {@code MathUtilRoundSaturationTest} rather than an
	 *     existing observer.
	 * @param value  value to be rounded
	 * @param digits digits to be adjusted
	 * @return value rounded at the given number of digits
	 * @see "docs/port2/b7-fix-plan.md &sect;2 F-2 &mdash; deviation ledger (decided 2026-08-17)"
	 * @author Víctor Manuel Ortiz
	 */
	public static double round(double value, int digits) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return value;
		}
		// HALF_UP above zero, HALF_DOWN below it: together, half-toward-positive-infinity, which is
		// what Math.round does. HALF_UP alone is half-away-from-zero and would move every negative
		// half. See the javadoc.
		java.math.RoundingMode mode = value < 0
				? java.math.RoundingMode.HALF_DOWN
				: java.math.RoundingMode.HALF_UP;
		return java.math.BigDecimal.valueOf(value).setScale(digits, mode).doubleValue();
	}
}
