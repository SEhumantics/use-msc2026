package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * {@code X->includesAll(Y)} between two populations that are NOT index-aligned.
 *
 * <p>{@code ExpressionTranslator.collectionIncludesAll} used to pair the two populations slot by
 * slot ({@code Y[k] => X[k]}), on the assumption that two navigations reaching the same class draw
 * from that class's one shared slot view in the same order. Two real, reachable shapes break that
 * assumption, and each was confirmed defective end to end before this test existed:
 *
 * <ul>
 *   <li>a UNION-declared end, whose population is the CONCATENATION of every {@code
 *       subsets}-declaring association's own population (and in {@code HashSet} order at that, so
 *       neither the size nor the order matches any single subsetter's);
 *   <li>a REDEFINED end, redirected by {@code resolveRedefinedDestination} to the redefining
 *       association's narrower end, whose view holds only the subclass's slots while the
 *       unredefined side's view is FOLDED over the whole polymorphic population.
 * </ul>
 *
 * <p>Each test below therefore drives a genuine SAT/UNSAT discrimination pair through the real
 * solver, with the SAT side's verdict independently re-established by USE's own evaluator on the
 * reconstructed witness -- an encoding that merely stopped crashing would still fail the UNSAT
 * half, and one that answered "vacuously true" would fail it too.
 */
public class IncludesAllPopulationAlignmentTest {

  // ------------------------------------------------------------------ shape 1: a union end

  private static final String UNION_MODEL =
      """
      model UnionIncludesAll
      class W
      end
      class P
      end
      association wp_all between
        W[*] role wholes union
        P[*] role allParts union
      end
      association wp_mech between
        W[*] role mechWholes subsets wholes
        P[*] role mechParts subsets allParts
      end
      association wp_elec between
        W[*] role elecWholes subsets wholes
        P[*] role elecParts subsets allParts
      end
      constraints
      context w : W inv MechInAll:
        w.allParts->includesAll(w.mechParts)
      context w : W inv ElecInAll:
        w.allParts->includesAll(w.elecParts)
      context w : W inv AllInMech:
        w.mechParts->includesAll(w.allParts)
      """;

  /**
   * The union on the CONTAINING side. Both invariants are tautologies under USE's own union
   * semantics (a subsetting association's links are, by derivation, part of the union role's
   * content), so the only correct verdict is SAT with both true -- for EITHER {@code HashSet}
   * order over the two subsetting ends, which is why both are asserted together: with the
   * index-pairing encoding, whichever subsetter landed in the union population's leading half
   * satisfied its own invariant vacuously while the OTHER one's members were tested against that
   * half's unrelated guards, so one of the two always refuted. (Observed directly on the
   * pre-fix encoding: {@code satisfiable=false}.)
   */
  @Test
  public void everySubsettersMembersAreContainedInTheUnionTheyDeriveIt() throws Exception {
    ModelFinderResult result =
        findUnion(Set.of("W::MechInAll", "W::ElecInAll"), true);
    assertTrue(
        "mechParts and elecParts each derive part of allParts, so both containments hold",
        result.satisfiable());
    assertTrue(verdictFor(result, "W::MechInAll").holds());
    assertTrue(verdictFor(result, "W::ElecInAll").holds());
  }

  /**
   * The union on the CONTAINED side -- the direction the index pairing could not even reach: the
   * union population is twice the subsetter's length, so {@code collectionPopulation.get(k)} ran
   * off the end ({@code IndexOutOfBoundsException: Index 2 out of bounds for length 2}). The
   * SAT/UNSAT pair here is a real discrimination: with only wp_mech linked the union holds exactly
   * what mechParts holds, and with wp_elec contributing a SECOND part the containment genuinely
   * fails.
   */
  @Test
  public void theUnionIsContainedInASubsetterExactlyWhenNoOtherSubsetterContributes()
      throws Exception {
    ModelFinderResult contained = findUnion(Set.of("W::AllInMech"), false);
    assertTrue(
        "wp_elec has no links, so allParts is exactly mechParts", contained.satisfiable());
    assertTrue(verdictFor(contained, "W::AllInMech").holds());

    ModelFinderResult notContained = findUnion(Set.of("W::AllInMech"), true);
    assertFalse(
        "wp_elec contributes p2 to allParts, which mechParts does not hold",
        notContained.satisfiable());
  }

