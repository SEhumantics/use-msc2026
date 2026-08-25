package org.tzi.use.smt.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import org.junit.Test;

public class SmtModelParserTest {

  @Test
  public void parsesBooleanIntegerAndDecimalDefinitions() {
    String model =
        """
        (
          (define-fun p () Bool
            true)
          (define-fun n () Int
            7)
          (define-fun x () Real
            0.5)
        )
        """;

    Map<String, SmtValue> values = SmtModelParser.parse(model);

    assertEquals(new SmtValue.Bool(true), values.get("p"));
    assertEquals(new SmtValue.Int(BigInteger.valueOf(7)), values.get("n"));
    assertEquals(new BigDecimal("0.50"), ((SmtValue.Rational) values.get("x")).asBigDecimal(2));
  }

  @Test
  public void parsesRationalsAndNegatedValues() {
    String model =
        """
        (
          (define-fun a () Real
            (/ 3.0 4.0))
          (define-fun b () Real
            (- 1.25))
          (define-fun c () Int
            (- 7))
        )
        """;

    Map<String, SmtValue> values = SmtModelParser.parse(model);

    assertEquals(new BigDecimal("0.75"), ((SmtValue.Rational) values.get("a")).asBigDecimal(2));
    assertEquals(new BigDecimal("-1.25"), ((SmtValue.Rational) values.get("b")).asBigDecimal(2));
    assertEquals(new SmtValue.Int(BigInteger.valueOf(-7)), values.get("c"));
  }

  @Test
  public void parsesNegatedRationals() {
    String model = "((define-fun a () Real (- (/ 1.0 3.0))))";
    SmtValue.Rational value = (SmtValue.Rational) SmtModelParser.parse(model).get("a");
    assertEquals(new BigDecimal("-0.333"), value.asBigDecimal(3));
  }

  @Test
  public void emptyModelYieldsEmptyMap() {
    assertTrue(SmtModelParser.parse("").isEmpty());
  }
}
