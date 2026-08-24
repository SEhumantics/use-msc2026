package org.tzi.use.smt.solver;

/** Raised when the pinned SMT solver cannot be resolved or does not match its pinned version. */
public class SolverConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SolverConfigurationException(String message) {
        super(message);
    }

    public SolverConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
