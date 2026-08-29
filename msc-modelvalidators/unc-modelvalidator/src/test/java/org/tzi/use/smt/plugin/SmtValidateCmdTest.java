package org.tzi.use.smt.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import org.junit.Test;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.ConfigurationReadException;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Tests for the {@code smtmodelvalidator -validate} shell command ({@link SmtValidateCmd}) and its
 * registration in {@code useplugin.xml} -- the command path the {@code cmd.validate} row names.
 * Mirrors the incumbent's {@code KodkodValidateCmd} contract: no-model guard, an optional
 * properties-file path with an optional named section, and the outcome printed DIRECTLY to the
 * invoking shell's stream (the incumbent's own {@code reportOutcomeDirectly} documents why: log4j
 * output cannot reliably reach the console in a multi-plugin distribution).
 *
 * <p>One documented divergence, deliberate: with NO arguments the incumbent auto-creates a generic
 * configuration file next to the specification, but this finder's configurations require
 * explicitly bounded domains (an auto-generated one would be meaningless), so the command prints
 * its usage instead.
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

  /** The core: validate() runs the finder and the report names the outcome for the shell. */
  @Test
  public void validateRunsTheFinderAndTheReportNamesTheOutcome() throws Exception {
    Session session = sessionWithModel();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    ModelFinderResult result =
        SmtValidateCmd.validate(session, session.system().model(), PROPERTIES, null, newPrintStream(out));
    SmtValidateCmd.report(newPrintStream(out), result);

    assertTrue("the [main] section's configuration is satisfiable", result.satisfiable());
    assertTrue(
        "the shell must learn the outcome directly, not via log4j",
        out.toString().contains("[smtmodelvalidator] outcome: SATISFIABLE"));
    assertTrue(out.toString().contains("all 1 active invariant(s) hold."));
  }

  /** A named section selects a different configuration: [tight] shrinks the domain to force UNSAT. */
  @Test
  public void aNamedSectionSelectsADifferentConfiguration() throws Exception {
    Session session = sessionWithModel();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    ModelFinderResult result =
        SmtValidateCmd.validate(session, session.system().model(), PROPERTIES, "tight", newPrintStream(out));
    SmtValidateCmd.report(newPrintStream(out), result);

    assertFalse("the [tight] section pins i to 3, contradicting i = 5", result.satisfiable());
    assertTrue(
        "the shell outcome line must say UNSATISFIABLE",
        out.toString().contains("[smtmodelvalidator] outcome: UNSATISFIABLE"));
  }

  /** The fail-closed configuration policy surfaces as a caught, printed error -- never a crash. */
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

  /** The command delegate itself degrades gracefully when no model is loaded. */
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

  /**
   * The plugin manifest must register the command on the SAME extension surface the incumbent
   * uses: a {@code <commands>} block with {@code shellcmd="smtmodelvalidator -validate"} and the
   * delegate class wired as both id and class.
   */
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
