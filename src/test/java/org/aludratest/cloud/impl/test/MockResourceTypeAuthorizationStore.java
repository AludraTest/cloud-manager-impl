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
package org.aludratest.cloud.impl.test;

import java.util.HashMap;
import java.util.Map;

import org.aludratest.cloud.impl.MockResourceTypeAuthorizationConfig;
import org.aludratest.cloud.resource.ResourceType;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationConfig;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationStore;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;
import org.springframework.stereotype.Component;

@Component
public class MockResourceTypeAuthorizationStore implements ResourceTypeAuthorizationStore {

	private Map<String, MockResourceTypeAuthorizationConfig> authConfigs = new HashMap<>();

	@Override
	public ResourceTypeAuthorizationConfig loadResourceTypeAuthorizations(ResourceType resourceType) throws StoreException {
		return authConfigs.get(resourceType.getName());
	}

	@Override
	public void saveResourceTypeAuthorizations(ResourceType resourceType, ResourceTypeAuthorizationConfig authorizations)
			throws StoreException {
		// no-op
	}

	public void addAuthorization(User user, String resourceType, int maxResources, int niceLevel) {
		MockResourceTypeAuthorizationConfig authConfig = authConfigs.getOrDefault(resourceType,
				new MockResourceTypeAuthorizationConfig());
		authConfig.addAuthorization(user, maxResources, niceLevel);
		authConfigs.put(resourceType, authConfig);
	}

}
