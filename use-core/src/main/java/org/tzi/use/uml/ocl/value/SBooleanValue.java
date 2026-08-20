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

/*
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/main/org/tzi/use/uml/ocl/value/SBooleanValue.java.
 *
 * The import of the uncertainty datatypes is edited: they were vendored into
 * org.tzi.use.uncertainty.datatypes rather than the original package `uDataTypes` (B1); see
 * docs/port2/stage-03-scope.md sec. 5.
 *
 * SEMANTICS ARE NOT UNCHANGED. This header said "Semantics unchanged" until 2026-08-20, which
 * documented the reverse of a binding user decision: B7 (2026-08-17) is that the port FIXES the
 * fork's defects rather than reproducing them bug-for-bug. The rows corrected in this file are
 * below, each justified in full at its own site, and each is a deliberate divergence from the
 * historical oracle:
 *
 *   M-18  compareTo()'s entire body was `return 0;`, so every opinion compared equal to every
 *         Value, including UndefinedValue and StringValue. It does NOT delegate to
 *         uDataTypes.SBoolean.compareTo, whose 0.001 tolerance is not transitive
 *
 * See docs/port2/stage-09.md sec. 3.1 and docs/port2/b7-fix-plan.md.
 */
package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.util.MathUtil;
import org.tzi.use.uncertainty.datatypes.SBoolean;

import java.util.Iterator;
import java.util.LinkedList;

public final class SBooleanValue extends UncertainBooleanValue {

	public static final SBooleanValue TRUE = new SBooleanValue(1, 0, 0, 1);
	public static final SBooleanValue FALSE = new SBooleanValue(0, 1, 0, 1);

	private SBoolean sBoolean;

	SBooleanValue(double b, double d, double u, double a) {
		super(TypeFactory.mkSBoolean());
		sBoolean = new SBoolean(b, d, u, a);
	}

	SBooleanValue(SBoolean sBoolean) {
		super(TypeFactory.mkSBoolean());
		this.sBoolean = sBoolean;
	}

	public static class Builder {
		private double belief = 0;
		private double disbelief = 0;
		private double uncertainty = 0;
		private double agent = 0;

		public Builder() {
		}

		public Builder belief(double b) {
			this.belief = b;
			return this;
		}

		public Builder disbelief(double d) {
			this.disbelief = d;
			return this;
		}

		public Builder uncertainty(double u) {
			this.uncertainty = u;
			return this;
		}

		public Builder agent(double a) {
			this.agent = a;
			return this;
		}

		public SBooleanValue build() {
			SBooleanValue ret;

			if (belief == 1 && disbelief == 0 && uncertainty == 0 && agent == 1)
				ret = TRUE;
			else if (belief == 0 && disbelief == 1 && uncertainty == 0 && agent == 1)
				ret = FALSE;
			else
				ret = new SBooleanValue(belief, disbelief, uncertainty, agent);

			return ret;
		}
	}

	public static SBooleanValue valueOf(Value value) {
		SBooleanValue ret = null;

		if (value.isSBoolean()) {
			ret = (SBooleanValue) value;
		} else if (value.isUBoolean()) {
			UBooleanValue ub = (UBooleanValue) value;
			ret = new SBooleanValue(new SBoolean(ub.getuBoolean()));
		} else if (value.isBoolean()) {

			if (((BooleanValue) value).value())
				ret = TRUE;
			else
				ret = FALSE;
		} 

		return ret;
	}

	private static SBooleanValue valueOf(SBoolean sBoolean) {
		return new SBooleanValue(sBoolean);
	}

	@Override
	public boolean isSBoolean() {
		return true;
	}

	@Override
	public UncertainBooleanValue uEquals(Value other) {
		SBooleanValue result = FALSE;

		if (other.type().isKindOfSBoolean(Type.VoidHandling.EXCLUDE_VOID)) {
			SBooleanValue aux = valueOf(other);
			result = valueOf(sBoolean.equivalent(aux.sBoolean));
		}

		return result;
	}

