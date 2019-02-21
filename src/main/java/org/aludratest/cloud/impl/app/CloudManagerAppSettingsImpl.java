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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.aludratest.cloud.app.CloudManagerAppConfigAdmin;
import org.aludratest.cloud.app.CloudManagerAppSettings;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.ConfigManager;
import org.aludratest.cloud.config.MainPreferences;
import org.aludratest.cloud.config.MutablePreferences;
import org.aludratest.cloud.config.Preferences;
import org.aludratest.cloud.config.admin.AbstractConfigurationAdmin;
import org.springframework.util.StringUtils;

public class CloudManagerAppSettingsImpl implements CloudManagerAppSettings {

	private static final String CONFIG_HOST_NAME = "hostName";

	private static final String CONFIG_USE_PROXY = "useProxy";

	private static final String CONFIG_PROXY_HOST = "proxyHost";

	private static final String CONFIG_PROXY_PORT = "proxyPort";

	private static final String CONFIG_PROXY_BYPASS_REGEXP = "bypassProxyRegexp";

	private static final String CONFIG_USER_AUTH = "userAuthentication";

	// "non-public" properties
	// TODO check if still needed, or if this is a Selenium specific configuration
	private static final String CONFIG_PHANTOMJS_EXE = "phantomJSExecutable";

	private String hostName;

	private String userAuthenticationSource;

	private String phantomJsExecutable;

	private boolean useProxy;

	private String proxyHost;

	private int proxyPort;

	private String bypassProxyRegexp;

	/**
	 * Creates a new instance of this class which reads its configuration values
	 * from the given Preferences object.
	 *
	 * @param prefs
	 *            Preferences object to read configuration values from.
	 *
	 * @throws ConfigException
	 *             If the configuration stored in the Preferences object is invalid.
	 */
	public CloudManagerAppSettingsImpl(Preferences prefs) throws ConfigException {
		hostName = prefs.getStringValue(CONFIG_HOST_NAME);
		useProxy = prefs.getBooleanValue(CONFIG_USE_PROXY);
		proxyHost = prefs.getStringValue(CONFIG_PROXY_HOST);

		String s = prefs.getStringValue(CONFIG_PROXY_PORT);
		if (s != null) {
			try {
				proxyPort = Integer.parseInt(s);
			} catch (NumberFormatException e) {
				throw new ConfigException("Proxy port is invalid", CONFIG_PROXY_PORT);
			}

			if (proxyPort < 1 || proxyPort > 65535) {
				throw new ConfigException("Proxy port is invalid", CONFIG_PROXY_PORT);
			}
		}

		bypassProxyRegexp = prefs.getStringValue(CONFIG_PROXY_BYPASS_REGEXP);
		if (bypassProxyRegexp != null) {
			try {
				Pattern.compile(bypassProxyRegexp);
			} catch (PatternSyntaxException e) {
				throw new ConfigException("Regular expression for proxy bypass hosts is invalid: " + e.getMessage(),
						CONFIG_PROXY_BYPASS_REGEXP);
			}
		}

		if (useProxy && StringUtils.isEmpty(proxyHost)) {
			throw new ConfigException("Proxy host must be specified when proxy shall be used", CONFIG_PROXY_HOST);
		}
		if (useProxy && proxyPort == 0) {
			throw new ConfigException("Proxy port must be specified when proxy shall be used", CONFIG_PROXY_PORT);
		}

		userAuthenticationSource = prefs.getStringValue(CONFIG_USER_AUTH);
		phantomJsExecutable = prefs.getStringValue(CONFIG_PHANTOMJS_EXE);
	}

	/**
	 * Fills the given Preferences object with the default configuration values
	 * provided by this implementation.
	 *
	 * @param preferences
	 *            Preferences object to fill with default configuration values.
	 */
	public static void fillDefaults(MutablePreferences preferences) {
		preferences.setValue(CONFIG_HOST_NAME, "localhost");
		preferences.setValue(CONFIG_USER_AUTH, "local-file");
	}

	@Override
	public String getHostName() {
		return hostName;
	}

	@Override
	public boolean isUseProxy() {
		return useProxy;
	}

	@Override
	public String getProxyHost() {
		return proxyHost;
	}

	@Override
	public int getProxyPort() {
		return proxyPort;
	}

	@Override
	public String getBypassProxyRegexp() {
		return bypassProxyRegexp;
	}

	@Override
	public String getUserAuthenticationSource() {
		return userAuthenticationSource;
	}

	/**
	 * Returns the full path to a Phantom JS executable, as it has been read from
	 * the Preferences object.
	 *
	 * @return The full path to a Phantom JS executable, as it has been read from
	 *         the Preferences object.
	 */
	public String getPhantomJsExecutable() {
		return phantomJsExecutable;
	}

	static class Admin extends AbstractConfigurationAdmin implements CloudManagerAppConfigAdmin {

		private CloudManagerAppConfigImpl configImpl;

		public Admin(MainPreferences mainPreferences, ConfigManager configManager,
				CloudManagerAppConfigImpl configImpl) {
			super(mainPreferences, configManager);
			this.configImpl = configImpl;
		}

		@Override
		public void commit() throws ConfigException {
			super.commit();
			configImpl.refreshSettings();
		}

		@Override
		public void setHostName(String hostName) {
			getPreferences().setValue(CONFIG_HOST_NAME, hostName);
		}

		@Override
		public void setUseProxy(boolean useProxy) {
			getPreferences().setValue(CONFIG_USE_PROXY, useProxy);
		}

		@Override
		public void setProxyHost(String proxyHost) {
			getPreferences().setValue(CONFIG_PROXY_HOST, proxyHost);
		}

		@Override
		public void setProxyPort(int proxyPort) {
			getPreferences().setValue(CONFIG_PROXY_PORT, proxyPort);
		}

		@Override
		public void setBypassProxyRegex(String bypassProxyRegex) {
			getPreferences().setValue(CONFIG_PROXY_BYPASS_REGEXP, bypassProxyRegex);
		}

		@Override
		protected void validateConfig(Preferences preferences) throws ConfigException {
			new CloudManagerAppSettingsImpl(preferences);
		}

	}

}
