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
package org.aludratest.cloud.impl.app;

import org.aludratest.cloud.app.CloudManagerAppConfig;
import org.aludratest.cloud.app.CloudManagerAppConfigAdmin;
import org.aludratest.cloud.app.CloudManagerAppSettings;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.ConfigManager;
import org.aludratest.cloud.config.MainPreferences;
import org.aludratest.cloud.config.MutablePreferences;
import org.aludratest.cloud.config.Preferences;
import org.aludratest.cloud.config.SimplePreferences;
import org.aludratest.cloud.config.admin.ConfigurationAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Default implementation of the CloudManagerAppConfig interface. Is initially
 * configured from {@link CloudManagerAppImpl}.
 *
 * @author falbrech
 *
 */
@Component
public class CloudManagerAppConfigImpl implements CloudManagerAppConfig {

	private ConfigManager configManager;

	private CloudManagerAppSettings settings;

	private MainPreferences preferences;

	@Autowired
	public CloudManagerAppConfigImpl(ConfigManager configManager) {
		this.configManager = configManager;

		SimplePreferences defaultPrefs = new SimplePreferences(null);
		CloudManagerAppSettingsImpl.fillDefaults(defaultPrefs);
		try {
			settings = new CloudManagerAppSettingsImpl(defaultPrefs);
		} catch (ConfigException e) {
			throw new RuntimeException("Default app settings are invalid", e);
		}
	}

	@Override
	public CloudManagerAppSettings getCurrentSettings() {
		return settings;
	}

	@Override
	public void fillDefaults(MutablePreferences preferences) {
		CloudManagerAppSettingsImpl.fillDefaults(preferences);
	}

	@Override
	public void validateConfiguration(Preferences preferences) throws ConfigException {
		new CloudManagerAppSettingsImpl(preferences);
	}

	@Override
	public void setPreferences(MainPreferences preferences) throws ConfigException {
		this.preferences = preferences;
		settings = new CloudManagerAppSettingsImpl(preferences);
	}

	@Override
	public <T extends ConfigurationAdmin> T getAdminInterface(Class<T> ifaceClass) {
		if (ifaceClass == CloudManagerAppConfigAdmin.class) {
			return ifaceClass
					.cast(new CloudManagerAppSettingsImpl.Admin(preferences, configManager, this));
		}
		return null;
	}

	// called by SettingsImpl Admin
	void refreshSettings() throws ConfigException {
		settings = new CloudManagerAppSettingsImpl(preferences);
	}
}
