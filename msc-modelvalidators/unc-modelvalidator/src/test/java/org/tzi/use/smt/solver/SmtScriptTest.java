package org.tzi.use.smt.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.Test;

public class SmtScriptTest {

  @Test
  public void emitsHeaderDeclarationsAssertionsAndFooter() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("x", SmtSort.REAL);
    script.declareConst("p", SmtSort.BOOL);
    script.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("0.30"))));
    script.assertThat(Smt.sym("p"));

    String text = script.toSmtLib();

    assertTrue(text, text.startsWith("(set-logic QF_LIRA)\n"));
    assertTrue(text, text.contains("(declare-const x Real)"));
    assertTrue(text, text.contains("(declare-const p Bool)"));
    assertTrue(text, text.contains("(assert (> x 0.30))"));
    assertTrue(text, text.contains("(assert p)"));
    assertTrue(text, text.contains("(check-sat)"));
    assertTrue(text, text.trim().endsWith("(get-model)"));
  }

  @Test
  public void negativeRealsUseSmtLibUnaryMinus() {
    assertEquals("(- 2.0)", Smt.realLit(new BigDecimal("-2.0")).toSmtLib());
  }

  @Test
  public void negativeIntegersUseSmtLibUnaryMinus() {
    assertEquals("(- 7)", Smt.intLit(BigInteger.valueOf(-7)).toSmtLib());
  }

  @Test
  public void emptyConjunctionIsTrueAndSingletonIsTransparent() {
    assertEquals("true", Smt.and(List.of()).toSmtLib());
    assertEquals("p", Smt.and(List.of(Smt.sym("p"))).toSmtLib());
  }

  @Test
  public void emptyDisjunctionIsFalse() {
    assertEquals("false", Smt.or(List.of()).toSmtLib());
  }

  @Test
  public void declaringTheSameNameTwiceIsRejected() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("x", SmtSort.REAL);
    try {
      script.declareConst("x", SmtSort.INT);
      org.junit.Assert.fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("x"));
    }
  }
}
