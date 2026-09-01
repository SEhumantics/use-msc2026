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
 * Declared-end-identity regression for REFLEXIVE n-ary navigation: a genuinely three-way
 * reflexive ternary association (all three ends on Person), navigated with USE's own
 * explicit-rolename qualifier syntax {@code p.c[b]} (grammar: {@code object.dst[srcRolename]}).
 * USE's parser accepts this -- the earlier "reflexive n-ary navigation is a parser
 * limitation" note was true only for BARE unqualified navigation over 3+ same-class ends.
 *
 * <p>The forced tuples are chosen so that the DECLARED source end and the first
 * content-matched end genuinely disagree:
 *
 * <pre>
 *   declared ends (a, b, c):  T1=(p1,p2,p3)  T2=(p2,p1,p3)  T3=(p3,p1,p2)
 *   via a (sourceEnd=a):  p1 -> {p3},  p2 -> {p3},  p3 -> {p2}   (all nonempty)
 *   via b (sourceEnd=b):  p1 -> {p3,p2}, p2 -> {p3}, p3 -> {}    (p3 empty!)
 * </pre>
 *
 * <p>Before the declared-end-identity fix in naryNavigationPopulation, the source end was
 * found by CONTENT match (first end e != destEnd whose end view contains the source
 * binding) — with all three end views over the same Person slots, that always picked end
 * a, so {@code p.c[b]} silently answered the end-a question (wrong orientation).
 */
public class ReflexiveNaryNavigationTest {

  private static final String MODEL =
      """
      model Reflex
      class Person
      attributes
        name : String
      end
      association Tri between
        Person[0..2] role a
        Person[0..2] role b
        Person[0..2] role c
      end
      constraints
      context p : Person inv viaANotEmpty:
        p.c[a]->notEmpty()
      context p : Person inv viaBNotEmpty:
        p.c[b]->notEmpty()
      """;

  private static final List<ClassScope> SCOPES =
      List.of(new ClassScope("Person", 3, 3, List.of("p1", "p2", "p3")));

  private static final List<AssociationScope> TUPLES =
      List.of(new AssociationScope("Tri", 3, 3,
          List.of(List.of("p1", "p2", "p3"),
              List.of("p2", "p1", "p3"),
              List.of("p3", "p1", "p2"))));

  /**
   * The DECLARED orientation is satisfiable: every person occupies end a of some tuple, so
   * every p.c[a] projection is nonempty. (USE-confirmed by the finder's independent
   * re-evaluation.)
   */
  @Test
  public void qualifiedNavigationByRolenameAIsSatisfiable() throws Exception {
    MModel model = compile();
    ModelFinderResult match = SmtModelFinder.find(model, config(model,
        Set.of("Person::viaANotEmpty")));
    assertTrue("via a: every person sits at end a, all projections nonempty",
        match.satisfiable());
    assertTrue(verdictFor(match, "Person::viaANotEmpty").holds());
  }

  /**
   * THE DISCRIMINATOR: p3 never occupies end b of any tuple, so under the DECLARED source
   * end the projection p3.c[b] is empty and the universal notEmpty must refute. The old
   * content-match code answered the end-a question instead (every person occupies end a),
   * which is satisfiable -- exactly the wrong-orientation symptom this test pins.
   */
  @Test
  public void qualifiedNavigationUsesTheDeclaredEndNotTheFirstContentMatch() throws Exception {
    MModel model = compile();
    ModelFinderResult miss = SmtModelFinder.find(model, config(model,
        Set.of("Person::viaBNotEmpty")));
    assertFalse("via b: p3 never sits at end b, so p3.c[b] is empty -> notEmpty refutes; "
        + "a satisfiable answer here means the source end was content-matched to a",
        miss.satisfiable());
  }

  private static AnalysisConfiguration config(MModel model, Set<String> invariants) {
    return new AnalysisConfiguration(
        SCOPES,
        TUPLES,
        List.of(new AttributeDomain("Person", "name", null, List.of("'x'"), null, null)),
        invariants,
        QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
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
    MModel model = USECompiler.compileSpecification(MODEL, "Reflex", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
