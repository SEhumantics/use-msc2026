package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import org.tzi.use.smt.encode.FragmentBoundary;
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * {@code MModel.getClass(String)} and {@code MModel.getAssociation(String)} are both documented
 * {@code @Nullable}/"null if not found" ({@code use-core}'s own Javadoc). A hand-built {@link
 * AnalysisConfiguration} is not required to route through {@link
 * org.tzi.use.smt.config.ConfigurationReader} first (the {@code find} overloads below are a public
 * API in their own right, exercised directly by callers such as this test and {@code
 * ZeroCapacityNavigatedReceiverOperationTest}), and neither {@link
 * org.tzi.use.smt.encode.ObjectSlotEncoder#encode} nor {@link
 * org.tzi.use.smt.config.ConfigurationVocabulary} cross-checks a {@link ClassScope}/{@link
 * AssociationScope} name against the real model -- a class or association scope name is trusted
 * verbatim. Six call sites in {@link SmtModelFinder} dereferenced one of those two lookups (or its
 * follow-on {@code MClass.attribute(...)}, itself also {@code @return null if not found})
 * immediately, with no null check: four raised a raw {@link NullPointerException} straight out of
 * {@code find}, and two ({@code registerTypeWideFallbackAttributes}, {@code
 * registerDeclarationBoundedAttributes}) silently {@code continue}d past a bogus class-scope name
 * instead, dropping every attribute of every class configured after it in {@link
 * java.util.LinkedHashMap} iteration order with no diagnostic at all -- the opposite of this file's
 * fail-closed convention everywhere else. Each fix below is verified end to end through the real
 * {@code find} entry point: a hand-built, otherwise-valid configuration whose one typo'd name is
 * the sole defect, confirming a clean, located {@link SmtTranslationException} now stands where an
 * unguarded NPE or a silent drop used to.
 */
public class UnguardedModelLookupTest {

  // ------------------------------------------------------------------ site 1 (~line 681)
  // U-type component-domain loop: `MClass cls = model.getClass(className)` dereferenced by
  // `cls.attribute(...)` with no check, immediately after `componentDomainsByAttribute` hands it
  // a className/attributeName pair pulled straight from the configured component AttributeDomain
  // -- unlike the plain-attribute-domain loop ~30 lines above it (site 2's sibling guard), this
  // loop had no defence at all against a class-scope name absent from the model.

  @Test
  public void uTypeComponentDomainRefusesAClassScopeNameAbsentFromTheModel() throws Exception {
    MModel model = compile(
        """
        model UTypeComponentClassTypo
        class Anchor
        attributes
          n : Integer
        end
        constraints
        context Anchor inv Trivial: self.n >= 0
        """,
        "UTypeComponentClassTypo");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Anchor", 1, 1), new ClassScope("Ghost", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("Anchor", "n", null, List.of("1"), null, null),
                new AttributeDomain("Ghost", "level", "value", List.of("1"), null, null)),
            Set.of("Anchor::Trivial"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    try {
      SmtModelFinder.find(model, config);
      fail("a U-type component domain naming a class absent from the model must be refused");
    } catch (SmtTranslationException expected) {
      assertEquals(FragmentBoundary.ENCODING_SCOPE, expected.boundary());
      assertTrue(
          "must name the offending pair, got: " + expected.getMessage(),
          expected.getMessage().contains("Ghost.level"));
      assertTrue(
          "must say the class is absent from the model, got: " + expected.getMessage(),
          expected.getMessage().contains("not present in the model"));
    }
  }

  // ------------------------------------------------------------------ site 2 (~line 642)
  // Plain per-attribute domain loop: the class-half (`slotsByClass.get(domain.className())`) is
  // already guarded a few lines above; `cls.attribute(domain.attributeName(), true)` was not,
  // and NPE'd on a typo'd attribute name against an otherwise perfectly real, scoped class.

