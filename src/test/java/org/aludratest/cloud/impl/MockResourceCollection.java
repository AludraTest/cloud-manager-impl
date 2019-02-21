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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.aludratest.cloud.resource.Resource;
import org.aludratest.cloud.resource.ResourceCollection;
import org.aludratest.cloud.resource.ResourceCollectionListener;

public class MockResourceCollection<R extends Resource> implements ResourceCollection<R> {

	private Set<R> resources = new HashSet<>();

	private List<ResourceCollectionListener> listeners = new ArrayList<>();

	public void add(R resource) {
		resources.add(resource);
		fireResourceAdded(resource);
	}

	public void remove(R resource) {
		if (resources.contains(resource)) {
			resources.remove(resource);
			fireResourceRemoved(resource);
		}
	}

	@Override
	public Iterator<R> iterator() {
		return resources.iterator();
	}

	@Override
	public void addResourceCollectionListener(ResourceCollectionListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeResourceCollectionListener(ResourceCollectionListener listener) {
		listeners.remove(listener);
	}

	protected void fireResourceAdded(R resource) {
		for (ResourceCollectionListener l : new ArrayList<>(listeners)) {
			l.resourceAdded(resource);
		}
	}

	protected void fireResourceRemoved(R resource) {
		for (ResourceCollectionListener l : new ArrayList<>(listeners)) {
			l.resourceRemoved(resource);
		}
	}

	@Override
	public int getResourceCount() {
		return resources.size();
	}

	@Override
	public boolean contains(Resource resource) {
		return resources.contains(resource);
	}

}
