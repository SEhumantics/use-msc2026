package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.*;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

public class ForAllTranslationTest {

    @Test
    public void titleIsKeyOnTheRealAstRejectsTwoExistingBooksWithTheSameTitle() throws Exception {
        MModel model = compileLibrary();
        MClassInvariant inv = findInvariant(model, "titleIsKey");

        SmtScript script = new SmtScript("QF_LIA");
        ObjectSlots books = ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 1, 2))).get("Book");
        AttributeDomain titleDomain = new AttributeDomain("Book", "title", null,
                List.of("DBforDummies", "IntrotoAI", "PrincsofNW"), null, null);
        AttributeValues titleValues = AttributeEncoder.encode(script, books, "title", AttributeType.STRING, titleDomain);

        TranslationContext ctx = new TranslationContext(
                Map.of("b1", new VariableBinding("Book", 0)),
                Map.of("Book.title", titleValues),
                Map.of("Book.title", titleDomain),
                Map.of("Book", books));
        var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

        script.assertThat(Smt.sym("Book_0_exists"));
        script.assertThat(Smt.sym("Book_1_exists"));
        script.assertThat(Smt.eq(Smt.sym(titleValues.valueNames().get(0)), Smt.sym(titleValues.valueNames().get(1))));
        script.assertThat(translated);

        assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
    }

    @Test
    public void titleIsKeyOnTheRealAstIsSatisfiableWithDistinctTitles() throws Exception {
        MModel model = compileLibrary();
        MClassInvariant inv = findInvariant(model, "titleIsKey");

        SmtScript script = new SmtScript("QF_LIA");
        ObjectSlots books = ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 1, 2))).get("Book");
        AttributeDomain titleDomain = new AttributeDomain("Book", "title", null,
                List.of("DBforDummies", "IntrotoAI", "PrincsofNW"), null, null);
        AttributeValues titleValues = AttributeEncoder.encode(script, books, "title", AttributeType.STRING, titleDomain);

        TranslationContext ctx = new TranslationContext(
                Map.of("b1", new VariableBinding("Book", 0)),
                Map.of("Book.title", titleValues),
                Map.of("Book.title", titleDomain),
                Map.of("Book", books));
        var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

        script.assertThat(Smt.sym("Book_0_exists"));
        script.assertThat(Smt.sym("Book_1_exists"));
        script.assertThat(translated);

        assertEquals(SolverOutcome.SAT, solve(script).outcome());
    }

    /** Proves the existence guard is load-bearing: a duplicate on a non-existent slot is harmless. */
    @Test
    public void titleIsKeyIsVacuouslySatisfiedWhenTheSecondSlotDoesNotExist() throws Exception {
        MModel model = compileLibrary();
        MClassInvariant inv = findInvariant(model, "titleIsKey");

        SmtScript script = new SmtScript("QF_LIA");
        ObjectSlots books = ObjectSlotEncoder.encode(script, List.of(new ClassScope("Book", 1, 2))).get("Book");
        AttributeDomain titleDomain = new AttributeDomain("Book", "title", null,
                List.of("DBforDummies", "IntrotoAI", "PrincsofNW"), null, null);
        AttributeValues titleValues = AttributeEncoder.encode(script, books, "title", AttributeType.STRING, titleDomain);

        TranslationContext ctx = new TranslationContext(
                Map.of("b1", new VariableBinding("Book", 0)),
                Map.of("Book.title", titleValues),
                Map.of("Book.title", titleDomain),
                Map.of("Book", books));
        var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

        script.assertThat(Smt.sym("Book_0_exists"));
        script.assertThat(Smt.not(Smt.sym("Book_1_exists")));
        script.assertThat(Smt.eq(Smt.sym(titleValues.valueNames().get(0)), Smt.sym(titleValues.valueNames().get(1))));
        script.assertThat(translated);

        assertEquals(SolverOutcome.SAT, solve(script).outcome());
    }

    /** A different class, different variable names, rules out anything accidentally Book-specific. */
    @Test
    public void nameIsKeyOnADifferentClassAlsoRejectsADuplicate() throws Exception {
        MModel model = compileLibrary();
        MClassInvariant inv = findInvariant(model, "nameIsKey");

        SmtScript script = new SmtScript("QF_LIA");
        ObjectSlots users = ObjectSlotEncoder.encode(script, List.of(new ClassScope("User", 1, 2))).get("User");
        AttributeDomain nameDomain = new AttributeDomain("User", "name", null, List.of("Ada", "Bob", "Cyd"), null, null);
        AttributeValues nameValues = AttributeEncoder.encode(script, users, "name", AttributeType.STRING, nameDomain);

        TranslationContext ctx = new TranslationContext(
                Map.of("u1", new VariableBinding("User", 0)),
                Map.of("User.name", nameValues),
                Map.of("User.name", nameDomain),
                Map.of("User", users));
        var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

        script.assertThat(Smt.sym("User_0_exists"));
        script.assertThat(Smt.sym("User_1_exists"));
        script.assertThat(Smt.eq(Smt.sym(nameValues.valueNames().get(0)), Smt.sym(nameValues.valueNames().get(1))));
        script.assertThat(translated);

        assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
    }

    /** The third target invariant, same shape, on a third class — completes coverage of all three. */
    @Test
    public void signatureIsKeyOnAThirdClassAlsoRejectsADuplicate() throws Exception {
        MModel model = compileLibrary();
        MClassInvariant inv = findInvariant(model, "signatureIsKey");

        SmtScript script = new SmtScript("QF_LIA");
        ObjectSlots copies = ObjectSlotEncoder.encode(script, List.of(new ClassScope("Copy", 1, 2))).get("Copy");
        AttributeDomain sigDomain = new AttributeDomain("Copy", "signature", null,
                List.of("DBS42", "DBS43", "NW21"), null, null);
        AttributeValues sigValues = AttributeEncoder.encode(script, copies, "signature", AttributeType.STRING, sigDomain);

        TranslationContext ctx = new TranslationContext(
                Map.of("c1", new VariableBinding("Copy", 0)),
                Map.of("Copy.signature", sigValues),
                Map.of("Copy.signature", sigDomain),
                Map.of("Copy", copies));
        var translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

        script.assertThat(Smt.sym("Copy_0_exists"));
        script.assertThat(Smt.sym("Copy_1_exists"));
        script.assertThat(Smt.eq(Smt.sym(sigValues.valueNames().get(0)), Smt.sym(sigValues.valueNames().get(1))));
        script.assertThat(translated);

        assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
    }

    private static MModel compileLibrary() throws Exception {
        Path file = Path.of("../benchmark/examples/Library/Library.use");
        if (!Files.isRegularFile(file)) {
            file = Path.of("msc-modelvalidators/benchmark/examples/Library/Library.use");
        }
        String source = Files.readString(file);
        ModelFactory factory = new ModelFactory();
        PrintWriter err = new PrintWriter(System.err);
        MModel model = USECompiler.compileSpecification(source, "Library", err, factory);
        err.flush();
        return model;
    }

    private static MClassInvariant findInvariant(MModel model, String name) {
        for (MClassInvariant inv : model.classInvariants()) {
            if (inv.name().equals(name)) {
                return inv;
            }
        }
        throw new IllegalStateException("invariant not found: " + name);
    }

    private static SolverResult solve(SmtScript script) {
        return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
    }
}
