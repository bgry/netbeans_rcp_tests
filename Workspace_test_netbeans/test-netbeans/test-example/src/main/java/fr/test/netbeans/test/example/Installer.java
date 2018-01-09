package fr.test.netbeans.test.example;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.netbeans.Module;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.ModuleInfo;
import org.openide.modules.ModuleInstall;
import org.openide.util.Lookup;
import org.openide.util.Utilities;

public class Installer extends ModuleInstall {

	private final Logger logger = LogManager.getLogger(getClass());

	@Override
	public void restored() {
		File editorConfiguration = InstalledFileLocator.getDefault().locate(
				"conf/config.xml",
				"test-example",
				false);

		logger.info("Editor config file = " + editorConfiguration);
		DefaultConfigurationBuilder builder = new DefaultConfigurationBuilder();
		builder.setFile(editorConfiguration);
		try {
			Configuration configuration = builder.getConfiguration(true);
			synchronized (CentralLookup.getDefault()) {
				CentralLookup.getDefault().add(configuration);
			}
		} catch (ConfigurationException e) {
			logger.error(e.getMessage(), e);
		}

		List<URL> modulesUrls = new ArrayList<>();
		List<ClassLoader> modulesClassLoaders = new ArrayList<>();
		Collection<? extends ModuleInfo> modules = Lookup.getDefault().lookupAll(ModuleInfo.class);
		for (ModuleInfo module : modules) {
			if (module instanceof Module && module.isEnabled()) {
				try {
					List<File> moduleJarFiles = ((Module) module).getAllJars();
					if (moduleJarFiles != null) {
						for (File moduleJarFile : moduleJarFiles) {
							if (moduleJarFile.isFile()) {
								modulesUrls.add(new URL("jar:" + Utilities.toURI(moduleJarFile) + "!/"));
							}
						}
					}
					modulesClassLoaders.add(module.getClassLoader());
				} catch (MalformedURLException ex) {
					// ignore this module
					logger.warn("Module ignored due to this exception:" + ex.getMessage(), ex);
				}
			}
		}

	}
}