  @Test
  public void plainAttributeDomainRefusesAnAttributeNameAbsentFromItsClass() throws Exception {
    MModel model = compile(
        """
        model PlainAttributeDomainTypo
        class Gizmo
        attributes
          count : Integer
        end
        constraints
        context Gizmo inv Trivial: self.count >= 0
        """,
        "PlainAttributeDomainTypo");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Gizmo", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("Gizmo", "count_typo", null, List.of("1"), null, null)),
            Set.of("Gizmo::Trivial"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    try {
      SmtModelFinder.find(model, config);
      fail("an attribute domain naming an attribute absent from its class must be refused");
    } catch (SmtTranslationException expected) {
      assertEquals(FragmentBoundary.ENCODING_SCOPE, expected.boundary());
      assertTrue(
          "must name the offending pair, got: " + expected.getMessage(),
          expected.getMessage().contains("Gizmo.count_typo"));
      assertTrue(
          "must say the attribute is not declared, got: " + expected.getMessage(),
          expected.getMessage().contains("not declared on class 'Gizmo'"));
    }
  }

  // ------------------------------------------------------------------ site 3 (~line 476)
  // owningClasses, feeding scenarioSpace() for COVER/UNIFORM: `scoped.contains(domain.className())`
  // only proves the name is a configured ClassScope, not that it names a real model class --
  // `model.getClass(domain.className()).allChildren()` NPE'd right after.

  @Test
  public void owningClassesRefusesAClassScopeNameAbsentFromTheModelUnderCover() throws Exception {
    MModel model = compile(
        """
        model OwningClassesClassTypo
        class Anchor
        attributes
          n : Integer
        end
        constraints
        context Anchor inv Trivial: self.n >= 0
        """,
        "OwningClassesClassTypo");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Anchor", 1, 1), new ClassScope("Ghost", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("Anchor", "n", null, List.of("1"), null, null),
                new AttributeDomain("Ghost", "speed", "value", List.of("1"), null, null),
                new AttributeDomain(
                    "Ghost",
                    "speed",
                    "uncertainty",
                    List.of(),
                    BigDecimal.valueOf(0),
                    BigDecimal.valueOf(1))),
            Set.of("Anchor::Trivial"),
            QueryParser.parse("cover satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    try {
      SmtModelFinder.find(model, config);
      fail(
          "a COVER/UNIFORM scenario space over a class-scope name absent from the model must be"
              + " refused");
    } catch (SmtTranslationException expected) {
      assertEquals(FragmentBoundary.ENCODING_SCOPE, expected.boundary());
      assertTrue(
          "must name the offending pair, got: " + expected.getMessage(),
          expected.getMessage().contains("Ghost.speed"));
      assertTrue(
          "must say the class is absent from the model, got: " + expected.getMessage(),
          expected.getMessage().contains("not present in the model"));
    }
  }

  // ------------------------------------------------------------------ site 4 (~line 863)
  // Main association loop: `MAssociation association = model.getAssociation(...)` is @Nullable;
  // `association instanceof MAssociationClass` is simply false for null (never throws), so
  // control fell through to `association.associationEnds()` with `association` still null.

  @Test
  public void associationLoopRefusesAnAssociationNameAbsentFromTheModel() throws Exception {
    MModel model = compile(
        """
        model AssociationScopeTypo
        class A
        attributes
          x : Integer
        end
        constraints
        context A inv Trivial: self.x >= 0
        """,
        "AssociationScopeTypo");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("A", 1, 1)),
            List.of(new AssociationScope("Ghost", 0, 1)),
            List.of(new AttributeDomain("A", "x", null, List.of("1"), null, null)),
            Set.of("A::Trivial"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    try {
      SmtModelFinder.find(model, config);
      fail("an association scope naming an association absent from the model must be refused");
    } catch (SmtTranslationException expected) {
      assertEquals(FragmentBoundary.ENCODING_SCOPE, expected.boundary());
      assertTrue(
          "must name the offending association, got: " + expected.getMessage(),
          expected.getMessage().contains("'Ghost'"));
      assertTrue(
          "must say it is not declared in the model, got: " + expected.getMessage(),
          expected.getMessage().contains("not declared in the model"));
    }
  }

  // ---------------------------------------------------- site 5 (~1216, registerTypeWideFallback)
  // Silent-swallow, not a crash: `if (cls == null) continue;` skipped a bogus class-scope name
  // instead of raising a located refusal -- inconsistent with this file's fail-closed philosophy
  // everywhere else (and, concretely, silently dropped every fallback-eligible attribute of every
  // class configured AFTER the bogus one in LinkedHashMap iteration order).

