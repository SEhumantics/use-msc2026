package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.config.QueryRequirements;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Milestone 4.4 at the compiler boundary: the algebra lowers over the ALREADY-REIFIED {@code
 * def[m,i]}/{@code val[m,i]} symbols Milestone 4.2 bound, so "query evaluation never causes a
 * second expression translation or weakens the fragment ledger" is a measured property of the
 * emitted script rather than a claim.
 */
public class QueryCompilerAlgebraTest {

  private static final String MODEL =
      """
      model JointFragility
      class Sample
      attributes
        p : Integer
        q : Integer
      end
      constraints
      context s : Sample inv PIsOne: s.p = 1
      context s : Sample inv QIsOne: s.q = 1
      """;

  private static final Set<String> ACTIVE =
      new LinkedHashSet<>(List.of("Sample::PIsOne", "Sample::QIsOne"));

  /**
   * A query naming the same invariant three times in three different atoms still reifies it ONCE:
   * one ledger entry, one {@code def}/{@code val} declaration pair, one defining assertion each --
   * while the compiled query term references the symbol once per mention.
   */
  @Test
  public void aQueryMentioningOneInvariantThreeTimesReifiesItOnce() throws Exception {
    MModel model = compile();
    Fixture fixture = new Fixture();
    QueryExpr query =
        QueryParser.parse(
            "uncertain Sample::PIsOne is false or uncertain Sample::PIsOne is true or not"
                + " (uncertain Sample::PIsOne is undefined)",
            ConfigurationVocabulary.fromModel(model));

    Map<String, Set<TranslationMode>> requirements =
        QueryRequirements.requiredClassifications(query, ACTIVE);
    FragmentChecker.ReifiedResult checked =
        FragmentChecker.checkAndReify(
            invariantsOf(model), requirements, fixture.context, fixture.script);

    assertEquals(
        "three mentions of one invariant must be one (invariant, mode) ledger entry",
        1,
        checked.ledger().entries().size());
    assertEquals(1, checked.classifications().size());

    QueryCompiler.Obligation obligation =
        QueryCompiler.compile(query, ACTIVE, checked.classifications());

    String script = fixture.script.toSmtLib();
    assertEquals(
        "the definedness symbol is declared exactly once",
        1,
        occurrences(script, "(declare-const def_uncertain_Sample__PIsOne Bool)"));
    assertEquals(
        "the invariant body is translated into that symbol exactly once",
        1,
        occurrences(script, "(= def_uncertain_Sample__PIsOne"));
    assertEquals(
        "and into its value symbol exactly once",
        1,
        occurrences(script, "(= val_uncertain_Sample__PIsOne"));
    assertEquals(
        "while the compiled query term references the reified symbol once per mention",
        3,
        occurrences(obligation.constraint().toSmtLib(), "def_uncertain_Sample__PIsOne"));
  }

  /**
   * The ledger stays mode-separated: one invariant requested in both modes is two entries and two
   * symbol pairs, never one entry covering both.
   */
  @Test
  public void theSameInvariantInBothModesIsTwoLedgerEntriesAndTwoSymbolPairs() throws Exception {
    MModel model = compile();
    Fixture fixture = new Fixture();
    QueryExpr query =
        QueryParser.parse(
            "nominal Sample::PIsOne is true and uncertain Sample::PIsOne is undefined",
            ConfigurationVocabulary.fromModel(model));

    FragmentChecker.ReifiedResult checked =
        FragmentChecker.checkAndReify(
            invariantsOf(model),
            QueryRequirements.requiredClassifications(query, ACTIVE),
            fixture.context,
            fixture.script);

    assertEquals(2, checked.ledger().entries().size());
    assertEquals(2, checked.classifications().size());

    QueryCompiler.Obligation obligation =
        QueryCompiler.compile(query, ACTIVE, checked.classifications());
    String constraint = obligation.constraint().toSmtLib();
    assertTrue(constraint.contains("def_nominal_Sample__PIsOne"));
    assertTrue(constraint.contains("def_uncertain_Sample__PIsOne"));
  }

  /**
   * The macros really are syntax sugar over the same atoms: each compiles to the term its
   * spelled-out §5.2 form compiles to, character for character.
   */
  @Test
  public void everyMacroCompilesToExactlyItsSpelledOutForm() throws Exception {
    assertEquals(compiled("satisfy"), compiled("uncertain all are true"));
    assertEquals(
        compiled("counterexample(Sample::PIsOne)"),
        compiled("uncertain Sample::PIsOne is false and uncertain others are true"));
  }

