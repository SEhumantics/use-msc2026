package org.tzi.kodkod.model.config;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.LegacyListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.tzi.kodkod.model.config.impl.PropertyEntry;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.plugin.PropertiesWriter;
import org.tzi.use.kodkod.plugin.gui.model.data.SettingsConfiguration;
import org.tzi.use.kodkod.plugin.gui.util.ChangeConfiguration;

public class ConfigurationFileManager {

	public static final String DEFAULT_CONFIG_PREFIX = "config";
	public static final int MAX_CONFIGURATION_NAME_LENGTH = 64;
	
	private final IModel model;
	private final SettingsConfiguration sc;
	
	private final Map<String, Configuration> configurations = new LinkedHashMap<String, Configuration>();

	public ConfigurationFileManager(IModel model, SettingsConfiguration sc, File configFile) throws ConfigurationException {
		this.model = model;
		this.sc = sc;
		
		INIConfiguration iniFile = new INIConfiguration();
		// see ConfigurablePlugin.readConfiguration for why this is needed (config2 no longer splits
		// comma-delimited "Set{a,b,c}" list values by default like config1 did).
		iniFile.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
		try (FileReader reader = new FileReader(configFile)) {
			iniFile.read(reader);
		} catch (IOException ex) {
			throw new ConfigurationException(ex);
		}
		for(String section : iniFile.getSections()){
			String sectionName = (section != null) ? section : createDefaultConfigName() ;
			configurations.put(sectionName, iniFile.getSection(section));
		}
		
		if(configurations.isEmpty()){
			throw new ConfigurationException("Could not load configurations from file. File is empty or might not exist.");
		}
	}
	
	public ConfigurationFileManager(IModel model, SettingsConfiguration sc) {
		this.model = model;
		this.sc = sc;
		createDefaultConfiguration();
	}
	
	private void createDefaultConfiguration() {
		sc.reset();
		Configuration c = ChangeConfiguration.toProperties(sc, model);
		addOrUpdateConfiguration(createDefaultConfigName(), c);
	}
	
	public String[] getConfigurationNames() {
		return configurations.keySet().toArray(new String[0]);
	}
	
	public int getConfigutationCount(){
		return configurations.size();
	}
	
	public Configuration getConfiguration(String name) {
		return configurations.get(name);
	}
	
	public boolean isConfigNameTaken(String name){
		return configurations.containsKey(name);
	}

	public void addOrUpdateConfiguration(String name, Configuration c) {
		configurations.put(name, c);
	}
	
	public void removeConfiguration(String name) {
		configurations.remove(name);
		if(configurations.isEmpty()){
			createDefaultConfiguration();
		}
	}

	public String createNewConfigName(String prefix){
		int i = 1;
		while(configurations.containsKey(prefix + i)){
			++i;
		}
		
		return prefix + i;
	}
	
	public String createDefaultConfigName(){
		if(!configurations.containsKey(PropertyEntry.DEFAULT_SECTION_NAME)){
			return PropertyEntry.DEFAULT_SECTION_NAME;
		}
		
		return createNewConfigName(PropertyEntry.DEFAULT_SECTION_NAME);
	}
	
	public void save(File configFile) throws IOException {
		PropertiesWriter pw = new PropertiesWriter(model);
		pw.writeToFile(configFile, configurations);
	}
	
}
