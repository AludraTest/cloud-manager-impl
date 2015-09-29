package org.aludratest.cloud.impl.user;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.aludratest.cloud.impl.ImplConstants;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;
import org.aludratest.cloud.user.UserDatabase;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.component.annotations.Component;
import org.databene.commons.Filter;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Implementation of a user database based on a file <code>~/.atcloudmanager/users.json</code>. This implementation is quite
 * simple and straightforward; its performance will decrease with an increasing number of users.
 * 
 * @author falbrech
 * 
 */
@Component(role = UserDatabase.class, hint = LocalUserDatabaseImpl.HINT)
public class LocalUserDatabaseImpl implements UserDatabase {

	/**
	 * Plexus Hint of this component.
	 */
	public static final String HINT = "local-file";
	
	private static final String DB_FILE = ImplConstants.CONFIG_DIR_NAME + "/users.json";

	@Override
	public String getSource() {
		return HINT;
	}

	@Override
	public Iterator<User> getAllUsers(Filter<User> userFilter) throws StoreException {
		JSONObject contents = load();

		try {
			List<User> result = new ArrayList<User>();
			JSONObject users = contents.getJSONObject("users");
			Iterator<?> iter = users.keys();

			while (iter.hasNext()) {
				String userName = iter.next().toString();
				JSONObject o = users.getJSONObject(userName);
				LocalUserImpl user = new LocalUserImpl(userName, toMap(o.getJSONObject("attributes")));

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
			JSONObject users = contents.getJSONObject("users");
			if (!users.has(userName)) {
				return null;
			}
			JSONObject userObject = users.getJSONObject(userName);
			String hash = calculateHash(userName, password);
			if (userObject.getString("passwordHash").equals(hash)) {
				return new LocalUserImpl(userName, toMap(userObject.getJSONObject("attributes")));
			}
		}
		catch (JSONException e) {
			throw new StoreException("Unexpected format expection", e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new StoreException("SHA-1 algorithm is unsupported on this machine");
		}

		return null;
	}

	@Override
	public User findUser(String userName) throws StoreException {
		JSONObject contents = load();

		try {
			JSONObject users = contents.getJSONObject("users");
			if (!users.has(userName)) {
				return null;
			}

			JSONObject o = users.getJSONObject(userName);
			return new LocalUserImpl(userName, toMap(o.getJSONObject("attributes")));
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
			JSONObject users = contents.getJSONObject("users");
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
			JSONObject users = contents.getJSONObject("users");
			if (users.has(userName)) {
				throw new IllegalArgumentException("User " + userName + " does already exist");
			}

			JSONObject newUserObject = new JSONObject();
			newUserObject.put("passwordHash", calculateHash(userName, "password"));
			newUserObject.put("attributes", new JSONObject());
			users.put(userName, newUserObject);

			save(contents);

			return new LocalUserImpl(userName, new HashMap<String, String>());
		}
		catch (JSONException e) {
			throw new StoreException("Unexpected format expection", e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new StoreException("SHA-1 algorithm is unsupported on this machine");
		}
	}

	@Override
	public void changePassword(User user, String newPassword) throws StoreException {
		if (!user.getSource().equals(getSource())) {
			throw new StoreException("Unsupported user object: Source is " + user.getSource() + " instead of " + getSource());
		}

		JSONObject contents = load();

		try {
			JSONObject users = contents.getJSONObject("users");
			if (!users.has(user.getName())) {
				throw new IllegalArgumentException("User " + user.getName() + " does not exist");
			}

			JSONObject userObject = users.getJSONObject(user.getName());
			userObject.put("passwordHash", calculateHash(user.getName(), newPassword));

			save(contents);
		}
		catch (JSONException e) {
			throw new StoreException("Unexpected format expection", e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new StoreException("SHA-1 algorithm is unsupported on this machine");
		}
	}

	@Override
	public void modifyUserAttribute(User user, String attributeKey, String newAttributeValue) throws StoreException {
		if (!user.getSource().equals(getSource())) {
			throw new StoreException("Unsupported user object: Source is " + user.getSource() + " instead of " + getSource());
		}
		LocalUserImpl localUser = (LocalUserImpl) user;

		JSONObject contents = load();

		try {
			JSONObject users = contents.getJSONObject("users");
			if (!users.has(user.getName())) {
				throw new IllegalArgumentException("User " + user.getName() + " does not exist");
			}

			JSONObject userObject = users.getJSONObject(user.getName());
			Map<String, String> attributes = toMap(userObject.getJSONObject("attributes"));
			if (newAttributeValue == null) {
				attributes.remove(attributeKey);
			}
			else {
				attributes.put(attributeKey, newAttributeValue);
			}
			// inject into user object
			localUser.setAttributes(attributes);

			userObject.put("attributes", new JSONObject(attributes));

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

	/**
	 * This method is protected only for unit test classes (otherwise, would be private).
	 * 
	 * @return Loaded contents.
	 * 
	 * @throws StoreException
	 *             If contents could not be stored.
	 */
	protected JSONObject load() throws StoreException {
		File home = new File(System.getProperty("user.home"));
		File dbFile = new File(home, DB_FILE);

		if (!dbFile.isFile()) {
			JSONObject empty = new JSONObject();
			try {
				empty.put("users", new JSONObject());
			}
			catch (JSONException e) {
				throw new RuntimeException(e);
			}
			return empty;
		}

		FileInputStream fis = null;
		try {
			fis = new FileInputStream(dbFile);
			return new JSONObject(IOUtils.toString(fis, "UTF-8"));
		}
		catch (IOException e) {
			throw new StoreException("Could not load local user database", e);
		}
		catch (JSONException e) {
			throw new StoreException("Local user database " + dbFile.getAbsolutePath() + " is corrupt.");
		}
		finally {
			IOUtils.closeQuietly(fis);
		}
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
		File home = new File(System.getProperty("user.home"));
		File dbFile = new File(home, DB_FILE);

		FileOutputStream fos = null;
		try {
			dbFile.getParentFile().mkdirs();
			fos = new FileOutputStream(dbFile);
			fos.write(contents.toString().getBytes("UTF-8"));
		}
		catch (IOException e) {
			throw new StoreException("Could not write local user database", e);
		}
		finally {
			IOUtils.closeQuietly(fos);
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

	private static String calculateHash(String userName, String password) throws NoSuchAlgorithmException {
		try {
			// calculate SHA1 hash
			MessageDigest crypt = MessageDigest.getInstance("SHA-1");
			crypt.reset();
			crypt.update((userName + "/" + password).getBytes("UTF-8"));
			byte[] data = crypt.digest();

			// encode as BASE64
			return new String(Base64.encodeBase64(data), "UTF-8");
		}
		catch (UnsupportedEncodingException e) {
			// no UTF-8??
			throw new RuntimeException(e);
		}
	}
}