  /**
   * Milestone 4.5's macro, held to the proposal's {@code W_FRAGILE(j)} character for character. The
   * two modes are not interchangeable and the "others are true" conjunct is part of the predicate,
   * so swapping {@code nominal}/{@code uncertain} or dropping the aggregate changes the emitted
   * term and this equality stops holding.
   */
  @Test
  public void fragileCompilesToExactlyItsSpelledOutForm() throws Exception {
    assertEquals(
        compiled("fragile(Sample::PIsOne)"),
        compiled(
            "nominal Sample::PIsOne is true and uncertain Sample::PIsOne is false and uncertain"
                + " others are true"));
  }

  /** {@code others} without exactly one target has no meaning and must fail closed. */
  @Test
  public void othersWithoutExactlyOneTargetFailsClosed() throws Exception {
    MModel model = compile();
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                QueryParser.parse(
                    "uncertain Sample::PIsOne is false and uncertain Sample::QIsOne is false and"
                        + " uncertain others are true",
                    vocabulary));
    assertTrue(exception.getMessage().contains("others"));
  }

  /** Everything Milestone 4.4 does not own keeps failing closed by the milestone that does. */
  @Test
  public void shapesOutsideThisMilestoneStillFailClosedByName() throws Exception {
    IllegalArgumentException independence =
        assertThrows(IllegalArgumentException.class, () -> compiled("invariant-independence"));
    assertTrue(independence.getMessage().contains("independenceSweep"));

    IllegalArgumentException profile =
        assertThrows(IllegalArgumentException.class, () -> compiled("cover satisfy"));
    assertTrue(profile.getMessage().contains("4.6"));
  }

  /** An atom naming an invariant outside the active set A_K has no witness predicate to state. */
  @Test
  public void anAtomAboutAnInactiveInvariantFailsClosed() throws Exception {
    MModel model = compile();
    Fixture fixture = new Fixture();
    QueryExpr query =
        QueryParser.parse(
            "uncertain Sample::QIsOne is true", ConfigurationVocabulary.fromModel(model));
    Set<String> onlyP = Set.of("Sample::PIsOne");
    FragmentChecker.ReifiedResult checked =
        FragmentChecker.checkAndReify(
            invariantsOf(model),
            QueryRequirements.requiredClassifications(query, onlyP),
            fixture.context,
            fixture.script);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> QueryCompiler.compile(query, onlyP, checked.classifications()));
    assertTrue(exception.getMessage().contains("Sample::QIsOne"));
  }

  private static String compiled(String query) throws Exception {
    MModel model = compile();
    Fixture fixture = new Fixture();
    QueryExpr parsed = QueryParser.parse(query, ConfigurationVocabulary.fromModel(model));
    FragmentChecker.ReifiedResult checked =
        FragmentChecker.checkAndReify(
            invariantsOf(model),
            QueryRequirements.requiredClassifications(parsed, ACTIVE),
            fixture.context,
            fixture.script);
    return QueryCompiler.compile(parsed, ACTIVE, checked.classifications()).constraint().toSmtLib();
  }

  private static int occurrences(String haystack, String needle) {
    int count = 0;
    int from = 0;
    while (true) {
      int at = haystack.indexOf(needle, from);
      if (at < 0) {
        return count;
      }
      count++;
      from = at + needle.length();
    }
  }

  private static List<MClassInvariant> invariantsOf(MModel model) {
    return List.copyOf(model.classInvariants(true));
  }

  /** One Sample slot with two configured Integer attributes -- enough to translate both bodies. */
  private static final class Fixture {
    private final SmtScript script = new SmtScript("QF_LIA");
    private final TranslationContext context;

    private Fixture() {
      ObjectSlots samples =
          ObjectSlotEncoder.encode(script, List.of(new ClassScope("Sample", 1, 1))).get("Sample");
      AttributeDomain pDomain =
          new AttributeDomain(
              "Sample", "p", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(2));
      AttributeDomain qDomain =
          new AttributeDomain(
              "Sample", "q", null, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(2));
      AttributeValues pValues =
          AttributeEncoder.encode(script, samples, "p", AttributeType.INTEGER, pDomain);
      AttributeValues qValues =
          AttributeEncoder.encode(script, samples, "q", AttributeType.INTEGER, qDomain);
      context =
          new TranslationContext(
              Map.of(),
              Map.of("Sample.p", pValues, "Sample.q", qValues),
              Map.of("Sample.p", pDomain, "Sample.q", qDomain),
              Map.of("Sample", samples),
              Map.of());
    }
  }

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "JointFragility", err, factory);
    err.flush();
    return model;
  }
}
