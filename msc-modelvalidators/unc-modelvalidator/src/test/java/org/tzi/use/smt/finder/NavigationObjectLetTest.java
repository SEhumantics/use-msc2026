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
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for object lets whose initializer is a single-valued NAVIGATION --
 * {@code let b : B = a.b in body} -- the {@code ocl.let} remainder slice between the scalar
 * lets and the any()-object-let form. The bound variable names a slot of the navigation's
 * destination view: its definedness is the link term (b is defined exactly when a link exists),
 * and the body translates per destination slot exactly as {@code objectAnyLet} does for
 * any()-seeded slots.
 *
 * <p>Semantics checked against USE's own evaluator: a linked b makes the body see the linked
 * object's attributes; an unlinked navigation leaves b undefined, so the body is undefined and
 * a demanding invariant fails.
 */
public class NavigationObjectLetTest {

  private static final String MODEL =
      """
      model NavigationObjectLet
      class A
      attributes
        s : String
      end
      class B
      attributes
        t : String
      end
      association R between
        A [0..*] role a
        B [0..1] role b
      end
      constraints
      context x : A inv LetNavMatchesS:
        let b : B = x.b in b.t = x.s
      """;

  /** With the link present and matching values, the let-bound b sees the linked object's state. */
  @Test
  public void navigationBoundLetSeesTheLinkedObject() throws Exception {
    ModelFinderResult linked = find("x", "x", List.of(new AssociationScope("R", 1, 1)));

    assertTrue("the linked b carries t = 'x', so the body holds", linked.satisfiable());
    assertTrue(verdictFor(linked, "A::LetNavMatchesS").holds());
  }

  /** With the link present but t = 'y', the body is genuinely unsatisfiable. */
  @Test
  public void navigationBoundLetWithMismatchedAttributeFails() throws Exception {
    ModelFinderResult mismatched = find("y", "x", List.of(new AssociationScope("R", 1, 1)));

    assertFalse("x.s = 'y' but b.t is forced to 'x', so the body cannot hold",
        mismatched.satisfiable());
  }

  /** With no link at all, the let variable is undefined and the body cannot hold. */
  @Test
  public void navigationBoundLetWithoutALinkLeavesTheBodyUndefined() throws Exception {
    ModelFinderResult unlinked = find("x", "x", List.of(new AssociationScope("R", 0, 0)));

    assertFalse("no link means b is undefined, so b.t = 'x' cannot hold",
        unlinked.satisfiable());
  }

  private static ModelFinderResult find(
      String sDomain, String tDomain, List<AssociationScope> associationScopes) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("A", 1, 1), new ClassScope("B", 1, 1)),
            associationScopes,
            List.of(
                new AttributeDomain("A", "s", null, List.of(sDomain), null, null),
                new AttributeDomain("B", "t", null, List.of(tDomain), null, null)),
            Set.of("A::LetNavMatchesS"),
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

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "NavigationObjectLet", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
