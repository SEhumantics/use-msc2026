package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Parser-backed regression coverage for {@code X.allInstances()->one(body)}.
 *
 * <p>A real, non-obvious configuration-semantics trap found and worked around while writing this
 * suite, recorded so it doesn't cost the next person the same debugging session: {@code
 * ClassName_attr = Set{v1,...,vN}} against N NAMED objects is NOT positional per-object pinning --
 * it is a SHARED candidate domain each object independently draws from (with repetition allowed).
 * A first attempt at these tests assumed positional pinning, silently let the solver pick a
 * DIFFERENT witness than intended, and produced a false "it's broken" signal that took a direct
 * SMT-LIB probe (confirming the emitted formula is correct) plus a direct witness inspection
 * (confirming the config layer, not this feature, was the source of the surprise) to resolve.
 * These tests sidestep it entirely by asserting each object's score directly via {@code
 * script.assertThat}, the same way {@code LetTranslationTest}'s CompanyER round trip does.
 */
public class OneTranslationTest {

  @Test
  public void exactlyOneMatchIsSatisfiable() throws Exception {
    assertEquals(SolverOutcome.SAT, solveWithScores(20, 5, 3));
  }

  @Test
  public void twoMatchesIsUnsatisfiableWhenEnforced() throws Exception {
    assertEquals(SolverOutcome.UNSAT, solveWithScores(20, 15, 3));
  }

  @Test
  public void zeroMatchesIsUnsatisfiableWhenEnforced() throws Exception {
    assertEquals(SolverOutcome.UNSAT, solveWithScores(5, 5, 3));
  }

  @Test
  public void moreThanOneLoopVariableIsRefused() throws Exception {
    MModel model = compileFixture("Item.allInstances()->one(x, y | x.score > y.score)");
    MClassInvariant invariant = findInvariant(model, "probe");
    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    invariant.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("more than one loop variable"));
  }

  @Test
  public void fullRoundTripSingleCandidateConfirmedByUseEvaluator() throws Exception {
    MModel model = compileFixture("Item.allInstances()->one(x | x.score > 10)");
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Item_min = 1
            Item_max = 1
            Item_score = Set{20}
            Item_probe = active
            """);
    org.tzi.use.smt.finder.ModelFinderResult result =
        org.tzi.use.smt.finder.SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "one candidate scoring above 10 out of one total is exactly one match",
        result.allActiveInvariantsHold());
  }

  private static SolverOutcome solveWithScores(int s0, int s1, int s2) throws Exception {
    MModel model = compileFixture("Item.allInstances()->one(x | x.score > 10)");
    MClassInvariant invariant = findInvariant(model, "probe");
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Item", 3, 3))).get("Item");
    AttributeDomain scoreDomain = new AttributeDomain("Item", "score", null, List.of(), null, null);
    AttributeValues score =
        AttributeEncoder.encode(script, objects, "score", AttributeType.INTEGER, scoreDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of("i", new VariableBinding("Item", 0)),
            Map.of("Item.score", score),
            Map.of("Item.score", scoreDomain),
            Map.of("Item", objects),
            Map.of());
    script.assertThat(Smt.eq(Smt.sym(score.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(s0))));
    script.assertThat(Smt.eq(Smt.sym(score.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(s1))));
    script.assertThat(Smt.eq(Smt.sym(score.valueNames().get(2)), Smt.intLit(BigInteger.valueOf(s2))));
    TranslatedExpression translated =
        ExpressionTranslator.translate(invariant.bodyExpression(), context, TranslationMode.UNCERTAIN);
    script.assertThat(translated.trueTerm());
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
        .run(script.toSmtLib())
        .outcome();
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("one", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compileFixture(String invariantBody) throws Exception {
    String source =
        """
        model OneScope
        class Item
        attributes
          score : Integer
        end
        constraints
        context i : Item inv probe:
          %s
        """
            .formatted(invariantBody);
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "OneScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("OneScope fixture model did not compile: " + invariantBody);
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
