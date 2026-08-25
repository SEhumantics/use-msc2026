package org.tzi.use.smt.solver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An SMT-LIB script under construction: a logic, declarations, and assertions. */
public final class SmtScript {

    private final String logic;
    private final Map<String, SmtSort> declarations = new LinkedHashMap<>();
    private final List<SmtTerm> assertions = new ArrayList<>();

    public SmtScript(String logic) {
        this.logic = logic;
    }

    public void declareConst(String name, SmtSort sort) {
        SmtSort previous = declarations.putIfAbsent(name, sort);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "constant '" + name + "' is already declared with sort " + previous.smtName());
        }
    }

    public void assertThat(SmtTerm term) {
        assertions.add(term);
    }

    public Set<String> declaredNames() {
        return declarations.keySet();
    }

    public String toSmtLib() {
        StringBuilder out = new StringBuilder();
        out.append("(set-logic ").append(logic).append(")\n");
        for (Map.Entry<String, SmtSort> declaration : declarations.entrySet()) {
            out.append("(declare-const ")
                    .append(declaration.getKey())
                    .append(' ')
                    .append(declaration.getValue().smtName())
                    .append(")\n");
        }
        for (SmtTerm assertion : assertions) {
            out.append("(assert ").append(assertion.toSmtLib()).append(")\n");
        }
        out.append("(check-sat)\n");
        out.append("(get-model)");
        return out.toString();
    }
}
