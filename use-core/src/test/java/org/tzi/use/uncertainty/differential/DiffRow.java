package org.tzi.use.uncertainty.differential;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One comparison: an operation, the inputs it was applied to, what each side produced, and the
 * verdict. Immutable; renders itself as one TSV record.
 *
 * <p>{@code historical} / {@code ported} carry either a {@link UValue#canonical()} form or, when
 * that side threw, {@code THROWN:<throwable class name>}. That keeps the report one flat column
 * pair whatever happened.
 *
 * <p>Test-scoped. Not part of the product.
 */
public final class DiffRow {

    /** Column header written above the rows; kept in sync with {@link #toTsv()} by construction. */
    public static final String TSV_HEADER =
            String.join("\t", "index", "operation", "inputs", "historical", "ported", "verdict", "note");

    private final int index;
    private final String operation;
    private final List<String> inputs;
    private final String historical;
    private final String ported;
    private final DiffVerdict verdict;
    private final String note;

    public DiffRow(int index, String operation, List<String> inputs, String historical, String ported,
                   DiffVerdict verdict, String note) {
        this.index = index;
        this.operation = operation;
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        this.historical = historical;
        this.ported = ported;
        this.verdict = verdict;
        this.note = note == null ? "" : note;
    }

    public int index() {
        return index;
    }

    public String operation() {
        return operation;
    }

    public List<String> inputs() {
        return inputs;
    }

    public String historical() {
        return historical;
    }

    public String ported() {
        return ported;
    }

    public DiffVerdict verdict() {
        return verdict;
    }

    public String note() {
        return note;
    }

    /** Marker used in the result columns when a side threw instead of returning. */
    public static String thrown(Throwable t) {
        return "THROWN:" + t.getClass().getName();
    }

    /** One TSV record, without the trailing newline. All fields are already tab- and newline-free. */
    public String toTsv() {
        return String.join("\t",
                Integer.toString(index),
                scrub(operation),
                scrub(String.join(" | ", inputs)),
                scrub(historical),
                scrub(ported),
                verdict.name(),
                scrub(note));
    }

    /**
     * Defence in depth. {@link UValue#canonical()} already escapes control characters, but a
     * throwable class name or a caller-supplied note is not guaranteed to.
     */
    private static String scrub(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    @Override
    public String toString() {
        return toTsv();
    }
}
