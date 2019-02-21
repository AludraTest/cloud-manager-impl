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
package org.aludratest.cloud.impl.module;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aludratest.cloud.module.ResourceModule;
import org.aludratest.cloud.module.ResourceModuleRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResourceModuleRegistryImpl implements ResourceModuleRegistry {

	private Set<ResourceModule> resourceModules;

	@Autowired
	public ResourceModuleRegistryImpl(List<ResourceModule> resourceModules) {
		this.resourceModules = Collections.unmodifiableSet(new HashSet<>(resourceModules));
	}

	@Override
	public Set<? extends ResourceModule> getAllResourceModules() {
		return resourceModules;
	}

}
