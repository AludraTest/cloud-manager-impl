package org.aludratest.cloud.impl.rest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.aludratest.cloud.app.CloudManagerApp;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.Configurable;
import org.aludratest.cloud.manager.ManagedResourceQuery;
import org.aludratest.cloud.manager.ResourceManager;
import org.aludratest.cloud.module.ResourceModule;
import org.aludratest.cloud.request.ResourceRequest;
import org.aludratest.cloud.resource.Resource;
import org.aludratest.cloud.resource.ResourceStateHolder;
import org.aludratest.cloud.resourcegroup.AuthorizingResourceGroup;
import org.aludratest.cloud.resourcegroup.AuthorizingResourceGroupAdmin;
import org.aludratest.cloud.resourcegroup.ResourceGroup;
import org.aludratest.cloud.resourcegroup.ResourceGroupManager;
import org.aludratest.cloud.resourcegroup.ResourceGroupManagerAdmin;
import org.aludratest.cloud.rest.AbstractRestConnector;
import org.aludratest.cloud.rest.RestConnector;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;
import org.codehaus.plexus.component.annotations.Component;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST endpoint for access to Resource Group Manager functions.
 * 
 * @author falbrech
 * 
 */
@Component(role = RestConnector.class, hint = "groups")
@Path("/groups")
public class GroupEndpoint extends AbstractRestConnector {

	private static final Logger LOG = LoggerFactory.getLogger(GroupEndpoint.class);

	private static final DateTimeFormatter ISO_FORMATTER = new DateTimeFormatterBuilder()
			.appendPattern("YYYY-MM-dd'T'HH:mm:ssZZ").toFormatter();

	/**
	 * Returns a JSON object enumerating all resource groups registered in the application's current resource group manager.
	 * 
	 * @return A JSON object enumerating all resource groups registered in the application's current resource group manager.
	 * @throws JSONException
	 *             If the result object could not be constructed.
	 */
	@GET
	@Produces(JSON_TYPE)
	public Response getAllGroups() throws JSONException {
		JSONObject result = new JSONObject();
		JSONArray arr = new JSONArray();

		ResourceGroupManager manager = CloudManagerApp.getInstance().getResourceGroupManager();

		for (int groupId : manager.getAllResourceGroupIds()) {
			ResourceGroup group = manager.getResourceGroup(groupId);

			JSONObject obj = new JSONObject();
			obj.put("id", groupId);
			obj.put("name", manager.getResourceGroupName(groupId));
			obj.put("type", group.getResourceType().getName());
			obj.put("resourceCount", group.getResourceCollection().getResourceCount());
			arr.put(obj);
		}

		result.put("groups", arr);
		return wrapResultObject(result);
	}

	/**
	 * Returns a JSON object describing the given resource group.
	 * 
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 * 
	 * @return A JSON object describing the given resource group.
	 * @throws JSONException
	 *             If the result object could not be constructed.
	 */
	@GET
	@Path("/{groupId: [0-9]{1,10}}")
	@Produces(JSON_TYPE)
	public Response getGroup(@PathParam("groupId") int groupId) throws JSONException {
		return getGroup(groupId, HttpServletResponse.SC_OK);
	}

	private Response getGroup(int groupId, int returnCode) throws JSONException {
		ResourceGroupManager manager = CloudManagerApp.getInstance().getResourceGroupManager();
		ResourceGroup group = manager.getResourceGroup(groupId);
		if (group == null) {
			return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
		}

		JSONObject result = new JSONObject();
		result.put("id", groupId);
		result.put("name", manager.getResourceGroupName(groupId));
		result.put("type", group.getResourceType().getName());
		result.put("resourceCount", group.getResourceCollection().getResourceCount());

		return wrapResultObject(result, returnCode);
	}

