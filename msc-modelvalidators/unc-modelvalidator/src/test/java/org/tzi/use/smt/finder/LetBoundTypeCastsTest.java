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
 * End-to-end pin for casts and type tests over LET-BOUND object variables -- the
 * ocl.type-tests-casts row's residual, which the audit proved was already closed by the
 * object-let binding work (let bindings register in the translation CONTEXT, so cast and
 * type-test paths resolve them like any context variable) but never pinned. Both shapes
 * must stay green: a let-bound navigation's oclAsType attribute read, and its oclIsKindOf.
 */
public class LetBoundTypeCastsTest {

  private static final String MODEL =
      """
      model LetCast
      class X
      end
      class Vehicle
      end
      class Truck < Vehicle
      attributes
        payload : Integer
      end
      association Fleet between
        X[0..1] role owner
        Vehicle[0..1] role vehicle
      end
      constraints
      context x : X inv letCastAttr:
        let v : Vehicle = x.vehicle in v.oclAsType(Truck).payload > 0
      context x : X inv letKindOf:
        let v : Vehicle = x.vehicle in v.oclIsKindOf(Truck)
      """;

  @Test
  public void letBoundVariableAcceptsACastAttributeRead() throws Exception {
    ModelFinderResult match = find("X::letCastAttr");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::letCastAttr").holds());
  }

  @Test
  public void letBoundVariableAcceptsATypeTest() throws Exception {
    ModelFinderResult match = find("X::letKindOf");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::letKindOf").holds());
  }

    private static ModelFinderResult find(String invariant) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("X", 1, 1, List.of("x1")),
                new ClassScope("Vehicle", 0, 0),
                new ClassScope("Truck", 1, 1, List.of("trk1"))),
            List.of(new AssociationScope("Fleet", 1, 1, List.of(List.of("x1", "trk1")))),
            List.of(new AttributeDomain("Truck", "payload", null, List.of("5"), null, null)),
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

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(MODEL, "LetCast", err, factory);
    err.flush();
    if (model == null) throw new AssertionError("did not compile:\n" + buffer);
    return model;
  }
}
