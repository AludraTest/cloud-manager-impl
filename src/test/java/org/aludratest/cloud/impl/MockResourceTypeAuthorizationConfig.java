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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.aludratest.cloud.resource.user.ResourceTypeAuthorization;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationConfig;
import org.aludratest.cloud.user.User;

public class MockResourceTypeAuthorizationConfig implements ResourceTypeAuthorizationConfig {

	private Map<User, ResourceTypeAuthorization> authorizations = new HashMap<>();

	@Override
	public ResourceTypeAuthorization getResourceTypeAuthorizationForUser(User user) {
		return authorizations.get(user);
	}

	@Override
	public List<User> getConfiguredUsers() {
		return authorizations.keySet().stream().collect(Collectors.toList());
	}

	public void addAuthorization(User user, int maxResources, int niceLevel) {
		authorizations.put(user, new MockResourceTypeAuthorization(maxResources, niceLevel));
	}

}
