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

import org.aludratest.cloud.resource.Resource;
import org.aludratest.cloud.resource.ResourceCollection;
import org.aludratest.cloud.resource.ResourceStateHolder;
import org.aludratest.cloud.resource.ResourceType;
import org.aludratest.cloud.resourcegroup.ResourceGroup;

public class MockResourceGroup<R extends Resource> implements ResourceGroup {

	private ResourceType resourceType;

	private MockResourceCollection<R> resourceCollection = new MockResourceCollection<>();

	public MockResourceGroup(ResourceType resourceType) {
		this.resourceType = resourceType;
	}

	public void add(R resource) {
		resourceCollection.add(resource);
	}

	public void remove(R resource) {
		resourceCollection.remove(resource);
	}

	@Override
	public ResourceType getResourceType() {
		return resourceType;
	}

	@Override
	public ResourceCollection<? extends ResourceStateHolder> getResourceCollection() {
		return resourceCollection;
	}

}

