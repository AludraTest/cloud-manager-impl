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

import java.util.HashMap;
import java.util.Map;

import org.aludratest.cloud.request.ResourceRequest;
import org.aludratest.cloud.resource.ResourceType;
import org.aludratest.cloud.user.User;

public class MockResourceRequest implements ResourceRequest {

	private User requestingUser;

	private ResourceType resourceType;

	private int niceLevel;

	private String jobName;

	private Map<String, Object> attributes = new HashMap<>();

	public MockResourceRequest(User requestingUser, ResourceType resourceType, int niceLevel, String jobName) {
		this.requestingUser = requestingUser;
		this.resourceType = resourceType;
		this.niceLevel = niceLevel;
		this.jobName = jobName;
	}

	public void setAttribute(String name, Object value) {
		attributes.put(name, value);
	}

	@Override
	public User getRequestingUser() {
		return requestingUser;
	}

	@Override
	public ResourceType getResourceType() {
		return resourceType;
	}

	@Override
	public int getNiceLevel() {
		return niceLevel;
	}

	@Override
	public String getJobName() {
		return jobName;
	}

	@Override
	public Map<String, Object> getCustomAttributes() {
		return attributes;
	}

}