	@Override
	public UncertainBooleanValue uDistinct(Value other) {
		SBooleanValue result = TRUE;

		if (other.type().isKindOfSBoolean(Type.VoidHandling.EXCLUDE_VOID)) {
			SBooleanValue aux = valueOf(other);
			result = valueOf(sBoolean.xor(aux.sBoolean));
		}

		return result;
	}

	@Override
	public StringBuilder toString(StringBuilder sb) {
		sb.append(type().toString()).append("(").append(MathUtil.round(sBoolean.belief(), 3)).append(", ")
				.append(MathUtil.round(sBoolean.disbelief(), 3)).append(", ")
				.append(MathUtil.round(sBoolean.uncertainty(), 3)).append(", ")
				.append(MathUtil.round(sBoolean.baseRate(), 3)).append(")");
		return sb;
	}

	@Override
	public int hashCode() {
		return sBoolean.hashCode();
	}

	@Override
	public boolean equals(Object obj) {

		if (obj == this)
			return true;

		if (!(obj instanceof SBooleanValue))
			return false;

		SBooleanValue that = (SBooleanValue) obj;
		return that.sBoolean.equals(this.sBoolean);
	}

	/**
	 * B7 / ledger M-18 — <strong>behaviour deliberately changed from the fork.</strong>
	 *
	 * <p>The fork's entire body was {@code return 0;} (fork
	 * {@code src/main/org/tzi/use/uml/ocl/value/SBooleanValue.java:150-153}). Every
	 * {@code SBooleanValue} therefore compared equal to every {@code Value} it was ever handed —
	 * another opinion, an {@code UndefinedValue}, a {@code StringValue}, anything. Sorting a
	 * collection containing opinions left them in insertion order while claiming they were ordered,
	 * and {@code Collections.sort} had no way to know.
	 *
	 * <h4>Why this does not delegate to {@code SBoolean.compareTo}</h4>
	 * The obvious fix — hand the job to the library, as every sibling does — would trade one defect
	 * for a crash. {@code uDataTypes.SBoolean.compareTo} is:
	 * <pre>
	 *   double x = |b1-b2| + |d1-d2| + |u1-u2| + |a1-a2|;
	 *   if (x &lt; 0.001D) return 0;
	 *   return this.projection() - other.projection() &lt; 0 ? -1 : 1;
	 * </pre>
	 * A tolerance-based "equal" is <strong>not transitive</strong>: three opinions spaced 0.0006
	 * apart give {@code a == b}, {@code b == c} and {@code a &lt; c}. That is precisely the input
	 * Java's TimSort rejects with
	 * {@code IllegalArgumentException: Comparison method violates its general contract}, and it does
	 * so at 32 elements and above. The port would then crash where the fork merely mis-sorted, on
	 * collections no existing test reaches. So the order is implemented here instead, totally.
	 *
	 * <h4>The order</h4>
	 * Lexicographic {@link Double#compare} over the four masses, which is a genuine total order
	 * (antisymmetric, transitive, and consistent with {@link #equals(Object)}, which compares the
	 * same four components through {@code SBoolean.equals}). The {@code UndefinedValue} and
	 * {@code toString()} arms are the idiom {@link URealValue#compareTo(Value)} and
	 * {@link UStringValue#compareTo(Value)} already use, so a mixed collection behaves the way the
	 * rest of this package behaves.
	 *
	 * <p><strong>Declared consequence.</strong> {@code SET} (order) and {@code ERR}. Any collection
	 * containing an {@code SBooleanValue} may now print in a different order — a correct one.
	 *
	 * <p><strong>Declared residual.</strong> This makes <em>this</em> comparator total; it does not
	 * repair the pre-existing asymmetries elsewhere in the package ({@code RealValue.compareTo} falls
	 * through to a {@code toString()} comparison for a {@code URealValue} argument while
	 * {@code URealValue.compareTo} compares numerically). A mixed sort with an asymmetric comparator
	 * is still undefined; at the corpus sizes in this repo TimSort uses binary insertion sort and
	 * performs no contract check, so it is latent rather than live. See
	 * {@code docs/port2/b7-fix-plan.md} section 7.2 item 4.
	 *
	 * <p>Decided by the user on 2026-08-17 (B7). Designed in
	 * {@code docs/port2/b7-fix-plan.md} section 2 M-18.
	 */
	@Override
	public int compareTo(Value o) {
		if (o == this)
			return 0;
		if (o instanceof UndefinedValue)
			return +1;
		if (!(o instanceof SBooleanValue))
			return toString().compareTo(o.toString());

		SBoolean other = ((SBooleanValue) o).sBoolean;
		int res = Double.compare(sBoolean.belief(), other.belief());
		if (res != 0)
			return res;
		res = Double.compare(sBoolean.disbelief(), other.disbelief());
		if (res != 0)
			return res;
		res = Double.compare(sBoolean.uncertainty(), other.uncertainty());
		if (res != 0)
			return res;
		return Double.compare(sBoolean.baseRate(), other.baseRate());
	}

