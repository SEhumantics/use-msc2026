package org.tzi.use.smt.encode;

/** An inclusive cardinality bound. {@code upper == -1} means unbounded. */
public record Multiplicity(int lower, int upper) {
    public Multiplicity {
        if (lower < 0 || (upper != -1 && upper < lower)) {
            throw new IllegalArgumentException("invalid multiplicity: lower=" + lower + ", upper=" + upper);
        }
    }
    public boolean isUnbounded() { return upper == -1; }
}