	/**
	 * Creates a new resource group in the application's current resource group manager.
	 * 
	 * @param name
	 *            Name of the new resource group.
	 * @param type
	 *            Type of resources to be managed by the new resource group.
	 * 
	 * @return A JSON object describing the new resource group, including its registration ID, as returned by
	 *         {@link #getGroup(int)}
	 * @throws JSONException
	 *             If the result object could not be constructed.
	 */
	@PUT
	@Consumes({FORM_TYPE})
	@Produces(JSON_TYPE)
	public Response createGroup(@FormParam("name") String name, @FormParam("type") String type) throws JSONException {
		// check that type exists
		ResourceModule module = null;
		for (ResourceModule m : CloudManagerApp.getInstance().getAllResourceModules()) {
			if (m.getResourceType().getName().equals(type)) {
				module = m;
			}
		}
		
		if (module == null) {
			return createErrorObject(new IllegalArgumentException("Unknown resource type: " + type));
		}
		
		ResourceGroupManager manager = CloudManagerApp.getInstance().getResourceGroupManager();
		ResourceGroupManagerAdmin admin = ((Configurable) manager).getAdminInterface(ResourceGroupManagerAdmin.class);
		try {
			int groupId = admin.createResourceGroup(module.getResourceType(), name);
			admin.commit();
			return getGroup(groupId, HttpServletResponse.SC_CREATED);
		}
		catch (ConfigException e) {
			return createErrorObject(e);
		}
	}

	/**
	 * Renames the given resource group.
	 * 
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 * @param name
	 *            New name for the resource group.
	 * 
	 * @return A JSON object describing the resource group, including its new name, or an HTTP Status 404 if the group with the
	 *         given registration ID could not be found in the application's resource group manager.
	 * 
	 * @throws JSONException
	 *             If the result object could not be constructed.
	 */
	@POST
	@Path("/{groupId: [0-9]{1,10}}")
	@Consumes({ FORM_TYPE })
	@Produces(JSON_TYPE)
	public Response renameGroup(@PathParam("groupId") int groupId, @FormParam("name") String name) throws JSONException {
		ResourceGroupManager manager = CloudManagerApp.getInstance().getResourceGroupManager();
		ResourceGroupManagerAdmin admin = ((Configurable) manager).getAdminInterface(ResourceGroupManagerAdmin.class);

		ResourceGroup group = manager.getResourceGroup(groupId);
		if (group == null) {
			return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
		}

		try {
			admin.renameResourceGroup(groupId, name);
			admin.commit();
			return getGroup(groupId, HttpServletResponse.SC_OK);
		}
		catch (ConfigException e) {
			return createErrorObject(e);
		}
	}
	
	/**
	 * Deletes the given group from the application's resource group manager.
	 * 
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 * @return An empty response with HTTP status 200 if deletion was successful. HTTP status 404 if no group with the given
	 *         registration ID could not be found in the application's resource group manager. A JSON error object if the group
	 *         could not be deleted, including HTTP status 400.
	 */
	@DELETE
	@Path("/{groupId: [0-9]{1,10}}")
	@Produces(JSON_TYPE)
	public Response deleteGroup(@PathParam("groupId") int groupId) {
		ResourceGroupManager manager = CloudManagerApp.getInstance().getResourceGroupManager();
		ResourceGroupManagerAdmin admin = ((Configurable) manager).getAdminInterface(ResourceGroupManagerAdmin.class);

		ResourceGroup group = manager.getResourceGroup(groupId);
		if (group == null) {
			return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
		}

		try {
			admin.deleteResourceGroup(groupId);
			admin.commit();
			return Response.ok().build();
		}
		catch (ConfigException e) {
			return createErrorObject(e);
		}
	}
	
