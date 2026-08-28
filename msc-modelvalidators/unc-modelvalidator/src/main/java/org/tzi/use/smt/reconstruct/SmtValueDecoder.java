package org.tzi.use.smt.reconstruct;

import java.math.BigInteger;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.solver.SmtValue;
import org.tzi.use.uml.ocl.type.EnumType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.EnumValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.ocl.value.UIntegerValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.UStringValue;
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
    if (attributeType.isTypeOfEnum()) {
      int index = intValue(raw).intValueExact();
      return new EnumValue((EnumType) attributeType, domain.enumeratedValues().get(index));
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
   * Decodes the SMT Int representative and SMT Real uncertainty that jointly represent one UInteger
   * attribute value.
   *
   * <p>The representative is read with {@link #intValue}, NOT by rounding a decimal: the solver
   * assigned it on the Int sort, so a non-integral model value would mean the encoding declared the
   * wrong sort, and that must surface as a failure rather than be quietly rounded away here. The
   * rounding this slice relies on is the SOLVER's, performed while searching, not the decoder's.
   */
  public static UIntegerValue decodeUInteger(SmtValue rawValue, SmtValue rawUncertainty) {
    int value = intValue(rawValue).intValueExact();
    double uncertainty = rationalValue(rawUncertainty).asBigDecimal(10).doubleValue();
    return new UIntegerValue(value, uncertainty);
  }

  /**
   * Decodes the single SMT Real that represents one {@code UBoolean} attribute value.
   *
   * <p>The proposal canonicalises a UBoolean to one truth probability and its solver-representation
   * table says "no independent carried Boolean", so there is nothing else to read back: the value
   * component is reconstructed as {@code true} and the probability carries the whole meaning.
   * {@code UBooleanValue.valueOf(true, p)} is USE's own normalising factory, which is what returns
   * the {@code TRUE}/{@code FALSE} singletons at the endpoints -- reconstructing those by hand
   * would produce values USE considers unequal to its own.
   */
  public static UBooleanValue decodeUBoolean(SmtValue rawProbability) {
    return UBooleanValue.valueOf(
        true, rationalValue(rawProbability).asBigDecimal(10).doubleValue());
  }

  /**
   * Decodes the SMT Int spelling INDEX and SMT Real confidence that jointly represent one {@code
   * UString} attribute value.
   *
   * <p>The index is read against the same configured candidate list a crisp {@code String}
   * attribute's index is read against -- the proposal's "configured spellings become a finite Z3
   * enumeration" is literally the crisp String encoding, reused. It is read with {@link #intValue},
   * not by rounding: the solver assigned it on the Int sort, so a non-integral model value would
   * mean the encoding declared the wrong sort and must surface as a failure.
   *
   * <p>The second argument is passed to {@code UStringValue}'s parameter named {@code uncertainty},
   * which is a MISNOMER in USE: the constructor stores it in {@code UString.sConf} and {@code
   * confidence()} returns it unchanged. Reconstructing a confidence here is therefore correct, and
   * the complement would be wrong.
   */
  public static UStringValue decodeUString(
      SmtValue rawSpelling, SmtValue rawConfidence, AttributeDomain spellingDomain) {
    int index = intValue(rawSpelling).intValueExact();
    double confidence = rationalValue(rawConfidence).asBigDecimal(10).doubleValue();
    return new UStringValue(spellingDomain.enumeratedValues().get(index), confidence);
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
