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
 * End-to-end regression for {@code ->at(i)} over constant-content ORDERED collections (Sequence,
 * OrderedSet) -- a {@code collection.sequence-specific-api} extension. Semantics confirmed
 * against {@code Op_sequence_at} (use-core): 1-based, out-of-range yields UNDEFINED (never a
 * fallback element). A constant index folds to the element; a symbolic index becomes an ite
 * chain over the positions whose definedness requires the index in range.
 */
public class SequenceAtTest {

  private static final String MODEL =
      """
      model SeqAt
      class X
      attributes
        i : Integer
        n : Integer
      end
      constraints
      context x : X inv ConstantAt:
        Sequence{10,20,30}->at(2) = 20
      context x : X inv AtOutOfRangeUndefined:
        Sequence{10,20,30}->at(7) = 20
      context x : X inv SymbolicAt:
        Sequence{10,20,30}->at(x.i) = x.n
      context x : X inv OrderedSetAt:
        OrderedSet{10,20,30}->at(1) = x.n
      """;

  /** A constant in-range index folds to the element. */
  @Test
  public void constantAtFoldsToTheElement() throws Exception {
    ModelFinderResult match = find("ConstantAt", List.of("2"), List.of("20"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::ConstantAt").holds());
  }

  /** at(7) of a 3-element sequence is UNDEFINED: enforcing the equality refutes. */
  @Test
  public void constantOutOfRangeAtIsUndefined() throws Exception {
    ModelFinderResult miss = find("AtOutOfRangeUndefined", List.of("2"), List.of("20"));
    assertFalse("at(7) is undefined, so the equality can never hold", miss.satisfiable());
  }

  /** The symbolic index selects the element: i = 2 yields 20. */
  @Test
  public void symbolicAtSelectsByIndex() throws Exception {
    ModelFinderResult match = find("SymbolicAt", List.of("2"), List.of("20"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::SymbolicAt").holds());

    ModelFinderResult wrong = find("SymbolicAt", List.of("2"), List.of("10"));
    assertFalse("at(2) is 20, not 10", wrong.satisfiable());

    ModelFinderResult outOfRange = find("SymbolicAt", List.of("7"), List.of("20"));
    assertFalse("at(7) is undefined, so nothing can satisfy", outOfRange.satisfiable());
  }

  /** OrderedSet at() shares the semantics. */
  @Test
  public void orderedSetAtWorks() throws Exception {
    ModelFinderResult match = find("OrderedSetAt", List.of("1"), List.of("10"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::OrderedSetAt").holds());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> iDomain, List<String> nDomain) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "i", null, iDomain, null, null),
                new AttributeDomain("X", "n", null, nDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "SeqAt", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + err);
    }
    return model;
  }
}