	/**
	 * Enumerates all resources in a given resource group.
	 * 
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 * 
	 * @return A JSON object listing all resources in the given resource group, or HTTP status code 404 if no group with the given
	 *         registration ID could not be found in the application's resource group manager.
	 * 
	 * @throws JSONException
	 *             If the result object could not be constructed.
	 */
	@GET
	@Path("/{groupId: [0-9]{1,10}}/resources")
	@Produces(JSON_TYPE)
	public Response getResources(@PathParam("groupId") int groupId) throws JSONException {
		ResourceGroupManager groupManager = CloudManagerApp.getInstance().getResourceGroupManager();

		ResourceGroup group = groupManager.getResourceGroup(groupId);
		if (group == null) {
			return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
		}

		ResourceManager manager = CloudManagerApp.getInstance().getResourceManager();

		List<? extends ManagedResourceQuery> queries = manager.getAllRunningQueries();

		JSONArray resources = new JSONArray();

		for (ResourceStateHolder rsh : group.getResourceCollection()) {
			Resource res = (Resource) rsh;

			// is there a query for this resource?
			ManagedResourceQuery query = null;
			for (ManagedResourceQuery q : queries) {
				if (rsh.equals(q.getReceivedResource())) {
					query = q;
					break;
				}
			}

			JSONObject obj = new JSONObject();
			obj.put("type", res.getResourceType().getName());
			obj.put("state", res.getState().toString());
			obj.put("resourceText", res.toString());
			// TODO how to add more information about the resource? Somehow delegate to a special resource writer?

			if (query != null) {
				JSONObject queryObj = new JSONObject();
				ResourceRequest request = query.getRequest();
				queryObj.put("user", request.getRequestingUser().getName());
				if (request.getJobName() != null) {
					queryObj.put("jobName", request.getJobName());
				}
				if (request.getCustomAttributes() != null && !request.getCustomAttributes().isEmpty()) {
					JSONObject attrs = new JSONObject();
					for (Map.Entry<String, Object> attr : request.getCustomAttributes().entrySet()) {
						if (attr.getValue() != null) {
							attrs.put(attr.getKey(), attr.getValue().toString());
						}
					}
					queryObj.put("customAttributes", attrs);
				}
				queryObj.put("enqueueStartTime", query.getEnqueueStartTime().toString(ISO_FORMATTER));
				queryObj.put("resourceReceivedTime", query.getResourceReceivedTime().toString(ISO_FORMATTER));
				obj.put("query", queryObj);
			}

			resources.put(obj);
		}

		JSONObject result = new JSONObject();
		result.put("resources", resources);
		return wrapResultObject(result);
	}

	/**
	 * Lists all users which are allowed to access the resources of a given resource group. If the given resource group is not
	 * found or no resource group which is capable of setting the "limit users" flag, HTTP status 404 is returned. If the
	 * "limit users" flag is not active for the resource group, an empty array is contained in the result object.
	 * 
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 * 
	 * @return JSON object listing all users having access to the resources of the resource group, an empty list when the
	 *         "limit users" flag is not active, or HTTP status 404 if no group with the given registration was found, or the
	 *         group does not support the "limit users" flag.
	 * 
	 * @throws JSONException
	 *             If the result object could not be constructed.
	 */
	@GET
	@Path("/{groupId: [0-9]{1,10}}/users")
	@Produces(JSON_TYPE)
	public Response getUsers(@PathParam("groupId") int groupId) throws JSONException {
		return getUsers(groupId, HttpServletResponse.SC_OK);
	}

	private Response getUsers(int groupId, int returnCode) throws JSONException {
		ResourceGroupManager manager = CloudManagerApp.getInstance().getResourceGroupManager();

		ResourceGroup group = manager.getResourceGroup(groupId);
		if (group == null || !(group instanceof Configurable) || !(group instanceof AuthorizingResourceGroup)) {
			return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
		}

		AuthorizingResourceGroupAdmin admin = ((Configurable) group).getAdminInterface(AuthorizingResourceGroupAdmin.class);
		if (admin == null) {
			return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
		}

		List<User> users;
		if (!((AuthorizingResourceGroup) group).isLimitingUsers()) {
			users = Collections.emptyList();
		}
		else {
			try {
				users = admin.getConfiguredAuthorizedUsers();
			}
			catch (StoreException e) {
				LOG.error("Exception when querying user database", e);
				// do not reveal exception details to client
				return Response.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).build();
			}
		}

		JSONObject result = new JSONObject();
		JSONArray array = new JSONArray();

		for (User user : users) {
			JSONObject o = new JSONObject();
			o.put("source", user.getSource());
			o.put("name", user.getName());
			array.put(o);
		}