	public static SBooleanValue assertKindOfSBoolean(Value value) {
		SBooleanValue sbool = valueOf(value);

		if (sbool == null)
			throw new RuntimeException("A value kind of SBoolean expected");

		return sbool;
	}

	// WRAPPED METHODS

	public RealValue projectiveDistance(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		return new RealValue(sBoolean.projectiveDistance(sBooleanValue.sBoolean));
	}

	public RealValue conjunctiveCertainty(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		return new RealValue(sBoolean.conjunctiveCertainty(sBooleanValue.sBoolean));
	}

	public RealValue degreeOfConflict(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		return new RealValue(sBoolean.degreeOfConflict(sBooleanValue.sBoolean));
	}

	public SBooleanValue uncertaintyMaximized() {
		return new SBooleanValue(sBoolean.uncertaintyMaximized());
	}

	public SBooleanValue deduceY(Value yGivenX, Value yGivenNotX) {
		SBooleanValue sboolA = assertKindOfSBoolean(yGivenX);
		SBooleanValue sboolB = assertKindOfSBoolean(yGivenNotX);
		return new SBooleanValue(sBoolean.deduceY(sboolA.sBoolean, sboolB.sBoolean));
	}

	public UBooleanValue toUBoolean() {
		return new UBooleanValue(sBoolean.toUBoolean());
	}

	public RealValue belief() {
		return new RealValue(sBoolean.belief());
	}

	public RealValue disbelief() {
		return new RealValue(sBoolean.disbelief());
	}

	public RealValue uncertainty() {
		return new RealValue(sBoolean.uncertainty());
	}

	public RealValue baseRate() {
		return new RealValue(sBoolean.baseRate());
	}

	public RealValue projection() {
		return new RealValue(sBoolean.projection());
	}

	public SBooleanValue and(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		return new SBooleanValue(this.sBoolean.and(sBooleanValue.sBoolean));
	}

	@Override
	public UncertainBooleanValue not() {
		return new SBooleanValue(this.sBoolean.not());
	}

	public SBooleanValue or(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		return new SBooleanValue(this.sBoolean.or(sBooleanValue.sBoolean));
	}

	public SBooleanValue xor(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		return new SBooleanValue(this.sBoolean.xor(sBooleanValue.sBoolean));
	}

	public SBooleanValue equivalent(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		return new SBooleanValue(this.sBoolean.equivalent(sBooleanValue.sBoolean));
	}

	public SBooleanValue implies(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		return new SBooleanValue(this.sBoolean.implies(sBooleanValue.sBoolean));
	}

	public RealValue getRelativeWeight() {
		return new RealValue(sBoolean.getRelativeWeight());
	}

	public BooleanValue isAbsolute() {
		return BooleanValue.get(sBoolean.isAbsolute());
	}

	public BooleanValue isVacuous() {
		return BooleanValue.get(sBoolean.isVacuous());
	}

	public BooleanValue isCertain(Value threshold) {
		RealValue v = RealValue.valueOf(threshold);
		return BooleanValue.get(sBoolean.isCertain(v.value()));
	}

	public BooleanValue isDogmatic() {
		return BooleanValue.get(sBoolean.isDogmatic());
	}

