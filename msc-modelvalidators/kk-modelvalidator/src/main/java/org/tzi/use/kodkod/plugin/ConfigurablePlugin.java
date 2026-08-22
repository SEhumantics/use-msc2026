package org.tzi.use.kodkod.plugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.LegacyListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.config.impl.DefaultConfigurationVisitor;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;

/**
 * A base class for plugins that can be configured using property files.
 * With just a file name without a sector name given, the first sector of
 * the properties file will be used. 
 * 
 * @author Frank Hilken
 */
public abstract class ConfigurablePlugin extends AbstractPlugin {

	/**
	 * Configuration of the model with the data from the given configuration.
	 */
	protected void configureModel(Configuration config, PrintWriter warningsOut) throws ConfigurationException {
		model().reset();
		PropertyConfigurationVisitor newConfigurationVisitor = new PropertyConfigurationVisitor(config, warningsOut);
		model().accept(newConfigurationVisitor);
		
		if (newConfigurationVisitor.containErrors()) {
			throw new ConfigurationException(LogMessages.configurationError);
		}
		
		LOG.info(LogMessages.modelConfigurationSuccessful);
	}
	
	/**
	 * Configuration with the default search space.
	 */
	protected void configureModel(PrintWriter warningsOut) throws IOException, ConfigurationException {
		model().reset();
		DefaultConfigurationVisitor configurationVisitor = new DefaultConfigurationVisitor(mModel.filename());
		model().accept(configurationVisitor);
		configureModel(extractConfigFromFile(configurationVisitor.getFile()), warningsOut);
		
		LOG.info(LogMessages.modelConfigurationSuccessful);
	}
	
	protected Configuration extractConfigFromFile(File file) throws ConfigurationException {
		INIConfiguration iniConfiguration = readConfiguration(file);
		if(iniConfiguration.getSections().isEmpty()){
			return iniConfiguration.getSection(null);
		} else {
			String section = iniConfiguration.getSections().iterator().next();
			return iniConfiguration.getSection(section);
		}
	}

	protected Configuration extractConfigFromFile(File file, String section) throws ConfigurationException {
		INIConfiguration iniConfiguration = readConfiguration(file);
		if(!iniConfiguration.getSections().contains(section)){
			if(section == null){
				String sName = iniConfiguration.getSections().iterator().next();
				return iniConfiguration.getSection(sName);
			} else {
				throw new ConfigurationException("Selected section does not exist in properties file.");
			}
		}

		return iniConfiguration.getSection(section);
	}

	private INIConfiguration readConfiguration(File f) throws ConfigurationException {
		INIConfiguration iniConfiguration = new INIConfiguration();
		// commons-configuration2 disabled comma-as-list-delimiter by default (commons-configuration
		// 1.x always split unescaped commas into list values). This plugin's "Set{a,b,c}" property
		// syntax (see PropertyConfigurationVisitor.readSingleElements) depends on that 1.x splitting
		// behavior to tokenize the elements; LegacyListDelimiterHandler is config2's own migration aid
		// for reproducing it exactly. Must be set before read(), since splitting happens at parse time.
		iniConfiguration.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
		try (USECommentFilterReader reader = new USECommentFilterReader(new FileReader(f))) {
			iniConfiguration.read(reader);
		} catch (IOException ex) {
			throw new ConfigurationException(ex);
		}
		return iniConfiguration;
	}
	
}
