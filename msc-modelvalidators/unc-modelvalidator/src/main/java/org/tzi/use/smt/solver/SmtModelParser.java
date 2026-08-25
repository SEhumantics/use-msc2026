package org.tzi.use.smt.solver;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses a solver {@code (get-model)} block into decoded values. */
public final class SmtModelParser {

    private SmtModelParser() {
    }

    public static Map<String, SmtValue> parse(String modelText) {
        Map<String, SmtValue> values = new LinkedHashMap<>();
        if (modelText == null || modelText.isBlank()) {
            return values;
        }
        Object parsed = new Reader(modelText).readForm();
        collect(parsed, values);
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
        private final String text;
        private int position;

        Reader(String text) {
            this.text = text;
        }

        Object readForm() {
            skipWhitespace();
            if (position >= text.length()) {
                return List.of();
            }
            if (text.charAt(position) == '(') {
                position++;
                List<Object> items = new ArrayList<>();
                while (true) {
                    skipWhitespace();
                    if (position >= text.length()) {
                        break;
                    }
                    if (text.charAt(position) == ')') {
                        position++;
                        break;
                    }
                    items.add(readForm());
                }
                return items;
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
    }
}
