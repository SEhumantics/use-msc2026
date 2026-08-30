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
 * End-to-end regression for TYPE TESTS on a NAVIGATED receiver ({@code x.part.oclIsKindOf(B)},
 * {@code x.part.oclIsTypeOf(B)}) -- one of the shapes the {@code ocl.navigation-regular-assoc}
 * general case names. The navigation's destination end view folds the configured subclasses, so
 * the test reduces to a per-slot disjunction: the navigation's link term AND the linked slot's
 * concrete class match. Type tests stay TOTAL ({@code ExpIsKindOf#eval}: an undefined source
 * tests FALSE, never undefined), so an unlinked navigation contributes false rather than
 * undefinedness.
 */
public class NavigatedTypeTest {

  private static final String MODEL =
      """
      model NavType
      class X
      end
      abstract class A
      end
      class B < A
      end
      class C < A
      end
      association Links between
        X[0..1] role owner
        A[0..1] role part
      end
      constraints
      context x : X inv partIsB:
        x.part.oclIsKindOf(B)
      context x : X inv partIsExactlyB:
        x.part.oclIsTypeOf(B)
      """;

  private static final List<ClassScope> B_POPULATION =
      List.of(
          new ClassScope("X", 1, 1, List.of("x1")),
          new ClassScope("A", 0, 0),
          new ClassScope("B", 1, 1, List.of("b1")),
          new ClassScope("C", 0, 0));

  private static final List<ClassScope> C_POPULATION =
      List.of(
          new ClassScope("X", 1, 1, List.of("x1")),
          new ClassScope("A", 0, 0),
          new ClassScope("B", 0, 0),
          new ClassScope("C", 1, 1, List.of("c1")));

  /** kindOf over a folded end: true exactly when the linked part is a B (or subclass). */
  @Test
  public void navigatedKindOfHoldsExactlyWhenALinkedInstanceIsPresent() throws Exception {
    ModelFinderResult match = find(B_POPULATION, "x1", "b1", "partIsB");
    assertTrue("the forced link reaches a B, so kindOf(B) must hold", match.satisfiable());
    assertTrue(verdictFor(match, "X::partIsB").holds());
  }

  /** The same invariant over a C-only population refutes: kindOf(B) is false for a C link. */
  @Test
  public void navigatedKindOfRefutesWhenOnlyNonBLinkable() throws Exception {
    ModelFinderResult miss = findC("partIsB");
    assertFalse("only a C can be linked, so kindOf(B) must be violated", miss.satisfiable());
  }

  /** isTypeOf over the navigation: exact-class, so a subclass link would refute. */
  @Test
  public void navigatedIsTypeOfDistinguishesExactClass() throws Exception {
    ModelFinderResult match = find(B_POPULATION, "x1", "b1", "partIsExactlyB");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::partIsExactlyB").holds());
  }

  private static ModelFinderResult findC(String invariantName) throws Exception {
    return findRaw(
        C_POPULATION,
        List.of(new org.tzi.use.smt.config.AssociationScope("Links", 1, 1,
            List.of(List.of("x1", "c1")))),
        invariantName);
  }

  private static ModelFinderResult find(
      List<ClassScope> scopes, String ownerName, String partName, String invariantName)
      throws Exception {
    return findRaw(
        scopes,
        List.of(new org.tzi.use.smt.config.AssociationScope("Links", 1, 1,
            List.of(List.of(ownerName, partName)))),
        invariantName);
  }

  private static ModelFinderResult findRaw(
      List<ClassScope> scopes,
      List<org.tzi.use.smt.config.AssociationScope> associationScopes,
      String invariantName)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            scopes,
            associationScopes,
            List.of(),
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
    MModel model = USECompiler.compileSpecification(MODEL, "NavType", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + err);
    }
    return model;
  }
}
