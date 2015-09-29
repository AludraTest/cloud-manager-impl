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

import java.io.Serializable;
import java.util.Map;

import org.aludratest.cloud.user.User;

/**
 * Implementation of a user object to use with the {@link LocalUserDatabaseImpl} implementation.
 * 
 * @author falbrech
 * 
 */
public final class LocalUserImpl implements User, Comparable<User>, Serializable {

	private static final long serialVersionUID = -213722482438496383L;

	private String name;

	private Map<String, String> attributes;

	LocalUserImpl(String name, Map<String, String> attributes) {
		this.name = name;
		this.attributes = attributes;
	}

	void setAttributes(Map<String, String> attributes) {
		this.attributes = attributes;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String[] getDefinedUserAttributes() {
		return attributes == null ? new String[0] : attributes.keySet().toArray(new String[0]);
	}

	@Override
	public String getUserAttribute(String attributeKey) {
		return attributes == null ? null : attributes.get(attributeKey);
	}

	@Override
	public String getSource() {
		return LocalUserDatabaseImpl.HINT;
	}

	@Override
	public int compareTo(User o) {
		return getName().compareToIgnoreCase(o.getName());
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != getClass()) {
			return false;
		}

		LocalUserImpl user = (LocalUserImpl) obj;
		return user.getName().equals(name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return name;
	}

}
