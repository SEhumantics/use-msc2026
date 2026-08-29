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
 * End-to-end regression for String/Enum comparisons involving a LET-BOUND variable or a
 * free-standing literal, extending the soundness fix documented in {@link
 * CrossDomainStringEqualityTest}: a String/Enum value's SMT integer is POSITIONAL WITHIN ITS OWN
 * attribute's configured candidate list, so any equality that is not built by CONTENT (a
 * disjunction over index pairs whose configured literals actually match) can silently equate two
 * different literals or deny two equal ones whenever the operands' domains disagree.
 *
 * <p>{@code comparison()}'s content-aware branches deliberately excluded let-bound variables (left
 * as "an explicitly open extension" when the bare-vs-bare and navigated-vs-bare shapes were fixed),
 * so {@code let s : String = c.s1 in s = c.s2} still compared raw indices and manufactured witnesses
 * USE's own re-evaluation denied ({@code WitnessAttributionException}, reproduced by these tests'
 * first two cases before the fix). The free-standing literal shapes -- literal vs. let-bound
 * variable, literal as the let initializer, free-standing enum literal -- failed closed with
 * "string literal compared against a non-attribute expression" / "free-standing string literal
 * outside an attribute comparison", the exact remaining shapes the feature matrix names under
 * {@code prim.string-literals-equality} and {@code ocl.let}.
 */
public class LetBoundStringEqualityTest {

  private static final String MODEL =
      """
      model LetBoundStringEquality
      class C
      attributes
        s1 : String
        s2 : String
      end
      constraints
      context c : C inv LetCrossEqual:
        let s : String = c.s1 in s = c.s2
      context c : C inv LetNestedCrossEqual:
        let a : String = c.s1 in let b : String = a in b = c.s2
      context c : C inv LetLitEqual:
        let s : String = c.s1 in s = 'Yankee'
      context c : C inv LetLitNotEqual:
        let s : String = c.s1 in s <> 'Yankee'
      context c : C inv LetInitLitEqual:
        let s : String = 'Zulu' in s = c.s1
      """;

  private static final String ENUM_MODEL =
      """
      model LetBoundEnumEquality
      enum Status { active, retired }
      class C
      attributes
        st : Status
      end
      constraints
      context c : C inv LetEnumLitEqual:
        let s : Status = c.st in s = #active
      """;

  private static final String NAV_MODEL =
      """
      model LetBoundNavStringEquality
      class C
      attributes
        s1 : String
      end
      class D
      attributes
        name : String
      end
      association R between
        C[1] role c
        D[0..1] role d
      end
      constraints
      context c : C inv LetNavLitEqual:
        let s : String = c.d.name in s = 'Yankee'
      """;

  /**
   * Swapped domains: {@code s1}'s index 0 is 'Zulu', {@code s2}'s index 0 is 'Yankee'. The old raw
   * comparison let the solver pick both at index 0 and claim a match USE denies
   * ({@code WitnessAttributionException}). The correct answer is still SATISFIABLE -- both domains
   * genuinely share both literals -- so the fix must produce a CORRECTLY cross-checked witness.
   */
  @Test
  public void letVarComparedAcrossSwappedDomainsStillFindsAGenuinelyCorrectWitness()
      throws Exception {
    ModelFinderResult result =
        find(
            MODEL,
            "LetCrossEqual",
            List.of(new AttributeDomain("C", "s1", null, List.of("Zulu", "Yankee"), null, null),
                new AttributeDomain("C", "s2", null, List.of("Yankee", "Zulu"), null, null)));

    assertTrue(
        "both domains genuinely share 'Zulu' and 'Yankee', so a real witness exists",
        result.satisfiable());
    assertTrue(
        "USE's own re-evaluation must confirm the witness -- find would already have thrown"
            + " WitnessAttributionException otherwise",
        verdictFor(result, "C::LetCrossEqual").holds());
  }

