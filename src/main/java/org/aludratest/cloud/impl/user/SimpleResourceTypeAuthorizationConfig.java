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
package org.aludratest.cloud.impl.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aludratest.cloud.resource.user.MutableResourceTypeAuthorizationConfig;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorization;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationConfig;
import org.aludratest.cloud.user.User;

/**
 * Simple memory-based implementation of the mutable authorization configuration interface.
 * 
 * @author falbrech
 * 
 */
public class SimpleResourceTypeAuthorizationConfig implements MutableResourceTypeAuthorizationConfig {

	private Map<User, ResourceTypeAuthorization> userAuthorizations = new HashMap<User, ResourceTypeAuthorization>();

	/**
	 * Constructs a new, empty authorization configuration.
	 */
	public SimpleResourceTypeAuthorizationConfig() {
	}

	/**
	 * Constructs a new authorization configuration based on the given configuration object.
	 * 
	 * @param original
	 *            Configuration object to copy initial values from.
	 */
	public SimpleResourceTypeAuthorizationConfig(ResourceTypeAuthorizationConfig original) {
		for (User u : original.getConfiguredUsers()) {
			userAuthorizations.put(u, original.getResourceTypeAuthorizationForUser(u));
		}
	}

	@Override
	public ResourceTypeAuthorization getResourceTypeAuthorizationForUser(User user) {
		return userAuthorizations.get(user);
	}

	@Override
	public List<User> getConfiguredUsers() {
		return new ArrayList<User>(userAuthorizations.keySet());
	}

	@Override
	public void addUser(User user, ResourceTypeAuthorization authorization) throws IllegalArgumentException {
		if (userAuthorizations.containsKey(user)) {
			throw new IllegalArgumentException("Authorization for user " + user + " already set");
		}
		userAuthorizations.put(user, authorization);
	}

	@Override
	public void removeUser(User user) throws IllegalArgumentException {
		if (!userAuthorizations.containsKey(user)) {
			throw new IllegalArgumentException("No authorization for user " + user + " found.");
		}
		userAuthorizations.remove(user);
	}

	@Override
	public void editUserAuthorization(User user, ResourceTypeAuthorization newAuthorization) throws IllegalArgumentException {
		removeUser(user);
		addUser(user, newAuthorization);
	}

}
