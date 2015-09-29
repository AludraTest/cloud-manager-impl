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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.aludratest.cloud.resource.user.MutableResourceTypeAuthorizationConfig;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorization;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationConfig;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;
import org.aludratest.cloud.user.UserDatabase;
import org.aludratest.cloud.user.admin.UserDatabaseRegistry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * An implementation of the Mutable resource type authorization config interface which is based on JSON data.
 * 
 * @author falbrech
 * 
 */
public class JSONResourceTypeAuthorizationConfig implements MutableResourceTypeAuthorizationConfig {

	private JSONArray jsonArray;

	private List<User> allUsers = new ArrayList<User>();

	/**
	 * Constructs a new authorization configuration object based on JSON data.
	 * 
	 * @param jsonArray
	 *            JSON array of JSON objects, each objects must have keys <code>userSource</code> and <code>userName</code>, and
	 *            can optionally have keys <code>maxResources</code> and <code>niceLevel</code>.
	 * @param userDatabaseRegistry
	 *            User Database registry which is used to retrieve user objects for the entries in the JSON array.
	 * @throws JSONException
	 *             If the passed JSON data is invalid.
	 * @throws StoreException
	 *             If one of the User Databases could not be queried.
	 */
	public JSONResourceTypeAuthorizationConfig(JSONArray jsonArray, UserDatabaseRegistry userDatabaseRegistry)
			throws JSONException, StoreException {
		this.jsonArray = jsonArray;

		// build user list using registry
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject o = jsonArray.getJSONObject(i);

			String source = o.getString("userSource");
			String userName = o.getString("userName");

			UserDatabase db = userDatabaseRegistry.getUserDatabase(source);
			if (db != null) {
				User u = db.findUser(userName);
				if (u != null) {
					allUsers.add(u);
				}
			}
		}
	}

	/**
	 * Converts a given authorization configuration object to a JSON array, which then could e.g. be passed to the constructor of
	 * this class.
	 * 
	 * @param config
	 *            Authorization configuration object from which to construct a JSON array.
	 * @return JSON array describing the authorization configuration object.
	 * @throws JSONException
	 *             If the JSON object could not be created.
	 */
	public static JSONArray toJSONArray(ResourceTypeAuthorizationConfig config) throws JSONException {
		List<JSONObject> values = new ArrayList<JSONObject>();

		for (User user : config.getConfiguredUsers()) {
			ResourceTypeAuthorization auth = config.getResourceTypeAuthorizationForUser(user);

			JSONObject obj = new JSONObject();
			obj.put("userName", user.getName());
			obj.put("userSource", user.getSource());
			obj.put("maxResources", auth.getMaxResources());
			obj.put("niceLevel", auth.getNiceLevel());

			values.add(obj);
		}

		return new JSONArray(values);
	}

	/**
	 * Returns the internal JSON array containing the authorization configuration.
	 * 
	 * @return The internal JSON array containing the authorization configuration.
	 */
	public JSONArray getJsonArray() {
		return jsonArray;
	}

	@Override
	public ResourceTypeAuthorization getResourceTypeAuthorizationForUser(User user) {
		try {
			int i = getUserIndex(user);
			if (i == -1) {
				return null;
			}
			JSONObject o = jsonArray.getJSONObject(i);
			return new SimpleResourceTypeAuthorization(o.getInt("maxResources"), o.getInt("niceLevel"));
		}
		catch (JSONException e) {
			// should never occur, because we deal internally with structure
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<User> getConfiguredUsers() {
		return Collections.unmodifiableList(allUsers);
	}

	@Override
	public void addUser(User user, ResourceTypeAuthorization authorization) throws IllegalArgumentException {
		try {
			if (getUserIndex(user) > -1) {
				throw new IllegalArgumentException("A resource type authorization for user " + user + " is already set.");
			}

			JSONObject o = new JSONObject();
			o.put("userName", user.getName());
			o.put("userSource", user.getSource());
			o.put("maxResources", authorization.getMaxResources());
			o.put("niceLevel", authorization.getNiceLevel());
			jsonArray.put(o);
			allUsers.add(user);
		}
		catch (JSONException e) {
			// should never occur, because we deal internally with structure
			throw new RuntimeException(e);
		}
	}

	@Override
	public void removeUser(User user) throws IllegalArgumentException {
		try {
			int index = getUserIndex(user);
			if (index == -1) {
				throw new IllegalArgumentException("No resource type authorization for user " + user + " is set.");
			}

			List<JSONObject> ls = new ArrayList<JSONObject>();
			for (int i = 0; i < index; i++) {
				ls.add(jsonArray.getJSONObject(i));
			}
			for (int i = index + 1; i < jsonArray.length(); i++) {
				ls.add(jsonArray.getJSONObject(i));
			}

			jsonArray = new JSONArray(ls);
			allUsers.remove(user);
		}
		catch (JSONException e) {
			// should never occur, because we deal internally with structure
			throw new RuntimeException(e);
		}
	}

	@Override
	public void editUserAuthorization(User user, ResourceTypeAuthorization newAuthorization) throws IllegalArgumentException {
		try {
			int index = getUserIndex(user);
			if (index == -1) {
				throw new IllegalArgumentException("No resource type authorization for user " + user + " is set.");
			}

			JSONObject o = jsonArray.getJSONObject(index);
			o.put("maxResources", newAuthorization.getMaxResources());
			o.put("niceLevel", newAuthorization.getNiceLevel());
		}
		catch (JSONException e) {
			// should never occur, because we deal internally with structure
			throw new RuntimeException(e);
		}
	}

	private int getUserIndex(User user) throws JSONException {
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject o = jsonArray.getJSONObject(i);
			if (user.getName().equals(o.getString("userName")) && user.getSource().equals(o.getString("userSource"))) {
				return i;
			}
		}

		return -1;
	}

}
