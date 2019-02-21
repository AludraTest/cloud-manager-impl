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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.aludratest.cloud.app.CloudManagerApp;
import org.aludratest.cloud.app.CloudManagerAppFileStore;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.ConfigManager;
import org.aludratest.cloud.config.ConfigUtil;
import org.aludratest.cloud.config.Configurable;
import org.aludratest.cloud.config.MainPreferences;
import org.aludratest.cloud.config.MutablePreferences;
import org.aludratest.cloud.config.Preferences;
import org.aludratest.cloud.config.PreferencesListener;
import org.aludratest.cloud.config.SimplePreferences;
import org.aludratest.cloud.impl.config.MainPreferencesImpl;
import org.aludratest.cloud.impl.user.UserDatabaseRegistryImpl;
import org.aludratest.cloud.impl.util.SpringBeanUtil;
import org.aludratest.cloud.manager.ResourceManager;
import org.aludratest.cloud.module.ResourceModule;
import org.aludratest.cloud.module.ResourceModuleRegistry;
import org.aludratest.cloud.plugin.CloudManagerPlugin;
import org.aludratest.cloud.resourcegroup.ResourceGroupManager;
import org.aludratest.cloud.user.UserDatabase;
import org.aludratest.cloud.user.admin.UserDatabaseRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Default implementation of the CloudManagerApp interface.
 *
 * @author falbrech
 *
 */
@Component
public final class CloudManagerAppImpl extends CloudManagerApp implements PreferencesListener {

	private static final Log LOG = LogFactory.getLog(CloudManagerAppImpl.class);

	private static final String CONFIG_FILENAME = "acm.config";

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private UserDatabaseRegistry userDatabaseRegistry;

	@Autowired
	private ResourceModuleRegistry resourceModuleRegistry;

	@Autowired
	private ResourceManager resourceManager;

	@Autowired
	private ResourceGroupManager resourceGroupManager;

	@Autowired
	private ConfigManager configManager;

	@Autowired
	private CloudManagerAppFileStore fileStore;

	@Autowired
	private CloudManagerAppConfigImpl configurable;

	// populated by PostConstruct
	// FIXME must be replaced with PluginRegistry, analogous to
	// ResourceModuleRegistry, to avoid dependency cycles
	private Map<String, CloudManagerPlugin> pluginRegistry;

	private MainPreferences preferencesRoot;

	private ScheduledExecutorService saveScheduler;

	private ScheduledFuture<?> scheduledSave;

	@Override
	public void start() throws ConfigException {
		pluginRegistry = SpringBeanUtil.getBeansOfTypeByQualifier(CloudManagerPlugin.class, applicationContext);

		createMainPreferences();
		saveScheduler = Executors.newScheduledThreadPool(1);

		SimplePreferences mutableRoot = new SimplePreferences(null);
		ConfigUtil.copyPreferences(preferencesRoot, mutableRoot);

		fillDefaults(mutableRoot);

		MutablePreferences modulesRoot = mutableRoot.createChildNode("modules");
		for (ResourceModule module : resourceModuleRegistry.getAllResourceModules()) {
			if (module instanceof Configurable) {
				String nodeName = module.getResourceType().getName();
				if (modulesRoot.getChildNode(nodeName) == null) {
					((Configurable) module).fillDefaults(modulesRoot.createChildNode(nodeName));
				}
			}
		}

		// set defaults for all plugins
		MutablePreferences pluginsRoot = mutableRoot.createChildNode("plugins");
		for (Map.Entry<String, CloudManagerPlugin> entry : pluginRegistry.entrySet()) {
			if (entry.getValue() instanceof Configurable) {
				String nodeName = entry.getKey();
				if (pluginsRoot.getChildNode(nodeName) == null) {
					((Configurable) entry.getValue()).fillDefaults(pluginsRoot.createChildNode(nodeName));
				}
			}
		}

		// assert that "groups" root is present
		if (mutableRoot.getChildNode("groups") == null) {
			MutablePreferences groupsRoot = mutableRoot.createChildNode("groups");
			if (resourceGroupManager instanceof Configurable) {
				((Configurable) resourceGroupManager).fillDefaults(groupsRoot);
			}
		}

		if (ConfigUtil.differs(mutableRoot, preferencesRoot)) {
			configManager.applyConfig(mutableRoot, preferencesRoot);
		}
		configure(preferencesRoot);
		resourceManager.start(resourceGroupManager);

		for (CloudManagerPlugin plugin : pluginRegistry.values()) {
			plugin.applicationStarted();
		}
	}

	@Override
	public void shutdown() {
		if (resourceManager != null) {
			resourceManager.shutdown();
		}
		if (saveScheduler != null) {
			saveScheduler.shutdown();
		}

		if (resourceModuleRegistry != null) {
			for (ResourceModule module : resourceModuleRegistry.getAllResourceModules()) {
				module.handleApplicationShutdown();
			}
		}

		if (pluginRegistry != null) {
			for (CloudManagerPlugin plugin : pluginRegistry.values()) {
				plugin.applicationStopped();
			}
		}

		preferencesRoot = null;
	}

