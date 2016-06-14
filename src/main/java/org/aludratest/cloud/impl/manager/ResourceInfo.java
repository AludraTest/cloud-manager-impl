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
package org.aludratest.cloud.impl.manager;

import org.aludratest.cloud.resource.Resource;

@SuppressWarnings("javadoc")
public class ResourceInfo implements ResourceInfoMBean {

	private Resource resource;

	public ResourceInfo() {
	}

	public static ResourceInfo create(Resource res) {
		ResourceInfo info = new ResourceInfo();
		info.resource = res;
		return info;
	}

	@Override
	public String getResourceState() {
		return resource.getState().toString();
	}

	@Override
	public String getResourceType() {
		return resource.getResourceType().getName();
	}

	@Override
	public String getStringRepresentation() {
		return resource.toString();
	}

}