  /**
   * @param splitLinks true: wp_mech = &#123;(w1,p1)&#125; and wp_elec = &#123;(w1,p2)&#125;;
   *     false: wp_mech = &#123;(w1,p1)&#125; and wp_elec has no links at all.
   */
  private static ModelFinderResult findUnion(Set<String> active, boolean splitLinks)
      throws Exception {
    MModel model = compile(UNION_MODEL, "UnionIncludesAll");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("W", 1, 1, List.of("w1")),
                new ClassScope("P", 2, 2, List.of("p1", "p2"))),
            List.of(
                new AssociationScope("wp_all", 0, 0),
                new AssociationScope("wp_mech", 1, 1, List.of(List.of("w1", "p1"))),
                splitLinks
                    ? new AssociationScope("wp_elec", 1, 1, List.of(List.of("w1", "p2")))
                    : new AssociationScope("wp_elec", 0, 0)),
            List.of(),
            active,
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  // -------------------------------------------------------------- shape 2: a redefined end

  private static final String REDEFINES_MODEL =
      """
      model RedefIncludesAll
      class A
      end
      class B
      end
      class C < A
      end
      class D < B
      end
      association AB between
        A[*] role a
        B[*] role b
      end
      association CD between
        C[*] role c redefines a
        D[*] role d redefines b
      end
      association AB2 between
        A[*] role a2
        B[*] role b2
      end
      constraints
      context x : C inv RedefInclContainer:
        x.b->includesAll(x.b2)
      context x : C inv RedefInclContained:
        x.b2->includesAll(x.b)
      """;

  /**
   * {@code x.b} from a {@code C} source is redirected to CD's {@code d} end (D's slots only, one
   * slot), while {@code x.b2} keeps AB2's declared {@code B} end -- whose view is FOLDED over B's
   * own slot AND D's, so it is twice as long. The pre-fix encoding indexed the short side with the
   * long side's index ({@code IndexOutOfBoundsException: Index 1 out of bounds for length 1}).
   * Linking AB2 to d1 makes both sides hold exactly d1 (SAT); linking it to b1 instead puts a
   * plain B object in {@code x.b2} that {@code x.b} can never hold (UNSAT).
   */
  @Test
  public void aRedefinedContainerHoldsOnlyTheRedefiningEndsOwnCandidates() throws Exception {
    ModelFinderResult contains = findRedefines("C::RedefInclContainer", "d1");
    assertTrue("x.b and x.b2 both hold exactly d1", contains.satisfiable());
    assertTrue(verdictFor(contains, "C::RedefInclContainer").holds());

    ModelFinderResult doesNotContain = findRedefines("C::RedefInclContainer", "b1");
    assertFalse(
        "x.b2 holds b1, a plain B that the redefined x.b (D objects only) cannot hold",
        doesNotContain.satisfiable());
  }

  /**
   * The mirror direction, and the one that produced a WRONG ANSWER rather than a crash: with the
   * short (D-only) population on the CONTAINED side, the index pairing stayed in bounds and tested
   * d1's membership against B's slot instead of D's, so a solve claimed SAT for a state USE's own
   * evaluator then read as FALSE -- caught only by the witness checker, as {@code
   * WitnessAttributionException}. A wrong answer defended by a downstream tripwire is still a
   * wrong answer, so this asserts the encoding itself now refutes it.
   */
  @Test
  public void aRedefinedContainedSideIsMatchedByCandidateNotBySlotPosition() throws Exception {
    ModelFinderResult contained = findRedefines("C::RedefInclContained", "d1");
    assertTrue("x.b2 holds d1, which is exactly what x.b holds", contained.satisfiable());
    assertTrue(verdictFor(contained, "C::RedefInclContained").holds());

    ModelFinderResult notContained = findRedefines("C::RedefInclContained", "b1");
    assertFalse(
        "x.b holds d1 while x.b2 holds only b1, so the containment genuinely fails",
        notContained.satisfiable());
  }

