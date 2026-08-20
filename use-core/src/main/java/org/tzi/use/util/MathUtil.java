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
	 * <h4>B7 / ledger F-2 — behaviour deliberately changed from the fork</h4>
	 * The fork's body was:
	 * <pre>
	 *   double exp = Math.pow(10, digits);
	 *   return Math.round(value * exp) / exp;
	 * </pre>
	 * (fork {@code src/main/org/tzi/use/util/MathUtil.java:106-109}). {@code Math.round(double)}
	 * returns a {@code long} and <strong>saturates at {@code Long.MAX_VALUE}</strong>. Every one of
	 * the fifteen call sites in the uncertainty code passes {@code digits = 10}, so {@code value} is
	 * multiplied by 1e10 before rounding, and the saturation point is therefore about
	 * {@code 9.2e8} — not some astronomical magnitude, but a nine-digit number.
	 *
	 * <p>Above it, every input collapses onto the same output:
	 * <pre>
	 *   round(9.3e8, 10) == round(9.4e8, 10) == 9.223372036854776E8
	 * </pre>
	 * and since {@code URealValue.equals} compares rounded values, <strong>two unequal
	 * {@code URealValue}s above 9.2e8 compare equal</strong> — a silently wrong answer, not an
	 * exception.
	 *
	 * <p>{@code BigDecimal.setScale} has no such ceiling. The rounding mode is chosen to reproduce
	 * {@code Math.round} <strong>exactly</strong>, which takes two modes and not one:
	 * {@code Math.round} rounds a half toward <em>positive infinity</em>, so
	 * {@code Math.round(2.5) == 3} but {@code Math.round(-2.5) == -2}. {@code HALF_UP} rounds a half
	 * <em>away from zero</em>, which agrees for positives and disagrees for negatives —
	 * {@code HALF_UP} would send {@code -0.5} to {@code -1.0} where {@code Math.round} sends it to
	 * {@code 0}. So the mode is {@code HALF_UP} above zero and {@code HALF_DOWN} below it, and the
	 * pair is exactly half-toward-positive-infinity.
	 *
	 * <p>That is not pedantry. A plain {@code HALF_UP} would have been a second, undeclared
	 * behaviour change riding along inside F-2, on negative halves — a population the corpora do
	 * reach. It was caught by {@code MathUtilRoundSaturationTest.Preserved.agreesBelowTheCeiling},
	 * which asserts the two bodies agree on every input below the ceiling, and that assertion is
	 * the reason this method can claim to change one thing.
	 *
	 * <p>{@code BigDecimal.valueOf(double)} is used rather than {@code new BigDecimal(double)}: the
	 * constructor takes the exact binary value, so {@code new BigDecimal(0.1)} is
	 * {@code 0.1000000000000000055511151231257827...} and rounding it to ten places would preserve
	 * the very double error this method exists to erase. {@code valueOf} goes through
	 * {@code Double.toString} and yields {@code 0.1}.
	 *
	 * <p><strong>Declared consequence.</strong> {@code VALUE}, above 9.2e8 only. No test and no
	 * corpus entry reaches that magnitude, which is exactly why this fix needs a purpose-built test
	 * rather than an existing observer — see {@code MathUtilRoundSaturationTest}.
	 *
	 * <p><strong>Declared limit.</strong> {@code NaN} and the infinities have no {@code BigDecimal}
	 * representation and are returned unchanged. The fork's body mapped all three onto {@code 0.0}
	 * via {@code Math.round}, which is not a rounding of anything; passing them through is the
	 * behaviour every other numeric path in this package already has. Reachable from OCL: dividing
	 * by zero produces an infinity, and {@code UReal(1,0) / UReal(0,0)} then flows into
	 * {@code toString} and {@code equals}.
	 *
	 * <p>Decided by the user on 2026-08-17 (B7). Landed as a SECOND commit, after the byte-identical
	 * body was shown green against the ten-decimal assertions, per
	 * {@code docs/port2/b7-fix-plan.md} section 7.1 bundle D: one commit would have conflated "the
	 * helper exists" with "the helper is different".
	 *
	 * @param value  value to be rounded
	 * @param digits digits to be adjusted
	 * @return value rounded at the given number of digits
	 *
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
