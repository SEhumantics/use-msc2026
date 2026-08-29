package org.tzi.use.smt.plugin;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.runtime.shell.IPluginShellCmdDelegate;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.uml.mm.MModel;

/**
 * Shell command {@code smtmodelvalidator -validate <properties-file> [section]} -- the command
 * path this plugin previously lacked entirely (only a GUI action existed), mirroring the
 * incumbent's {@code modelvalidator -validate} ({@code KodkodValidateCmd}): a no-model guard, an
 * optional named configuration section, validation of the currently loaded model, and the outcome
 * printed DIRECTLY to the invoking shell's stream. The direct printing is deliberate, for the
 * reason the incumbent's own {@code reportOutcomeDirectly} documents: in a multi-plugin
 * distribution log4j output cannot reliably reach the console, so it is the dependable channel
 * for the result.
 *
 * <p>One deliberate divergence from the incumbent: with NO arguments it does not auto-create a
 * generic configuration file next to the specification. This finder's configurations require
 * explicitly bounded domains per attribute (its {@link ConfigurationReader} enforces a strict
 * fail-closed vocabulary), so an auto-generated configuration would be meaningless; the command
 * prints its usage instead.
 *
 * <p>All diagnostics go through the same shell stream, and every failure mode (unreadable file,
 * unsupported configuration keys, a refused invariant) degrades to a printed
 * {@code [smtmodelvalidator] error: ...} line rather than an exception escaping into the shell.
 */
public class SmtValidateCmd implements IPluginShellCmdDelegate {

  private static final String PREFIX = "[smtmodelvalidator]";

  @Override
  public void performCommand(IPluginShellCmd pluginCommand) {
    Session session = pluginCommand.getSession();
    PrintStream out = out(pluginCommand);
    if (!session.hasSystem()) {
      out.println(PREFIX + " No model loaded.");
      return;
    }

    String[] arguments = pluginCommand.getCmdArgumentList();
    if (arguments.length < 1 || arguments[0].isBlank()) {
      usage(out);
      return;
    }

    try {
      MModel model = session.system().model();
      Path propertiesFile = Paths.get(resolve(arguments[0]));
      if (!Files.isRegularFile(propertiesFile)) {
        out.println(PREFIX + " properties file not found: " + propertiesFile);
        return;
      }
      String section = arguments.length >= 2 ? arguments[1].trim() : null;
      ModelFinderResult result = validate(session, model, propertiesFile, section, out);
      report(out, result);
    } catch (RuntimeException | org.tzi.use.api.UseApiException e) {
      out.println(PREFIX + " error: " + e.getMessage());
    }
  }

  /**
   * Runs the finder for the currently loaded model against one configuration file, optionally
   * selecting a named section, streaming the finder's own progress to {@code out}.
   */
  static ModelFinderResult validate(
      Session session, MModel model, Path propertiesFile, String section, PrintStream out)
      throws org.tzi.use.api.UseApiException {
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw = ConfigurationReader.read(propertiesFile, section);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, vocabulary).requireSupported();
    return SmtModelFinder.find(session, model, config);
  }

  /**
   * The outcome line uses the SAME tokens the incumbent prints ({@code SATISFIABLE} /
   * {@code UNSATISFIABLE}), so scripts written against either tool read the same; the follow-up
   * line summarizes the active invariants' fate in the returned witness.
   */
  static void report(PrintStream out, ModelFinderResult result) {
    out.println(
        PREFIX + " outcome: " + (result.satisfiable() ? "SATISFIABLE" : "UNSATISFIABLE"));
    if (result.satisfiable()) {
      long failing = result.verdicts().stream().filter(v -> !v.holds()).count();
      if (failing == 0) {
        out.println(PREFIX + " all " + result.verdicts().size() + " active invariant(s) hold.");
      } else {
        out.println(
            PREFIX
                + " "
                + failing
                + " of "
                + result.verdicts().size()
                + " active invariant(s) do NOT hold in the witness.");
      }
    }
  }

  private static void usage(PrintStream out) {
    out.println(PREFIX + " usage: smtmodelvalidator -validate <properties-file> [section]");
    out.println(
        PREFIX
            + " unlike the incumbent, no generic configuration is auto-created: this finder's"
            + " configurations require explicitly bounded domains");
  }

  /**
   * Resolves the configured path the same way the incumbent does, so relative paths and
   * {@code spec:}-style locations behave identically; falls back to the raw argument when no
   * shell instance exists (unit-test and embedded contexts).
   */
  private static String resolve(String fileName) {
    Shell shell = Shell.getInstance();
    return shell != null
        ? shell.getFilenameToOpen(fileName.trim(), false)
        : fileName.trim();
  }

  private static PrintStream out(IPluginShellCmd pluginCommand) {
    var shell = pluginCommand.getShell();
    return shell != null ? shell.getOut() : System.out;
  }
}
