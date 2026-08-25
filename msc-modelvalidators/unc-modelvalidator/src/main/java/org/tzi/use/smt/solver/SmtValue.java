package org.tzi.use.smt.solver;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** A decoded value from a solver model. */
public sealed interface SmtValue {

  record Bool(boolean value) implements SmtValue {}

  record Int(BigInteger value) implements SmtValue {}

  /** An exact rational whose rounding is explicit and deferred to its consumer. */
  record Rational(BigInteger numerator, BigInteger denominator) implements SmtValue {

    public Rational {
      if (denominator.signum() == 0) {
        throw new IllegalArgumentException("zero denominator");
      }
    }

    public BigDecimal asBigDecimal(int scale) {
      return new BigDecimal(numerator)
          .divide(new BigDecimal(denominator), scale, RoundingMode.HALF_EVEN);
    }
  }
}
