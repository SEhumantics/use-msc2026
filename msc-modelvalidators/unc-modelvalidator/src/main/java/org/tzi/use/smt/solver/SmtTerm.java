package org.tzi.use.smt.solver;

import java.util.List;

/** A term in the SMT-LIB output language. */
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

  /** One simultaneous SMT-LIB {@code let} binding. */
  record Binding(String symbol, SmtTerm value) {}

  /** A native SMT-LIB {@code (let ((name value) ...) body)} term. */
  record Let(List<Binding> bindings, SmtTerm body) implements SmtTerm {
    public Let {
      bindings = List.copyOf(bindings);
      if (bindings.isEmpty()) {
        throw new IllegalArgumentException("let requires at least one binding");
      }
    }

    @Override
    public String toSmtLib() {
      StringBuilder out = new StringBuilder("(let (");
      for (int i = 0; i < bindings.size(); i++) {
        if (i > 0) {
          out.append(' ');
        }
        Binding binding = bindings.get(i);
        out.append('(')
            .append(binding.symbol())
            .append(' ')
            .append(binding.value().toSmtLib())
            .append(')');
      }
      return out.append(") ").append(body.toSmtLib()).append(')').toString();
    }
  }
}
