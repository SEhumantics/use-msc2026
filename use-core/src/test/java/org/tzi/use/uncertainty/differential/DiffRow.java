package org.tzi.use.uncertainty.differential;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One comparison: an operation, the inputs it was applied to, what each side produced, and the
 * verdict. Immutable; renders itself as one TSV record.
 *
 * <p>{@code historical} / {@code ported} carry one of three things: a {@link UValue#canonical()}
 * form; {@code THROWN:<throwable class name>} when that side threw; or
 * {@code HARNESS_ERROR:<throwable class name>} when the harness could not drive that side at all
 * (see {@link DiffVerdict#HARNESS_ERROR}). That keeps the report one flat column pair whatever
 * happened, while keeping "the code under test threw" and "the harness failed" distinguishable in
 * the column itself and not only in the verdict.
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

    /**
     * Marker used when the <em>harness</em> could not drive that side at all. Textually distinct
     * from {@link #thrown(Throwable)} so that a harness failure can never be mistaken, by eye or by
     * grep, for a throw by the code under test.
     */
    public static String harnessError(Throwable t) {
        return "HARNESS_ERROR:" + t.getClass().getName();
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
