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
 * End-to-end regression for the acyclicity idiom built from QUERY OPERATIONS -- the exact shape
 * CompanyERSchema's {@code Component::acyclic} uses:
 *
 * <pre>
 *   contained(): Set(Part) =
 *     Part.allInstances()-&gt;select(p | Component.allInstances()-&gt;
 *       exists(c | c.containername = self.pname and c.containedname = p.pname))
 *   containedPlus(): Set(Part) = self.contained()-&gt;closure(p | p.contained())
 *   context c : Component inv acyclic:
 *     let p = Part.allInstances()-&gt;any(pname = c.containername) in
 *       p.containedPlus()-&gt;excludes(p)
 * </pre>
 *
 * <p>This exercises the whole chain at once: query-operation inlining (containedPlus inlines to
 * a closure whose body re-calls contained(), which inlines to a select over allInstances with a
 * nested exists over the derived association end), the derived end's own any()-match encoding,
 * the let-any object binding, and a bounded least-fixed-point reachability whose membership
 * relation is the inlined select's predicate with {@code self} and the select iterator bound per
 * candidate pair. The element is in the closure exactly when the containment relation loops back
 * to it.
 */
public class OperationClosureAcyclicTest {

  private static final String MODEL =
      """
      model OperationClosureAcyclic
      class Part
      attributes
        pname : String
      operations
        contained(): Set(Part) =
          Part.allInstances()->select(p|Component.allInstances()->
            exists(c|c.containername=self.pname and c.containedname=p.pname))
        containedPlus(): Set(Part)=self.contained()->closure(p|p.contained())
      end
      class Component
      attributes
        containername : String
        containedname : String
      end
      association Contains between
        Component [*] role containedComponent
        Part [1] role containedPart derived =
          Part.allInstances()->any(p|p.pname=self.containedname)
      end
      constraints
      context c:Component inv acyclic:
        let p=Part.allInstances()->any(pname=c.containername) in
        p.containedPlus()->excludes(p)
      """;

  /**
   * An acyclic containment chain (P1 contains P2, nothing else): for the one Component,
   * p = P1 and containedPlus(P1) = {P2}, which excludes P1. Satisfiable, USE-confirmed.
   */
  @Test
  public void anAcyclicContainmentChainSatisfiesTheOperationClosureExclusion() throws Exception {
    ModelFinderResult result = find(
        List.of("P1"), List.of("P2"), 1);

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "Component::acyclic").holds());
  }

  /**
   * The forced self-loop: with a single component whose containername and containedname are both
   * 'P1', contained(P1) contains P1 itself, so containedPlus(P1) reaches P1 and the exclusion
   * cannot hold -- genuinely unsatisfiable. (A two-component two-cycle cannot be FORCED through
   * shared attribute domains: the solver may legally give both components identical, acyclic
   * records, which is a solver-freedom artifact of the config, not a translation fact.)
   */
  @Test
  public void aForcedContainmentSelfLoopViolatesTheOperationClosureExclusion() throws Exception {
    ModelFinderResult result = find(List.of("P1"), List.of("P1"), 1);

    assertFalse(
        "contained(P1) = {P1} is a self-loop, so containedPlus(P1) reaches P1 and excludes"
            + " cannot hold",
        result.satisfiable());
  }

  private static ModelFinderResult find(
      List<String> containerDomain, List<String> containedDomain, int componentCount)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Part", 2, 2, List.of("P1", "P2")),
                new ClassScope("Component", componentCount, componentCount)),
            List.of(new AssociationScope("Contains", 0, -1)),
            List.of(
                new AttributeDomain("Part", "pname", null, List.of("P1", "P2"), null, null),
                new AttributeDomain("Component", "containername", null, containerDomain, null, null),
                new AttributeDomain("Component", "containedname", null, containedDomain, null, null)),
            Set.of("Component::acyclic"),
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
    MModel model = USECompiler.compileSpecification(MODEL, "OperationClosureAcyclic", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
