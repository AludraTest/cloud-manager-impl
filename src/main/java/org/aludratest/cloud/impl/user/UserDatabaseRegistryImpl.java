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
import java.util.List;
import java.util.Map;

import org.aludratest.cloud.user.UserDatabase;
import org.aludratest.cloud.user.admin.UserDatabaseRegistry;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

/**
 * Default implementation of the <code>UserDatabaseRegistry</code> interface.
 * 
 * @author falbrech
 * 
 */
@Component(role = UserDatabaseRegistry.class)
public final class UserDatabaseRegistryImpl implements UserDatabaseRegistry {

	@Requirement(role = UserDatabase.class)
	private Map<String, UserDatabase> userDatabases;

	@Override
	public List<UserDatabase> getAllUserDatabases() {
		return new ArrayList<UserDatabase>(userDatabases.values());
	}

	@Override
	public UserDatabase getUserDatabase(String sourceName) {
		return userDatabases.get(sourceName);
	}

}
