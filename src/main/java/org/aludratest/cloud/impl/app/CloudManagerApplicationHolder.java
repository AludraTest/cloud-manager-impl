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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.aludratest.cloud.app.CloudManagerApp;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.MainPreferences;
import org.aludratest.cloud.config.MutablePreferences;
import org.aludratest.cloud.config.Preferences;
import org.aludratest.cloud.config.PreferencesListener;
import org.aludratest.cloud.config.SimplePreferences;
import org.aludratest.cloud.impl.ImplConstants;
import org.aludratest.cloud.impl.config.MainPreferencesImpl;
import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The internal application holder for AludraTest Cloud Manager. Provides configuration from / to a config file which is stored in
 * a subdirectory in the user's home directory. Also provides a handle to the internal request logging database.
 * 
 * @author falbrech
 * 
 */
public class CloudManagerApplicationHolder implements PreferencesListener {

	private static final Logger LOG = LoggerFactory.getLogger(CloudManagerApplicationHolder.class);

	private static final String CONFIG_FILENAME = "acm.config";

	private static CloudManagerApplicationHolder instance;

	private PlexusContainer plexus;

	private CloudManagerApp application;

	private MainPreferences rootPreferences;

	private File configFile;

	private LogDatabase logDatabase;

	private DatabaseRequestLogger requestLogger;

	private Thread requestLoggerThread;

	private ScheduledExecutorService saveScheduler;

	private ScheduledFuture<?> scheduledSave;

	private CloudManagerApplicationHolder() {
	}

	/**
	 * Returns the one and only instance of this class. This returns <code>null</code> if the implementation is not running.
	 * 
	 * @return The one and only instance of this class, or <code>null</code> if <code>startup()</code> has not been called yet, or
	 *         <code>shutdown()</code> has already been called.
	 */
	public static CloudManagerApplicationHolder getInstance() {
		return instance;
	}

	/**
	 * Starts the AludraTest Cloud Manager implementation. Also starts the application implementation and everything else needed
	 * for application execution. <br>
	 * After startup of the implementation, you can access the AludraTest Cloud Manager application object using
	 * 
	 * <pre>
	 * CloudManagerApp.getInstance();
	 * </pre>
	 * 
	 * @throws PlexusContainerException
	 *             If the Plexus IoC Container could not be initialized.
	 * @throws ComponentLookupException
	 *             If any required component is missing from the IoC container.
	 * @throws ConfigException
	 *             If the persistent configuration for the application is invalid.
	 */
	public static void startup() throws PlexusContainerException, ComponentLookupException, ConfigException {
		instance = new CloudManagerApplicationHolder();
		instance.internalStartup();
	}

	private void internalStartup() throws PlexusContainerException, ComponentLookupException,
			ConfigException {
		plexus = new DefaultPlexusContainer();
		saveScheduler = Executors.newScheduledThreadPool(1);
		application = plexus.lookup(CloudManagerApp.class);

		rootPreferences = new MainPreferencesImpl(null);
		((MainPreferencesImpl) rootPreferences).applyPreferences(readConfig());
		attachPreferencesListener(rootPreferences);

		String sDbPort = System.getProperty("derby.port", "1527");
		Integer dbPort = null;
		if (sDbPort != null) {
			try {
				dbPort = Integer.valueOf(sDbPort);
			}
			catch (NumberFormatException e) {
				throw new ConfigException("Invalid DB port number: " + sDbPort);
			}
			LOG.info("Starting up Derby network server on TCP port " + dbPort);
		}
		else {
			LOG.info("No Derby Port specified, not starting up Derby Network Server");
		}

		try {
			logDatabase = new LogDatabase(configFile.getParentFile(), dbPort);
			requestLogger = new DatabaseRequestLogger(logDatabase);
			requestLoggerThread = new Thread(requestLogger);
			requestLoggerThread.start();
		}
		catch (Exception e) {
			throw new ConfigException("Could not initialize internal Derby Database", e);
		}

		application.start(rootPreferences);
	}

	private void attachPreferencesListener(MainPreferences preferences) {
		preferences.addPreferencesListener(this);
		for (String nodeName : preferences.getChildNodeNames()) {
			attachPreferencesListener(preferences.getChildNode(nodeName));
		}
	}

	/**
	 * Shuts the AludraTest Cloud Manager implementation down. Also stops the internal application implementation. After a call to
	 * this method, {@link #getInstance()} will return <code>null</code>.
	 */
	public static void shutdown() {
		instance.internalShutdown();
		instance = null;
	}

	private void internalShutdown() {
		application.shutdown();
		saveScheduler.shutdown();
		plexus.dispose();

		requestLoggerThread.interrupt();
		try {
			requestLoggerThread.join(10000);
		}
		catch (InterruptedException e) {
			// ignore
		}
		logDatabase.shutdown();
	}

	/**
	 * Returns the Plexus IoC container. This is a powerful component; with great power comes great responsibility. Do not use it
	 * if you do not know what you are doing.
	 * 
	 * @return The Plexus IoC container.
	 */
	public PlexusContainer getPlexusContainer() {
		return plexus;
	}

	/**
	 * Returns the root node of the Main Preferences of the application.
	 * 
	 * @return The root node of the Main Preferences of the application.
	 */
	public MainPreferences getRootPreferences() {
		return rootPreferences;
	}

