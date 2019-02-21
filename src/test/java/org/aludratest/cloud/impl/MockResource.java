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

import org.aludratest.cloud.resource.AbstractUsableResource;
import org.aludratest.cloud.resource.ResourceState;
import org.aludratest.cloud.resource.ResourceType;

public class MockResource extends AbstractUsableResource {

	private ResourceType resourceType;

	private ResourceState state = ResourceState.DISCONNECTED;

	private int index;

	public MockResource(final String resourceType, int index) {
		this.resourceType = new ResourceType() {
			@Override
			public String getName() {
				return resourceType;
			}
		};
		this.index = index;
	}

	@Override
	public ResourceType getResourceType() {
		return resourceType;
	}

	@Override
	public ResourceState getState() {
		return state;
	}

	public void setState(ResourceState state) {
		if (this.state != state) {
			ResourceState oldState = this.state;
			this.state = state;
			fireResourceStateChanged(oldState, state);
		}
	}

	public void fireOrphaned() {
		fireResourceOrphaned();
	}

	@Override
	public void startUsing() {
		setState(ResourceState.IN_USE);
	}

	@Override
	public void stopUsing() {
		setState(ResourceState.READY);
	}

	@Override
	public String toString() {
		return "MockResource #" + index + " [" + state + "]";
	}

}
