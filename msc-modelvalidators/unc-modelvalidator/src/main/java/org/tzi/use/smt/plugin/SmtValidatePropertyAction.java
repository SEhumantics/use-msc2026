package org.tzi.use.smt.plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.stream.Collectors;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import org.tzi.use.api.UseApiException;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.ResultClassification;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.uml.mm.MModel;

/**
 * Action-class letting a user choose a {@code .properties} configuration file and validate the
 * currently loaded model with the SMT (Z3) model finder, mirroring {@code
 * org.tzi.use.kodkod.plugin.KodkodValidatePropertyAction}'s own flow: get the live session from the
 * framework, get its currently loaded model, prompt for a properties file, run, report. On a
 * satisfiable instance, reconstructs directly into the running session's own system (via {@link
 * SmtModelFinder#find(Session, MModel, AnalysisConfiguration)}) so already-open object diagram
 * views redraw with the found instance, the same way Kodkod's own validation does.
 */
public class SmtValidatePropertyAction implements IPluginActionDelegate {

  @Override
  public void performAction(IPluginAction pluginAction) {
    Session session = pluginAction.getSession();
    if (!session.hasSystem()) {
      JOptionPane.showMessageDialog(
          pluginAction.getParent(), "No model present.", "No Model", JOptionPane.ERROR_MESSAGE);
      return;
    }

    MModel model = session.system().model();
    File modelDirectory = modelDirectoryOf(model);
    JFileChooser fileChooser =
        modelDirectory != null ? new JFileChooser(modelDirectory) : new JFileChooser();
    fileChooser.setFileFilter(propertiesFileFilter());

    if (fileChooser.showOpenDialog(pluginAction.getParent()) != JFileChooser.APPROVE_OPTION) {
      return;
    }

    try {
      ValidatedResult validated = validate(session, model, fileChooser.getSelectedFile().toPath());
      report(pluginAction, validated);
    } catch (RuntimeException | UseApiException e) {
      JOptionPane.showMessageDialog(
          pluginAction.getParent(),
          "SMT validation failed: " + e.getMessage(),
          "SMT Validation Error",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  record ValidatedResult(ModelFinderResult result, ResultClassification classification,
      java.util.Set<String> activeInvariants) {}

  private static ValidatedResult validate(
      Session session, MModel model, Path propertiesFile) throws UseApiException {
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw = ConfigurationReader.read(propertiesFile, null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, vocabulary).requireSupported();
    ModelFinderResult result = SmtModelFinder.find(session, model, config);
    ResultClassification classification =
        ResultClassification.of(result, config.activeInvariants());
    return new ValidatedResult(result, classification, config.activeInvariants());
  }

  private static void report(
      IPluginAction pluginAction, ValidatedResult validated) {
    ModelFinderResult result = validated.result();
    ResultClassification classification = validated.classification();

    switch (classification) {
      case SAT_VALIDATED -> {
        var activeVerdicts = result.verdicts().stream()
            .filter(v -> validated.activeInvariants().contains(v.invariantName())).toList();
        long failing = activeVerdicts.stream().filter(v -> !v.holds()).count();
        String message =
            failing == 0
                ? "A valid instance was found; all "
                    + activeVerdicts.size()
                    + " active invariant(s) hold (SAT_VALIDATED)."
                : failing
                    + " of "
                    + activeVerdicts.size()
                    + " active invariant(s) do not hold in the found instance"
                    + " (FALSE and UNDEFINED are not interchangeable): "
                    + activeVerdicts.stream()
                        .filter(v -> !v.holds())
                        .map(v -> v.invariantName() + " [" + v.outcome() + "]")
                        .collect(java.util.stream.Collectors.joining(", "));
        JOptionPane.showMessageDialog(
            pluginAction.getParent(), message, "SMT Validation",
            JOptionPane.INFORMATION_MESSAGE);
      }
      case UNSAT_EXACT -> {
        JOptionPane.showMessageDialog(
            pluginAction.getParent(),
            "No valid instance exists within the configured scope"
                + " (exact bounded refutation: UNSAT_EXACT).",
            "SMT Validation",
            JOptionPane.INFORMATION_MESSAGE);
      }
      case INCONCLUSIVE_NUMERICAL -> {
        JOptionPane.showMessageDialog(
            pluginAction.getParent(),
            "Satisfiability was not decided: the encoding used a numerical"
                + " enclosure, so this negative result is inconclusive"
                + " (INCONCLUSIVE_NUMERICAL), not an exact refutation.",
            "SMT Validation",
            JOptionPane.WARNING_MESSAGE);
      }
      case SOLVER_UNKNOWN -> {
        JOptionPane.showMessageDialog(
            pluginAction.getParent(),
            "The solver did not decide satisfiability within the configured"
                + " bounds (SOLVER_UNKNOWN).",
            "SMT Validation",
            JOptionPane.WARNING_MESSAGE);
      }
      case UNSUPPORTED -> {
        JOptionPane.showMessageDialog(
            pluginAction.getParent(),
            "The configuration uses constructs outside the supported"
                + " fragment and was refused (UNSUPPORTED).",
            "SMT Validation",
            JOptionPane.ERROR_MESSAGE);
      }
      case VALIDATION_ERROR -> {
        JOptionPane.showMessageDialog(
            pluginAction.getParent(),
            "The oracle contradicted the solver on the reconstructed"
                + " witness (VALIDATION_ERROR); this signals a defect in"
                + " the encoding or the checker.",
            "SMT Validation",
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private static File modelDirectoryOf(MModel model) {
    String filename = model.filename();
    return filename != null && !filename.isEmpty() ? new File(filename).getParentFile() : null;
  }

  private static FileFilter propertiesFileFilter() {
    return new FileFilter() {
      @Override
      public String getDescription() {
        return "Properties files";
      }

      @Override
      public boolean accept(File f) {
        return f.isDirectory() || f.getName().endsWith(".properties");
      }
    };
  }
}