  /** Disjoint domains: raw-index comparison spuriously matched index 0 with index 0. */
  @Test
  public void letVarComparedAcrossDisjointDomainsIsGenuinelyUnsatisfiableNotASpuriousMatch()
      throws Exception {
    ModelFinderResult result =
        find(
            MODEL,
            "LetCrossEqual",
            List.of(new AttributeDomain("C", "s1", null, List.of("Alpha", "Beta"), null, null),
                new AttributeDomain("C", "s2", null, List.of("Gamma", "Delta"), null, null)));

    assertFalse(
        "s1 and s2 share no literal, so the active let-based equality is genuinely unsatisfiable",
        result.satisfiable());
  }

  /** A let-bound variable inherits its initializer's domain through a nested let chain. */
  @Test
  public void nestedLetChainStillComparesByContent() throws Exception {
    ModelFinderResult result =
        find(
            MODEL,
            "LetNestedCrossEqual",
            List.of(new AttributeDomain("C", "s1", null, List.of("Zulu", "Yankee"), null, null),
                new AttributeDomain("C", "s2", null, List.of("Yankee", "Zulu"), null, null)));

    assertTrue("a real shared literal exists ('Zulu' or 'Yankee')", result.satisfiable());
    assertTrue(verdictFor(result, "C::LetNestedCrossEqual").holds());
  }

  /**
   * The literal is resolved within the let variable's OWN domain ('Yankee' sits at index 0 of
   * {'Yankee','Zulu'}), so equality is satisfiable exactly when s1 can really be 'Yankee'.
   */
  @Test
  public void letVarComparedAgainstALiteralResolvesByContentNotIndexPosition() throws Exception {
    ModelFinderResult result =
        find(
            MODEL,
            "LetLitEqual",
            List.of(new AttributeDomain("C", "s1", null, List.of("Yankee", "Zulu"), null, null)));

    assertTrue("'Yankee' is a configured candidate of s1", result.satisfiable());
    assertTrue(verdictFor(result, "C::LetLitEqual").holds());
  }

  /** A literal outside the let variable's domain can never match: clean UNSAT, no spurious SAT. */
  @Test
  public void letVarLiteralOutsideTheDomainIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult result =
        find(
            MODEL,
            "LetLitEqual",
            List.of(new AttributeDomain("C", "s1", null, List.of("Alpha", "Beta"), null, null)));

