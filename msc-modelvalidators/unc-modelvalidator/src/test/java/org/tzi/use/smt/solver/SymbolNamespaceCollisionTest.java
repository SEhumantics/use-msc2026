package org.tzi.use.smt.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * F6 -- SYMBOL NAMESPACE COLLISIONS, pinned as a KNOWN LIMITATION, not fixed.
 *
 * <p>The slot, attribute and link symbol builders all mint names into ONE flat SMT-LIB namespace by
 * string concatenation: a slot is {@code <Class>_<i>}, an attribute is {@code <Class>_<i>_<attr>},
 * a UReal/UInteger component is {@code <Class>_<i>_<attr>_value}, and a link is {@code
 * <Association>_<i>}. Nothing reserves or escapes any of those shapes, so a model whose own
 * identifiers happen to spell one of them collides. Three LEGAL USE models are refused for this
 * reason, and each test below reproduces one end to end through {@link SmtModelFinder#find}.
 *
 * <p><b>Why this is documented rather than fixed.</b> Every collision hits {@link
 * SmtScript#declareConst}'s {@code putIfAbsent}-and-throw, which fails LOUDLY at declaration time:
 * no aliasing survives into the script, no assertion is silently retargeted, and no malformed
 * SMT-LIB is emitted. The defect is entirely a DIAGNOSTIC one -- a valid model is refused with a
 * message about an internal symbol the modeller never wrote and cannot find in their model. A real
 * fix means a reserved-name discipline across every symbol builder (or a mangling scheme with a
 * collision-free inverse), which is a design change, not a patch.
 *
 * <p><b>The malformed-SMT-LIB concern is UNREACHABLE, recorded here so it is not re-investigated.</b>
 * A neighbouring worry -- that a USE identifier containing {@code |}, whitespace, a quote or a
 * non-ASCII character could break out of an SMT-LIB symbol -- cannot arise, because USE's own
 * grammar never admits such an identifier in the first place. {@code
 * use-core/src/main/resources/grammars/base/OCLLexerRules.gpart:118-120} defines IDENT as {@code
 * ('$'|'a'..'z'|'A'..'Z'|'_') ('a'..'z'|'A'..'Z'|'_'|'0'..'9')*} -- letters, digits, underscore,
 * and a leading {@code $}, nothing else. (The {@code $} is easy to miss and is why this note cites
 * the rule verbatim: it is admitted, and it is also a legal SMT-LIB simple-symbol character, so it
 * changes nothing here.) Every name that can reach a symbol builder is therefore already a legal
 * simple SMT-LIB symbol, and the ONLY failure mode left is the in-namespace collision this class
 * pins.
 *
 * <p>The fourth known instance is not reachable from here and is recorded in the feature matrix
 * instead: {@code InvariantAssembler}'s {@code qualifiedName().replaceAll("[^A-Za-z0-9_]", "_")}
 * maps class {@code Foo_} inv {@code b_c} and class {@code Foo} inv {@code _b_c} to the same
 * {@code Foo___b_c}.
 */
public class SymbolNamespaceCollisionTest {

  /**
   * An attribute literally named {@code exists} on a class whose slot symbol is {@code A_0}
   * collides with the slot-existence guard {@code A_0_exists}, which is Bool while the attribute is
   * Int.
   */
  @Test
  public void attributeNamedExistsCollidesWithTheSlotExistenceGuard() throws Exception {
    String message =
        refusalFor(
            """
            model NsExists
            class A
            attributes
              exists : Integer
            end
            constraints
            context a : A inv Trivial:
              a.exists >= 0
            """,
            "NsExists",
            "A::Trivial",
            new AttributeDomain("A", "exists", null, List.of("1"), null, null));
    assertTrue(
        "the collided symbol must be named: " + message, message.contains("'A_0_exists'"));
  }

  /**
   * A UReal attribute {@code x} mints the component symbol {@code A_0_x_value}, which is exactly
   * the symbol a plain Real attribute spelled {@code x_value} already claims.
   */
  @Test
  public void urealComponentSymbolCollidesWithAnAttributeSpelledXValue() throws Exception {
    String message =
        refusalFor(
            """
            model NsUReal
            class A
            attributes
              x : UReal
              x_value : Real
            end
            constraints
            context a : A inv Trivial:
              a.x_value >= 0.0
            """,
            "NsUReal",
            "A::Trivial",
            new AttributeDomain("A", "x", "value", List.of("0.5"), null, null),
            new AttributeDomain("A", "x", "uncertainty", List.of("0.01"), null, null),
            new AttributeDomain("A", "x_value", null, List.of("1.0"), null, null));
    assertTrue(
        "the collided symbol must be named: " + message, message.contains("'A_0_x_value'"));
  }

  /**
   * A class literally named {@code R_0} mints the slot symbol {@code R_0_0}, which is exactly the
   * link symbol association {@code R}'s first link claims.
   */
  @Test
  public void classNamedRUnderscoreZeroCollidesWithAssociationRsFirstLink() throws Exception {
    String message =
        refusalForLinkModel(
            """
            model NsLink
            class R_0
            end
            class B
            end
            association R between
              R_0[1] role r0
              B[1] role b
            end
            constraints
            context b : B inv Trivial:
              b.r0 <> null
            """,
            "NsLink",
            "B::Trivial");
    assertTrue("the collided symbol must be named: " + message, message.contains("'R_0_0'"));
  }

  /**
   * The message-only improvement this commit DOES make: every collision above must now say what a
   * modeller can act on -- that the clashing name is an INTERNAL symbol and that a model identifier
   * is the likely cause -- instead of only naming a symbol that appears nowhere in the model.
   */
  @Test
  public void theRefusalExplainsThatTheClashingNameIsAnInternalSymbol() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("A_0_exists", SmtSort.BOOL);
    try {
      script.declareConst("A_0_exists", SmtSort.INT);
      fail("a redeclaration must be refused");
    } catch (IllegalArgumentException expected) {
      String message = expected.getMessage();
      assertTrue(
          "must still name the symbol and both sorts: " + message,
          message.contains("'A_0_exists'") && message.contains("Bool") && message.contains("Int"));
      assertTrue(
          "must say the name is internal: " + message,
          message.contains("internal"));
      assertTrue(
          "must point at a model identifier as the likely cause: " + message,
          message.contains("identifier"));
    }
  }

  /** The refusal is loud: it also never leaves a half-written declaration behind. */
  @Test
  public void aRefusedRedeclarationLeavesTheFirstDeclarationIntact() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("A_0_exists", SmtSort.BOOL);
    try {
      script.declareConst("A_0_exists", SmtSort.INT);
      fail("a redeclaration must be refused");
    } catch (IllegalArgumentException expected) {
      // intentionally empty -- the assertion is on the script's state below
    }
    assertEquals(Set.of("A_0_exists"), script.declaredNames());
    assertTrue(
        "the FIRST declaration's sort must survive",
        script.toSmtLib().contains("(declare-const A_0_exists Bool)"));
  }

  private static String refusalFor(
      String source, String modelName, String invariant, AttributeDomain... domains)
      throws Exception {
    MModel model = compile(source, modelName);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("A", 1, 1)),
            List.of(),
            List.of(domains),
            Set.of(invariant),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return captureRefusal(model, config);
  }

  private static String refusalForLinkModel(String source, String modelName, String invariant)
      throws Exception {
    MModel model = compile(source, modelName);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("R_0", 1, 1), new ClassScope("B", 1, 1)),
            List.of(new AssociationScope("R", 1, 1)),
            List.of(),
            Set.of(invariant),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return captureRefusal(model, config);
  }

  private static String captureRefusal(MModel model, AnalysisConfiguration config)
      throws Exception {
    try {
      SmtModelFinder.find(model, config);
    } catch (Exception expected) {
      Throwable cursor = expected;
      while (cursor != null) {
        String message = cursor.getMessage();
        if (message != null && message.contains("already declared")) {
          return message;
        }
        cursor = cursor.getCause();
      }
      throw new AssertionError(
          "refused, but not with a declaration collision: " + expected, expected);
    }
    throw new AssertionError("expected a symbol-collision refusal, but the find() succeeded");
  }

  private static MModel compile(String source, String modelName) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, modelName, err, factory);
    err.flush();
    assertNotNull("fixture model did not compile", model);
    return model;
  }
}
