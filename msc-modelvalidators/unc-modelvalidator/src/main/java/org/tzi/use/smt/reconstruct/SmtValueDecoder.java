package org.tzi.use.smt.reconstruct;

import java.math.BigInteger;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.solver.SmtValue;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.Value;

/**
 * Decodes one solved SMT value back to a USE {@link Value}, using the real declared attribute type
 * to tell STRING (an index into the domain's candidates) apart from INTEGER (the literal value
 * itself) -- both are encoded on an SMT Int sort by {@code AttributeEncoder} and cannot be told
 * apart from the raw {@link SmtValue} alone.
 */
public final class SmtValueDecoder {
  private SmtValueDecoder() {}

  public static Value decode(SmtValue raw, Type attributeType, AttributeDomain domain) {
    if (attributeType.isTypeOfString()) {
      int index = intValue(raw).intValueExact();
      return new StringValue(domain.enumeratedValues().get(index));
    }
    if (attributeType.isTypeOfInteger()) {
      return new IntegerValue(intValue(raw).intValueExact());
    }
    if (attributeType.isTypeOfReal()) {
      return new RealValue(rationalValue(raw).asBigDecimal(10).doubleValue());
    }
    if (attributeType.isTypeOfBoolean()) {
      return BooleanValue.get(boolValue(raw));
    }
    throw new IllegalArgumentException("unsupported attribute type for decoding: " + attributeType);
  }

  /** Decodes the two SMT Real terms that jointly represent one UReal attribute value. */
  public static URealValue decodeUReal(SmtValue rawValue, SmtValue rawUncertainty) {
    double value = rationalValue(rawValue).asBigDecimal(10).doubleValue();
    double uncertainty = rationalValue(rawUncertainty).asBigDecimal(10).doubleValue();
    return new URealValue(value, uncertainty);
  }

  /**
   * The exact decimal a solver-assigned Real symbol carries, at the same ten-place precision {@link
   * #decodeUReal} rounds to -- used to read a chosen measurement scenario back out of an EXISTS
   * assignment.
   */
  public static java.math.BigDecimal decodeReal(SmtValue raw) {
    return rationalValue(raw).asBigDecimal(10).stripTrailingZeros();
  }

  private static BigInteger intValue(SmtValue raw) {
    if (raw instanceof SmtValue.Int i) {
      return i.value();
    }
    throw new IllegalArgumentException("expected an integer model value, got " + raw);
  }

  private static SmtValue.Rational rationalValue(SmtValue raw) {
    if (raw instanceof SmtValue.Rational r) {
      return r;
    }
    if (raw instanceof SmtValue.Int i) {
      return new SmtValue.Rational(i.value(), BigInteger.ONE);
    }
    throw new IllegalArgumentException("expected a numeric model value, got " + raw);
  }

  private static boolean boolValue(SmtValue raw) {
    if (raw instanceof SmtValue.Bool b) {
      return b.value();
    }
    throw new IllegalArgumentException("expected a boolean model value, got " + raw);
  }
}
