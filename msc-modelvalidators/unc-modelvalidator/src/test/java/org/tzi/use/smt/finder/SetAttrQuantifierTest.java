package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
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
 * End-to-end regression for {@code ExpressionTranslator.setAttrQuantifier} -- the {@code
 * x.tags->forAll(...)}/{@code ->exists(...)} translation over a {@code Set(Integer)}-typed
 * attribute. Covers three chained defects an adversarial audit found:
 *
 * <ol>
 *   <li>the per-element loop-variable DEFINEDNESS symbol was let-bound to the pool INTEGER
 *       instead of {@code true} -- a sort mismatch Z3 rejects outright;
 *   <li>the per-element SMT-LIB symbol stem already carried a closing {@code |}, so appending
 *       {@code -defined|}/{@code -value|} produced a malformed doubly-quoted symbol;
 *   <li>once the solver was actually reached, the local forAll/exists definedness construction
 *       violated strong-Kleene semantics (no false/true-dominates rescue), the same bug class as
 *       this project's {@code visitForAll}/{@code visitExists} defect B3.
 * </ol>
 *
 * Every test here runs the REAL pipeline end to end: {@link SmtModelFinder#find} translates,
 * solves with the real pinned Z3 binary, reconstructs the witness system, and independently
 * re-evaluates every active invariant with USE's own evaluator (via {@code InvariantReEvaluator})
 * before {@code QueryWitnessChecker} accepts the witness -- a mismatch there throws {@code
 * WitnessAttributionException}, so a test simply completing (no exception) and asserting the
 * expected {@link ModelFinderResult#satisfiable()} already exercises that whole safety net.
 */
public class SetAttrQuantifierTest {

  private static final String SIMPLE_MODEL =
      """
      model SetAttrSimple
      class X
      attributes
        tags : Set(Integer)
      end
      constraints
      context x : X inv AllPositive:
        x.tags->forAll(t | t > 0)
      context x : X inv AllTooLarge:
        x.tags->forAll(t | t > 5)
      context x : X inv SomeAboveFive:
        x.tags->exists(t | t > 5)
      """;

  private static final String KLEENE_MODEL =
      """
      model SetAttrKleene
      class X
      attributes
        tags : Set(Integer)
      end
      class Y
      attributes
        flag : Boolean
      end
      association R between
        X [1] role x
        Y [0..1] role y
      end
      constraints
      context x : X inv MixedForAll:
        x.tags->forAll(t | (t > 5) and x.y.flag)
      context x : X inv MixedExists:
        x.tags->exists(t | (t > 5) or x.y.flag)
      context x : X inv MixedExistsAnd:
        x.tags->exists(t | (t > 5) and x.y.flag)
      """;

  // ---- SAT case: forAll/exists with a body genuinely referencing the loop variable ----

  /** Every pool member is forced into the set and all satisfy {@code t > 0}: forAll holds. */
  @Test
  public void forAllHoldsWhenEveryMemberSatisfiesTheBody() throws Exception {
    ModelFinderResult match =
        findSimple("X::AllPositive", List.of("1", "2", "3"), 3, 3);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::AllPositive").holds());
  }

  /**
   * Deterministic soundness regression for defects 1+2 (sort mismatch / malformed symbol):
   * every candidate (1,2,3) is &lt;= 5, so {@code forAll(t | t > 5)} can never hold for ANY
   * possible member -- this must be UNSAT regardless of which subset ends up in the set.
   */
  @Test
  public void forAllOverPoolThatCanNeverSatisfyTheBodyIsUnsatisfiable() throws Exception {
    ModelFinderResult result = findSimple("X::AllTooLarge", List.of("1", "2", "3"), 1, 1);
    assertFalse(
        "every candidate member (1,2,3) is <= 5, so this must be UNSAT under any correct"
            + " reading, regardless of Kleene subtleties",
        result.satisfiable());
  }

  /** exists holds once a genuine witness (10 > 5) is forced into the set. */
  @Test
  public void existsHoldsWhenAWitnessMemberIsForcedIn() throws Exception {
    ModelFinderResult match =
        findSimple("X::SomeAboveFive", List.of("10"), 1, 1);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::SomeAboveFive").holds());
  }

  /** exists refutes when the pool cannot possibly contain a member satisfying the body. */
  @Test
  public void existsRefutesWhenNoPoolMemberCanSatisfyTheBody() throws Exception {
    ModelFinderResult miss = findSimple("X::SomeAboveFive", List.of("1", "2", "3"), 1, 2);
    assertFalse(
        "no candidate (1,2,3) is > 5, so exists(t | t > 5) can never hold", miss.satisfiable());
  }

  // ---- Kleene-undefined case: mirrors the audit's "MixedForAll" reproduction ----

  /**
   * {@code X.tags} is forced to be exactly {1, 10}; {@code Y}'s scope is pinned to [0,0] so
   * {@code x.y} can never link. Per element: t=1 -&gt; {@code false and undefined} = Kleene
   * -absorbing DEFINED-FALSE; t=10 -&gt; {@code true and undefined} = UNDEFINED. The definite
   * counterexample at t=1 must dominate the undefined element at t=10, so the correct forAll
   * outcome is DEFINED-FALSE, never UNDEFINED.
   */
  @Test
  public void forAllTheDefiniteFalseWitnessDominatesTheUndefinedOne() throws Exception {
    ModelFinderResult falseClassification = findKleene("uncertain X::MixedForAll is false");
    assertTrue(
        "t=1 gives (t>5)=false, Kleene-absorbing false regardless of x.y.flag's"
            + " undefinedness; that dominates t=10's undefined element, so the forAll must"
            + " classify as DEFINED-FALSE",
        falseClassification.satisfiable());
  }

  /** The mirror check: this scenario must NOT classify as undefined. */
  @Test
  public void forAllMustNotClassifyAsUndefined() throws Exception {
    ModelFinderResult undefinedClassification = findKleene("uncertain X::MixedForAll is undefined");
    assertFalse(
        "a definite-false witness (t=1) is present, so Kleene forAll must NOT read as undefined",
        undefinedClassification.satisfiable());
  }

  /**
   * The exists mirror, with the connective flipped to {@code or} so the SAME pool ({1, 10})
   * produces the opposite dominance: t=1 -&gt; {@code false or undefined} = UNDEFINED; t=10 -&gt;
   * {@code true or undefined} = Kleene-absorbing DEFINED-TRUE. The definite witness at t=10 must
   * dominate the undefined element at t=1, so the correct exists outcome is DEFINED-TRUE, never
   * UNDEFINED.
   */
  @Test
  public void existsTheDefiniteTrueWitnessDominatesTheUndefinedOne() throws Exception {
    ModelFinderResult trueClassification = findKleene("uncertain X::MixedExists is true");
    assertTrue(
        "t=10 gives (t>5)=true, Kleene-absorbing true regardless of x.y.flag's"
            + " undefinedness; that dominates t=1's undefined element, so the exists must"
            + " classify as DEFINED-TRUE",
        trueClassification.satisfiable());
  }

  /** The mirror check: this scenario must NOT classify as undefined. */
  @Test
  public void existsMustNotClassifyAsUndefined() throws Exception {
    ModelFinderResult undefinedClassification = findKleene("uncertain X::MixedExists is undefined");
    assertFalse(
        "a definite-true witness (t=10) is present, so Kleene exists must NOT read as undefined",
        undefinedClassification.satisfiable());
  }

  /**
   * The exists-side analogue of {@code MixedForAll}'s own body ({@code and} rather than {@code
   * or}), which specifically distinguishes the correct Kleene exists reading from the pre-fix
   * hardcoded-{@code true} definedness bug: per element, t=1 -&gt; {@code false and undefined} =
   * defined-FALSE (no witness); t=10 -&gt; {@code true and undefined} = UNDEFINED. Neither element
   * is a definite witness, so exists must classify UNDEFINED -- the hardcoded-true bug would
   * instead have reported this as defined-FALSE (since no {@code trueTerm} is ever true here, so
   * {@code any} stays false, and the old code unconditionally marked that "false" as DEFINED).
   */
  @Test
  public void existsWithNoDefiniteWitnessAndOneUndefinedElementIsUndefined() throws Exception {
    ModelFinderResult undefinedClassification =
        findKleene("uncertain X::MixedExistsAnd is undefined");
    assertTrue(
        "neither t=1 (defined-false) nor t=10 (undefined) is a definite witness, so exists must"
            + " classify UNDEFINED -- the pre-fix hardcoded-true definedness would instead force"
            + " this to defined-FALSE",
        undefinedClassification.satisfiable());
  }

  /** The mirror check: this scenario must NOT classify as defined-false. */
  @Test
  public void existsWithNoDefiniteWitnessMustNotClassifyAsFalse() throws Exception {
    ModelFinderResult falseClassification = findKleene("uncertain X::MixedExistsAnd is false");
    assertFalse(
        "no element is a definite witness and one is undefined, so exists must NOT read as"
            + " defined-false",
        falseClassification.satisfiable());
  }

  private static ModelFinderResult findSimple(
      String invariant, List<String> pool, int minSize, int maxSize) throws Exception {
    MModel model = compile(SIMPLE_MODEL, "SetAttrSimple");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain(
                    "X", "tags", null, pool, new BigDecimal(minSize), new BigDecimal(maxSize))),
            Set.of(invariant),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static ModelFinderResult findKleene(String query) throws Exception {
    MModel model = compile(KLEENE_MODEL, "SetAttrKleene");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1), new ClassScope("Y", 0, 0)),
            List.of(new AssociationScope("R", 0, 0)),
            List.of(
                new AttributeDomain(
                    "X", "tags", null, List.of("1", "10"), new BigDecimal(2), new BigDecimal(2)),
                new AttributeDomain("Y", "flag", null, List.of("true"), null, null)),
            Set.of("X::MixedForAll", "X::MixedExists", "X::MixedExistsAnd"),
            QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
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

  private static MModel compile(String modelText, String modelName) {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(modelText, modelName, err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
