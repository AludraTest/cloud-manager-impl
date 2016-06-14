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
package org.aludratest.cloud.impl.resourcegroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aludratest.cloud.app.CloudManagerApp;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.ConfigUtil;
import org.aludratest.cloud.config.Configurable;
import org.aludratest.cloud.config.MainPreferences;
import org.aludratest.cloud.config.MutablePreferences;
import org.aludratest.cloud.config.Preferences;
import org.aludratest.cloud.config.PreferencesListener;
import org.aludratest.cloud.config.SimplePreferences;
import org.aludratest.cloud.config.admin.AbstractConfigurationAdmin;
import org.aludratest.cloud.config.admin.ConfigurationAdmin;
import org.aludratest.cloud.module.ResourceModule;
import org.aludratest.cloud.resource.Resource;
import org.aludratest.cloud.resource.ResourceStateHolder;
import org.aludratest.cloud.resource.ResourceType;
import org.aludratest.cloud.resourcegroup.ResourceGroup;
import org.aludratest.cloud.resourcegroup.ResourceGroupManager;
import org.aludratest.cloud.resourcegroup.ResourceGroupManagerAdmin;
import org.aludratest.cloud.resourcegroup.ResourceGroupManagerListener;
import org.aludratest.cloud.resourcegroup.ResourceGroupNature;
import org.aludratest.cloud.resourcegroup.ResourceGroupNatureAssociation;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the <code>ResourceGroupManager</code> interface.
 * 
 * @author falbrech
 * 
 */
@Component(role = ResourceGroupManager.class, hint = "default")
public class ResourceGroupManagerImpl implements ResourceGroupManager, Configurable, ResourceGroupManagerImplMBean {

	private static final String METADATA_PREFS_NODE = "groupMetadata";

	private static final String METADATA_TYPE_PREF_KEY = "resourceType";

	private static final String METADATA_NAME_PREF_KEY = "groupName";

	private static final String METADATA_RANK_PREF_KEY = "rank";

	private static final String METADATA_NATURES_PREF_KEY = "groupNatures";

	private Map<Integer, ResourceGroup> resourceGroups = new HashMap<Integer, ResourceGroup>();

	private Map<Integer, Map<String, ResourceGroupNatureAssociation>> natureAssociations = new HashMap<Integer, Map<String, ResourceGroupNatureAssociation>>();

	private Map<Integer, ResourceGroupMetadata> resourceGroupMetadata = new HashMap<Integer, ResourceGroupMetadata>();

	private List<ResourceGroupManagerListener> listeners = new ArrayList<ResourceGroupManagerListener>();

	private ArrayList<ResourceModule> availableModules;
	
	@Requirement(role = ResourceGroupNature.class)
	private Map<String, ResourceGroupNature> availableNatures;

	private MainPreferences preferences;

	/**
	 * Constructs a new Resource Group Manager instance.
	 */
	public ResourceGroupManagerImpl() {
		// query application for available modules - they won't change at runtime
		this.availableModules = new ArrayList<ResourceModule>(CloudManagerApp.getInstance().getAllResourceModules());
	}

