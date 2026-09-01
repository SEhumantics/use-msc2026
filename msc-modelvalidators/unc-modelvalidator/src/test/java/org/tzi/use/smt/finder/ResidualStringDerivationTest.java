package org.tzi.use.smt.finder;

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
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * The two RESIDUAL String-derivation shapes must refuse with a located
 * SmtTranslationException, not a raw NullPointerException: before the null check in
 * assertStringDerivedAttribute, resolveStringCandidates returned null for any
 * expression outside the supported fragment (navigation-sourced derive,
 * numeric toString()) and the caller dereferenced it unchecked.
 */
public class ResidualStringDerivationTest {

  private static final String NAV_MODEL =
      """
      model NavDerive
      class C
      attributes
        label : String derive: self.d.name
      end
      class D
      attributes
        name : String
      end
      association A between
        C[0..1] role c
        D[0..1] role d
      end
      constraints
      context c : C inv labelMatches:
        c.label = 'x'
      """;

  @Test
  public void navigationSourcedDeriveRefusesLocatedNotNpe() throws Exception {
    MModel model = compile(NAV_MODEL, "NavDerive");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("C", 1, 1, List.of("c1")),
                new ClassScope("D", 1, 1, List.of("d1"))),
            List.of(new AssociationScope("A", 1, 1, List.of(List.of("c1", "d1")))),
            List.of(
                new AttributeDomain("C", "label", null, List.of("x"), null, null),
                new AttributeDomain("D", "name", null, List.of("x"), null, null)),
            Set.of("C::labelMatches"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    try {
      SmtModelFinder.find(model, config);
      throw new AssertionError("expected SmtTranslationException, got a result");
    } catch (SmtTranslationException expected) {
      org.junit.Assert.assertTrue(
          "refusal must name the derived attribute, got: " + expected.getMessage(),
          expected.getMessage().contains("String derivation for C.label"));
    }
  }

  private static final String TOSTRING_MODEL =
      """
      model ToStringDerive
      class C
      attributes
        count : Integer
        text : String derive: self.count.toString()
      end
      constraints
      context c : C inv textMatches:
        c.text = '3'
      """;

  @Test
  public void toStringDeriveRefusesLocatedNotNpe() throws Exception {
    MModel model = compile(TOSTRING_MODEL, "ToStringDerive");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("C", 1, 1, List.of("c1"))),
            List.of(),
            List.of(
                new AttributeDomain("C", "count", null, List.of("3"), null, null),
                new AttributeDomain("C", "text", null, List.of("3"), null, null)),
            Set.of("C::textMatches"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    try {
      SmtModelFinder.find(model, config);
      throw new AssertionError("expected SmtTranslationException, got a result");
    } catch (SmtTranslationException expected) {
      org.junit.Assert.assertTrue(
          "refusal must name the derived attribute, got: " + expected.getMessage(),
          expected.getMessage().contains("String derivation for C.text"));
    }
  }

  private static MModel compile(String spec, String name) {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(spec, name, err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
