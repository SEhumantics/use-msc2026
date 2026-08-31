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
 * Depth-3 discriminators for three composed machineries, one test each: a THREE-LEVEL
 * override chain (Vehicle -> Truck -> HeavyTruck), a THREE-HOP deep receiver chain
 * (x.a.b.c.tagged()), and a THREE-SOURCE String concat derivation chain
 * (a.concat(b).concat(c)). Each asserts the depth-3 result in both polarities where a
 * shallower encoding would produce a different number.
 */
public class DepthThreeCompositionTest {

  // ------------------------------------------------- override chain, depth 3

  private static final String OVERRIDE_MODEL =
      """
      model Override3
      class Fleet
      attributes
        chosen : Integer
      end
      class Vehicle
      operations
        cap(p : Integer) : Integer = p + 1
      end
      class Truck < Vehicle
      operations
        cap(p : Integer) : Integer = p + 2
      end
      class HeavyTruck < Truck
      operations
        cap(p : Integer) : Integer = p + 3
      end
      association Owns between
        Fleet[0..1] role fleet
        Vehicle[0..1] role vehicle
      end
      constraints
      context f : Fleet inv level3BodyWins:
        f.chosen = 3 implies f.vehicle.cap(0) = 3
      """;

  /**
   * HeavyTruck's body (+3) must win over Truck's (+2) and Vehicle's (+1): chosen=3 selects
   * the HeavyTruck slot and demands its own depth-3 result.
   */
  @Test
  public void threeLevelOverrideDispatchesToTheDeepestBody() throws Exception {
    MModel model = compile(OVERRIDE_MODEL, "Override3");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Fleet", 1, 1, List.of("f1")),
                new ClassScope("Vehicle", 0, 0),
                new ClassScope("Truck", 0, 0),
                new ClassScope("HeavyTruck", 1, 1, List.of("h1"))),
            List.of(new AssociationScope("Owns", 1, 1, List.of(List.of("f1", "h1")))),
            List.of(new AttributeDomain("Fleet", "chosen", null, List.of("3"), null, null)),
            Set.of("Fleet::level3BodyWins"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue("HeavyTruck's cap(0) = 3 must hold through the depth-3 dispatch",
        match.satisfiable());
    assertTrue(verdictFor(match, "Fleet::level3BodyWins").holds());
  }

  // ------------------------------------------------- deep receiver chain, 3 hops

  private static final String CHAIN_MODEL =
      """
      model Chain3
      class A3
      attributes
        seed : Integer
      end
      class B3
      end
      class C3
      end
      class D3
      attributes
        mark : Integer
      end
      association A3B between
        A3[0..1] role a
        B3[0..1] role b
      end
      association B3C between
        B3[0..1] role b
        C3[0..1] role c
      end
      association C3D between
        C3[0..1] role c
        D3[0..1] role d
      end
      constraints
      context a : A3 inv threeHopRead:
        a.b.c.d.mark = 1
      """;

  /** Three hops with every link forced: the terminal attribute flows to the comparison. */
  @Test
  public void threeHopChainDeliversTheTerminalValue() throws Exception {
    MModel model = compile(CHAIN_MODEL, "Chain3");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("A3", 1, 1, List.of("a1")),
                new ClassScope("B3", 1, 1, List.of("b1")),
                new ClassScope("C3", 1, 1, List.of("c1")),
                new ClassScope("D3", 1, 1, List.of("d1"))),
            List.of(
                new AssociationScope("A3B", 1, 1, List.of(List.of("a1", "b1"))),
                new AssociationScope("B3C", 1, 1, List.of(List.of("b1", "c1"))),
                new AssociationScope("C3D", 1, 1, List.of(List.of("c1", "d1")))),
            List.of(new AttributeDomain("D3", "mark", null, List.of("1"), null, null)),
            Set.of("A3::threeHopRead"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "A3::threeHopRead").holds());
  }

  /** Breaking the LAST hop refutes: the chain's definedness spans all three links. */
  @Test
  public void brokenThirdHopRefutesTheThreeHopChain() throws Exception {
    MModel model = compile(CHAIN_MODEL, "Chain3");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("A3", 1, 1, List.of("a1")),
                new ClassScope("B3", 1, 1, List.of("b1")),
                new ClassScope("C3", 1, 1, List.of("c1")),
                new ClassScope("D3", 1, 1, List.of("d1"))),
            List.of(
                new AssociationScope("A3B", 1, 1, List.of(List.of("a1", "b1"))),
                new AssociationScope("B3C", 1, 1, List.of(List.of("b1", "c1"))),
                new AssociationScope("C3D", 0, 0)),
            List.of(new AttributeDomain("D3", "mark", null, List.of("1"), null, null)),
            Set.of("A3::threeHopRead"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult miss = SmtModelFinder.find(model, config);
    assertFalse("the third hop is unlinked: the read is undefined", miss.satisfiable());
  }

  // ------------------------------------------------- concat derivation chain, 3 sources

  private static final String CONCAT_MODEL =
      """
      model Concat3
      class Doc
      attributes
        p1 : String
        p2 : String
        p3 : String
        joined : String derive: self.p1.concat(self.p2).concat(self.p3)
      end
      constraints
      context d : Doc inv joinedIsAbc:
        d.joined = 'abc'
      """;

  /** p1='a', p2='b', p3='c' pins joined to 'abc' (USE-confirmed). */
  @Test
  public void threeSourceConcatPinsTheDerivedValue() throws Exception {
    MModel model = compile(CONCAT_MODEL, "Concat3");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Doc", 1, 1, List.of("d1"))),
            List.of(),
            List.of(
                new AttributeDomain("Doc", "p1", null, List.of("a"), null, null),
                new AttributeDomain("Doc", "p2", null, List.of("b"), null, null),
                new AttributeDomain("Doc", "p3", null, List.of("c"), null, null),
                new AttributeDomain("Doc", "joined", null, List.of("abc"), null, null)),
            Set.of("Doc::joinedIsAbc"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "Doc::joinedIsAbc").holds());
  }

  /** A joined domain that only offers 'abc' refutes a candidate pair forcing a different
   * concatenation: p2='x' would require 'axc', absent from the derived domain. */
  @Test
  public void threeSourceConcatRefutesWhenTheResultIsUnofferable() throws Exception {
    MModel model = compile(CONCAT_MODEL, "Concat3");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Doc", 1, 1, List.of("d1"))),
            List.of(),
            List.of(
                new AttributeDomain("Doc", "p1", null, List.of("a"), null, null),
                new AttributeDomain("Doc", "p2", null, List.of("b", "x"), null, null),
                new AttributeDomain("Doc", "p3", null, List.of("c"), null, null),
                new AttributeDomain("Doc", "joined", null, List.of("abc"), null, null)),
            Set.of("Doc::joinedIsAbc"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult only = SmtModelFinder.find(model, config);
    assertTrue("p2='b' is selectable, giving 'abc'", only.satisfiable());
    assertTrue(verdictFor(only, "Doc::joinedIsAbc").holds());
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