	public BooleanValue isMaximizedUncertainty() {
		return BooleanValue.get(sBoolean.isMaximizedUncertainty());
	}

	public BooleanValue isUncertain(Value threshold) {
		RealValue v = RealValue.valueOf(threshold);
		return BooleanValue.get(sBoolean.isUncertain(v.value()));
	}

	public SBooleanValue uncertainOpinion() {
		return new SBooleanValue(sBoolean.uncertaintyMaximized());
	}

	public RealValue certainty() {
		return new RealValue(sBoolean.certainty());
	}

	public static SBooleanValue createDogmaticOpinion(Value projection, Value baseRate) {
		RealValue p = RealValue.valueOf(projection);
		RealValue br = RealValue.valueOf(baseRate);
		return new SBooleanValue(SBoolean.createDogmaticOpinion(p.value(), br.value()));
	}

	public static SBooleanValue createVacuousOpinion(Value projection) {
		RealValue p = RealValue.valueOf(projection);
		return new SBooleanValue(SBoolean.createVacuousOpinion(p.value()));
	}

	public SBooleanValue minimumFusion(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();
		collection.add(this.sBoolean);
		collection.add(sBooleanValue.sBoolean);
		return new SBooleanValue(SBoolean.minimumBeliefFusion(collection));
	}

	public SBooleanValue majorityFusion(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();
		collection.add(this.sBoolean);
		collection.add(sBooleanValue.sBoolean);
		return new SBooleanValue(SBoolean.majorityBeliefFusion(collection));
	}

	public SBooleanValue averageFusion(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();
		collection.add(this.sBoolean);
		collection.add(sBooleanValue.sBoolean);
		return new SBooleanValue(SBoolean.averageBeliefFusion(collection));
	}

	public SBooleanValue cumulativeFusion(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();
		collection.add(this.sBoolean);
		collection.add(sBooleanValue.sBoolean);
		return new SBooleanValue(SBoolean.cumulativeBeliefFusion(collection));
	}

	public SBooleanValue epistemicCumulativeFusion(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();
		collection.add(this.sBoolean);
		collection.add(sBooleanValue.sBoolean);
		return new SBooleanValue(SBoolean.epistemicCumulativeBeliefFusion(collection));
	}

	public SBooleanValue weightedFusion(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();
		collection.add(this.sBoolean);
		collection.add(sBooleanValue.sBoolean);
		return new SBooleanValue(SBoolean.weightedBeliefFusion(collection));
	}

	public SBooleanValue minimumBeliefFusion(Value value) {
		CollectionValue cValue = (CollectionValue) value;
		SequenceValue seq = cValue.asSequence();
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();  collection.add(this.sBoolean);
		Iterator<Value> it = seq.iterator();
		while (it.hasNext()) {
			Value v = it.next();
			SBooleanValue sBooleanValue = assertKindOfSBoolean(v);
			collection.add(sBooleanValue.sBoolean);
		}
		return new SBooleanValue(SBoolean.minimumBeliefFusion(collection));
	}
	
	public SBooleanValue majorityBeliefFusion(Value value) {
		CollectionValue cValue = (CollectionValue) value;
		SequenceValue seq = cValue.asSequence();
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();  collection.add(this.sBoolean);
		Iterator<Value> it = seq.iterator();
		while (it.hasNext()) {
			Value v = it.next();
			SBooleanValue sBooleanValue = assertKindOfSBoolean(v);
			collection.add(sBooleanValue.sBoolean);
		}
		return new SBooleanValue(SBoolean.majorityBeliefFusion(collection));
	}
	
	public SBooleanValue beliefConstraintFusion(Value value) {
		CollectionValue cValue = (CollectionValue) value;
		SequenceValue seq = cValue.asSequence();
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>(); collection.add(this.sBoolean);
		Iterator<Value> it = seq.iterator();
		while (it.hasNext()) {
			Value v = it.next();
			SBooleanValue sBooleanValue = assertKindOfSBoolean(v);
			collection.add(sBooleanValue.sBoolean);
		}
		return new SBooleanValue(org.tzi.use.uncertainty.datatypes.SBoolean.beliefConstraintFusion(collection));
	}
	