		result.put("users", array);
		return wrapResultObject(result, returnCode);
	}

	/**
	 * Adds a user to the list of users having access to the resources of the given resource group.
	 * 
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 * 
	 * @param user
	 *            Name of the user to add. Must match an existing user name in the application's current user database.
	 * @return The new list of users having access to the resources of the given resource group, or HTTP status 404 if no user
	 *         with the given user name was found, or the group does not support the "limit users" flag.
	 * 
	 * @throws JSONException
	 *             If the result object could not be constructed.
	 */
	@PUT
	@Path("/{groupId: [0-9]{1,10}}/users/{user}")
	@Produces(JSON_TYPE)
	public Response addUser(@PathParam("groupId") int groupId, @PathParam("user") String user)
 throws JSONException {
		return doUserAction(groupId, user, false);
	}

	/**
	 * Removes a user from the list of users having access to the resources of the given resource group.
	 * 
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 * 
	 * @param user
	 *            Name of the user to remove. Must match an existing user name in the application's current user database.
	 * @return The new list of users having access to the resources of the given resource group, or HTTP status 404 if no user
	 *         with the given user name was found, or the group does not support the "limit users" flag.
	 * 
	 * @throws JSONException
	 *             If the result object could not be constructed.
	 */
	@DELETE
	@Path("/{groupId: [0-9]{1,10}}/users/{user}")
	@Produces(JSON_TYPE)
	public Response removeUser(@PathParam("groupId") int groupId, @PathParam("user") String user) throws JSONException {
		return doUserAction(groupId, user, true);
	}

	/**
	 * Disables the "limit users" flag for the given resource group.
	 * 
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 * @return An empty response with HTTP status OK when the flag has successfully been disabled, or HTTP status 404 if no group
	 *         with the given ID was found or if the group does not support this flag, or a JSON error object if a configuration
	 *         listener disapproves the configuration change.
	 */
	@DELETE
	@Path("/{groupId: [0-9]{1,10}}/users")
	@Produces(JSON_TYPE)
	public Response disableUserLimit(@PathParam("groupId") int groupId) {
		ResourceGroupManager manager = CloudManagerApp.getInstance().getResourceGroupManager();

		ResourceGroup group = manager.getResourceGroup(groupId);
		if (group == null || !(group instanceof Configurable) || !(group instanceof AuthorizingResourceGroup)) {
			return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
		}
		
		AuthorizingResourceGroupAdmin admin = ((Configurable) group).getAdminInterface(AuthorizingResourceGroupAdmin.class);
		if (admin == null) {
			return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
		}
		
		try {
			admin.setLimitingUsers(false);
			admin.commit();
			return Response.ok().build();
		}
		catch (ConfigException e) {
			return createErrorObject(e);
		}
	}

	private Response doUserAction(int groupId, String user, boolean delete) throws JSONException {
		ResourceGroupManager manager = CloudManagerApp.getInstance().getResourceGroupManager();

		ResourceGroup group = manager.getResourceGroup(groupId);
		if (group == null || !(group instanceof Configurable) || !(group instanceof AuthorizingResourceGroup)) {
			return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
		}

		// user must also exist
		User userObject;
		try {
			userObject = CloudManagerApp.getInstance().getSelectedUserDatabase().findUser(user);
		}
		catch (StoreException e) {
			LOG.error("Exception when querying user database", e);
			// do not reveal exception details to client
			return Response.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).build();
		}

		if (userObject == null) {
			return createErrorObject(new IllegalArgumentException("User " + user + " not found in current user database."));
		}

		AuthorizingResourceGroupAdmin admin = ((Configurable) group).getAdminInterface(AuthorizingResourceGroupAdmin.class);
		if (admin == null) {
			return Response.status(HttpServletResponse.SC_NOT_FOUND).build();
		}

		if (delete) {
			admin.removeAuthorizedUser(userObject);
		}
		else {
			admin.setLimitingUsers(true);
			admin.addAuthorizedUser(userObject);
		}

		try {
			admin.commit();
			if (delete) {
				return Response.ok().build();
			}
			else {
				return getUsers(groupId, HttpServletResponse.SC_CREATED);
			}
		}
		catch (ConfigException e) {
			return createErrorObject(e);
		}

	}

}
