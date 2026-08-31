package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for the EMPTINESS of a closure over the two shapes the P3 probes
 * captured: a closure whose body re-navigates a REDEFINED end, and a closure over an N-ARY
 * end. The landing is the sound identity an OCL closure always satisfies -- the closure
 * includes its direct image, so it is empty exactly when its range is empty -- which routes
 * the emptiness decision to the range's own machinery (redirect-aware for the redefined end,
 * n-ary population for the n-ary end) without ever reaching the closure fixed point. The
 * captured experiment: BEFORE this, both shapes refused at the consumer layer, so the
 * closure translation was never even reached.
 */
public class ClosureEmptinessTest {

  private static final String REDEFINES_MODEL =
      """
      model RedefClosure
      class P
      end
      class Q < P
      end
      association PP between
        P[*] role p
        P[*] role p2
      end
      association QQ between
        Q[*] role q redefines p
        Q[*] role q2 redefines p2
      end
      constraints
      context p : P inv closureOverRedefinedNotEmpty:
        p.p->closure(x | x.p)->notEmpty()
      context p : P inv closureOverRedefinedEmpty:
        p.p->closure(x | x.p)->isEmpty()
      """;

  private static final String NARY_MODEL =
      """
      model NaryClosure
      class N
      end
      class M
      end
      association NN between
        N[*] role n
        M[*] role m
        M[*] role m2
      end
      constraints
      context n : N inv closureOverNaryNotEmpty:
        n.m->closure(m1 | m1.m)->notEmpty()
      context n : N inv closureOverNaryEmpty:
        n.m->closure(m1 | m1.m)->isEmpty()
      """;

  /**
   * A QQ link (q1,q2) makes q1.p non-empty through the REDIRECTED end, so the closure's
   * direct image is non-empty (USE-confirmed).
   */
  @Test
  public void redefinedClosureSeesTheRedirectedLink() throws Exception {
    ModelFinderResult match = findRedefines("P::closureOverRedefinedNotEmpty", true);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "P::closureOverRedefinedNotEmpty").holds());
  }

  /** Mirror polarity: no QQ links at all, so the redirected closure is empty. */
  @Test
  public void redefinedClosureIsEmptyWhenTheRedirectedAssociationIsEmpty() throws Exception {
    ModelFinderResult match = findRedefines("P::closureOverRedefinedEmpty", false);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "P::closureOverRedefinedEmpty").holds());

    ModelFinderResult miss = findRedefines("P::closureOverRedefinedNotEmpty", false);
    assertFalse("the range is empty: notEmpty is violated", miss.satisfiable());
  }

  /** The n-ary range's forced tuple makes the closure's direct image non-empty. */
  @Test
  public void naryClosureSeesTheProjectedLink() throws Exception {
    ModelFinderResult match = findNary("N::closureOverNaryNotEmpty", true);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "N::closureOverNaryNotEmpty").holds());
  }

  /** Mirror polarity on the n-ary shape: empty grid, empty closure. */
  @Test
  public void naryClosureIsEmptyWhenTheGridIsEmpty() throws Exception {
    ModelFinderResult match = findNary("N::closureOverNaryEmpty", false);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "N::closureOverNaryEmpty").holds());

    ModelFinderResult miss = findNary("N::closureOverNaryNotEmpty", false);
    assertFalse("the n-ary range is empty: notEmpty is violated", miss.satisfiable());
  }

  private static ModelFinderResult findRedefines(String invariant, boolean linked)
      throws Exception {
    MModel model = compile(REDEFINES_MODEL, "RedefClosure");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("P", 0, 0),
                new ClassScope("Q", 2, 2, List.of("q1", "q2"))),
            List.of(
                new AssociationScope(
                    "QQ", linked ? 2 : 0, linked ? 2 : 0,
                    linked ? List.of(List.of("q1", "q2"), List.of("q2", "q1")) : List.of())),
            List.of(),
            Set.of(invariant),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static ModelFinderResult findNary(String invariant, boolean linked) throws Exception {
    MModel model = compile(NARY_MODEL, "NaryClosure");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("N", 1, 1, List.of("n1")),
                new ClassScope("M", 2, 2, List.of("m1", "m2"))),
            List.of(
                new AssociationScope(
                    "NN", linked ? 1 : 0, linked ? 1 : 0,
                    linked ? List.of(List.of("n1", "m1", "m2")) : List.of())),
            List.of(),
            Set.of(invariant),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compile(String spec, String name) {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(spec, name, err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
