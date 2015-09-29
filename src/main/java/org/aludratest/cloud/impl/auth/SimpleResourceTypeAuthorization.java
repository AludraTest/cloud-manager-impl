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
package org.aludratest.cloud.impl.auth;

import org.aludratest.cloud.resource.user.ResourceTypeAuthorization;

/**
 * Simple default implementation of the <code>ResourceTypeAuthorization</code> interface.
 * 
 * @author falbrech
 * 
 */
public class SimpleResourceTypeAuthorization implements ResourceTypeAuthorization {

	private int maxResources;

	private int niceLevel;

	/**
	 * Creates a new authorization object.
	 * 
	 * @param maxResources
	 *            Maximum number of allowed resources.
	 * @param niceLevel
	 *            Default nice level for the user.
	 */
	public SimpleResourceTypeAuthorization(int maxResources, int niceLevel) {
		this.maxResources = maxResources;
		this.niceLevel = niceLevel;
	}

	@Override
	public int getMaxResources() {
		return maxResources;
	}

	@Override
	public int getNiceLevel() {
		return niceLevel;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (obj.getClass() != getClass()) {
			return false;
		}

		SimpleResourceTypeAuthorization auth = (SimpleResourceTypeAuthorization) obj;
		return (auth.maxResources == maxResources && auth.niceLevel == niceLevel);
	}

	@Override
	public int hashCode() {
		return maxResources + niceLevel;
	}

}
