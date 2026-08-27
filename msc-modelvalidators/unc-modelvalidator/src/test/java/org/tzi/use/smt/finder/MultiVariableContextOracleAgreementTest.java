package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * The multi-variable context quantifier, checked END TO END against the INDEPENDENT oracle rather
 * than against another reading of the same encoder.
 *
 * <p>This is the check the corpus cannot currently make. Removing the multi-variable refusal moved
 * eleven refused (invariant, row) pairs but no corpus row to a verdict -- NQueens is still blocked
 * by {@code Queen::noAttack}'s Tier 2 {@code '+'} -- so no benchmark run exercises the
 * encoder/oracle pair on a multi-variable invariant at all. A unit-scale model does, and it is
 * worth having precisely because the two sides are genuinely independent code: {@link
 * org.tzi.use.smt.encode.InvariantAssembler} enumerates the product as SMT text before solving,
 * while {@link org.tzi.use.smt.verify.InvariantReEvaluator} recurses over the same product with
 * USE's own OCL evaluator on the RECONSTRUCTED snapshot, after solving. A quantifier that only
 * looked right in SMT would produce a witness the oracle rejects, and {@link
 * ModelFinderResult#allActiveInvariantsHold()} would be false.
 *
 * <p>The pigeonhole half is the other direction: three objects, two available values and a pairwise
 * distinctness invariant is UNSATISFIABLE, which no encoding that quietly drops off-diagonal pairs
 * can report -- with the diagonal alone the constraint is vacuous and every assignment satisfies
 * it.
 */
public class MultiVariableContextOracleAgreementTest {

  private static final String DISTINCTNESS_MODEL =
      """
      model Distinctness
      class Person
      attributes
        n : Integer
      end
      constraints
      context p1, p2 : Person inv distinctN:
        p1 <> p2 implies p1.n <> p2.n
      """;

  @Test
  public void threeObjectsOverThreeValuesSatisfyPairwiseDistinctnessAndTheOracleAgrees()
      throws Exception {
    MModel model = compile(DISTINCTNESS_MODEL);

    ModelFinderResult result = SmtModelFinder.find(model, configuration(3, List.of("1", "2", "3")));

    assertTrue("three objects can take three distinct values", result.satisfiable());
    assertTrue(
        "the SMT witness must survive InvariantReEvaluator's own expansion of the SAME cartesian"
            + " product over the reconstructed snapshot",
        result.allActiveInvariantsHold());

    MSystemState state = result.system().state();
    Set<Integer> values = new HashSet<>();
    for (MObject person : state.objectsOfClass(model.getClass("Person"))) {
      values.add((int) ((IntegerValue) person.state(state).attributeValue("n")).value());
    }
    assertEquals("the reconstructed objects really are pairwise distinct", 3, values.size());
  }

  @Test
  public void threeObjectsOverTwoValuesAreGenuinelyUnsatisfiableByPigeonhole() throws Exception {
    MModel model = compile(DISTINCTNESS_MODEL);

    ModelFinderResult result = SmtModelFinder.find(model, configuration(3, List.of("1", "2")));

    assertFalse(
        "three objects cannot be pairwise distinct over two values -- a quantifier that covered"
            + " only the diagonal would call this satisfiable",
        result.satisfiable());
  }

  private static AnalysisConfiguration configuration(int objects, List<String> values) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Person", objects, objects)),
        List.of(),
        List.of(new AttributeDomain("Person", "n", null, values, null, null)),
        Set.of("Person::distinctN"),
        QueryExpr.SATISFY,
        Duration.ofSeconds(30),
        1);
  }

  private static MModel compile(String source) {
    MModel model =
        USECompiler.compileSpecification(
            source, "Distinctness", new PrintWriter(System.err), new ModelFactory());
    if (model == null) {
      throw new AssertionError("model did not compile");
    }
    return model;
  }
}
