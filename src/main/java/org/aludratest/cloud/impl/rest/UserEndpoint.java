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
package org.aludratest.cloud.impl.rest;

import java.util.Iterator;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.aludratest.cloud.app.CloudManagerApp;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.rest.AbstractRestConnector;
import org.aludratest.cloud.rest.RestConnector;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;
import org.aludratest.cloud.user.UserDatabase;
import org.codehaus.plexus.component.annotations.Component;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * REST endpoint for managing the users of the application's selected user database.
 * 
 * @author falbrech
 * 
 */
@Component(role = RestConnector.class, hint = "users")
@Path("/users")
public class UserEndpoint extends AbstractRestConnector {

	/**
	 * Lists all users existing in the current user database.
	 * 
	 * @return A JSON object listing all users in the current user database.
	 * 
	 * @throws JSONException
	 *             If the result object could not be constructed.
	 */
	@GET
	@Produces(JSON_TYPE)
	public Response getUsers() throws JSONException {
		JSONObject result = new JSONObject();
		UserDatabase users = CloudManagerApp.getInstance().getSelectedUserDatabase();
		if (users == null) {
			return wrapResultObject(result);
		}

		result.put("isEditable", !users.isReadOnly());

		JSONArray arr = new JSONArray();

		try {
			Iterator<User> iter = users.getAllUsers(null);
			while (iter.hasNext()) {
				User user = iter.next();
				JSONObject u = new JSONObject();
				u.put("name", user.getName());
				u.put("source", user.getSource());

				String[] attrs = user.getDefinedUserAttributes();
				if (attrs.length > 0) {
					JSONObject a = new JSONObject();
					for (String attr : attrs) {
						a.put(attr, user.getUserAttribute(attr));
					}
					u.put("customAttributes", a);
				}

				arr.put(u);
			}

			result.put("users", arr);
			return wrapResultObject(result);
		}
		catch (StoreException e) {
			getLogger().error("Could not retrieve users list", e);
			return createErrorObject(new RuntimeException("Could not retrieve users list."),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Returns detailed information about a single user in the application's current user database.
	 * 
	 * @param userName
	 *            User name to retrieve information about.
	 * @return A JSON object describing the user, or HTTP status 404 if no user with the given name could be found.
	 * @throws JSONException
	 *             If the result object could not be constructed.
	 */
	@GET
	@Path("/{userName}")
	@Produces(JSON_TYPE)
	public Response getUser(@PathParam("userName") String userName) throws JSONException {
		JSONObject result = new JSONObject();
		UserDatabase users = CloudManagerApp.getInstance().getSelectedUserDatabase();
		if (users == null) {
			return wrapResultObject(result);
		}

		if (userName == null || "".equals(userName)) {
			return Response.status(HttpServletResponse.SC_BAD_REQUEST).build();
		}

		try {
			User user = users.findUser(userName);
			if (user == null) {
				return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
			}

			result.put("user", getUserJSON(user));
			return wrapResultObject(result);
		}
		catch (StoreException e) {
			getLogger().error("Could not retrieve user", e);
			return createErrorObject(new RuntimeException("Could not retrieve user information."),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Adds a new user to the application's current user database.
	 * 
	 * @param userName
	 *            User name of the user to add.
	 * @return A JSON object describing the newly created user, or a JSON error object describing any errors, e.g. user name
	 *         already exists, or current user database is read-only.
	 * 
	 * @throws JSONException
	 *             If the result object could not be constructed.
	 */
	@PUT
	@Path("/{userName}")
	@Produces(JSON_TYPE)
	public Response addUser(@PathParam("userName") String userName) throws JSONException {
		JSONObject result = new JSONObject();
		UserDatabase users = CloudManagerApp.getInstance().getSelectedUserDatabase();
		if (users == null) {
			return wrapResultObject(result);
		}

		if (users.isReadOnly()) {
			return createErrorObject(new ConfigException("The current user database is read only."));

		}

		if (userName == null || "".equals(userName)) {
			return Response.status(HttpServletResponse.SC_BAD_REQUEST).build();
		}

		try {
			User user = users.findUser(userName);
			if (user != null) {
				return createErrorObject(new ConfigException("A user with this name already exists."));
			}

			user = users.create(userName);
			result.put("user", getUserJSON(user));
			return wrapResultObject(result, HttpServletResponse.SC_CREATED);
		}
		catch (StoreException e) {
			getLogger().error("Could not query user database", e);
			return createErrorObject(new RuntimeException("Could not query user database"),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Deletes a user from the application's current user database.
	 * 
	 * @param userName
	 *            User name of the user to delete.
	 * 
	 * @return An empty response with HTTP status 200 if the deletion was successful, or HTTP status 404 if no user with the given
	 *         user name was found. A JSON error object if the deletion failed for any reason.
	 */
	@DELETE
	@Path("/{userName}")
	@Produces(JSON_TYPE)
	public Response deleteUser(@PathParam("userName") String userName) {
		JSONObject result = new JSONObject();
		UserDatabase users = CloudManagerApp.getInstance().getSelectedUserDatabase();
		if (users == null) {
			return wrapResultObject(result);
		}

		if (users.isReadOnly()) {
			return createErrorObject(new ConfigException("The current user database is read only."));

		}

		if (userName == null || "".equals(userName)) {
			return Response.status(HttpServletResponse.SC_BAD_REQUEST).build();
		}

		try {
			User user = users.findUser(userName);
			if (user == null) {
				return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
			}

			users.delete(user);
			return Response.ok().build();
		}
		catch (StoreException e) {
			getLogger().error("Could not query user database", e);
			return createErrorObject(new RuntimeException("Could not query user database"),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	private JSONObject getUserJSON(User user) throws JSONException {
		JSONObject u = new JSONObject();
		u.put("name", user.getName());
		u.put("source", user.getSource());

		String[] attrs = user.getDefinedUserAttributes();
		if (attrs.length > 0) {
			JSONObject a = new JSONObject();
			for (String attr : attrs) {
				a.put(attr, user.getUserAttribute(attr));
			}
			u.put("customAttributes", a);
		}

		return u;
	}

}
