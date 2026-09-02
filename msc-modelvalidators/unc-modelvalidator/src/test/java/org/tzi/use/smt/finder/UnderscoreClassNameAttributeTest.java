package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * End-to-end proof that {@code ConfigurationReader}'s flat {@code Class_attribute} vocabulary keys
 * are resolved against the model's REAL class names, not a naive first-underscore split.
 *
 * <p>USE's own {@code IDENT} grammar permits underscores inside a class name, so a model declaring
 * both {@code Order} and {@code Order_Item} is not a contrived corner case: {@code
 * "Order_Item_price"} must resolve to owner {@code Order_Item} / attribute {@code price}. A
 * first-underscore split instead resolves it to owner {@code Order} / attribute {@code Item_price}
 * -- {@code Order} genuinely exists in this model (so nothing fails at configuration-read time),
 * but it has no {@code Item_price} attribute, so {@code SmtModelFinder.find} fails downstream with
 * a real {@link org.tzi.use.smt.encode.SmtTranslationException} blaming the wrong (if
 * coincidentally real) class for an attribute it never declared -- while the actually-configured
 * {@code Order_Item.price} domain is silently never applied to anything. This test proves the FIXED
 * split instead reaches a genuine SAT witness whose {@code Order_Item.price} is exactly the
 * configured value.
 *
 * <p>No invariant is declared: the single-candidate {@code Order_Item_price = Set{42}} domain alone
 * already deterministically pins the reconstructed value, which is enough to discriminate correct
 * attribution from the bug without a separate invariant. (Note, out of this fix's scope: {@code
 * ConfigurationReader}'s SEPARATE {@code invariant.replaceFirst("_", "::")} qualification shares
 * the same first-underscore-split defect for an active invariant on an underscore- containing class
 * -- confirmed while building this test, an active {@code Order_Item_PriceIsFortyTwo} invariant
 * fails with "query names invariant(s) absent from model: [Order::Item_PriceIsFortyTwo]" -- but it
 * is not one of this task's 4 target bugs, so it is avoided here rather than fixed.)
 */
public class UnderscoreClassNameAttributeTest {

  @Test
  public void anAttributeKeyOnAClassNameContainingAnUnderscoreIsAttributedToTheRealClass()
      throws Exception {
    MModel model =
        compileModel(
            """
            model OrderModel
            class Order
            attributes
              status : Integer
            end
            class Order_Item
            attributes
              price : Integer
            end
            """,
            "OrderModel");
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw =
        ConfigurationReader.read(
            temporaryConfiguration(
                """
                Order_min = 1
                Order_max = 1
                Order_Item_min = 1
                Order_Item_max = 1
                Order_Item_price = Set{42}
                """),
            null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, vocabulary).requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    MSystemState state = result.system().state();
    MObject item = state.objectsOfClass(model.getClass("Order_Item")).iterator().next();
    IntegerValue price = (IntegerValue) item.state(state).attributeValue("price");
    assertEquals(
        "the configured 'Order_Item_price' domain must land on Order_Item.price, not be"
            + " misattributed to a nonexistent 'Item_price' on the unrelated 'Order' class",
        42,
        price.value());
  }

  private static MModel compileModel(String source, String name) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError(name + " fixture model did not compile:\n" + source);
    }
    return model;
  }

  private static java.nio.file.Path temporaryConfiguration(String contents) throws Exception {
    java.nio.file.Path file =
        java.nio.file.Files.createTempFile("underscore-class-name", ".properties");
    java.nio.file.Files.writeString(file, contents);
    file.toFile().deleteOnExit();
    return file;
  }
}
