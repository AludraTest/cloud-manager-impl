/*
 * Copyright (C) 2010-2015 AludraTest.org and the contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
