package org.tzi.use.smt.solver;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses a solver {@code (get-model)} block into decoded values. */
public final class SmtModelParser {

  private SmtModelParser() {}

  /**
   * Every top-level form in {@code modelText}, merged.
   *
   * <p>This used to read EXACTLY ONE form and collect only that, which silently dropped everything
   * after it and silently yielded an empty map for anything that was not a list at all. Both are
   * reachable: {@code SolverProcess}'s one-shot mode runs the solver with {@code
   * redirectErrorStream(true)}, so a solver stderr line can land between the {@code sat} verdict
   * and the model itself, and then the model was the SECOND form -- dropped without a word, and an
   * empty model is not distinguishable downstream from a model that legitimately binds nothing.
   *
   * <p>So: read until the input is exhausted, harvest {@code define-fun} bindings from every form
   * (later bindings of one name win, though a solver never emits a name twice), and raise a LOUD,
   * located error on a top-level form that is not a list -- a bare {@code unsupported} token, a
   * stray {@code )}, a truncated {@code (}. A list that carries no {@code define-fun} -- notably
   * {@code (error "...")} -- is skipped rather than rejected, because a diagnostic printed
   * alongside a real model must not stop that model from being read. The legacy {@code (model
   * ...)} wrapper needs no special case: {@link #collect} recurses into any list.
   */
  public static Map<String, SmtValue> parse(String modelText) {
    Map<String, SmtValue> values = new LinkedHashMap<>();
    if (modelText == null || modelText.isBlank()) {
      return values;
    }
    Reader reader = new Reader(modelText);
    while (reader.hasMoreForms()) {
      int start = reader.position();
      Object form = reader.readForm();
      if (!(form instanceof List<?> list)) {
        throw new IllegalArgumentException(
            "unrecognised top-level form in solver model output at offset "
                + start
                + ": "
                + Reader.snippet(modelText, start));
      }
      collect(list, values);
    }
    return values;
  }

  private static void collect(Object form, Map<String, SmtValue> into) {
    if (!(form instanceof List<?> list)) {
      return;
    }
    if (!list.isEmpty() && "define-fun".equals(list.get(0))) {
      String name = (String) list.get(1);
      into.put(name, value(list.get(4)));
      return;
    }
    for (Object child : list) {
      collect(child, into);
    }
  }

  private static SmtValue value(Object form) {
    if (form instanceof String atom) {
      if ("true".equals(atom)) {
        return new SmtValue.Bool(true);
      }
      if ("false".equals(atom)) {
        return new SmtValue.Bool(false);
      }
      if (atom.contains(".")) {
        return rationalOf(new BigDecimal(atom));
      }
      return new SmtValue.Int(new BigInteger(atom));
    }
    List<?> list = (List<?>) form;
    String op = (String) list.get(0);
    if ("-".equals(op) && list.size() == 2) {
      return negate(value(list.get(1)));
    }
    if ("/".equals(op) && list.size() == 3) {
      SmtValue.Rational left = asRational(value(list.get(1)));
      SmtValue.Rational right = asRational(value(list.get(2)));
      return new SmtValue.Rational(
          left.numerator().multiply(right.denominator()),
          left.denominator().multiply(right.numerator()));
    }
    throw new IllegalArgumentException("unsupported model value form: " + form);
  }

  private static SmtValue negate(SmtValue value) {
    if (value instanceof SmtValue.Int integer) {
      return new SmtValue.Int(integer.value().negate());
    }
    if (value instanceof SmtValue.Rational rational) {
      return new SmtValue.Rational(rational.numerator().negate(), rational.denominator());
    }
    throw new IllegalArgumentException("cannot negate " + value);
  }

  private static SmtValue.Rational asRational(SmtValue value) {
    if (value instanceof SmtValue.Rational rational) {
      return rational;
    }
    if (value instanceof SmtValue.Int integer) {
      return new SmtValue.Rational(integer.value(), BigInteger.ONE);
    }
    throw new IllegalArgumentException("not numeric: " + value);
  }

  private static SmtValue.Rational rationalOf(BigDecimal decimal) {
    BigDecimal stripped = decimal.stripTrailingZeros();
    int scale = Math.max(stripped.scale(), 0);
    BigInteger denominator = BigInteger.TEN.pow(scale);
    BigInteger numerator = stripped.movePointRight(scale).toBigIntegerExact();
    return new SmtValue.Rational(numerator, denominator);
  }

  /** Minimal S-expression reader: nested lists of atoms. */
  private static final class Reader {
    private static final int SNIPPET_LENGTH = 40;

    private final String text;
    private int position;

    Reader(String text) {
      this.text = text;
    }

    /** True when non-whitespace input remains; leaves the cursor on that input. */
    boolean hasMoreForms() {
      skipWhitespace();
      return position < text.length();
    }

    int position() {
      return position;
    }

    /**
     * Reads one form. An unterminated {@code (} and a stray {@code )} are ERRORS rather than
     * quietly-truncated or empty results: truncated solver output is not a model, and letting a
     * stray {@code )} return an empty atom would also spin {@link #parse}'s read loop forever,
     * since the cursor would never advance past it.
     */
    Object readForm() {
      skipWhitespace();
      if (position >= text.length()) {
        return List.of();
      }
      char first = text.charAt(position);
      if (first == '(') {
        int opened = position;
        position++;
        List<Object> items = new ArrayList<>();
        while (true) {
          skipWhitespace();
          if (position >= text.length()) {
            throw new IllegalArgumentException(
                "unterminated '(' at offset "
                    + opened
                    + " in solver model output: "
                    + snippet(text, opened));
          }
          if (text.charAt(position) == ')') {
            position++;
            break;
          }
          items.add(readForm());
        }
        return items;
      }
      if (first == ')') {
        throw new IllegalArgumentException(
            "unbalanced ')' at offset "
                + position
                + " in solver model output: "
                + snippet(text, position));
      }
      int start = position;
      while (position < text.length()
          && !Character.isWhitespace(text.charAt(position))
          && text.charAt(position) != '('
          && text.charAt(position) != ')') {
        position++;
      }
      return text.substring(start, position);
    }

    private void skipWhitespace() {
      while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
        position++;
      }
    }

    /** The offending text itself, bounded, so the message locates the problem in real output. */
    static String snippet(String text, int at) {
      int end = Math.min(text.length(), at + SNIPPET_LENGTH);
      String shown = text.substring(Math.min(at, text.length()), end);
      return "'" + shown + (end < text.length() ? "..." : "") + "'";
    }
  }
}
