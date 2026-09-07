package org.tzi.use.smt.verify;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.util.List;
import org.junit.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;

/**
 * The connective/quantifier/equality semantic kernel the paper's Section 3.1 anchors
 * {@code (s, sigma) models_U Inv} to, asserted over the FULL three-valued operand matrix rather
 * than the single cells earlier tests pin (depth: OracleThreeValuedDepthTest; xor/equality
 * cells: OracleXorAndEqualityTest; sibling gaps: OracleSiblingGapsTest).
 *
 * <p>Rules under test, all strong Kleene except where OCL is total:
 *
 * <ul>
 *   <li>{@code and}: FALSE dominates UNDEFINED (F and U = F); otherwise U infects;
 *   <li>{@code or}: TRUE dominates UNDEFINED (T or U = T); otherwise U infects;
 *   <li>{@code implies}: F implies X = T; T implies U = U; U implies F = U;
 *   <li>{@code xor}, {@code not}: no absorbing value, U infects;
 *   <li>{@code =} and {@code <>} are TOTAL: true iff both sides undefined / differ, so
 *       UNDEFINED = false is FALSE, never UNDEFINED;
 *   <li>{@code forAll}: a defined-false element dominates UNDEFINED elements;
 *   <li>{@code exists}: a defined-true element dominates UNDEFINED elements;
 *   <li>{@code isDefined}/{@code isUndefined} classify without propagating.
 * </ul>
 *
 * <p>These rules are what the encoder ({@code InvariantAssembler}, {@code ExpressionTranslator})
 * and the oracle ({@code ThreeValuedEvaluator}) share, so the matrix is agreement evidence for
 * both sides of the re-evaluation boundary. It is empirical agreement evidence, not a proof of
 * encoder correctness.
 */
public class OracleSemanticKernelMatrixTest {

  private static final String MODEL_SOURCE =
      """
      model KernelMatrix
      class P
      attributes
        n1 : Integer
        n2 : Integer
      end
      constraints
      context p : P inv andAB: (p.n1 > 0) and (p.n2 >= 1)
      context p : P inv orAB: (p.n1 > 0) or (p.n2 >= 1)
      context p : P inv impliesAB: (p.n1 > 0) implies (p.n2 >= 1)
      context p : P inv xorAB: (p.n1 > 0) xor (p.n2 >= 1)
      context p : P inv notA: not (p.n1 > 0)
      context p : P inv eqUU: (p.n1 > 0) = (p.n2 >= 1)
      context p : P inv undefEqUndef: oclUndefined(Integer) = oclUndefined(Integer)
      context p : P inv undefNeFalse: oclUndefined(Integer) <> 0
      context p : P inv isDef: p.n1.isUndefined
      context p : P inv isUndefLit: oclUndefined(Integer).isUndefined
      context p : P inv forAllDominance: P.allInstances->forAll(x | x.n1 > 0)
      context p : P inv existsDominance: P.allInstances->exists(x | x.n1 > 0)
      """;

  /** A=(T,T) B=(T,T): every connective definite. */
  @Test
  public void rowTrueTrue() throws Exception {
    List<InvariantVerdict> v = evaluateWith("P", "p1", "1", "1");
    assertEquals(InvariantOutcome.TRUE, outcomeOf(v, "P::andAB"));
    assertEquals(InvariantOutcome.TRUE, outcomeOf(v, "P::orAB"));
    assertEquals(InvariantOutcome.TRUE, outcomeOf(v, "P::impliesAB"));
    assertEquals(InvariantOutcome.FALSE, outcomeOf(v, "P::xorAB"));
    assertEquals(InvariantOutcome.FALSE, outcomeOf(v, "P::notA"));
  }

  /** A=T, B=F: and=F, or=T, implies=F (true implies false), xor=T. */
  @Test
  public void rowTrueFalse() throws Exception {
    List<InvariantVerdict> v = evaluateWith("P", "p1", "1", "0");
    assertEquals(InvariantOutcome.FALSE, outcomeOf(v, "P::andAB"));
    assertEquals(InvariantOutcome.TRUE, outcomeOf(v, "P::orAB"));
    assertEquals(InvariantOutcome.FALSE, outcomeOf(v, "P::impliesAB"));
    assertEquals(InvariantOutcome.TRUE, outcomeOf(v, "P::xorAB"));
  }

  /** A=T, B=U: and=U, or=T (true dominates), implies=U, xor=U. */
  @Test
  public void rowTrueUndefined() throws Exception {
    List<InvariantVerdict> v = evaluateWith("P", "p1", "1", null);
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::andAB"));
    assertEquals(InvariantOutcome.TRUE, outcomeOf(v, "P::orAB"));
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::impliesAB"));
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::xorAB"));
  }

