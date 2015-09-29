package org.aludratest.cloud.impl.app;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.aludratest.cloud.app.CloudManagerAppConfig;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.MutablePreferences;
import org.aludratest.cloud.config.Preferences;

/**
 * Default implementation of the CloudManagerAppConfig interface. Additional to the default configuration values defined by the
 * interface, the user authentication source and a path to a Phantom JS executable are also read from the stored Preferences.
 * 
 * @author falbrech
 * 
 */
public class CloudManagerAppConfigImpl implements CloudManagerAppConfig {

	private static final String CONFIG_HOST_NAME = "hostName";

	private static final String CONFIG_USE_PROXY = "useProxy";

	private static final String CONFIG_PROXY_HOST = "proxyHost";

	private static final String CONFIG_PROXY_PORT = "proxyPort";

	private static final String CONFIG_PROXY_BYPASS_REGEXP = "bypassProxyRegexp";

	// "non-public" properties
	private static final String CONFIG_USER_AUTH = "userAuthentication";

	private static final String CONFIG_PHANTOMJS_EXE = "phantomJSExecutable";

	private String hostName;

	private String userAuthenticationSource;

	private String phantomJsExecutable;

	private boolean useProxy;

	private String proxyHost;

	private int proxyPort;

	private String bypassProxyRegexp;

	/**
	 * Creates a new instance of this class which reads its configuration values from the given Preferences object.
	 * 
	 * @param prefs
	 *            Preferences object to read configuration values from.
	 * 
	 * @throws ConfigException
	 *             If the configuration stored in the Preferences object is invalid.
	 */
	public CloudManagerAppConfigImpl(Preferences prefs) throws ConfigException {
		hostName = prefs.getStringValue(CONFIG_HOST_NAME);
		useProxy = prefs.getBooleanValue(CONFIG_USE_PROXY);
		proxyHost = prefs.getStringValue(CONFIG_PROXY_HOST);

		String s = prefs.getStringValue(CONFIG_PROXY_PORT);
		if (s != null) {
			try {
				proxyPort = Integer.parseInt(s);
			}
			catch (NumberFormatException e) {
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
			}
			catch (PatternSyntaxException e) {
				throw new ConfigException("Regular expression for proxy bypass hosts is invalid: " + e.getMessage(),
						CONFIG_PROXY_BYPASS_REGEXP);
			}
		}

		userAuthenticationSource = prefs.getStringValue(CONFIG_USER_AUTH);
		phantomJsExecutable = prefs.getStringValue(CONFIG_PHANTOMJS_EXE);
	}

	/**
	 * Fills the given Preferences object with the default configuration values provided by this implementation.
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

	/**
	 * Returns the name of the user authentication source, as it has been read from the Preferences object.
	 * 
	 * @return The name of the user authentication source, as it has been read from the Preferences object.
	 */
	public String getUserAuthenticationSource() {
		return userAuthenticationSource;
	}

	/**
	 * Returns the full path to a Phantom JS executable, as it has been read from the Preferences object.
	 * 
	 * @return The full path to a Phantom JS executable, as it has been read from the Preferences object.
	 */
	public String getPhantomJsExecutable() {
		return phantomJsExecutable;
	}

}
