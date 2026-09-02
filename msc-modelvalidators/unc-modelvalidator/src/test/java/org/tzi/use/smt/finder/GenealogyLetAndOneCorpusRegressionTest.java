package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.verify.InvariantReEvaluator;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;

/**
 * The REAL, unmodified {@code benchmark/examples/Genealogy/Genealogy.use} corpus's own {@code
 * let}/{@code one} invariants (finding 2 and finding 1), read through the INDEPENDENT oracle
 * ({@link InvariantReEvaluator}) directly against the real compiled model -- NOT through {@link
 * SmtModelFinder#find}, for a reason confirmed empirically while writing this test rather than
 * assumed, and worth recording plainly:
 *
 * <p>{@code Person::parent_0_2_Set}, {@code Person::parent_0_2_size_EQUIV_parent_0_2_Set}, and
 * {@code Person::parent_0_2_size_EQUIV_parent_0_2_Set_ONE} all open with {@code let P =
 * Person.allInstances() in ...} -- a COLLECTION-typed {@code let} binding, which {@code
 * ExpressionTranslator.visitLet} refuses unconditionally ("only primitive Integer, Boolean, Real,
 * String, and Enum let bindings ... are supported"), for a reason that has nothing to do with the
 * {@code let}/{@code one} definedness-collapse bug this test guards against. {@code
 * Person::balancedBinaryTree} separately refuses on ITS OWN {@code one} body's inner {@code
 * Person.allInstances()->excluding(r)->forAll(...)} ("forAll over a range other than
 * X.allInstances() ... is not yet supported"). All three properties-file sections that ACTIVATE
 * any of these four invariants ({@code satisfiability}, {@code equivalence_check}, {@code
 * equivalence_one_check}) therefore throw {@code SmtTranslationException} before {@link
 * SmtModelFinder#solve} ever runs, let alone reaches {@link InvariantReEvaluator} -- confirmed
 * directly by running them, not merely read off the translator's source. This is a PRE-EXISTING,
 * unrelated limitation: it was already true before this fix, and remains true after it.
 *
 * <p>{@link InvariantReEvaluator}, by contrast, needs no translation at all -- it walks the REAL
 * parsed OCL AST with USE's own evaluator (routed through the fixed {@link
 * org.tzi.use.smt.verify.ThreeValuedEvaluator}), so it reaches these four invariants regardless.
 * And a second thing confirmed here, by hand-tracing every operand and then checking the trace
 * against the real evaluator: NONE of the four invariants' operands can EVER be genuinely
 * undefined for ANY population -- {@code p.parent}/{@code p.child} are association navigations
 * (always a defined, possibly-empty collection under USE's own multiplicity semantics) and {@code
 * size()} is a total function; nothing in any of the four bodies ever touches an attribute that
 * could be left unset. So the fix provably cannot change what these four invariants read on this
 * corpus, for any witness -- confirmed below with the smallest population that still makes the
 * fixed code paths (the {@code let}/{@code one} dispatch cases, not the empty-population
 * short-circuit that skips evaluating the body at all) actually run: exactly one, unlinked
 * {@code Person}. Every value asserted below is independently hand-derived in this class's own
 * javadoc method comments before being confirmed against the real evaluator, matching this
 * project's "confirmed directly, not assumed" standard.
 */
public class GenealogyLetAndOneCorpusRegressionTest {

  private static final List<String> LET_AND_ONE_INVARIANTS =
      List.of(
          "Person::parent_0_2_Set",
          "Person::parent_0_2_size_EQUIV_parent_0_2_Set",
          "Person::parent_0_2_size_EQUIV_parent_0_2_Set_ONE",
          "Person::balancedBinaryTree");

  /**
   * One {@code Person}, {@code p1}, with no {@code Parenthood} links at all: {@code
   * p1.parent = Set{}} and {@code p1.child = Set{}}, both defined-empty.
   *
   * <ul>
   *   <li>{@code parent_0_2_Set}: {@code p.parent=Set{}} is the FIRST disjunct and is TRUE, so the
   *       whole body is TRUE regardless of the other two ({@code one}/{@code exists}) disjuncts'
   *       own values.
   *   <li>{@code parent_0_2_size_EQUIV_parent_0_2_Set}(_ONE): {@code A} ({@code
   *       0<=p.parent->size()<=2}) is TRUE ({@code size()=0}); {@code B}/{@code B'} are each the
   *       {@code parent_0_2_Set}-shaped body above (with {@code exists}/{@code one} respectively)
   *       and are each TRUE by the same first-disjunct argument. {@code (T implies T) and (T
   *       implies T)} is TRUE.
   *   <li>{@code balancedBinaryTree}: {@code p.child->size()=0} makes the first AND-operand TRUE;
   *       the {@code one(r | r.parent->size()=0 and Person.allInstances()->excluding(r)
   *       ->forAll(p2 | p2.parent->size()=1))} operand has exactly one candidate {@code r=p1}
   *       ({@code r.parent->size()=0} TRUE, and {@code excluding(p1)} is EMPTY so the inner {@code
   *       forAll} is vacuously TRUE), so it matches and reads TRUE; the final {@code p.child->
   *       forAll(c1,c2 | ...)} operand ranges over the EMPTY {@code p1.child}, vacuously TRUE.
   *       {@code TRUE and TRUE and TRUE} is TRUE.
   * </ul>
   *
   * <p>Every one of these is a DEFINED-TRUE reading with no undefinedness anywhere in the trace --
   * exactly what both the unfixed and fixed oracle should agree on, since nothing here is ever
   * undefined; this pins the concrete value down rather than merely asserting "unchanged".
   */
  @Test
  public void theRealLetAndOneInvariantsReadDefinedTrueOverALoneUnlinkedPerson() throws Exception {
    MModel model = compileGenealogy();
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("Person"), "p1");

