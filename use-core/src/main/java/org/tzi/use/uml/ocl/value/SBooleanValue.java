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
	 * Compares this opinion to {@code o}, ordering {@link SBooleanValue} arguments lexicographically
	 * over their four masses (belief, disbelief, uncertainty, base rate) and falling back to a
	 * {@code toString()} comparison for any other kind of {@link Value}, so the order stays total.
	 *
	 * @implNote The fork's entire body was {@code return 0;}: every opinion compared equal to every
	 *     {@code Value}, including {@code UndefinedValue} and {@code StringValue}. This deliberately
	 *     does not delegate to {@code SBoolean.compareTo}: that method treats opinions within 0.001 L1
	 *     distance as equal, which is not a transitive relation, and a non-transitive comparator makes
	 *     Java's TimSort throw {@code IllegalArgumentException} at 32+ elements — trading a mis-sort
	 *     for a crash. The lexicographic order here is a genuine total order, consistent with {@link
	 *     #equals(Object)}. Residual: this does not repair the pre-existing asymmetry between {@code
	 *     RealValue#compareTo} and {@link URealValue#compareTo(Value)} elsewhere in the package, so a
	 *     mixed sort against those two remains technically undefined — latent rather than live at this
	 *     corpus's sizes.
	 * @see "docs/port2/b7-fix-plan.md &sect;2 M-18 &mdash; deviation ledger (decided 2026-08-17)"
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

	/**
	 * Narrows {@code value} to {@link SBooleanValue}, delegating to {@link #valueOf(Value)}.
	 *
	 * @implNote Deliberately throws the broad {@code RuntimeException} rather than narrowing to
	 *     {@code IllegalArgumentException}: some callers ({@code ExpConstSBoolean}, {@code
	 *     ASTSBooleanLiteral}) catch {@code Exception} generically and the full downstream catch set
	 *     could not be enumerated, so narrowing risks silently changing behavior for no benefit.
	 * @param value the value to coerce
	 * @return {@code value} narrowed to {@code SBoolean}
	 * @throws RuntimeException if {@code value} is not a kind of {@code SBoolean} (deliberately the
	 *     broad type, see {@code @implNote})
	 * @see "docs/port2/b7-fix-plan.md &sect;2 M-6 &mdash; deviation ledger (decided 2026-08-17)"
	 */
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

	/**
	 * Deduces the opinion about {@code y} from a conditional opinion pair, per the vendored
	 * {@code uncertainty.datatypes.SBoolean#deduceY}'s eight-case subjective-logic deduction.
	 *
	 * @implNote {@code SBoolean.deduceY} (vendored, not editable — see its file header) has three
	 *     hazards worth knowing before touching anything nearby, since the source itself is
	 *     off-limits to comment:
	 *     <ol>
	 *     <li><b>Six unguarded divisors, not the four you'd guess from a skim.</b> The four
	 *     non-difference divisors ({@code b+a*u}, {@code d+(1-a)*u}, {@code y.a}, {@code 1-y.a}) are
	 *     genuinely unguarded. Of the four <em>difference</em> divisors, two are safe (each sits
	 *     behind a strict {@code >} branch guard that keeps it nonzero), but the other two sit
	 *     behind {@code <=} guards that <em>permit equality</em> and so can still be zero: case
	 *     III.A.1's divisor is correctly guarded (strict), but case III.B.2's
	 *     {@code (yGivenNotX.b - yGivenX.b)} can be zero under its own {@code <=} branch condition.
	 *     <li><b>The eight {@code K}-selecting blocks are sequential {@code if}s, not
	 *     {@code else if}.</b> A later block's assignment silently overwrites an earlier one where
	 *     both fire. The case-II family partitions cleanly (all four compare against the same
	 *     threshold expression), but the case-III family does not — III.A.1 compares against a
	 *     different threshold than III.A.2/III.B.1/III.B.2, so for some inputs two case-III blocks
	 *     both fire and the textually-later one wins; for the complementary inputs none fire and
	 *     {@code K} stays {@code 0}. A bug-compatible port must preserve the source order of the
	 *     eight blocks exactly — converting them to {@code else if} to "clean up" the dead-looking
	 *     duplication would be a real behavior change, not a refactor.
	 *     <li><b>Two numerators mix belief and disbelief accumulators</b> (case III.A.2 subtracts a
	 *     disbelief term from a belief accumulator; case III.B.1 subtracts a belief term from a
	 *     disbelief accumulator), unlike their type-consistent case-II siblings. These read as
	 *     transcription errors in the upstream oracle itself — but a verbatim port must reproduce
	 *     them exactly, since {@code SBoolean.java} is byte-for-byte vendored.
	 *     </ol>
	 */
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

	/*
	 * Found and removed at S9 (dead-code sweep, not a B7 ledger row): minimumFusion, majorityFusion,
	 * averageFusion, cumulativeFusion, epistemicCumulativeFusion, weightedFusion used to live here,
	 * each called only from a matching enum constant in StandardOperationsSBoolean.java that was
	 * commented out byte-identically in the fork's own source. With no live registration, these six
	 * had no grammar path at all -- ungrammared semantics code. See
	 * StandardOperationsSBoolean.java's removal note (same commit) for the full account; the
	 * equivalent live, registered operations are minimumBeliefFusion/majorityBeliefFusion/
	 * averageBeliefFusion/cumulativeBeliefFusion/epistemicCumulativeBeliefFusion/weightedBeliefFusion
	 * just below.
	 */

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
