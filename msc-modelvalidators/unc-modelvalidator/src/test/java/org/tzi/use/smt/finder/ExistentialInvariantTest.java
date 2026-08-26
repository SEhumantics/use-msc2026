package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Regression cover for defect B2: {@code existential inv} is real USE syntax (USEBase.gpart:436,
 * parsed by {@code ASTExistentialInvariantClause} into an {@code MClassInvariant} whose {@link
 * org.tzi.use.uml.mm.MClassInvariant#isExistential()} is true, expanded by USE itself as {@code
 * C.allInstances()->exists(...)}). {@code InvariantAssembler.classify} guarded only {@code
 * !hasVar()} and so encoded every existential invariant as a UNIVERSAL one -- a silent
 * mistranslation, recorded in the ledger as supported.
 *
 * <p>Each fixture below separates the two readings so a universal encoding cannot coincidentally
 * pass: {@code Base} and its subclass {@code Sub} get their own pinned attribute domains, and
 * {@code Base.allInstances()} covers both (see {@code PolymorphicRange}).
 */
public class ExistentialInvariantTest {

  private static final String MODEL =
      """
      model ExistentialInvariant
      class Base
      attributes
        m : Integer
      end
      class Sub < Base
      end
      constraints
      context b : Base existential inv SomeSeven: b.m = 7
      """;

  /**
   * The executed reproduction: only the subclass instance can be 7. Existentially the invariant
   * holds, so SATISFY must be SAT; the universal reading is false for {@code Base_0} and made this
   * UNSAT -- and because the oracle only runs on SAT, nothing checked it.
   */
  @Test
  public void oneSatisfyingInstanceIsEnoughForAnExistentialInvariant() throws Exception {
    MModel model = compile(MODEL, "ExistentialInvariant");
    ModelFinderResult result = SmtModelFinder.find(model, config(model, "satisfy", 0, 0, 7, 7));

    assertTrue(
        "an existential invariant needs ONE satisfying instance, not every instance",
        result.satisfiable());
    assertEquals(InvariantOutcome.TRUE, outcomes(result).get("Base::SomeSeven"));
    assertEquals(
        "the other instance must be free to violate the body",
        2,
        result.system().state().allObjects().size());
  }

  /** No instance can be 7, so the existential invariant is genuinely FALSE and SATISFY is UNSAT. */
  @Test
  public void noSatisfyingInstanceMakesAnExistentialInvariantFalse() throws Exception {
    MModel model = compile(MODEL, "ExistentialInvariant");
    ModelFinderResult result = SmtModelFinder.find(model, config(model, "satisfy", 0, 0, 0, 0));

    assertFalse(result.satisfiable());
  }

  /**
   * And it is attributable: a targeted counterexample must find the same state as a DEFINED-false
   * violation of the existential invariant, which is what makes it usable in the query algebra.
   */
  @Test
  public void anUnsatisfiableExistentialInvariantIsADefinedFalseCounterexample() throws Exception {
    MModel model = compile(MODEL, "ExistentialInvariant");
    ModelFinderResult result =
        SmtModelFinder.find(model, config(model, "counterexample(Base::SomeSeven)", 0, 0, 0, 0));

    assertTrue(result.satisfiable());
    assertEquals(InvariantOutcome.FALSE, outcomes(result).get("Base::SomeSeven"));
  }

  /**
   * OCL's {@code exists} over an empty collection is FALSE, not vacuously true the way {@code
   * forAll} is. With no instances at all the existential invariant must therefore be unsatisfiable
   * -- the universal encoding made it trivially SAT.
   */
  @Test
  public void anExistentialInvariantOverAnEmptyPopulationIsFalseNotVacuouslyTrue()
      throws Exception {
    MModel model =
        compile(
            """
            model EmptyExistential
            class S
            attributes
              m : Integer
            end
            constraints
            context s : S existential inv SomeSeven: s.m = 7
            """,
            "EmptyExistential");
    AnalysisConfiguration empty =
        new AnalysisConfiguration(
            List.of(new ClassScope("S", 0, 0)),
            List.of(),
            List.of(
                new AttributeDomain(
                    "S", "m", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(7))),
            Set.of("S::SomeSeven"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);

    assertFalse(SmtModelFinder.find(model, empty).satisfiable());
  }

  private static Map<String, InvariantOutcome> outcomes(ModelFinderResult result) {
    Map<String, InvariantOutcome> outcomes = new LinkedHashMap<>();
    for (InvariantVerdict verdict : result.verdicts()) {
      outcomes.put(verdict.invariantName(), verdict.outcome());
    }
    return outcomes;
  }

  private static AnalysisConfiguration config(
      MModel model, String query, int baseMin, int baseMax, int subMin, int subMax) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Base", 1, 1), new ClassScope("Sub", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain(
                "Base",
                "m",
                null,
                List.of(),
                BigDecimal.valueOf(baseMin),
                BigDecimal.valueOf(baseMax)),
            new AttributeDomain(
                "Sub",
                "m",
                null,
                List.of(),
                BigDecimal.valueOf(subMin),
                BigDecimal.valueOf(subMax))),
        Set.of("Base::SomeSeven"),
        QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
  }

  private static MModel compile(String source, String name) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, factory);
    err.flush();
    return model;
  }
}
