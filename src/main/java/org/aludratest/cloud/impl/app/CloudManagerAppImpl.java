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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aludratest.cloud.app.CloudManagerApp;
import org.aludratest.cloud.app.CloudManagerAppConfig;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.ConfigManager;
import org.aludratest.cloud.config.ConfigUtil;
import org.aludratest.cloud.config.Configurable;
import org.aludratest.cloud.config.MainPreferences;
import org.aludratest.cloud.config.MutablePreferences;
import org.aludratest.cloud.config.Preferences;
import org.aludratest.cloud.config.PreferencesListener;
import org.aludratest.cloud.config.SimplePreferences;
import org.aludratest.cloud.manager.ResourceManager;
import org.aludratest.cloud.module.ResourceModule;
import org.aludratest.cloud.plugin.CloudManagerPlugin;
import org.aludratest.cloud.resource.ResourceType;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationStore;
import org.aludratest.cloud.resource.writer.ResourceWriterFactory;
import org.aludratest.cloud.resourcegroup.ResourceGroupManager;
import org.aludratest.cloud.user.UserDatabase;
import org.aludratest.cloud.user.admin.UserDatabaseRegistry;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the CloudManagerApp interface. This implementation is registered as a Plexus component.
 * 
 * @author falbrech
 * 
 */
@Component(role = CloudManagerApp.class)
public final class CloudManagerAppImpl extends CloudManagerApp {

	private static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerAppImpl.class);

	@Requirement(role = ResourceModule.class)
	private Map<String, ResourceModule> resourceModules = new HashMap<String, ResourceModule>();

	@Requirement
	private UserDatabaseRegistry userDatabaseRegistry;

	@Requirement
	private ResourceTypeAuthorizationStore authorizationStore;

	@Requirement
	private ResourceManager resourceManager;

	@Requirement
	private ResourceGroupManager resourceGroupManager;

	@Requirement
	private ConfigManager configManager;

	@Requirement(role = CloudManagerPlugin.class)
	private Map<String, CloudManagerPlugin> pluginRegistry;

	private UserDatabase selectedUserDatabase;

	private List<ResourceModule> readOnlyResourceModules;

	private MainPreferences preferencesRoot;

	private CloudManagerAppConfigImpl configuration;

	/**
	 * Creates a new instance of this implementation class.
	 */
	public CloudManagerAppImpl() {
		CloudManagerApp.instance = this;
	}

	@Override
	public void start(MainPreferences configuration) throws ConfigException {
		preferencesRoot = configuration;

		SimplePreferences mutableRoot = new SimplePreferences(null);
		ConfigUtil.copyPreferences(preferencesRoot, mutableRoot);

		// set defaults for all configurable modules, when no config is set
		if (mutableRoot.getChildNode("basic") == null) {
			MutablePreferences basicRoot = mutableRoot.createChildNode("basic");
			CloudManagerAppConfigImpl.fillDefaults(basicRoot);
		}

		MutablePreferences modulesRoot = mutableRoot.createChildNode("modules");
		for (ResourceModule module : getAllResourceModules()) {
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
			getConfigManager().applyConfig(mutableRoot, preferencesRoot);
		}
		preferencesRoot.getChildNode("basic").addPreferencesListener(new PreferencesListener() {
			@Override
			public void preferencesChanged(Preferences oldPreferences, MainPreferences newPreferences) throws ConfigException {
				CloudManagerAppImpl.this.configuration = new CloudManagerAppConfigImpl(newPreferences);
			}

			@Override
			public void preferencesAboutToChange(Preferences oldPreferences, Preferences newPreferences) throws ConfigException {
			}
		});
		configure(preferencesRoot);
		resourceManager.start(resourceGroupManager);

		for (CloudManagerPlugin plugin : pluginRegistry.values()) {
			plugin.applicationStarted();
		}

	}

	@Override
	public void shutdown() {
		resourceManager.shutdown();

		for (ResourceModule module : resourceModules.values()) {
			module.handleApplicationShutdown();
		}

		for (CloudManagerPlugin plugin : pluginRegistry.values()) {
			plugin.applicationStopped();
		}

		preferencesRoot = null;
	}

	@Override
	public boolean isStarted() {
		return preferencesRoot != null;
	}

	private void configure(MainPreferences preferences) throws ConfigException {
		MainPreferences basic = preferences.getOrCreateChildNode("basic");
		configuration = new CloudManagerAppConfigImpl(basic);

		selectedUserDatabase = userDatabaseRegistry.getUserDatabase(configuration.getUserAuthenticationSource());
		if (selectedUserDatabase == null) {
			selectedUserDatabase = userDatabaseRegistry.getAllUserDatabases().get(0);
			LOGGER.warn("Could not find user database of type " + configuration.getUserAuthenticationSource()
					+ ". Using first found user database "
					+ selectedUserDatabase + ".");
		}

		// update all configurable modules
		MainPreferences modulesRoot = preferences.getOrCreateChildNode("modules");
		for (ResourceModule module : getAllResourceModules()) {
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

	@Override
	public ResourceWriterFactory getResourceWriterFactory(ResourceType resourceType) {
		ResourceModule module = getResourceModule(resourceType);
		return module == null ? null : module.getResourceWriterFactory();
	}

	@Override
	public UserDatabaseRegistry getUserDatabaseRegistry() {
		return userDatabaseRegistry;
	}

	@Override
	public UserDatabase getSelectedUserDatabase() {
		return selectedUserDatabase;
	}

	@Override
	public ResourceTypeAuthorizationStore getResourceTypeAuthorizationStore() {
		return authorizationStore;
	}

	@Override
	public ResourceGroupManager getResourceGroupManager() {
		return resourceGroupManager;
	}

	@Override
	public ResourceManager getResourceManager() {
		return resourceManager;
	}

	@Override
	public ConfigManager getConfigManager() {
		return configManager;
	}

	@Override
	public CloudManagerAppConfig getBasicConfiguration() {
		return configuration;
	}

	@Override
	public List<ResourceModule> getAllResourceModules() {
		if (readOnlyResourceModules == null) {
			readOnlyResourceModules = Collections.unmodifiableList(new ArrayList<ResourceModule>(resourceModules.values()));
		}
		return readOnlyResourceModules;
	}

}
