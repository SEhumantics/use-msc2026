package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/** Parser-backed regression coverage for {@code let <var> = <expr> in <body>}. */
public class LetTranslationTest {

  @Test
  public void primitiveLetBindsItsValueInTheBody() throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, "primitiveLet");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    AttributeDomain iDomain = new AttributeDomain("A", "i", null, List.of(), null, null);
    AttributeDomain jDomain = new AttributeDomain("A", "j", null, List.of(), null, null);
    AttributeValues i =
        AttributeEncoder.encode(script, objects, "i", AttributeType.INTEGER, iDomain);
    AttributeValues j =
        AttributeEncoder.encode(script, objects, "j", AttributeType.INTEGER, jDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of("a", new VariableBinding("A", 0)),
            Map.of("A.i", i, "A.j", j),
            Map.of("A.i", iDomain, "A.j", jDomain),
            Map.of("A", objects),
            Map.of());

    SmtTerm translated = ExpressionTranslator.translate(invariant.bodyExpression(), context);

    assertEquals(
        "(let ((|ocl-let-threshold-value| (+ A_i_0 1))) (> |ocl-let-threshold-value| A_j_0))",
        translated.toSmtLib());
  }

  private static MModel compileFixture() throws Exception {
    Path file = Path.of("src/test/resources/LetScope.use");
    String source = Files.readString(file);
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "LetScope", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("LetScope fixture model did not compile");
    }
    return model;
  }

  private static MClassInvariant findInvariant(MModel model, String name) {
    for (MClassInvariant invariant : model.classInvariants()) {
      if (invariant.name().equals(name)) {
        return invariant;
      }
    }
    throw new IllegalStateException("invariant not found: " + name);
  }
}
