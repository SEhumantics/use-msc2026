package org.tzi.use.smt.finder;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystemState;

/**
 * End-to-end proof, through the real {@link SmtModelFinder#find} pipeline, that {@code
 * ClassName_max = -1} -- this codebase's own established "unbounded" sentinel, already accepted by
 * {@code ConfigurationReader.validateScopes} for class scopes and configured for associations
 * throughout the real benchmark corpus (e.g. {@code Library.properties}'s {@code Borrows_max = -1})
 * -- is honoured by {@link org.tzi.use.smt.encode.ObjectSlotEncoder} rather than rejected.
 *
 * <p>Before the fix, {@code -1 < min} was true for every non-negative configured minimum, so ANY
 * class scope shaped this way failed encoding outright with {@code IllegalArgumentException:
 * invalid class scope for '...': min=1, max=-1} -- {@code find} never even reached the solver. The
 * fixture below is deliberately built so that satisfying the invariant needs MORE objects than the
 * configured minimum alone (3, against {@code Widget_min = 1}): a fix that merely stopped throwing
 * but silently capped the population at {@code min} (no real headroom) would still fail this test
 * with a genuine UNSAT, not just a raw exception -- distinguishing "accepts the sentinel" from
 * "actually gives the solver room to explore a bigger population", which is the real feature this
 * pass restores. {@code satisfiable()} itself only reports true once {@link SmtModelFinder} has
 * independently re-run the real USE evaluator over the reconstructed witness ({@code
 * InvariantReEvaluator.reevaluate}), so this one assertion already covers real Z3 solving,
 * reconstruction, AND independent USE-evaluator re-verification.
 */
public class UnboundedClassScopeTest {

  private static final String MODEL =
      """
      model UnboundedScope

      class Anchor
      end

      class Widget
      end

      association Owns between
        Anchor[1] role owner
        Widget[0..*] role widget
      end

      constraints

      context Anchor inv AtLeastThree:
        self.widget->size() >= 3
      """;

  @Test
  public void anUnboundedClassScopeEncodesAndGivesTheSolverRoomBeyondTheConfiguredMinimum()
      throws Exception {
    MModel model = compile(MODEL, "UnboundedScope");
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Anchor_min = 1
            Anchor_max = 1
            Widget_min = 1
            Widget_max = -1
            Owns_min = 0
            Owns_max = -1
            Anchor_AtLeastThree = active
            """);

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue(
        "expected SAT: an unbounded class scope must be accepted and must give the solver room"
            + " beyond its forced minimum of 1, or the invariant demanding >= 3 instances could"
            + " never be independently re-verified",
        result.satisfiable());
    MSystemState state = result.system().state();
    assertTrue(
        "found only "
            + state.objectsOfClass(model.getClass("Widget")).size()
            + " Widget object(s), but the reconstructed, independently re-verified witness must"
            + " carry at least 3",
        state.objectsOfClass(model.getClass("Widget")).size() >= 3);
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("unboundedscope", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compile(String source, String name) {
    java.io.PrintWriter err = new java.io.PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, new ModelFactory());
    err.flush();
    return model;
  }
}