  private static ModelFinderResult findRedefines(String active, String ab2Partner)
      throws Exception {
    MModel model = compile(REDEFINES_MODEL, "RedefIncludesAll");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("A", 0, 0),
                new ClassScope("B", 1, 1, List.of("b1")),
                new ClassScope("C", 1, 1, List.of("c1")),
                new ClassScope("D", 1, 1, List.of("d1"))),
            List.of(
                new AssociationScope("AB", 0, 0),
                new AssociationScope("CD", 1, 1, List.of(List.of("c1", "d1"))),
                new AssociationScope("AB2", 1, 1, List.of(List.of("c1", ab2Partner)))),
            List.of(),
            Set.of(active),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  // ------------------------------------------ the resolved-destination check the above needs

  /**
   * The declared-class check alone cannot see redefinition, because {@code
   * resolveRedefinedDestination} runs later, inside {@code populationOf}. Two SIBLING
   * redefinitions off the same source class ({@code x.b} to D, {@code x.b2} to E, D and E
   * unrelated below B) leave the declared classes agreeing while the two populations can never
   * share a single candidate. That is refused with a located boundary error naming the RESOLVED
   * classes, rather than answered with the vacuous truth the candidate-matching subset test would
   * otherwise produce.
   */
  @Test
  public void siblingRedefinitionsToUnrelatedClassesAreRefusedNotAnsweredVacuously()
      throws Exception {
    MModel model =
        compile(
            """
            model SiblingRedefIncludesAll
            class A
            end
            class B
            end
            class C < A
            end
            class D < B
            end
            class E < B
            end
            association AB between
              A[*] role a
              B[*] role b
            end
            association AB2 between
              A[*] role a2
              B[*] role b2
            end
            association CD between
              C[*] role c redefines a
              D[*] role d redefines b
            end
            association CE between
              C[*] role c2 redefines a2
              E[*] role e redefines b2
            end
            constraints
            context x : C inv Siblings:
              x.b->includesAll(x.b2)
            """,
            "SiblingRedefIncludesAll");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("A", 0, 0),
                new ClassScope("B", 1, 1),
                new ClassScope("C", 1, 1),
                new ClassScope("D", 1, 1),
                new ClassScope("E", 1, 1)),
            List.of(
                new AssociationScope("AB", 0, 0),
                new AssociationScope("AB2", 0, 0),
                new AssociationScope("CD", 0, 1),
                new AssociationScope("CE", 0, 1)),
            List.of(),
            Set.of("C::Siblings"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    try {
      SmtModelFinder.find(model, config);
      fail("includesAll between two unrelated redefinition-resolved destinations must be refused");
    } catch (SmtTranslationException expected) {
      // SmtModelFinder aggregates every unsupported invariant into one refusal, so the per-
      // construct boundary rides the message rather than the wrapper's own boundary field.
      assertTrue(
          "must refuse at TIER_3, got: " + expected.getMessage(),
          expected.getMessage().contains("TIER_3"));
      assertTrue(
          "must blame includesAll and name the RESOLVED classes, got: " + expected.getMessage(),
          expected.getMessage().contains("includesAll")
              && expected.getMessage().contains("(D, E)"));
    }
  }

  // ------------------------------------------------------------------------------- helpers

  private static InvariantVerdict verdictFor(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compile(String source, String name) {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(source, name, err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError(name + " fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