  @Test
  public void typeWideFallbackRefusesABogusClassScopeNameInsteadOfSilentlySkippingIt()
      throws Exception {
    MModel model = compile(
        """
        model TypeWideFallbackClassTypo
        class Anchor
        attributes
          n : Integer
        end
        constraints
        context Anchor inv Trivial: self.n >= 0
        """,
        "TypeWideFallbackClassTypo");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            // Anchor first, Ghost second: with the bug, Anchor's own fallback registration would
            // succeed silently and only Ghost's would vanish without a trace. Ghost is OPTIONAL
            // (min=0): forcing it to exist would let a downstream, unrelated USE-core defect
            // (object creation dereferencing a null MClass during witness reconstruction) mask
            // the fix under test with its own crash instead of the clean silent-success this
            // guard exists to catch.
            List.of(new ClassScope("Anchor", 1, 1), new ClassScope("Ghost", 0, 1)),
            List.of(),
            List.of(
                new AttributeDomain(
                    "", "Integer", null, List.of(), BigDecimal.valueOf(0), BigDecimal.valueOf(5))),
            Set.of("Anchor::Trivial"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    try {
      SmtModelFinder.find(model, config);
      fail("a bogus class-scope name must be refused, not silently skipped");
    } catch (SmtTranslationException expected) {
      assertEquals(FragmentBoundary.ENCODING_SCOPE, expected.boundary());
      assertTrue(
          "must name the offending class scope, got: " + expected.getMessage(),
          expected.getMessage().contains("'Ghost'"));
      assertTrue(
          "must say the class is absent from the model, got: " + expected.getMessage(),
          expected.getMessage().contains("not present in the model"));
      // registerDeclarationBoundedAttributes runs right after this method with the exact same
      // guard shape over the exact same slotsByClass entries, so it would ALSO refuse "Ghost" if
      // this method's own guard were the one missing -- the message text alone cannot tell the
      // two apart. Pinning the throwing frame's method name is what actually isolates THIS site's
      // guard from its sibling's (verified live: with only this guard reverted, the exception
      // still carries this same message, but its top frame names
      // registerDeclarationBoundedAttributes instead).
      assertEquals(
          "must be thrown from THIS method's own guard, not merely caught somewhere downstream",
          "registerTypeWideFallbackAttributes",
          expected.getStackTrace()[0].getMethodName());
    }
  }

  // ------------------------------------------------ site 6 (~1294, registerDeclarationBounded)
  // The same silent-swallow bug, in registerDeclarationBoundedAttributes's own unconditional walk
  // over every configured class scope (Boolean/enum declaration-bounded attributes).

  @Test
  public void declarationBoundedRefusesABogusClassScopeNameInsteadOfSilentlySkippingIt()
      throws Exception {
    MModel model = compile(
        """
        model DeclarationBoundedClassTypo
        class Anchor
        attributes
          flag : Boolean
        end
        constraints
        context Anchor inv Trivial: self.flag = self.flag
        """,
        "DeclarationBoundedClassTypo");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            // No type-wide keys at all, so registerTypeWideFallbackAttributes returns before
            // ever touching slotsByClass -- isolating this test to registerDeclarationBounded's
            // own guard. Ghost is OPTIONAL (min=0) for the same reason as the sibling test above.
            List.of(new ClassScope("Anchor", 1, 1), new ClassScope("Ghost", 0, 1)),
            List.of(),
            List.of(),
            Set.of("Anchor::Trivial"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    try {
      SmtModelFinder.find(model, config);
      fail("a bogus class-scope name must be refused, not silently skipped");
    } catch (SmtTranslationException expected) {
      assertEquals(FragmentBoundary.ENCODING_SCOPE, expected.boundary());
      assertTrue(
          "must name the offending class scope, got: " + expected.getMessage(),
          expected.getMessage().contains("'Ghost'"));
      assertTrue(
          "must say the class is absent from the model, got: " + expected.getMessage(),
          expected.getMessage().contains("not present in the model"));
      assertEquals(
          "must be thrown from THIS method's own guard",
          "registerDeclarationBoundedAttributes",
          expected.getStackTrace()[0].getMethodName());
    }
  }

  // ------------------------------------------------------------------------------- helpers

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
