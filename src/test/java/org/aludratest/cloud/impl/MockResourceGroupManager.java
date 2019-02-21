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
package org.aludratest.cloud.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.aludratest.cloud.resourcegroup.ResourceGroup;
import org.aludratest.cloud.resourcegroup.ResourceGroupManager;
import org.aludratest.cloud.resourcegroup.ResourceGroupManagerListener;
import org.aludratest.cloud.resourcegroup.ResourceGroupNature;
import org.aludratest.cloud.resourcegroup.ResourceGroupNatureAssociation;

public class MockResourceGroupManager implements ResourceGroupManager {

	private List<ResourceGroup> resourceGroups = new ArrayList<>();

	private List<ResourceGroupManagerListener> listeners = new ArrayList<>();

	public void addResourceGroup(ResourceGroup resourceGroup) {
		resourceGroups.add(resourceGroup);
		fireResourceGroupAdded(resourceGroup);
	}

	public void removeResourceGroup(ResourceGroup resourceGroup) {
		resourceGroups.remove(resourceGroup);
		fireResourceGroupRemoved(resourceGroup);
	}

	@Override
	public int[] getAllResourceGroupIds() {
		int[] result = new int[resourceGroups.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = i;
		}
		return result;
	}

	@Override
	public ResourceGroup getResourceGroup(int groupId) {
		if (groupId >= 0 && groupId < resourceGroups.size()) {
			return resourceGroups.get(groupId);
		}
		return null;
	}

	@Override
	public String getResourceGroupName(int groupId) {
		if (getResourceGroup(groupId) != null) {
			return "Group " + groupId;
		}
		return null;
	}

	@Override
	public List<ResourceGroupNature> getAvailableNaturesFor(int groupId) {
		return Collections.emptyList();
	}

	@Override
	public ResourceGroupNatureAssociation getNatureAssociation(int groupId, String nature) {
		return null;
	}

	@Override
	public void addResourceGroupManagerListener(ResourceGroupManagerListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeResourceGroupManagerListener(ResourceGroupManagerListener listener) {
		listeners.add(listener);
	}

	protected void fireResourceGroupAdded(ResourceGroup resourceGroup) {
		for (ResourceGroupManagerListener l : new ArrayList<>(listeners)) {
			l.resourceGroupAdded(resourceGroup);
		}
	}

	protected void fireResourceGroupRemoved(ResourceGroup resourceGroup) {
		for (ResourceGroupManagerListener l : new ArrayList<>(listeners)) {
			l.resourceGroupRemoved(resourceGroup);
		}
	}
}
