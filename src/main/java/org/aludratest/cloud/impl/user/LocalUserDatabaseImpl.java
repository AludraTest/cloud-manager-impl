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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.aludratest.cloud.app.CloudManagerAppFileStore;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;
import org.aludratest.cloud.user.UserDatabase;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.databene.commons.Filter;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Implementation of a user database based on a file <code><i>&lt;acm.filestore.path&gt;</i>/users.json</code>. This
 * implementation is quite simple and straightforward; its performance will decrease with an increasing number of users.
 *
 * @author falbrech
 *
 */
@Component
@Qualifier(LocalUserDatabaseImpl.HINT)
public class LocalUserDatabaseImpl implements UserDatabase {

	private CloudManagerAppFileStore fileStore;

	/**
	 * Qualifier of this component.
	 */
	public static final String HINT = "local-file";

	public static final String ADMIN_ATTRIBUTE = "isAdmin";

	private static final String DB_FILE = "users.json";

	private static final String USERS_KEY = "users";

	private static final String ATTRIBUTES_KEY = "attributes";

	private static final String PASSWORD_HASH_KEY = "passwordHash";

	@Autowired
	public LocalUserDatabaseImpl(CloudManagerAppFileStore fileStore) {
		this.fileStore = fileStore;
	}

	@Override
	public String getSource() {
		return HINT;
	}

	@Override
	public Iterator<User> getAllUsers(Filter<User> userFilter) throws StoreException {
		JSONObject contents = load();

		try {
			List<User> result = new ArrayList<User>();
			JSONObject users = contents.getJSONObject(USERS_KEY);
			Iterator<?> iter = users.keys();

			while (iter.hasNext()) {
				String userName = iter.next().toString();
				JSONObject o = users.getJSONObject(userName);
				LocalUserImpl user = new LocalUserImpl(userName, toMap(o.getJSONObject(ATTRIBUTES_KEY)));

				if (userFilter == null || userFilter.accept(user)) {
					result.add(user);
				}
			}

			return result.iterator();
		}
		catch (JSONException e) {
			throw new StoreException("Unexpected format expection", e);
		}
	}

	@Override
	public User authenticate(String userName, String password) throws StoreException {
		JSONObject contents = load();

		try {
			JSONObject users = contents.getJSONObject(USERS_KEY);
			if (!users.has(userName)) {
				return null;
			}
			JSONObject userObject = users.getJSONObject(userName);
			String hash = calculateHash(userName, password);
			if (userObject.optString(PASSWORD_HASH_KEY, "$").equals(hash)) {
				return new LocalUserImpl(userName, toMap(userObject.getJSONObject(ATTRIBUTES_KEY)));
			}
		}
		catch (JSONException e) {
			throw new StoreException("Unexpected format expection", e);
		}

		return null;
	}