    List<InvariantVerdict> verdicts =
        InvariantReEvaluator.reevaluate(
            model, session.system(), TranslationMode.UNCERTAIN, Set.copyOf(LET_AND_ONE_INVARIANTS));

    for (String invariantName : LET_AND_ONE_INVARIANTS) {
      assertEquals(
          invariantName + " must read DEFINED-TRUE over a lone, unlinked Person -- see this"
              + " method's own javadoc for the hand-derivation",
          InvariantOutcome.TRUE,
          outcomeOf(verdicts, invariantName));
    }
  }

  /**
   * The same four invariants over a small, genuinely-branching population (a 2-generation family:
   * one grandparent {@code gp}, two parents {@code pa1}/{@code pa2} both children of {@code gp},
   * and one grandchild {@code gc} child of {@code pa1} only) -- still every operand is built from
   * association navigation/{@code size()}, so still no undefinedness is reachable, but this
   * confirms the fix does not perturb a population where {@code one}'s candidate set has more than
   * one member and the nested {@code forAll}/{@code exists}/{@code one} actually range over more
   * than a single (trivially vacuous) element.
   */
  @Test
  public void theRealLetAndOneInvariantsAgreeOverABranchingPopulationTooRegardlessOfTheFix()
      throws Exception {
    MModel model = compileGenealogy();
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("Person"), "gp");
    api.createObjectEx(model.getClass("Person"), "pa1");
    api.createObjectEx(model.getClass("Person"), "pa2");
    api.createObjectEx(model.getClass("Person"), "gc");
    api.createLink("Parenthood", "gp", "pa1");
    api.createLink("Parenthood", "gp", "pa2");
    api.createLink("Parenthood", "pa1", "gc");

    List<InvariantVerdict> verdicts =
        InvariantReEvaluator.reevaluate(
            model, session.system(), TranslationMode.UNCERTAIN, Set.copyOf(LET_AND_ONE_INVARIANTS));

    // gp has 2 children (balanced), pa1 has 1 child (unbalanced), pa2/gc have 0 (balanced-leaf) --
    // balancedBinaryTree must therefore read DEFINED-FALSE (pa1's own size()=1 dominates the
    // universal context quantifier), while every parent_0_2_* invariant's own population (0..2
    // parents per person, always true here: gp has 0, pa1/pa2 have 1, gc has 1) still holds.
    assertEquals(InvariantOutcome.TRUE, outcomeOf(verdicts, "Person::parent_0_2_Set"));
    assertEquals(
        InvariantOutcome.TRUE,
        outcomeOf(verdicts, "Person::parent_0_2_size_EQUIV_parent_0_2_Set"));
    assertEquals(
        InvariantOutcome.TRUE,
        outcomeOf(verdicts, "Person::parent_0_2_size_EQUIV_parent_0_2_Set_ONE"));
    assertEquals(InvariantOutcome.FALSE, outcomeOf(verdicts, "Person::balancedBinaryTree"));
  }

  private static InvariantOutcome outcomeOf(List<InvariantVerdict> verdicts, String name) {
    return verdicts.stream()
        .filter(verdict -> verdict.invariantName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + name))
        .outcome();
  }

  private static Session newSession(MModel model) {
    Session session = new Session();
    session.setSystem(new MSystem(model));
    return session;
  }

  private static MModel compileGenealogy() throws Exception {
    Path file = examplePath("Genealogy/Genealogy.use");
    String source = Files.readString(file);
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "Genealogy", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("Genealogy.use did not compile");
    }
    return model;
  }

  private static Path examplePath(String relative) {
    Path fromModule = Path.of("../benchmark/examples").resolve(relative);
    if (Files.isRegularFile(fromModule)) {
      return fromModule;
    }
    return Path.of("msc-modelvalidators/benchmark/examples").resolve(relative);
  }
}
