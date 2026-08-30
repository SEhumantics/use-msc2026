package org.tzi.msc.benchmark;

import static org.junit.Assert.assertTrue;

import java.io.FileReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.convert.LegacyListDelimiterHandler;
import org.junit.Test;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.UseKodkodModelValidator;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;

/**
 * RESEARCH PROBE (n-ary phase, research step 1): the master plan has documented Kodkod's own
 * n-ary association support as generic for years (Relation.nary(name, associationEnds.size()),
 * tuple parsing and bounds looping over arity), but its real behavior on an arity-3 model was
 * never EMPIRICALLY confirmed -- zero shipped example exercises it, on either tool. This probe
 * runs the REAL kk-modelvalidator pipeline (PluginModelFactory transform, PropertyConfigurationVisitor,
 * UseKodkodModelValidator solve -- the exact chain BenchmarkRunner uses) against a minimal
 * ternary association model and prints what actually happens, then pins the observation.
 *
 * <p>Kept as a permanent regression so the recorded finding cannot silently rot: if a kk-side
 * change alters n-ary behavior, this test moves and the research record must be revisited.
 */
public class NaryAssociationProbeTest {

  private static final String MODEL =
      """
      model NaryProbe
      class Supplier
      attributes
        name : String
      end
      class Part
      attributes
        sku : String
      end
      class Project
      attributes
        title : String
      end
      association Supplies between
        Supplier[1] role supplier
        Part[1] role part
        Project[1] role project
      end
      constraints
      context s : Supplier inv navigatedPartPresent:
        s.part->notEmpty()
      """;

  /**
   * The default section: exactly one Supplier, one Part, one Project, and exactly ONE ternary
   * link (sup, p1, j1) forced by the association-bounds tuple literal.
   */
  private static final String PROPERTIES =
      """
      Integer_min = 0
      Integer_max = 5

      Supplier_min = 1
      Supplier_max = 1
      Supplier = Set{sup}
      Supplier_name = Set{'ACME'}

      Part_min = 1
      Part_max = 1
      Part = Set{p1}
      Part_sku = Set{'p1'}

      Project_min = 1
      Project_max = 1
      Project = Set{j1}
      Project_title = Set{'apollo'}

      Supplies_min = 1
      Supplies_max = 1
      Supplies = Set{(sup,p1,j1)}

      Supplier_navigatedPartPresent = active
      """;

  @Test
  public void kodkodOnAMinimalTernaryAssociation() throws Exception {
    MModel model = compile(MODEL);

    Session session = new Session();
    session.setSystem(new MSystem(model));
    var reTransform = PluginModelFactory.class.getDeclaredField("reTransform");
    reTransform.setAccessible(true);
    reTransform.set(PluginModelFactory.INSTANCE, true);
    IModel kodkodModel = PluginModelFactory.INSTANCE.getModel(model);

    INIConfiguration ini = new INIConfiguration();
    ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
    try (FileReader reader = readerFor(PROPERTIES)) {
      ini.read(reader);
    }
    var config = ini.getSection(null);
    PrintWriter warnings = new PrintWriter(System.err, true);
    PropertyConfigurationVisitor configVisitor = new PropertyConfigurationVisitor(config, warnings);
    kodkodModel.accept(configVisitor);
    System.out.println("=== KK config errors: " + configVisitor.containErrors());

    UseKodkodModelValidator validator = new UseKodkodModelValidator(session);
    try {
      validator.validate(kodkodModel);
      var solution = validator.solution();
      System.out.println("=== KK outcome: " + solution.outcome());
      System.out.println("=== KK instance: " + solution.instance());
      // Observed 2026-08-30 (research step 1, first empirical confirmation): the real kk
      // pipeline transforms the ternary association, applies the forced 3-tuple bound
      // (Supplies=[[Supplier_sup, Part_p1, Project_j1]]), navigates the collection-valued
      // n-ary end (USE types s.part as a Bag), and reports SATISFIABLE.
      String instance = String.valueOf(solution.instance());
      assertTrue(
          "the recorded research finding must hold: KK solves the ternary association and the "
              + "witness carries exactly the forced 3-tuple",
          solution.outcome().name().startsWith("SATISFIABLE")
              && instance.contains("[[Supplier_sup, Part_p1, Project_j1]]"));
    } catch (Exception e) {
      System.out.println("=== KK THREW: " + e);
      throw new AssertionError(
          "observed exception -- record this as the finding instead of pinning SAT", e);
    }
  }

  private static FileReader readerFor(String content) {
    try {
      Path tmp = Files.createTempFile("nary-probe", ".properties");
      Files.writeString(tmp, content);
      return new FileReader(tmp.toFile());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static MModel compile(String spec) {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = org.tzi.use.parser.use.USECompiler.compileSpecification(spec, "NaryProbe", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("probe model did not compile:\n" + buffer);
    }
    return model;
  }

}