	/**
	 * Returns the database for logging resource access.
	 * 
	 * @return The database for logging resource access.
	 */
	public LogDatabase getDatabase() {
		return logDatabase;
	}

	/**
	 * Returns the directory which is used as the configuration directory for the application. Other classes (components) may
	 * store their configuration files here.
	 * 
	 * @return The directory which is used as the configuration directory for the application.
	 * 
	 * @since 1.1.0
	 */
	public File getConfigurationDirectory() {
		if (configFile == null) {
			readConfig();
		}
		return configFile.getParentFile();
	}

	/**
	 * Returns the internal logger for logging resource requests.
	 * 
	 * @return The internal logger for logging resource requests.
	 */
	public DatabaseRequestLogger getRequestLogger() {
		return requestLogger;
	}

	private MutablePreferences readConfig() {
		// 1. system property acm.home
		// 2. user.home/.atcloudmanager, if writeable

		String acmHome = System.getProperty("acm.home");
		if (acmHome != null) {
			File f = new File(acmHome);
			if (!f.isDirectory() && !f.mkdirs()) {
				LOG.warn("Cannot use directory " + acmHome
						+ " for AludraTest Cloud Manager configuration. Falling back user home.");
			}
			else {
				// try writeability
				try {
					testWriteability(f);
					configFile = new File(f, CONFIG_FILENAME);
				}
				catch (IOException e) {
					LOG.warn("Cannot store configuration in directory " + acmHome + ": Could not create config file in "
							+ f.getAbsolutePath());
					LOG.warn("Falling back to user home.");
				}
			}
		}

		if (configFile == null) {
			File f = new File(new File(System.getProperty("user.home")), ImplConstants.CONFIG_DIR_NAME);
			if (!f.isDirectory() && !f.mkdir()) {
				LOG.error("Cannot store configuration in user home directory: Could not create directory " + f.getAbsolutePath());
				return new SimplePreferences(null);
			}
			else {
				// try writeability
				try {
					testWriteability(f);
				}
				catch (IOException e) {
					LOG.error("Cannot store configuration in user home directory: Could not write config file to "
							+ f.getAbsolutePath());
					return new SimplePreferences(null);
				}
			}

			configFile = new File(f, CONFIG_FILENAME);
		}

		return readPreferences(configFile);
	}

	private void testWriteability(File directory) throws IOException {
		File f = new File(directory, CONFIG_FILENAME);
		if (!f.exists()) {
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(f);
			}
			finally {
				IOUtils.closeQuietly(fos);
				f.delete();
			}
		}
	}

	private MutablePreferences readPreferences(File f) {
		SimplePreferences prefs = new SimplePreferences(null);
		if (!f.exists()) {
			return prefs;
		}

		Properties p = new Properties();

		FileInputStream fis = null;
		try {
			fis = new FileInputStream(f);
			p.loadFromXML(fis);

			for (String key : p.stringPropertyNames()) {
				String value = p.getProperty(key);
				if ("".equals(value)) {
					value = null;
				}
				prefs.setValue(key, value);
			}
		}
		catch (IOException e) {
			LOG.error("Could not read preferences file " + f.getAbsolutePath(), e);
			return prefs;
		}
		finally {
			IOUtils.closeQuietly(fis);
		}

		return prefs;
	}

	private void writePreferences(File f, Preferences prefs) {
		// compress to properties
		Properties p = new Properties();
		copyToProperties(prefs, null, p);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(f);
			p.storeToXML(fos, "AludraTest Cloud Manager auto-generated config file. DO NOT MODIFY!!");
		}
		catch (IOException e) {
			LOG.error("Could not write preferences file " + f.getAbsolutePath(), e);
		}
		finally {
			IOUtils.closeQuietly(fos);
		}
	}

	private static void copyToProperties(Preferences prefs, String prefix, Properties p) {
		for (String key : prefs.getKeyNames()) {
			if (key == null) {
				throw new NullPointerException("key");
			}
			// if value == null, use empty string
			String value = prefs.getStringValue(key);
			if (value == null) {
				value = "";
			}
			p.setProperty(prefix == null ? key : (prefix + "/" + key), value);
		}

		for (String node : prefs.getChildNodeNames()) {
			copyToProperties(prefs.getChildNode(node), prefix == null ? node : (prefix + "/" + node), p);
		}
	}

	private void saveAllPreferences() {
		if (configFile == null) {
			throw new IllegalStateException("Config cannot be saved before loaded");
		}
		LOG.debug("Saving ACM preferences");
		writePreferences(configFile, rootPreferences);
	}

	@Override
	public void preferencesAboutToChange(Preferences oldPreferences, Preferences newPreferences) throws ConfigException {
	}

	@Override
	public void preferencesChanged(Preferences oldPreferences, MainPreferences newPreferences) throws ConfigException {
		// attach us as listener to all child nodes (if we are already registered, this is a no-op)
		attachPreferencesListener(newPreferences);

		synchronized (this) {
			if (scheduledSave != null) {
				scheduledSave.cancel(false);
			}
		}

		scheduledSave = saveScheduler.schedule(new Runnable() {
			@Override
			public void run() {
				synchronized (CloudManagerApplicationHolder.this) {
					scheduledSave = null;
				}
				saveAllPreferences();
			}
		}, 100, TimeUnit.MILLISECONDS);
	}

}
