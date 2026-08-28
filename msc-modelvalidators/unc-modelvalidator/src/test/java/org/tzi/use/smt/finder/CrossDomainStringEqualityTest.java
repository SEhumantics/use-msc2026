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
 * Regression for a genuine soundness bug in {@code ExpressionTranslator.comparison}'s generic
 * fallback: two BARE String/Enum attribute accesses compared directly ({@code a.attr1 = a.attr2},
 * neither side a compile-time literal) used to be translated as raw SMT-value equality. {@code
 * AttributeEncoder.guardString} assigns each attribute's value an index that is POSITIONAL WITHIN
 * THAT ONE ATTRIBUTE'S OWN configured candidate list, with no global identity tying the same
 * literal to the same integer across two independently-configured domains -- so whenever two
 * different String attributes' domains disagree on ordering, the old raw comparison could silently
 * equate two different literals (or deny two equal ones) purely by index-position coincidence.
 *
 * <p>Found while probing a candidate corpus scenario for {@code DerivedAssociationEncoder}
 * (see {@link DerivedAssociationAnyMatchTest}'s own added regression): a derived association's
 * {@code any()}-match predicate compares exactly two bare String attributes this way, and the bug
 * there surfaced as an unhandled {@code WitnessAttributionException} -- the solver claimed a match
 * existed that USE's own independent re-evaluation of the reconstructed witness denied. Reproduced
 * here in the smallest possible shape: a single class with two String attributes, no association or
 * derivation involved at all, confirming the root cause is in {@code comparison()} itself, not
 * anything specific to derived associations.
 */
public class CrossDomainStringEqualityTest {

  private static final String MODEL =
      """
      model CrossDomainStringEquality
      class C
      attributes
        s1 : String
        s2 : String
      end
      constraints
      context c : C inv CrossAttrEqual:
        c.s1 = c.s2
      """;

  /**
   * {@code s1}'s domain is {@code {'Zulu','Yankee'}} (index 0 = Zulu), {@code s2}'s is the SAME two
   * literals but SWAPPED ({@code {'Yankee','Zulu'}}, index 0 = Yankee). Raw index equality would let
   * the solver pick {@code s1=0, s2=0} ("Zulu" = "Yankee" by the old, broken reading) and claim
   * {@code CrossAttrEqual} true -- exactly the shape {@code WitnessAttributionException} exists to
   * catch, and did, before the fix. The genuinely correct answer is still SATISFIABLE (both domains
   * really do share both literals, e.g. {@code s1=s2='Zulu'} is a real solution), so the fix must
   * find a CORRECTLY cross-checked witness, not merely refuse.
   */
  @Test
  public void sameLiteralsAtSwappedIndexPositionsStillFindsAGenuinelyCorrectWitness()
      throws Exception {
    ModelFinderResult result = find(List.of("Zulu", "Yankee"), List.of("Yankee", "Zulu"));

    assertTrue(
        "both domains genuinely share 'Zulu' and 'Yankee', so a real witness exists",
        result.satisfiable());
    assertTrue(
        "USE's own independent re-evaluation of the reconstructed witness must confirm the match"
            + " -- SmtModelFinder.find would already have thrown WitnessAttributionException"
            + " otherwise",
        verdictFor(result, "C::CrossAttrEqual").holds());
  }

  /**
   * {@code s1}'s domain and {@code s2}'s domain share NO literal at all. The old raw-index
   * comparison could still spuriously "match" whichever pair happens to land on the same index
   * (e.g. both attributes' first-configured candidate), silently or via a crash depending on the
   * query shape. The correct answer is a clean UNSATISFIABLE: no assignment can make two disjoint
   * literals equal.
   */
  @Test
  public void disjointDomainsAreGenuinelyUnsatisfiableNotASpuriousMatch() throws Exception {
    ModelFinderResult result = find(List.of("Alpha", "Beta"), List.of("Gamma", "Delta"));

    assertFalse(
        "s1 and s2 share no literal, so the active CrossAttrEqual constraint is genuinely"
            + " unsatisfiable",
        result.satisfiable());
  }

  private static ModelFinderResult find(List<String> s1Domain, List<String> s2Domain)
      throws Exception {
    MModel model = compile(MODEL, "CrossDomainStringEquality");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("C", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("C", "s1", null, s1Domain, null, null),
                new AttributeDomain("C", "s2", null, s2Domain, null, null)),
            Set.of("C::CrossAttrEqual"),
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

  private static MModel compile(String source, String name) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, factory);
    err.flush();
    return model;
  }
}
