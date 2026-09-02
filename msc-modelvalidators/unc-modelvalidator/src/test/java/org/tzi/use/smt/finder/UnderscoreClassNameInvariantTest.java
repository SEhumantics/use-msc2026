package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * An invariant declared on a class whose own name contains an underscore.
 *
 * <p>The flat vocabulary key is {@code cls.name() + "_" + invariant.name()} ({@code
 * ConfigurationVocabulary.fromModel}), so {@code class Order_Item { inv PriceIsFortyTwo }} is keyed
 * {@code Order_Item_PriceIsFortyTwo}. Three sites split that key at its FIRST underscore --
 * {@code ConfigurationReader}'s active and negate paths and {@code QueryParser.resolveInvariant} --
 * yielding {@code Order::Item_PriceIsFortyTwo}, a qualified name no invariant in the model carries.
 * The consequences were total rather than cosmetic: an ACTIVE or NEGATED invariant on such a class
 * made {@code SmtModelFinder.solve} throw {@code IllegalArgumentException: query names invariant(s)
 * absent from model: [Order::Item_PriceIsFortyTwo]}, so the only way to run the model at all was to
 * mark every affected invariant inactive; and a {@code query} key could reach the invariant by
 * NEITHER its correct qualified spelling ({@code Order_Item::PriceIsFortyTwo}) NOR its simple one,
 * while the flat spelling resolved to the same nonexistent name.
 *
 * <p>USE's own {@code IDENT} grammar permits underscores inside a class name, and the sibling
 * defect for ATTRIBUTE keys was already closed by longest-prefix matching against the model's real
 * class names ({@code ConfigurationReader.splitAttribute}, see {@link
 * UnderscoreClassNameAttributeTest}); these tests hold the invariant path to the same rule, end to
 * end through a real solve.
 */
public class UnderscoreClassNameInvariantTest {

  private static final String MODEL =
      """
      model OrderModel
      class Order
      end
      class Order_Item
      attributes
        price : Integer
      end
      constraints
      context Order_Item inv PriceIsFortyTwo: self.price = 42
      """;

  /**
   * The default (unconfigured) status is ACTIVE, so this is the plain "run a model that happens to
   * declare an underscore-named class" case: the invariant must constrain the search, picking 42
   * out of the two-candidate domain rather than aborting the solve outright.
   */
  @Test
  public void anActiveInvariantOnAnUnderscoreNamedClassConstrainsTheSolve() throws Exception {
    ModelFinderResult result = find("Order_Item_price = Set{42,7}\n");

    assertTrue("expected SAT", result.satisfiable());
    assertEquals(42, priceOf(result));
    assertTrue(verdictFor(result, "Order_Item::PriceIsFortyTwo").holds());
  }

  /**
   * The negate path is a SEPARATE call site with the same split, and it feeds the counterexample
   * query machinery: the witness must be one where the invariant is genuinely FALSE.
   */
  @Test
  public void aNegatedInvariantOnAnUnderscoreNamedClassYieldsACounterexample() throws Exception {
    ModelFinderResult result =
        find(
            """
            Order_Item_price = Set{42,7}
            Order_Item_PriceIsFortyTwo = negate
            """);

    assertTrue("expected a counterexample witness", result.satisfiable());
    assertEquals(7, priceOf(result));
    assertFalse(verdictFor(result, "Order_Item::PriceIsFortyTwo").holds());
  }

  /** And the negation is a real obligation, not a relabelling: with only 42 available it refutes. */
  @Test
  public void aNegatedInvariantWithNoFalsifyingValueIsUnsatisfiable() throws Exception {
    ModelFinderResult result =
        find(
            """
            Order_Item_price = Set{42}
            Order_Item_PriceIsFortyTwo = negate
            """);

    assertFalse("42 is the only candidate, so the invariant cannot be falsified",
        result.satisfiable());
  }

  /**
   * {@code QueryParser} must accept the CORRECT qualified spelling. It previously accepted only
   * {@code Order::Item_PriceIsFortyTwo} -- a name that then failed downstream -- and rejected this
   * one outright with "unknown invariant".
   */
  @Test
  public void aQueryReachesTheInvariantByItsQualifiedSpelling() throws Exception {
    ModelFinderResult holds =
        find(
            """
            Order_Item_price = Set{42,7}
            query = nominal Order_Item::PriceIsFortyTwo is true
            """);
    assertTrue(holds.satisfiable());
    assertEquals(42, priceOf(holds));

    ModelFinderResult fails =
        find(
            """
            Order_Item_price = Set{42,7}
            query = nominal Order_Item::PriceIsFortyTwo is false
            """);
    assertTrue(fails.satisfiable());
    assertEquals(
        "the query must target THIS invariant, so asking for it to be false must pick 7",
        7,
        priceOf(fails));
  }

  /** The bare spelling has to resolve too -- it is unambiguous in this one-invariant model. */
  @Test
  public void aQueryReachesTheInvariantByItsSimpleSpelling() throws Exception {
    ModelFinderResult result =
        find(
            """
            Order_Item_price = Set{42,7}
            query = nominal PriceIsFortyTwo is false
            """);

    assertTrue(result.satisfiable());
    assertEquals(7, priceOf(result));
  }

  /** And the flat vocabulary spelling, which used to resolve to a nonexistent invariant. */
  @Test
  public void aQueryReachesTheInvariantByItsFlatVocabularySpelling() throws Exception {
    ModelFinderResult result =
        find(
            """
            Order_Item_price = Set{42,7}
            query = nominal Order_Item_PriceIsFortyTwo is false
            """);

    assertTrue(result.satisfiable());
    assertEquals(7, priceOf(result));
  }

  // ------------------------------------------------------------------------------- helpers

  private static ModelFinderResult find(String extraConfiguration) throws Exception {
    MModel model = compileModel();
    RawConfiguration raw =
        ConfigurationReader.read(
            temporaryConfiguration(
                """
                Order_min = 1
                Order_max = 1
                Order_Item_min = 1
                Order_Item_max = 1
                """
                    + extraConfiguration),
            null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
            .requireSupported();
    return SmtModelFinder.find(model, config);
  }

  private static int priceOf(ModelFinderResult result) {
    MSystemState state = result.system().state();
    MObject item =
        state
            .objectsOfClass(result.system().model().getClass("Order_Item"))
            .iterator()
            .next();
    return ((IntegerValue) item.state(state).attributeValue("price")).value();
  }

  private static InvariantVerdict verdictFor(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compileModel() {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(MODEL, "OrderModel", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }

  private static java.nio.file.Path temporaryConfiguration(String contents) throws Exception {
    java.nio.file.Path file =
        java.nio.file.Files.createTempFile("underscore-class-name-invariant", ".properties");
    java.nio.file.Files.writeString(file, contents);
    file.toFile().deleteOnExit();
    return file;
  }
}
