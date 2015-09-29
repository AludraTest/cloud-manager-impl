package org.aludratest.cloud.impl.config;

import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.ConfigManager;
import org.aludratest.cloud.config.MainPreferences;
import org.aludratest.cloud.config.Preferences;
import org.codehaus.plexus.component.annotations.Component;

/**
 * Default implementation of the <code>ConfigManager</code> interface. Uses the internal implementation of the
 * <code>MainPreferences</code> interface to persist and apply the Preferences.
 * 
 * @author falbrech
 * 
 */
@Component(role = ConfigManager.class)
public class ConfigManagerImpl implements ConfigManager {

	@Override
	public void applyConfig(Preferences newConfig, MainPreferences mainConfig) throws ConfigException, IllegalArgumentException {
		if (newConfig == null) {
			throw new IllegalArgumentException("newConfig must not be null");
		}
		if (!(mainConfig instanceof MainPreferencesImpl)) {
			throw new IllegalArgumentException("mainConfig is not part of the main configuration tree");
		}

		((MainPreferencesImpl) mainConfig).applyPreferences(newConfig);
	}

}
