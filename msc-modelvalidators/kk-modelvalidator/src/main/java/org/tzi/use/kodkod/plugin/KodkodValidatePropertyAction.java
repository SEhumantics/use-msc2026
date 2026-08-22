package org.tzi.use.kodkod.plugin;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

/**
 * Action-Class to extend the toolbar with a button to call the model validator
 * with a configuration file.
 * 
 * @author Hendrik Reitmann
 * 
 */
public class KodkodValidatePropertyAction extends KodkodValidateCmd implements IPluginActionDelegate {

	@Override
	public void performAction(IPluginAction pluginAction) {
		if(!pluginAction.getSession().hasSystem()){
			JOptionPane.showMessageDialog(pluginAction.getParent(),
					"No model present.", "No Model", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		initialize(pluginAction.getSession(), pluginAction.getParent());

		// MModel.getModelDirectory() no longer exists; MModel.filename() (the .use file's own path)
		// is the current equivalent, and its parent directory is what the old call supplied.
		String modelFilename = mModel.filename();
		File modelDirectory = (modelFilename != null && !modelFilename.isEmpty())
				? new File(modelFilename).getParentFile() : null;

		JFileChooser fileChooser;
		if (modelDirectory != null) {
			fileChooser = new JFileChooser(modelDirectory.getPath());
		} else {
			fileChooser = new JFileChooser();
		}
		fileChooser.setFileFilter(new FileFilter() {

			@Override
			public String getDescription() {
				return "Property-Dateien";
			}

			@Override
			public boolean accept(File f) {
				if (f.isDirectory())
					return true;
				return f.getName().endsWith(".properties");
			}
		});

		if (fileChooser.showOpenDialog(pluginAction.getParent()) == 0) {
			File file = fileChooser.getSelectedFile();
			extractConfigureAndValidate(file);
		}
	}

}