	@Override
	public boolean isStarted() {
		return preferencesRoot != null;
	}

	private void createMainPreferences() throws ConfigException {
		preferencesRoot = new MainPreferencesImpl(null);
		((MainPreferencesImpl) preferencesRoot).applyPreferences(readConfig());
		attachPreferencesListener(preferencesRoot);
	}

	private void attachPreferencesListener(MainPreferences preferences) {
		preferences.addPreferencesListener(this);
		for (String nodeName : preferences.getChildNodeNames()) {
			attachPreferencesListener(preferences.getChildNode(nodeName));
		}
	}

	private MutablePreferences readConfig() {
		if (!fileStore.existsFile(CONFIG_FILENAME)) {
			return new SimplePreferences(null);
		}

		try (InputStream in = fileStore.openFile(CONFIG_FILENAME)) {
			return readPreferences(in);
		} catch (IOException e) {
			LOG.error("Could not read preferences file " + CONFIG_FILENAME);
			return new SimplePreferences(null);
		}
	}

	private MutablePreferences readPreferences(InputStream in) throws IOException {
		Properties p = new Properties();
		SimplePreferences prefs = new SimplePreferences(null);

		p.loadFromXML(in);

		for (String key : p.stringPropertyNames()) {
			String value = p.getProperty(key);
			if ("".equals(value)) {
				value = null;
			}
			prefs.setValue(key, value);
		}

		return prefs;
	}

	private byte[] serializePreferences(Preferences prefs) {
		// compress to properties
		Properties p = new Properties();
		copyToProperties(prefs, null, p);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			p.storeToXML(baos, "AludraTest Cloud Manager auto-generated config file. DO NOT MODIFY!!");
		} catch (IOException e) {
			LOG.error("Could not serialize preferences", e);
			return new byte[0];
		}

		return baos.toByteArray();
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
		if (preferencesRoot == null) {
			throw new IllegalStateException("Config cannot be saved before loaded");
		}

		LOG.debug("Saving ACM preferences");
		try {
			fileStore.saveFile(CONFIG_FILENAME, new ByteArrayInputStream(serializePreferences(preferencesRoot)));
		} catch (IOException e) {
			LOG.error("Could not write preferences file", e);
		}
	}

	@Override
	public void preferencesAboutToChange(Preferences oldPreferences, Preferences newPreferences)
			throws ConfigException {
	}

	@Override
	public void preferencesChanged(Preferences oldPreferences, MainPreferences newPreferences) throws ConfigException {
		// attach us as listener to all child nodes (if we are already registered, this
		// is a no-op)
		attachPreferencesListener(newPreferences);

		synchronized (this) {
			if (scheduledSave != null) {
				scheduledSave.cancel(false);
			}
		}

		scheduledSave = saveScheduler.schedule(new Runnable() {
			@Override
			public void run() {
				synchronized (CloudManagerAppImpl.this) {
					scheduledSave = null;
				}
				saveAllPreferences();
			}
		}, 100, TimeUnit.MILLISECONDS);
	}

	private void configure(MainPreferences preferences) throws ConfigException {
		MainPreferences basic = preferences.getOrCreateChildNode("basic");
		configurable.setPreferences(basic);

		UserDatabase userDb = userDatabaseRegistry
				.getUserDatabase(configurable.getCurrentSettings().getUserAuthenticationSource());
		if (userDb == null) {
			userDb = userDatabaseRegistry.getAllUserDatabases().get(0);
			LOG.warn("Could not find user database of type "
					+ configurable.getCurrentSettings().getUserAuthenticationSource()
					+ ". Using first found user database " + userDb + ".");
		}
		((UserDatabaseRegistryImpl) userDatabaseRegistry).setSelectedUserDatabase(userDb);

		// update all configurable modules
		MainPreferences modulesRoot = preferences.getOrCreateChildNode("modules");
		for (ResourceModule module : resourceModuleRegistry.getAllResourceModules()) {
			if (module instanceof Configurable) {
				MainPreferences prefs = modulesRoot.getChildNode(module.getResourceType().getName());
				if (prefs != null) {
					((Configurable) module).setPreferences(prefs);
				}
			}
		}

		// update all configurable plugins
		MainPreferences pluginsRoot = preferences.getOrCreateChildNode("plugins");
		for (Map.Entry<String, CloudManagerPlugin> entry : pluginRegistry.entrySet()) {
			if (entry.getValue() instanceof Configurable) {
				MainPreferences prefs = pluginsRoot.getChildNode(entry.getKey());
				if (prefs != null) {
					((Configurable) entry.getValue()).setPreferences(prefs);
				}
			}
		}

		// update group manager
		MainPreferences groupRoot = preferences.getOrCreateChildNode("groups");

		if (resourceGroupManager instanceof Configurable) {
			((Configurable) resourceGroupManager).setPreferences(groupRoot);
		}
	}

	private void fillDefaults(MutablePreferences preferences) {
		CloudManagerAppSettingsImpl.fillDefaults(preferences.createChildNode("basic"));
	}

}
