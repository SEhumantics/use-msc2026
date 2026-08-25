package org.tzi.use.smt.encode;

import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import org.junit.Test;
import org.tzi.use.uncertainty.datatypes.UReal;

public class URealThresholdBoundaryTest {

  @Test
  public void enclosureStraddlesTheRealUseEvaluatorTransition() {
    URealThresholdBoundary.Enclosure enclosure =
        URealThresholdBoundary.enclose(new BigDecimal("0.95"));

    assertTrue(probability(enclosure.lower()) < 0.95);
    assertTrue(probability(enclosure.upper()) >= 0.95);
    assertTrue(enclosure.upper().subtract(enclosure.lower()).doubleValue() < 1.0e-8);
  }

  private static double probability(BigDecimal standardizedMean) {
    return new UReal(standardizedMean.doubleValue(), 1.0).gt(new UReal(0.0)).getC();
  }
}