	@Override
	public User findUser(String userName) throws StoreException {
		JSONObject contents = load();

		try {
			JSONObject users = contents.getJSONObject(USERS_KEY);
			if (!users.has(userName)) {
				return null;
			}

			JSONObject o = users.getJSONObject(userName);
			return new LocalUserImpl(userName, toMap(o.getJSONObject(ATTRIBUTES_KEY)));
		}
		catch (JSONException e) {
			throw new StoreException("Unexpected format expection", e);
		}
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	@Override
	public void delete(User user) throws StoreException {
		if (!user.getSource().equals(getSource())) {
			throw new StoreException("Unsupported user object: Source is " + user.getSource() + " instead of " + getSource());
		}
		JSONObject contents = load();

		try {
			JSONObject users = contents.getJSONObject(USERS_KEY);
			if (users.has(user.getName())) {
				users.remove(user.getName());
				save(contents);
			}
		}
		catch (JSONException e) {
			throw new StoreException("Unexpected format expection", e);
		}
	}

	@Override
	public User create(String userName) throws IllegalArgumentException, StoreException {
		JSONObject contents = load();

		try {
			JSONObject users = contents.getJSONObject(USERS_KEY);
			if (users.has(userName)) {
				throw new IllegalArgumentException("User " + userName + " does already exist");
			}

			JSONObject newUserObject = new JSONObject();
			newUserObject.put(PASSWORD_HASH_KEY, calculateHash(userName, "password"));
			newUserObject.put(ATTRIBUTES_KEY, new JSONObject());
			users.put(userName, newUserObject);

			save(contents);

			return new LocalUserImpl(userName, new HashMap<String, String>());
		}
		catch (JSONException e) {
			throw new StoreException("Unexpected format expection", e);
		}
	}

	@Override
	public void changePassword(User user, String newPassword) throws StoreException {
		if (!user.getSource().equals(getSource())) {
			throw new StoreException("Unsupported user object: Source is " + user.getSource() + " instead of " + getSource());
		}

		JSONObject contents = load();

		try {
			JSONObject users = contents.getJSONObject(USERS_KEY);
			if (!users.has(user.getName())) {
				throw new IllegalArgumentException("User " + user.getName() + " does not exist");
			}

			JSONObject userObject = users.getJSONObject(user.getName());
			userObject.put(PASSWORD_HASH_KEY, calculateHash(user.getName(), newPassword));

			save(contents);
		}
		catch (JSONException e) {
			throw new StoreException("Unexpected format expection", e);
		}
	}

	@Override
	public void modifyUserAttribute(User user, String attributeKey, String newAttributeValue) throws StoreException {
		if (!user.getSource().equals(getSource())) {
			throw new StoreException("Unsupported user object: Source is " + user.getSource() + " instead of " + getSource());
		}

		JSONObject contents = load();

		try {
			JSONObject users = contents.getJSONObject(USERS_KEY);
			if (!users.has(user.getName())) {
				throw new IllegalArgumentException("User " + user.getName() + " does not exist");
			}

			JSONObject userObject = users.getJSONObject(user.getName());
			JSONObject attributes = userObject.getJSONObject(ATTRIBUTES_KEY);
			if (attributes == null) {
				attributes = new JSONObject();
			}
			if (newAttributeValue == null) {
				attributes.remove(attributeKey);
			}
			else {
				attributes.put(attributeKey, newAttributeValue);
			}

			userObject.put(ATTRIBUTES_KEY, attributes);

			save(contents);
		}
		catch (JSONException e) {
			throw new StoreException("Unexpected format expection", e);
		}
	}

	@Override
	public boolean supportsUserAttribute(String attributeKey) {
		// we do support ALL user attributes, as we store them in JSON map
		return true;
	}

	@Override
	public boolean canChangeAdminFlag() {
		return true;
	}

	@Override
	public void setAdminFlag(User user, boolean isAdmin) throws StoreException {
		modifyUserAttribute(user, ADMIN_ATTRIBUTE, Boolean.toString(isAdmin));
	}

	/**
	 * This method is protected only for unit test classes (otherwise, would be private).
	 *
	 * @return Loaded contents.
	 *
	 * @throws StoreException
	 *             If contents could not be stored.
	 */
	protected JSONObject load() throws StoreException {
		if (!fileStore.existsFile(DB_FILE)) {
			// create and initialize default store with default admin user
			JSONObject defaultContents = createDefaultContents();
			save(defaultContents);
			return defaultContents;
		}

		try (InputStream in = fileStore.openFile(DB_FILE)) {
			return new JSONObject(IOUtils.toString(in, "UTF-8"));
		}
		catch (IOException e) {
			throw new StoreException("Could not load local user database", e);
		}
		catch (JSONException e) {
			throw new StoreException("Local user database " + DB_FILE + " is corrupt.");
		}
	}

	private JSONObject createDefaultContents() {
		JSONObject defaultContents = new JSONObject();

		JSONObject users = new JSONObject();

		JSONObject admin = new JSONObject();
		admin.put(PASSWORD_HASH_KEY, calculateHash("admin", "admin123"));

		JSONObject attrs = new JSONObject();
		attrs.put(ADMIN_ATTRIBUTE, "true");
		admin.put(ATTRIBUTES_KEY, attrs);
		users.put("admin", admin);

		defaultContents.put(USERS_KEY, users);
		return defaultContents;
	}

	/**
	 * This method is protected only for unit test classes (otherwise, would be private).
	 *
	 * @param contents
	 *            Contents to store.
	 *
	 * @throws StoreException
	 *             If contents could not be stored.
	 */
	protected void save(JSONObject contents) throws StoreException {
		try (ByteArrayInputStream data = new ByteArrayInputStream(contents.toString().getBytes("UTF-8"))) {
			fileStore.saveFile(DB_FILE, data);
		}
		catch (IOException e) {
			throw new StoreException("Could not write local user database", e);
		}
	}

	private static Map<String, String> toMap(JSONObject obj) throws JSONException {
		Map<String, String> result = new HashMap<String, String>();

		Iterator<?> keys = obj.keys();
		while (keys.hasNext()) {
			Object o = keys.next();
			Object val = obj.get(o.toString());
			if (val instanceof String) {
				result.put(o.toString(), val.toString());
			}
		}

		return result;
	}

	private static String calculateHash(String userName, String password) {
		try {
			// calculate SHA256 hash
			MessageDigest crypt = MessageDigest.getInstance("SHA-256");
			crypt.reset();
			crypt.update((userName + "/" + password).getBytes("UTF-8"));
			byte[] data = crypt.digest();

			// encode as BASE64
			return new String(Base64.encodeBase64(data), "UTF-8");
		}
		catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
			// no UTF-8?? No SHA-256?
			throw new RuntimeException(e);
		}
	}
}