  /** A=F, B=U: and=F (false dominates), or=U, implies=T (false implies anything). */
  @Test
  public void rowFalseUndefined() throws Exception {
    List<InvariantVerdict> v = evaluateWith("P", "p1", "0", null);
    assertEquals(InvariantOutcome.FALSE, outcomeOf(v, "P::andAB"));
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::orAB"));
    assertEquals(InvariantOutcome.TRUE, outcomeOf(v, "P::impliesAB"));
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::xorAB"));
  }

  /** A=U, B=F: and=F (false dominates), or=U, implies=U (undefined implies false). */
  @Test
  public void rowUndefinedFalse() throws Exception {
    List<InvariantVerdict> v = evaluateWith("P", "p1", null, "0");
    assertEquals(InvariantOutcome.FALSE, outcomeOf(v, "P::andAB"));
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::orAB"));
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::impliesAB"));
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::xorAB"));
  }

  /** A=U, B=U: everything undefined. */
  @Test
  public void rowUndefinedUndefined() throws Exception {
    List<InvariantVerdict> v = evaluateWith("P", "p1", null, null);
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::andAB"));
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::orAB"));
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::impliesAB"));
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(v, "P::xorAB"));
  }

  /** Total equality: UNDEFINED = FALSE is FALSE; UNDEFINED = UNDEFINED is TRUE. */
  @Test
  public void totalEqualityWithUndefinedIsNeverUndefined() throws Exception {
    List<InvariantVerdict> v = evaluateWith("P", "p1", null, "0");
    assertEquals(
        "total '=' equates undefined only with undefined, so T=U compares a defined true with"
            + " an undefined false and is FALSE",
        InvariantOutcome.FALSE,
        outcomeOf(v, "P::eqUU"));
    assertEquals(
        "total '=' equates undefined with undefined",
        InvariantOutcome.TRUE,
        outcomeOf(v, "P::undefEqUndef"));
  }

  /** isUndefined classifies; isDefined on a defined value is FALSE (no propagation). */
  @Test
  public void isUndefinedClassifiesWithoutPropagating() throws Exception {
    List<InvariantVerdict> v = evaluateWith("P", "p1", null, "0");
    assertEquals(InvariantOutcome.TRUE, outcomeOf(v, "P::isDef"));
    assertEquals(InvariantOutcome.TRUE, outcomeOf(v, "P::isUndefLit"));
  }

  /**
   * Quantifier dominance over TWO objects, one defined-false and one undefined: the forAll is
   * defined-FALSE (a false element dominates), the exists is UNDEFINED (no true element exists
   * to dominate). Both aggregate over the same two-element population.
   */
  @Test
  public void forAllFalseDominatesAndExistsStaysUndefined() throws Exception {
    MModel model = compile(MODEL_SOURCE, "KernelMatrix");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "p2");
    api.createObjectEx(model.getClass("P"), "p3");
    api.setAttributeValue("p2", "n1", "0");
    // q2.m stays UNSET: undefined element
    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, session.system());
    assertEquals(InvariantOutcome.FALSE, outcomeOf(verdicts, "P::forAllDominance"));
    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(verdicts, "P::existsDominance"));
  }

  /**
   * Same two-object population, both elements DEFINED-TRUE: forAll and exists are both TRUE --
   * the acceptance rule an active invariant relies on (only definitely TRUE satisfies).
   */
  @Test
  public void definedTrueElementsMakeBothQuantifiersTrue() throws Exception {
    MModel model = compile(MODEL_SOURCE, "KernelMatrix");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "p2");
    api.createObjectEx(model.getClass("P"), "p3");
    api.setAttributeValue("p2", "n1", "5");
    api.setAttributeValue("p3", "n1", "7");
    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, session.system());
    assertEquals(InvariantOutcome.TRUE, outcomeOf(verdicts, "P::forAllDominance"));
    assertEquals(InvariantOutcome.TRUE, outcomeOf(verdicts, "P::existsDominance"));
  }

  private static List<InvariantVerdict> evaluateWith(
      String className, String objectName, String n1, String n2) throws Exception {
    MModel model = compile(MODEL_SOURCE, "KernelMatrix");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass(className), objectName);
    if (n1 != null) {
      api.setAttributeValue(objectName, "n1", n1);
    }
    if (n2 != null) {
      api.setAttributeValue(objectName, "n2", n2);
    }
    return InvariantReEvaluator.reevaluate(model, session.system());
  }

  private static Session newSession(MModel model) {
    Session session = new Session();
    session.setSystem(new MSystem(model));
    return session;
  }

  private static InvariantOutcome outcomeOf(List<InvariantVerdict> verdicts, String name) {
    return verdicts.stream()
        .filter(verdict -> verdict.invariantName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + name))
        .outcome();
  }

  private static MModel compile(String source, String name) {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(source, name, err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
