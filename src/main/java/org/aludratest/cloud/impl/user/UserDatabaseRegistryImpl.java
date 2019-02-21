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

import java.util.Collections;
import java.util.List;

import org.aludratest.cloud.user.UserDatabase;
import org.aludratest.cloud.user.admin.UserDatabaseRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Default implementation of the <code>UserDatabaseRegistry</code> interface.
 *
 * @author falbrech
 *
 */
@Component
public final class UserDatabaseRegistryImpl implements UserDatabaseRegistry {

	private List<UserDatabase> userDatabases;

	private volatile UserDatabase selectedDatabase;

	@Autowired
	public UserDatabaseRegistryImpl(List<UserDatabase> userDatabases) {
		this.userDatabases = Collections.unmodifiableList(userDatabases);
	}

	@Override
	public List<? extends UserDatabase> getAllUserDatabases() {
		return userDatabases;
	}

	@Override
	public UserDatabase getUserDatabase(String sourceName) {
		return userDatabases.stream().filter(ud -> ud.getSource().equals(sourceName)).findFirst().orElse(null);
	}

	@Override
	public UserDatabase getSelectedUserDatabase() {
		return selectedDatabase;
	}

	public void setSelectedUserDatabase(UserDatabase userDatabase) {
		this.selectedDatabase = userDatabase;
	}

}
