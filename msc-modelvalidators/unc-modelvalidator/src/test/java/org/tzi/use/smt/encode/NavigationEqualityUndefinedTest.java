package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * The second half of defect B1's fix, which the {@code oclUndefined} tests alone do not reach:
 * single-valued navigation equality ({@code i1.tag = i2.tag}, the shape Library's {@code
 * noDoubleBorrowings} uses) obeys the SAME total rule as every other USE equality. Two objects that
 * both have no link are both undefined there, and USE's {@code Op_equal} calls that TRUE -- so the
 * pair really does share a "tag", and an invariant forbidding that is DEFINED-FALSE, not undefined.
 *
 * <p>Verified against the real evaluator on exactly this fixture before being encoded: one Owner
 * owning two untagged Items makes USE report {@code Owner::NoSharedTag} FALSE.
 *
 * <p>Library itself cannot discriminate this: {@code BelongsTo}'s {@code Book[1]} end forces every
 * Copy to have exactly one Book, so the both-undefined case never arises there. That is why this
 * fixture uses a {@code [0..1]} target end instead.
 */
public class NavigationEqualityUndefinedTest {

  private static final String MODEL =
      """
      model NavigationEquality
      class Owner end
      class Item end
      class Tag end
      association Owns between
        Owner[0..1] role owner
        Item[0..*] role item
      end
      association Tagged between
        Item[0..*] role item
        Tag[0..1] role tag
      end
      constraints
      context o : Owner inv NoSharedTag:
        not(o.item->exists(i1, i2 | i1 <> i2 and i1.tag = i2.tag))
      """;

  /**
   * With no {@code Tagged} link available at all, both Items' {@code tag} is undefined, they
   * therefore compare EQUAL, and the invariant is a DEFINED violation -- attributable as a
   * counterexample. A strict reading would make the equality undefined, the invariant undefined,
   * and this obligation unsatisfiable.
   */
  @Test
  public void twoUnlinkedNavigationsCompareEqualAndAreADefinedViolation() throws Exception {
    MModel model = compile();
    ModelFinderResult result =
        SmtModelFinder.find(model, config(model, "counterexample(Owner::NoSharedTag)", 1, 0, 0));

    assertTrue(
        "both items' tag is undefined, so they compare equal and the invariant is defined-false",
        result.satisfiable());
    assertEquals(InvariantOutcome.FALSE, outcomes(result).get("Owner::NoSharedTag"));
  }

  /** The control: given two Tags to go round, the same scope satisfies the invariant. */
  @Test
  public void distinctTargetsSatisfyTheSameInvariant() throws Exception {
    MModel model = compile();
    ModelFinderResult result = SmtModelFinder.find(model, config(model, "satisfy", 2, 2, 2));

    assertTrue(result.satisfiable());
    assertEquals(InvariantOutcome.TRUE, outcomes(result).get("Owner::NoSharedTag"));
  }

  private static Map<String, InvariantOutcome> outcomes(ModelFinderResult result) {
    Map<String, InvariantOutcome> outcomes = new LinkedHashMap<>();
    for (InvariantVerdict verdict : result.verdicts()) {
      outcomes.put(verdict.invariantName(), verdict.outcome());
    }
    return outcomes;
  }

  private static AnalysisConfiguration config(
      MModel model, String query, int tags, int taggedMin, int taggedMax) {
    return new AnalysisConfiguration(
        List.of(
            new ClassScope("Owner", 1, 1),
            new ClassScope("Item", 2, 2),
            new ClassScope("Tag", tags, tags)),
        List.of(
            new AssociationScope("Owns", 2, 2),
            new AssociationScope("Tagged", taggedMin, taggedMax)),
        List.of(),
        Set.of("Owner::NoSharedTag"),
        QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
  }

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "NavigationEquality", err, factory);
    err.flush();
    return model;
  }
}