	@Override
	public synchronized void addResourceGroupManagerListener(ResourceGroupManagerListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	@Override
	public void removeResourceGroupManagerListener(ResourceGroupManagerListener listener) {
		listeners.remove(listener);
	}

	@Override
	public void fillDefaults(MutablePreferences preferences) {
	}

	@Override
	public void validateConfiguration(Preferences preferences) throws ConfigException {
	}

	@Override
	public void setPreferences(MainPreferences preferences) throws ConfigException {
		if (this.preferences != null) {
			this.preferences.removePreferencesListener(preferencesListener);
		}
		this.preferences = preferences;
		preferences.addPreferencesListener(preferencesListener);

		configure(preferences);
	}

	private void configure(MainPreferences preferences) throws ConfigException {
		// build metadata map out of Preferences
		Map<Integer, ResourceGroupMetadata> newMeta = readMetadata(preferences);
		
		// build set of REMOVED, ADDED, and UNCHANGED groups
		Set<Integer> removedIds;
		Set<Integer> addedIds;

		// build sets of ADDED and REMOVED natures for UNCHANGED groups
		Map<Integer, Set<String>> addedNatures = new HashMap<Integer, Set<String>>();
		Map<Integer, Set<String>> removedNatures = new HashMap<Integer, Set<String>>();

		synchronized (this) {
			removedIds = new HashSet<Integer>(resourceGroupMetadata.keySet());
			removedIds.removeAll(newMeta.keySet());
			addedIds = new HashSet<Integer>(newMeta.keySet());
			addedIds.removeAll(resourceGroupMetadata.keySet());
			Set<Integer> unchangedIds = new HashSet<Integer>(resourceGroupMetadata.keySet());
			unchangedIds.retainAll(newMeta.keySet());

			for (Integer id : unchangedIds) {
				Set<String> set = new HashSet<String>(resourceGroupMetadata.get(id).getNatures());
				set.removeAll(newMeta.get(id).getNatures());
				removedNatures.put(id, set);
				set = new HashSet<String>(newMeta.get(id).getNatures());
				set.removeAll(resourceGroupMetadata.get(id).getNatures());
				addedNatures.put(id, set);
			}
		}

		for (Integer id : removedIds) {
			internalRemove(id);
		}

		for (Integer id : addedIds) {
			internalAdd(id, newMeta.get(id), preferences.getOrCreateChildNode(id.toString()));
		}

		for (Integer id : addedNatures.keySet()) {
			for (String nature : addedNatures.get(id)) {
				addNature(id, getResourceGroup(id.intValue()), nature, preferences.getOrCreateChildNode(id.toString()));
			}
		}

		for (Integer id : removedNatures.keySet()) {
			for (String nature : removedNatures.get(id)) {
				removeNature(id, nature, preferences.getOrCreateChildNode(id.toString()));
			}
		}

		synchronized (this) {
			resourceGroupMetadata.clear();
			resourceGroupMetadata.putAll(newMeta);
		}
	}

	@Override
	public synchronized int[] getAllResourceGroupIds() {
		int[] result = new int[resourceGroupMetadata.size()];
		List<Integer> ids = new ArrayList<Integer>(resourceGroupMetadata.keySet());
		Collections.sort(ids, new Comparator<Integer>() {
			@Override
			public int compare(Integer id1, Integer id2) {
				ResourceGroupMetadata meta1 = resourceGroupMetadata.get(id1);
				ResourceGroupMetadata meta2 = resourceGroupMetadata.get(id2);

				return meta1.getRank() - meta2.getRank();
			}
		});
		for (int i = 0; i < result.length; i++) {
			result[i] = ids.get(i).intValue();
		}

		return result;
	}

	@Override
	public ResourceGroup getResourceGroup(int id) {
		synchronized (this) {
			return resourceGroups.get(Integer.valueOf(id));
		}
	}

	@Override
	public String getResourceGroupName(int id) {
		synchronized (this) {
			ResourceGroupMetadata meta = resourceGroupMetadata.get(Integer.valueOf(id));
			return meta == null ? null : meta.getName();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends ConfigurationAdmin> T getAdminInterface(Class<T> ifaceClass) {
		if (ifaceClass == ResourceGroupManagerAdmin.class) {
			return (T) new GroupManagerAdminImpl();
		}
		return null;
	}

	@Override
	public List<ResourceGroupNature> getAvailableNaturesFor(int groupId) {
		ResourceGroup group = getResourceGroup(groupId);
		if (group == null) {
			return Collections.emptyList();
		}

		List<ResourceGroupNature> result = new ArrayList<ResourceGroupNature>();

		for (ResourceGroupNature nature : availableNatures.values()) {
			if (nature.isAvailableFor(group)) {
				result.add(nature);
			}
		}

		return result;
	}

	@Override
	public ResourceGroupNatureAssociation getNatureAssociation(int groupId, String nature) {
		ResourceGroup group = getResourceGroup(groupId);
		if (group == null) {
			return null;
		}

		Map<String, ResourceGroupNatureAssociation> assocMap = natureAssociations.get(Integer.valueOf(groupId));
		return assocMap.get(nature);
	}

	private ResourceModule getResourceModule(String resourceTypeName) {
		ResourceModule module = null;
		synchronized (this) {
			for (ResourceModule m : availableModules) {
				if (resourceTypeName.equals(m.getResourceType().getName())) {
					module = m;
					break;
				}
			}
		}
		return module;
	}

	private ResourceGroupNature getNature(String natureName) {
		return availableNatures == null ? null : availableNatures.get(natureName);
	}

	private void internalRemove(Integer groupId) {
		ResourceGroup group = getResourceGroup(groupId.intValue());

		if (group != null) {
			resourceGroups.remove(groupId);

			Map<String, ResourceGroupNatureAssociation> assocMap = natureAssociations.get(groupId);
			if (assocMap != null) {
				for (String natureName : new HashSet<String>(assocMap.keySet())) {
					removeNature(groupId, natureName, preferences.getOrCreateChildNode(groupId.toString()));
				}
			}

			fireResourceGroupRemoved(group);
		}
	}

	private void internalAdd(Integer groupId, ResourceGroupMetadata metadata, MainPreferences groupConfig) throws ConfigException {
		// find resource module
		ResourceModule module = getResourceModule(metadata.getResourceType().getName());
		if (module == null) {
			return;
		}

		ResourceGroup group = module.createResourceGroup();
		if (group instanceof Configurable) {
			((Configurable) group).setPreferences(groupConfig);
		}
		resourceGroups.put(groupId, group);

		// initialize natures, if any
		for (String natureName : metadata.getNatures()) {
			addNature(groupId, group, natureName, groupConfig);
		}

		fireResourceGroupAdded(group);
	}

	private void addNature(Integer groupId, ResourceGroup group, String natureName, MainPreferences groupConfig)
			throws ConfigException {
		ResourceGroupNature nature = getNature(natureName);
		if (nature == null) {
			return;
		}
		ResourceGroupNatureAssociation assoc = nature.createAssociationFor(group);
		
		if (assoc instanceof Configurable) {
			MainPreferences naturePrefs = groupConfig.getOrCreateChildNode("natures");
			if (naturePrefs.getChildNode(natureName) == null) {
				MutablePreferences defPrefs = new SimplePreferences(null);
				((Configurable) assoc).fillDefaults(defPrefs);
				MainPreferences prefs = naturePrefs.getOrCreateChildNode(natureName);
				CloudManagerApp.getInstance().getConfigManager().applyConfig(defPrefs, prefs);
			}
			// must now be non-null
			MainPreferences prefs = naturePrefs.getChildNode(natureName);
			((Configurable) assoc).setPreferences(prefs);
		}

		Map<String, ResourceGroupNatureAssociation> assocMap = natureAssociations.get(groupId);
		if (assocMap == null) {
			natureAssociations.put(groupId, assocMap = new HashMap<String, ResourceGroupNatureAssociation>());
		}
		assocMap.put(natureName, assoc);

		assoc.init();
	}

	private void removeNature(Integer groupId, String natureName, MainPreferences groupConfig) {
		Map<String, ResourceGroupNatureAssociation> assocMap = natureAssociations.get(groupId);
		if (assocMap != null) {
			ResourceGroupNatureAssociation assoc = assocMap.remove(natureName);
			if (assoc != null) {
				assoc.detach();
			}
			if (assocMap.isEmpty()) {
				natureAssociations.remove(groupId);
			}

			// also delete nature configuration
			MainPreferences naturePrefs = groupConfig.getChildNode("natures");
			if (naturePrefs != null && naturePrefs.getChildNode(natureName) != null) {
				MutablePreferences changePrefs = new SimplePreferences(null);
				ConfigUtil.copyPreferences(naturePrefs, changePrefs);
				changePrefs.removeChildNode(natureName);
				try {
					CloudManagerApp.getInstance().getConfigManager().applyConfig(changePrefs, naturePrefs);
				}
				catch (ConfigException e) {
					// should never occur - only log
					LoggerFactory.getLogger(ResourceGroupManagerImpl.class).error("Could not remove group nature", e);
				}
			}
		}
	}

	private void fireResourceGroupAdded(ResourceGroup resourceGroup) {
		List<ResourceGroupManagerListener> listeners;
		synchronized (this) {
			listeners = new ArrayList<ResourceGroupManagerListener>(this.listeners);
		}

		for (ResourceGroupManagerListener listener : listeners) {
			listener.resourceGroupAdded(resourceGroup);
		}
	}

	private void fireResourceGroupRemoved(ResourceGroup resourceGroup) {
		List<ResourceGroupManagerListener> listeners;
		synchronized (this) {
			listeners = new ArrayList<ResourceGroupManagerListener>(this.listeners);
		}

		for (ResourceGroupManagerListener listener : listeners) {
			listener.resourceGroupRemoved(resourceGroup);
		}
	}

	private Map<Integer, ResourceGroupMetadata> readMetadata(Preferences preferences) throws ConfigException {
		Preferences metaPrefs = preferences.getChildNode(METADATA_PREFS_NODE);
		if (metaPrefs == null) {
			return Collections.emptyMap();
		}

		Map<Integer, ResourceGroupMetadata> result = new HashMap<Integer, ResourceGroupMetadata>();

		for (String node : metaPrefs.getChildNodeNames()) {
			if (node.matches("[0-9]{1,10}")) {
				Integer id = Integer.valueOf(node);
				Preferences prefs = metaPrefs.getChildNode(node);

				String typeName = prefs.getStringValue(METADATA_TYPE_PREF_KEY);
				if (typeName == null) {
					continue;
				}

				ResourceModule module = getResourceModule(typeName);
				if (module == null) {
					throw new ConfigException("Unsupported resource type: " + typeName);
				}

				String natureJson = prefs.getStringValue(METADATA_NATURES_PREF_KEY);
				List<String> natures = Collections.emptyList();
				if (natureJson != null) {
					natures = jsonArrayToStringList(natureJson);
				}

				result.put(
						id,
						new ResourceGroupMetadata(prefs.getStringValue(METADATA_NAME_PREF_KEY), prefs
								.getIntValue(METADATA_RANK_PREF_KEY), module.getResourceType(), natures));
			}
		}

		return result;
	}

	/* MBean methods */
	@Override
	public List<Resource> getResourcesOfGroup(int groupId) {
		ResourceGroup group = getResourceGroup(groupId);
		if (group == null) {
			return null;
		}

		List<Resource> result = new ArrayList<Resource>();
		Iterator<? extends ResourceStateHolder> iter = group.getResourceCollection().iterator();
		while (iter.hasNext()) {
			ResourceStateHolder rsh = iter.next();
			if (rsh instanceof Resource) {
				result.add((Resource) rsh);
			}
		}

		return result;
	}

	private static List<String> jsonArrayToStringList(String jsonArraySource) {
		try {
			JSONArray arr = new JSONArray(jsonArraySource);
			List<String> result = new ArrayList<String>();
			for (int i = 0; i < arr.length(); i++) {
				result.add(arr.getString(i));
			}
			return result;
		}
		catch (JSONException e) {
			return Collections.emptyList();
		}
	}

	private class GroupManagerAdminImpl extends AbstractConfigurationAdmin implements ResourceGroupManagerAdmin {

		protected GroupManagerAdminImpl() {
			super(preferences);
		}

		@Override
		public void commit() throws ConfigException {
			cleanupRanks();
			super.commit();
		}

		@Override
		public void renameResourceGroup(int id, String newName) throws ConfigException {
			assertNotCommitted();
			if (newName == null || "".equals(newName)) {
				throw new ConfigException("Please specify a valid name for the group");
			}
			MutablePreferences metaPrefs = getPreferences().createChildNode(METADATA_PREFS_NODE);

			String groupNodeName = "" + id;

			setGroupName(metaPrefs, groupNodeName, newName);
		}

		@Override
		public int createResourceGroup(ResourceType resourceType, String name) throws ConfigException {
			assertNotCommitted();
			ResourceModule module = getResourceModule(resourceType.getName());
			if (module == null) {
				throw new IllegalArgumentException("Unsupported resource type: " + resourceType);
			}

			MutablePreferences metaPrefs = getPreferences().createChildNode(METADATA_PREFS_NODE);

			// find next free ID in Preferences
			int nextId = metaPrefs.getIntValue("nextGroupId");
			if (nextId == 0) {
				// classic way
				List<String> nodeNames = Arrays.asList(metaPrefs.getChildNodeNames());
				nextId = -1;
				for (int i = 1; nextId == -1; i++) {
					if (!nodeNames.contains("" + i)) {
						nextId = i;
					}
				}
			}

			// create group node and fill defaults
			ResourceGroup group = module.createResourceGroup();
			if (group instanceof Configurable) {
				MutablePreferences groupPrefs = getPreferences().createChildNode("" + nextId);
				((Configurable) group).fillDefaults(groupPrefs);
			}

			// calculate next rank
			int rank = metaPrefs.getChildNodeNames().length + 1;

			// assert group name does not yet exist
			setGroupName(metaPrefs, "" + nextId, name);
			metaPrefs.setValue("nextId", nextId + 1);

			metaPrefs = metaPrefs.createChildNode("" + nextId);
			metaPrefs.setValue(METADATA_TYPE_PREF_KEY, resourceType.getName());
			metaPrefs.setValue(METADATA_RANK_PREF_KEY, rank);

			return nextId;
		}

		@Override
		public void deleteResourceGroup(int id) {
			assertNotCommitted();
			String groupNode = "" + id;
			MutablePreferences metaPrefs = getPreferences().createChildNode(METADATA_PREFS_NODE);
			metaPrefs.removeChildNode(groupNode);
			getPreferences().removeChildNode(groupNode);
		}

		@Override
		public void moveGroup(int id, boolean up) {
			String groupNode = "" + id;

			// find group with next lower rank, if any
			List<String> nodes = getMetaNodesSortedByRank();

			MutablePreferences metaPrefs = getPreferences().createChildNode(METADATA_PREFS_NODE);

			int index = nodes.indexOf(groupNode);
			if (up && index > 0) {
				int rank1 = metaPrefs.getChildNode(nodes.get(index - 1)).getIntValue(METADATA_RANK_PREF_KEY);
				int rank2 = metaPrefs.getChildNode(groupNode).getIntValue(METADATA_RANK_PREF_KEY);
				metaPrefs.createChildNode(nodes.get(index - 1)).setValue(METADATA_RANK_PREF_KEY, rank2);
				metaPrefs.createChildNode(groupNode).setValue(METADATA_RANK_PREF_KEY, rank1);
			}
			else if (!up && index >= 0 && index < nodes.size() - 1) {
				int rank1 = metaPrefs.getChildNode(nodes.get(index + 1)).getIntValue(METADATA_RANK_PREF_KEY);
				int rank2 = metaPrefs.getChildNode(groupNode).getIntValue(METADATA_RANK_PREF_KEY);
				metaPrefs.createChildNode(nodes.get(index + 1)).setValue(METADATA_RANK_PREF_KEY, rank2);
				metaPrefs.createChildNode(groupNode).setValue(METADATA_RANK_PREF_KEY, rank1);
			}
		}

		@Override
		public void addGroupNature(int groupId, String natureName) {
			assertNotCommitted();

			ResourceGroupNature nature = getNature(natureName);
			if (nature == null) {
				throw new IllegalArgumentException("No group nature with name " + natureName + " found.");
			}

			MutablePreferences metaPrefs = getPreferences().createChildNode(METADATA_PREFS_NODE);
			String groupNodeName = "" + groupId;

			MutablePreferences groupPrefs = metaPrefs.createChildNode(groupNodeName);
			String oldJsonValue = groupPrefs.getStringValue(METADATA_NATURES_PREF_KEY);
			List<String> natures = new ArrayList<String>();
			if (oldJsonValue != null) {
				natures.addAll(jsonArrayToStringList(oldJsonValue));
			}
			if (!natures.contains(natureName)) {
				natures.add(natureName);
				groupPrefs.setValue(METADATA_NATURES_PREF_KEY, new JSONArray(natures).toString());
			}
		}

		@Override
		public void removeGroupNature(int groupId, String natureName) {
			assertNotCommitted();

			ResourceGroupNature nature = getNature(natureName);
			if (nature == null) {
				throw new IllegalArgumentException("No group nature with name " + natureName + " found.");
			}

			MutablePreferences metaPrefs = getPreferences().createChildNode(METADATA_PREFS_NODE);

			String groupNodeName = "" + groupId;

			MutablePreferences groupPrefs = metaPrefs.createChildNode(groupNodeName);
			String oldJsonValue = groupPrefs.getStringValue(METADATA_NATURES_PREF_KEY);
			List<String> natures = new ArrayList<String>();
			if (oldJsonValue != null) {
				natures.addAll(jsonArrayToStringList(oldJsonValue));
			}
			if (natures.contains(natureName)) {
				natures.remove(natureName);
				groupPrefs.setValue(METADATA_NATURES_PREF_KEY, new JSONArray(natures).toString());
			}
		}

		@Override
		public List<String> getGroupNatures(int groupId) {
			assertNotCommitted();

			MutablePreferences metaPrefs = getPreferences().createChildNode(METADATA_PREFS_NODE);

			String groupNodeName = "" + groupId;

			MutablePreferences groupPrefs = metaPrefs.createChildNode(groupNodeName);
			String jsonValue = groupPrefs.getStringValue(METADATA_NATURES_PREF_KEY);
			List<String> natures = new ArrayList<String>();
			if (jsonValue != null) {
				natures.addAll(jsonArrayToStringList(jsonValue));
			}

			return natures;
		}

		@Override
		protected void validateConfig(Preferences preferences) throws ConfigException {
			validateConfiguration(preferences);
		}

		private void setGroupName(MutablePreferences metaPrefs, String groupNode, String newName) throws ConfigException {
			// check for an already existing group with same name
			for (String nodeName : metaPrefs.getChildNodeNames()) {
				if (!nodeName.equals(groupNode)) {
					String groupName = metaPrefs.getChildNode(nodeName).getStringValue(METADATA_NAME_PREF_KEY);
					if (newName.equals(groupName)) {
						throw new ConfigException("A group with the same name already exists.");
					}
				}
			}

			metaPrefs = metaPrefs.createChildNode(groupNode);
			metaPrefs.setValue(METADATA_NAME_PREF_KEY, newName);
		}

		private void cleanupRanks() {
			List<String> sortedIds = getMetaNodesSortedByRank();

			MutablePreferences metaPrefs = getPreferences().createChildNode(METADATA_PREFS_NODE);
			for (int i = 0; i < sortedIds.size(); i++) {
				metaPrefs.createChildNode(sortedIds.get(i)).setValue(METADATA_RANK_PREF_KEY, i + 1);
			}
		}

		private List<String> getMetaNodesSortedByRank() {
			final MutablePreferences metaPrefs = getPreferences().createChildNode(METADATA_PREFS_NODE);

			List<String> sortedIds = new ArrayList<String>(Arrays.asList(metaPrefs.getChildNodeNames()));
			Collections.sort(sortedIds, new Comparator<String>() {
				@Override
				public int compare(String s1, String s2) {
					int rank1 = metaPrefs.getChildNode(s1).getIntValue(METADATA_RANK_PREF_KEY);
					int rank2 = metaPrefs.getChildNode(s2).getIntValue(METADATA_RANK_PREF_KEY);

					return rank1 - rank2;
				}
			});

			return sortedIds;
		}
	}

	private static class ResourceGroupMetadata {

		private String name;

		private int rank;

		private ResourceType resourceType;

		private List<String> natures;

		public ResourceGroupMetadata(String name, int rank, ResourceType resourceType, List<String> natures) {
			this.name = name;
			this.rank = rank;
			this.resourceType = resourceType;
			this.natures = natures;
		}

		public String getName() {
			return name;
		}

		public int getRank() {
			return rank;
		}

		public ResourceType getResourceType() {
			return resourceType;
		}

		public List<String> getNatures() {
			return natures;
		}

	}

	private PreferencesListener preferencesListener = new PreferencesListener() {
		@Override
		public void preferencesChanged(Preferences oldPreferences, MainPreferences newPreferences) throws ConfigException {
			configure(newPreferences);
		}

		@Override
		public void preferencesAboutToChange(Preferences oldPreferences, Preferences newPreferences) throws ConfigException {
			validateConfiguration(newPreferences);
		}
	};

}
