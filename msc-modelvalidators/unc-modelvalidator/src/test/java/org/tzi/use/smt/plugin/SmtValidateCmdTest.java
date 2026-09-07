package org.tzi.use.smt.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import org.junit.Test;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.ConfigurationReadException;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Tests for the {@code smtmodelvalidator -validate} shell command ({@link SmtValidateCmd}) and its
 * registration in {@code useplugin.xml}. The outcome line now names the six-way classification
 * (SAT_VALIDATED, UNSAT_EXACT, INCONCLUSIVE_NUMERICAL, UNSUPPORTED, SOLVER_UNKNOWN,
 * VALIDATION_ERROR) rather than a bare SATISFIABLE/UNSATISFIABLE.
 */
public class SmtValidateCmdTest {

  private static final String MODEL =
      """
      model CmdValidateScope
      class X
      attributes
        i : Integer
      end
      constraints
      context x : X inv IIsFive:
        x.i = 5
      """;

  private static final Path PROPERTIES = Paths.get("src/test/resources/CmdValidate.properties");

  @Test
  public void validateRunsTheFinderAndTheReportNamesTheClassification() throws Exception {
    Session session = sessionWithModel();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    var validated =
        SmtValidateCmd.validate(session, session.system().model(), PROPERTIES, null, newPrintStream(out));
    SmtValidateCmd.report(newPrintStream(out), validated);

    assertTrue("the [main] section's configuration is satisfiable",
        validated.result().satisfiable());
    assertTrue(
        "the shell must see the six-way classification, not a bare SATISFIABLE",
        out.toString().contains("[smtmodelvalidator] outcome: SAT_VALIDATED"));
    assertTrue(out.toString().contains("all 1 active invariant(s) hold."));
  }

  @Test
  public void aNamedSectionSelectsADifferentConfiguration() throws Exception {
    Session session = sessionWithModel();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    var validated =
        SmtValidateCmd.validate(session, session.system().model(), PROPERTIES, "tight", newPrintStream(out));
    SmtValidateCmd.report(newPrintStream(out), validated);

    assertFalse("the [tight] section pins i to 3, contradicting i = 5",
        validated.result().satisfiable());
    assertTrue(
        "the shell outcome line must carry the classification",
        out.toString().contains("[smtmodelvalidator] outcome: UNSAT_EXACT"));
  }

  @Test
  public void anUnsupportedConfigurationIsRefusedByTheReader() throws Exception {
    Session session = sessionWithModel();
    Path broken = Paths.get("src/test/resources/CmdValidateBroken.properties");

    try {
      SmtValidateCmd.validate(
          session, session.system().model(), broken, null, newPrintStream(new ByteArrayOutputStream()));
      throw new AssertionError("expected ConfigurationReadException");
    } catch (ConfigurationReadException e) {
      assertTrue(
          "the error names the offending key",
          e.getMessage().contains("unsupported configuration key(s)"));
    }
  }

  @Test
  public void performCommandWithoutAModelPrintsAGracefulMessage() {
    Session emptySession = new Session();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    java.io.PrintStream oldOut = System.out;
    System.setOut(new java.io.PrintStream(out));
    try {
      new SmtValidateCmd()
          .performCommand(
              new IPluginShellCmd() {
                @Override public void executeCmd(String cmd, String cmdArguments, String[] argList) {}
                @Override public String getCmd() { return "smtmodelvalidator -validate"; }
                @Override public Session getSession() { return emptySession; }
                @Override public org.tzi.use.main.shell.Shell getShell() { return null; }
                @Override public String getCmdArguments() { return ""; }
                @Override public String[] getCmdArgumentList() { return new String[0]; }
              });
    } finally {
      System.setOut(oldOut);
    }
    assertTrue(
        "no-model must print the graceful guard message",
        out.toString().contains("[smtmodelvalidator] No model loaded."));
  }

  @Test(timeout = 20_000)
  public void aDeeplyNestedQueryDegradesToAPrintedErrorRatherThanCrashingTheShell()
      throws Exception {
    Session session = sessionWithModel();
    int depth = 50_000;
    StringBuilder query = new StringBuilder(depth * 2 + 16);
    query.append("(".repeat(depth)).append("satisfy").append(")".repeat(depth));
    Path deepQuery = Files.createTempFile("msc-deep-query-", ".properties");
    Files.writeString(
        deepQuery,
        "X_min = 1\n"
            + "X_max = 1\n"
            + "X_i = Set{5}\n"
            + "X_IIsFive = active\n"
            + "query = "
            + query
            + "\n");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    java.io.PrintStream oldOut = System.out;
    System.setOut(new java.io.PrintStream(out));
    try {
      new SmtValidateCmd()
          .performCommand(
              new IPluginShellCmd() {
                @Override public void executeCmd(String cmd, String cmdArguments, String[] argList) {}
                @Override public String getCmd() { return "smtmodelvalidator -validate"; }
                @Override public Session getSession() { return session; }
                @Override public org.tzi.use.main.shell.Shell getShell() { return null; }
                @Override public String getCmdArguments() { return deepQuery.toString(); }
                @Override public String[] getCmdArgumentList() {
                  return new String[] {deepQuery.toString()};
                }
              });
    } finally {
      System.setOut(oldOut);
      Files.deleteIfExists(deepQuery);
    }
    assertTrue(
        "a deeply-nested query must degrade to a printed error line, not crash the shell",
        out.toString().contains("[smtmodelvalidator] error:"));
  }

  @Test
  public void usepluginXmlRegistersTheValidateShellCommand() throws Exception {
    try (InputStream in =
        SmtValidateCmdTest.class.getClassLoader().getResourceAsStream("useplugin.xml")) {
      Document doc =
          DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
      NodeList commands = doc.getElementsByTagName("command");
      assertEquals("exactly one shell command is registered", 1, commands.getLength());
      var cmd = commands.item(0).getAttributes();
      assertEquals(
          "org.tzi.use.smt.plugin.SmtValidateCmd", cmd.getNamedItem("class").getNodeValue());
      assertEquals(
          "org.tzi.use.smt.plugin.SmtValidateCmd", cmd.getNamedItem("id").getNodeValue());
      assertEquals("smtmodelvalidator -validate", cmd.getNamedItem("shellcmd").getNodeValue());
      assertEquals("smv -validate", cmd.getNamedItem("alias").getNodeValue());
    }
  }

  private static Session sessionWithModel() throws Exception {
    ModelFactory factory = new ModelFactory();
    MModel model =
        USECompiler.compileSpecification(MODEL, "CmdValidateScope", new PrintWriter(System.err, true), factory);
    Session session = new Session();
    session.setSystem(new MSystem(model));
    return session;
  }

  private static java.io.PrintStream newPrintStream(ByteArrayOutputStream out) {
    return new java.io.PrintStream(out, true);
  }
}
