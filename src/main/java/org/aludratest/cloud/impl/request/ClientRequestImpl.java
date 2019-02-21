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
package org.aludratest.cloud.impl.request;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.aludratest.cloud.request.ResourceRequest;
import org.aludratest.cloud.resource.ResourceType;
import org.aludratest.cloud.user.User;

/**
 * Default implementation of the <code>ResourceRequest</code> interface. Additionally to the attributes defined by the interface,
 * it also carries the used Request ID.
 * 
 * @author falbrech
 * 
 */
public class ClientRequestImpl implements ResourceRequest {

	private String requestId;

	private User user;

	private String jobName;

	private ResourceType resourceType;

	private int niceLevel;

	private Map<String, Object> customAttributes;

	/**
	 * Constructs a new object of this class.
	 * 
	 * @param requestId
	 *            Request ID.
	 * @param user
	 *            User submitting the request.
	 * @param resourceType
	 *            Requested resource type.
	 * @param niceLevel
	 *            Request-specific nice level (only has effect within all requests of the same user)
	 * @param jobName
	 *            User-specific job name this request belongs to, may be <code>null</code>.
	 * @param customAttributes
	 *            Custom request attributes.
	 */
	public ClientRequestImpl(String requestId, User user, ResourceType resourceType, int niceLevel, String jobName,
			Map<String, ?> customAttributes) {
		this.requestId = requestId;
		this.user = user;
		this.resourceType = resourceType;
		this.niceLevel = niceLevel;
		this.jobName = jobName;
		this.customAttributes = Collections.unmodifiableMap(new HashMap<String, Object>(customAttributes));
	}

	/**
	 * Returns the assigned request ID.
	 * 
	 * @return The assigned request ID.
	 */
	public String getRequestId() {
		return requestId;
	}

	@Override
	public User getRequestingUser() {
		return user;
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
		return customAttributes;
	}

	@Override
	public String toString() {
		return requestId + " (user: " + user.getName() + ", resourceType: " + resourceType.getName() + ", jobName: " + jobName
				+ ", niceLevel: " + niceLevel + ")";
	}

	@Override
	public int hashCode() {
		return requestId.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		if (obj.getClass() != getClass()) {
			return false;
		}

		// only compare request IDs
		return requestId.equals(((ClientRequestImpl) obj).requestId);
	}

}