	public SBooleanValue averageBeliefFusion(Value value) {
		CollectionValue cValue = (CollectionValue) value;
		SequenceValue seq = cValue.asSequence();
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>(); collection.add(this.sBoolean);
		Iterator<Value> it = seq.iterator();
		while (it.hasNext()) {
			Value v = it.next();
			SBooleanValue sBooleanValue = assertKindOfSBoolean(v);
			collection.add(sBooleanValue.sBoolean);
		}
		return new SBooleanValue(SBoolean.averageBeliefFusion(collection));
	}
	
	public SBooleanValue aleatoryCumulativeBeliefFusion(Value value) {
		CollectionValue cValue = (CollectionValue) value;
		SequenceValue seq = cValue.asSequence();
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();  collection.add(this.sBoolean);
		Iterator<Value> it = seq.iterator();
		while (it.hasNext()) {
			Value v = it.next();
			SBooleanValue sBooleanValue = assertKindOfSBoolean(v);
			collection.add(sBooleanValue.sBoolean);
		}
		return new SBooleanValue(SBoolean.cumulativeBeliefFusion(collection));
	}
	
	public SBooleanValue epistemicCumulativeBeliefFusion(Value value) {
		CollectionValue cValue = (CollectionValue) value;
		SequenceValue seq = cValue.asSequence();
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();  collection.add(this.sBoolean);
		Iterator<Value> it = seq.iterator();
		while (it.hasNext()) {
			Value v = it.next();
			SBooleanValue sBooleanValue = assertKindOfSBoolean(v);
			collection.add(sBooleanValue.sBoolean);
		}
		return new SBooleanValue(SBoolean.epistemicCumulativeBeliefFusion(collection));
	}
	
	public SBooleanValue weightedBeliefFusion(Value value) {
		CollectionValue cValue = (CollectionValue) value;
		SequenceValue seq = cValue.asSequence();
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();  collection.add(this.sBoolean);
		Iterator<Value> it = seq.iterator();
		while (it.hasNext()) {
			Value v = it.next();
			SBooleanValue sBooleanValue = assertKindOfSBoolean(v);
			collection.add(sBooleanValue.sBoolean);
		}
		return new SBooleanValue(SBoolean.weightedBeliefFusion(collection));
	}
	
	public SBooleanValue consensusAndCompromiseFusion(Value value) {
		CollectionValue cValue = (CollectionValue) value;
		SequenceValue seq = cValue.asSequence();
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();  collection.add(this.sBoolean);
		Iterator<Value> it = seq.iterator();
		while (it.hasNext()) {
			Value v = it.next();
			SBooleanValue sBooleanValue = assertKindOfSBoolean(v);
			collection.add(sBooleanValue.sBoolean);
		}
		return new SBooleanValue(SBoolean.consensusAndCompromiseFusion(collection));
	}

	public SBooleanValue discount(Value value) {
		CollectionValue cValue = (CollectionValue) value;
		SequenceValue seq = cValue.asSequence();
		LinkedList<SBoolean> collection = new LinkedList<SBoolean>();
		Iterator<Value> it = seq.iterator();
		while (it.hasNext()) {
			Value v = it.next();
			SBooleanValue sBooleanValue = assertKindOfSBoolean(v);
			collection.add(sBooleanValue.sBoolean);
		}

		return new SBooleanValue(this.sBoolean.discount(collection));
	}

	
	public SBooleanValue applyOn(Value value) {
		UBooleanValue ubool = (UBooleanValue) value;
		return new SBooleanValue(sBoolean.applyOn(ubool.getuBoolean()));
	}

	public SBooleanValue min(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		return new SBooleanValue(this.sBoolean.min(sBooleanValue.sBoolean));
	}

	public SBooleanValue max(Value value) {
		SBooleanValue sBooleanValue = assertKindOfSBoolean(value);
		return new SBooleanValue(this.sBoolean.max(sBooleanValue.sBoolean));
	}

}
