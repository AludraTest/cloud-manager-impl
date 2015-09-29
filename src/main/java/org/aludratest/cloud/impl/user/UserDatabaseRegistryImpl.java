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