    assertFalse(
        "s1 can never be 'Yankee', so the active equality is genuinely unsatisfiable",
        result.satisfiable());
  }

  /**
   * Inequality polarity: with 'Yankee' the ONLY configured candidate, {@code s <> 'Yankee'} is
   * unsatisfiable; with a second candidate it is satisfiable and USE must confirm the witness.
   */
  @Test
  public void letVarLiteralInequalityHasTheCorrectPolarity() throws Exception {
    ModelFinderResult only =
        find(
            MODEL,
            "LetLitNotEqual",
            List.of(new AttributeDomain("C", "s1", null, List.of("Yankee"), null, null)));
    assertFalse("s1 is forced to 'Yankee', so <> 'Yankee' cannot hold", only.satisfiable());

    ModelFinderResult both =
        find(
            MODEL,
            "LetLitNotEqual",
            List.of(
                new AttributeDomain("C", "s1", null, List.of("Yankee", "Zulu"), null, null)));
    assertTrue("s1 can be 'Zulu', satisfying <> 'Yankee'", both.satisfiable());
    assertTrue(verdictFor(both, "C::LetLitNotEqual").holds());
  }

  /** A literal let initializer carries its own singleton content domain. */
  @Test
  public void literalLetInitializerIsComparedByContentAgainstTheAttribute() throws Exception {
    ModelFinderResult match =
        find(
            MODEL,
            "LetInitLitEqual",
            List.of(new AttributeDomain("C", "s1", null, List.of("Zulu", "Yankee"), null, null)));
    assertTrue("'Zulu' is a configured candidate of s1", match.satisfiable());
    assertTrue(verdictFor(match, "C::LetInitLitEqual").holds());

    ModelFinderResult disjoint =
        find(
            MODEL,
            "LetInitLitEqual",
            List.of(new AttributeDomain("C", "s1", null, List.of("Alpha", "Beta"), null, null)));
    assertFalse("s1 can never be 'Zulu'", disjoint.satisfiable());
  }

  /** The enum sibling: a free-standing enum literal against a let-bound enum variable. */
  @Test
  public void enumLetComparedAgainstAFreeStandingLiteralResolvesByContent() throws Exception {
    ModelFinderResult match =
        find(
            ENUM_MODEL,
            "LetEnumLitEqual",
            List.of(new AttributeDomain("C", "st", null, List.of("retired", "active"), null, null)));
    assertTrue("'active' is a configured candidate of st", match.satisfiable());
    assertTrue(verdictFor(match, "C::LetEnumLitEqual").holds());

    ModelFinderResult disjoint =
        find(
            ENUM_MODEL,
            "LetEnumLitEqual",
            List.of(new AttributeDomain("C", "st", null, List.of("retired"), null, null)));
    assertFalse("st can only be 'retired', never 'active'", disjoint.satisfiable());
  }

  /**
   * A literal against a let-bound NAVIGATED attribute ({@code c.d.name}): the let inherits the
   * DESTINATION attribute's domain, and the comparison stays content-correct whether the link
   * exists, and sound (definedness-driven false) when it does not.
   */
  @Test
  public void literalAgainstALetBoundNavigatedAttributeResolvesByContent() throws Exception {
    ModelFinderResult match = findNav(NAV_MODEL,
        List.of(new ClassScope("C", 1, 1, List.of("c0")),
            new ClassScope("D", 1, 1, List.of("d0"))),
        List.of(new AssociationScope("R", 1, 1, List.of(List.of("c0", "d0")))),
        List.of(new AttributeDomain("D", "name", null, List.of("Yankee", "Zulu"), null, null)));
    assertTrue("d0 can be named 'Yankee'", match.satisfiable());
    assertTrue(verdictFor(match, "C::LetNavLitEqual").holds());

    ModelFinderResult disjoint = findNav(NAV_MODEL,
        List.of(new ClassScope("C", 1, 1, List.of("c0")),
            new ClassScope("D", 1, 1, List.of("d0"))),
        List.of(new AssociationScope("R", 1, 1, List.of(List.of("c0", "d0")))),
        List.of(new AttributeDomain("D", "name", null, List.of("Alpha", "Beta"), null, null)));
    assertFalse("d0 can never be named 'Yankee'", disjoint.satisfiable());

    ModelFinderResult unlinked = findNav(NAV_MODEL,
        List.of(new ClassScope("C", 1, 1, List.of("c0")),
            new ClassScope("D", 1, 1, List.of("d0"))),
        List.of(new AssociationScope("R", 0, 0)),
        List.of(new AttributeDomain("D", "name", null, List.of("Yankee", "Zulu"), null, null)));
    assertFalse(
        "with no link at all the let variable is undefined, so the equality cannot hold",
        unlinked.satisfiable());
  }

  private static ModelFinderResult find(
      String modelSource, String invariantName, List<AttributeDomain> domains) throws Exception {
    MModel model = compile(modelSource);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("C", 1, 1)),
            List.of(),
            domains,
            Set.of("C::" + invariantName),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static ModelFinderResult findNav(
      String modelSource,
      List<ClassScope> classScopes,
      List<AssociationScope> associationScopes,
      List<AttributeDomain> domains)
      throws Exception {
    MModel model = compile(modelSource);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            classScopes,
            associationScopes,
            domains,
            Set.of("C::LetNavLitEqual"),
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

  private static MModel compile(String source) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "LetBoundStringEquality", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
