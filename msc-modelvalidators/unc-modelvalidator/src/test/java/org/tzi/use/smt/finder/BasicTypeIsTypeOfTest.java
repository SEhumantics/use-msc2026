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
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for BASIC-TYPE {@code oclIsTypeOf}/{@code oclIsKindOf} (against
 * Integer/Real/String/UInteger targets on scalar attributes) -- the
 * {@code ocl.type-tests-casts-real} shape. Semantics are USE's OWN type lattice, read from
 * source and delegated to (not hand-derived): {@code ExpIsTypeOf} compares the value's runtime
 * type with {@code equals} (exact); {@code ExpIsKindOf} uses {@code conformsTo}. The lattice
 * facts that make the edge cases discriminate: Integer conformsTo Real AND UInteger/UReal
 * (isKindOfNumber), but Real does NOT conformTo Integer, and oclIsTypeOf is EXACT even where
 * conformance holds.
 *
 * <p>Every fact here is a compile-time property of the attribute's DECLARED type, so the tests
 * assert the enforced-invariant polarity: a TRUE fact is satisfiable, a FALSE fact refutes.
 */
public class BasicTypeIsTypeOfTest {

  private static final String MODEL =
      """
      model BasicTypes
      class X
      attributes
        i : Integer
        r : Real
        s : String
      end
      constraints
      context x : X inv IntIsTypeOfInteger:
        x.i.oclIsTypeOf(Integer)
      context x : X inv IntIsTypeOfReal:
        x.i.oclIsTypeOf(Real)
      context x : X inv IntIsKindOfReal:
        x.i.oclIsKindOf(Real)
      context x : X inv RealIsKindOfInteger:
        x.r.oclIsKindOf(Integer)
      context x : X inv RealIsKindOfReal:
        x.r.oclIsKindOf(Real)
      context x : X inv StringIsKindOfInteger:
        x.s.oclIsKindOf(Integer)
      context x : X inv IntIsKindOfUInteger:
        x.i.oclIsKindOf(UInteger)
      context x : X inv NotIntIsTypeOfReal:
        not x.i.oclIsTypeOf(Real)
      """;

  /** General case: an Integer attribute IS of type Integer. */
  @Test
  public void intAttributeIsTypeOfInteger() throws Exception {
    ModelFinderResult match = find("IntIsTypeOfInteger");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::IntIsTypeOfInteger").holds());
  }

  /** oclIsTypeOf is EXACT: Integer is not Real, even though Integer conformsTo Real. */
  @Test
  public void intAttributeIsNotTypeOfReal() throws Exception {
    ModelFinderResult miss = find("IntIsTypeOfReal");
    assertFalse("oclIsTypeOf is exact: Integer != Real", miss.satisfiable());
  }

  /**
   * THE ASYMMETRIC COMPANION: oclIsKindOf(Real) on the SAME Integer attribute is TRUE
   * (Integer conformsTo every number kind). A conformance implementation that copied the
   * exact-match rule fails here; one that copies the isKindOf rule into isTypeOf fails the
   * previous test.
   */
  @Test
  public void intAttributeIsKindOfReal() throws Exception {
    ModelFinderResult match = find("IntIsKindOfReal");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::IntIsKindOfReal").holds());
  }

  /** Conformance is not symmetric: Real does NOT conformTo Integer. */
  @Test
  public void realAttributeIsNotKindOfInteger() throws Exception {
    ModelFinderResult miss = find("RealIsKindOfInteger");
    assertFalse("Real does not conformTo Integer", miss.satisfiable());
  }

  /** Real IS kindOf Real. */
  @Test
  public void realAttributeIsKindOfReal() throws Exception {
    ModelFinderResult match = find("RealIsKindOfReal");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::RealIsKindOfReal").holds());
  }

  /** String and Integer are unrelated in the lattice. */
  @Test
  public void stringAttributeIsNotKindOfInteger() throws Exception {
    ModelFinderResult miss = find("StringIsKindOfInteger");
    assertFalse("String does not conformTo Integer", miss.satisfiable());
  }

  /**
   * Cross-family edge: Integer conformsTo UInteger too (isKindOfNumber installs the edge from
   * the supertype side) -- surprising but confirmed against IntegerType.conformsTo.
   */
  @Test
  public void intAttributeIsKindOfUInteger() throws Exception {
    ModelFinderResult match = find("IntIsKindOfUInteger");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::IntIsKindOfUInteger").holds());
  }

  /** Negation polarity: the FALSE fact composes with `not` and holds. */
  @Test
  public void negatedFalseTypeTestHolds() throws Exception {
    ModelFinderResult match = find("NotIntIsTypeOfReal");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::NotIntIsTypeOfReal").holds());
  }

  private static ModelFinderResult find(String invariantName) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "i", null, List.of("2"), null, null),
                new AttributeDomain("X", "r", null, List.of("0.5"), null, null),
                new AttributeDomain("X", "s", null, List.of("a"), null, null)),
            Set.of("X::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "BasicTypes", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + err);
    }
    return model;
  }
}
