package org.tzi.use.smt.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
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

  /**
   * Two top-level wrapper forms: the parser used to read EXACTLY ONE and drop the rest without a
   * word, so {@code y} vanished and the caller could not tell an incomplete model from a complete
   * one.
   */
  @Test
  public void everyTopLevelFormContributesItsBindings() {
    String model =
        """
        (
          (define-fun x () Int 1)
        )
        (
          (define-fun y () Int 2)
        )
        """;

    Map<String, SmtValue> values = SmtModelParser.parse(model);

    assertEquals(new SmtValue.Int(BigInteger.ONE), values.get("x"));
    assertEquals(new SmtValue.Int(BigInteger.TWO), values.get("y"));
    assertEquals(2, values.size());
  }

  /**
   * The reachable shape: {@code SolverProcess}'s one-shot mode merges the solver's stderr into its
   * stdout, so a diagnostic line can land BETWEEN the {@code sat} verdict and the model. The model
   * is then the second form -- and used to be dropped, yielding an empty map.
   */
  @Test
  public void aLeadingErrorLineDoesNotHideTheModelBehindIt() {
    String model =
        """
        (error "line 1 column 10: unknown constant q")
        (
          (define-fun x () Int 3)
        )
        """;

    Map<String, SmtValue> values = SmtModelParser.parse(model);

    assertEquals(new SmtValue.Int(BigInteger.valueOf(3)), values.get("x"));
    assertEquals(1, values.size());
  }

  /** The mirror: a diagnostic AFTER the model must not stop the model from being read either. */
  @Test
  public void aTrailingErrorLineStillLeavesTheModelParsed() {
    String model =
        """
        (
          (define-fun x () Int 4)
        )
        (error "model is not available")
        """;

    Map<String, SmtValue> values = SmtModelParser.parse(model);

    assertEquals(new SmtValue.Int(BigInteger.valueOf(4)), values.get("x"));
    assertEquals(1, values.size());
  }

  /** The legacy {@code (model ...)} wrapper keeps working, alongside a sibling form. */
  @Test
  public void theLegacyModelWrapperStillParses() {
    String model =
        """
        (model
          (define-fun x () Int 5)
        )
        (
          (define-fun y () Bool true)
        )
        """;

    Map<String, SmtValue> values = SmtModelParser.parse(model);

    assertEquals(new SmtValue.Int(BigInteger.valueOf(5)), values.get("x"));
    assertEquals(new SmtValue.Bool(true), values.get("y"));
  }

  /**
   * A bare non-list token is not a model. It used to yield an empty map SILENTLY, which is
   * indistinguishable downstream from a real model that binds nothing; now it names itself and its
   * offset.
   */
  @Test
  public void aBareTokenIsRejectedLoudly() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> SmtModelParser.parse("unsupported"));

    assertTrue(thrown.getMessage(), thrown.getMessage().contains("unsupported"));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("offset 0"));
  }

  /** A junk token AFTER a good model is just as loud -- the drop used to swallow it too. */
  @Test
  public void aTrailingJunkTokenIsRejectedLoudly() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SmtModelParser.parse("((define-fun x () Int 1))\nunsupported"));

    assertTrue(thrown.getMessage(), thrown.getMessage().contains("unsupported"));
  }

  /** Truncated output is not a model: an unterminated list says so instead of returning a prefix. */
  @Test
  public void truncatedOutputIsRejectedLoudly() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SmtModelParser.parse("(\n  (define-fun x () Int 1)\n"));

    assertTrue(thrown.getMessage(), thrown.getMessage().contains("unterminated"));
  }

  /**
   * A stray closing paren is rejected rather than read as an empty atom -- which, without the
   * check, would never advance the cursor and would spin the read loop forever.
   */
  @Test
  public void aStrayClosingParenIsRejectedLoudlyRatherThanLooping() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SmtModelParser.parse("((define-fun x () Int 1))\n)"));

    assertTrue(thrown.getMessage(), thrown.getMessage().contains("unbalanced"));
  }
}
