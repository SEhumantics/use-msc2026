package org.tzi.use.smt.solver;

import java.util.List;

/** A term in the SMT-LIB output language. Deliberately minimal: an atom or an application. */
public sealed interface SmtTerm {

    String toSmtLib();

    /** A literal or a symbol, emitted verbatim. */
    record Atom(String symbol) implements SmtTerm {
        @Override
        public String toSmtLib() {
            return symbol;
        }
    }

    /** An operator applied to arguments, emitted as an S-expression. */
    record App(String op, List<SmtTerm> args) implements SmtTerm {
        @Override
        public String toSmtLib() {
            StringBuilder out = new StringBuilder("(").append(op);
            for (SmtTerm arg : args) {
                out.append(' ').append(arg.toSmtLib());
            }
            return out.append(')').toString();
        }
    }
}
