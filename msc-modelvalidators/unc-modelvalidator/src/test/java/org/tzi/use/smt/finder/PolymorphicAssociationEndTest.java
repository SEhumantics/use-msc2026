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
 * End-to-end regression for {@code assoc.type-multiplicity-with-inheritance}: an association end
 * DECLARED on a superclass must accept links to its configured SUBCLASS instances. The link grid
 * used to span only the declared class's own candidate slots ({@code slotsByClass.get(name)}), so
 * with the superclass scoped 0..0 there was no column to link through at all: the owner could
 * never own the Car, and every navigation through the end collapsed to undefined.
 *
 * <p>The grid axes now span the FOLDED polymorphic population (the declared class plus its
 * configured subclasses, in {@link org.tzi.use.smt.encode.PolymorphicRange}'s own order), and
 * attribute reads through the end dispatch per slot on the slot's CONCRETE class -- matching how
 * inherited attributes are registered separately per concrete subclass. For an end whose class
 * has no configured subclasses the folded view IS the old own-slots view, so existing encodings
 * are unchanged.
 *
 * <p>Re-pinned against the real incumbent on the presence scenario: kk-modelvalidator reports
 * SATISFIABLE on the byte-identical model where the unfolded encoding reported UNSATISFIABLE.
 */
public class PolymorphicAssociationEndTest {

  private static final String MODEL =
      """
      model PolyEndFolded
      class Owner
      attributes
        name : String
      end
      class Vehicle
      attributes
        wheels : Integer
      end
      class Car < Vehicle
      end
      class Truck < Vehicle
      end
      association R between
        Owner[0..*] role owner
        Vehicle[0..1] role v
      end
      association F between
        Owner[0..*] role fleetOwner
        Vehicle[0..*] role fleet
      end
      constraints
      context o : Owner inv HasVehicle:
        not o.v.oclIsUndefined()
      context o : Owner inv VehicleWheels4:
        o.v.wheels = 4
      context o : Owner inv FleetAllWheels4:
        o.fleet->forAll(w | w.wheels = 4)
      """;

  /** The core divergence: the owner links a configured Car through the Vehicle-typed end. */
  @Test
  public void anOwnerLinksASubclassInstanceThroughASuperTypedEnd() throws Exception {
    ModelFinderResult result = find("HasVehicle", List.of("4"));

    assertTrue(
        "the Car is a Vehicle, so the owner can link it through the Vehicle-typed end"
            + " (kk-modelvalidator re-pin: SATISFIABLE)",
        result.satisfiable());
    assertTrue(verdictFor(result, "Owner::HasVehicle").holds());
  }

  /**
   * An attribute read through the super-typed end carries the CONCRETE instance's value: with
   * every vehicle's wheels pinned to 4 it holds, and with them pinned to 18 it is genuinely
   * unsatisfiable -- USE's own re-evaluation of the reconstructed witness guards the dispatch
   * (a mis-indexed grid would hand USE a different link than the solver reasoned about).
   */
  @Test
  public void attributeReadsThroughAFoldedEndDispatchToTheConcreteSlot() throws Exception {
    ModelFinderResult holds = find("VehicleWheels4", List.of("4"));
    assertTrue("wheels = 4 is satisfiable and USE must confirm the concrete link",
        holds.satisfiable());
    assertTrue(verdictFor(holds, "Owner::VehicleWheels4").holds());

    ModelFinderResult contradicts = find("VehicleWheels4", List.of("18"));
    assertFalse(
        "wheels can only be 18, so = 4 through the folded end is genuinely unsatisfiable",
        contradicts.satisfiable());
  }

  /**
   * forAll over a collection-valued FOLDED end: with the fleet forced non-empty (R-style forced
   * link counts do not apply to F, so the discriminator is the domain) the quantifier genuinely
   * ranges over the subclass slots.
   */
  @Test
  public void forAllOverAFoldedCollectionValuedEndGenuinelyConstrainsMembers() throws Exception {
    ModelFinderResult satisfiable = find("FleetAllWheels4", List.of("4"));
    assertTrue(satisfiable.satisfiable());
    assertTrue(verdictFor(satisfiable, "Owner::FleetAllWheels4").holds());

    ModelFinderResult impossible = find("FleetAllWheels4", List.of("99"));
    assertFalse(
        "the fleet contains the configured Car and Truck slots, so demanding wheels = 99"
            + " with a {4}-only domain is genuinely unsatisfiable (pre-fix this was vacuously"
            + " satisfiable: the fleet grid had no members at all)",
        impossible.satisfiable());
  }

  private static ModelFinderResult find(String invariantName, List<String> wheelsDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Owner", 1, 1),
                new ClassScope("Vehicle", 0, 0),
                new ClassScope("Car", 1, 1),
                new ClassScope("Truck", 1, 1)),
            // F is forced non-empty so the fleet forAll can never be vacuously true.
            List.of(new AssociationScope("R", 0, -1), new AssociationScope("F", 1, -1)),
            List.of(
                new AttributeDomain("Owner", "name", null, List.of("n"), null, null),
                new AttributeDomain("Vehicle", "wheels", null, wheelsDomain, null, null)),
            Set.of("Owner::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "PolyEndFolded", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
