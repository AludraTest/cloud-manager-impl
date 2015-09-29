package org.aludratest.cloud.impl.request;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.aludratest.cloud.app.CloudManagerApp;
import org.aludratest.cloud.manager.ManagedResourceQuery;
import org.aludratest.cloud.manager.ResourceManager;
import org.aludratest.cloud.rest.AbstractRestConnector;
import org.aludratest.cloud.rest.RestConnector;
import org.codehaus.plexus.component.annotations.Component;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A REST endpoint which provides debugging information; mostly statistical information about requests and request queues.
 * 
 * @author falbrech
 * 
 */
@Component(role = RestConnector.class, hint = "client-request")
@Path("/debug/requests")
public class ClientRequestDebugRestEndpoint extends AbstractRestConnector {

	/**
	 * Returns a JSON object describing all waiting requests.
	 * 
	 * @return A JSON object describing all waiting requests.
	 * @throws JSONException
	 *             If a JSON problem occurs.
	 */
	@GET
	@Path("/waiting")
	@Produces(JSON_TYPE)
	public Response getWaitingRequests() throws JSONException {
		ClientRequestServlet servlet = ClientRequestServlet.instance;
		if (servlet == null || servlet.getRequestHandler() == null) {
			return Response.noContent().build();
		}

		JSONObject obj = new JSONObject();
		obj.put("waitingRequests", servlet.waitingRequests.get());

		return wrapResultObject(obj);
	}

	/**
	 * Returns a JSON object informing about the current queue size.
	 * 
	 * @return A JSON object informing about the current queue size.
	 * @throws JSONException
	 *             If a JSON problem occurs.
	 */
	@GET
	@Path("/queue")
	@Produces(JSON_TYPE)
	public Response getQueue() throws JSONException {
		ResourceManager manager = CloudManagerApp.getInstance().getResourceManager();

		JSONObject obj = new JSONObject();
		obj.put("queueSize", manager.getTotalQueueSize());

		return wrapResultObject(obj);
	}

	/**
	 * Returns a JSON object describing all current requests (waiting and working).
	 * 
	 * @return A JSON object describing all current requests (waiting and working).
	 * @throws JSONException
	 *             If a JSON problem occurs.
	 */
	@GET
	@Produces(JSON_TYPE)
	public Response getKnownRequests() throws JSONException {
		ClientRequestServlet servlet = ClientRequestServlet.instance;
		if (servlet == null || servlet.getRequestHandler() == null) {
			return Response.noContent().build();
		}

		JSONObject obj = new JSONObject();
		obj.put("requests", servlet.getRequestHandler().getRequestQueries());
		return wrapResultObject(obj);
	}

	/**
	 * Returns a JSON object describing all currently working requests.
	 * 
	 * @return A JSON object describing all currently working requests.
	 * @throws JSONException
	 *             If a JSON problem occurs.
	 */
	@GET
	@Path("/running")
	@Produces(JSON_TYPE)
	public Response getRunningRequests() throws JSONException {
		ResourceManager manager = CloudManagerApp.getInstance().getResourceManager();

		JSONArray arr = new JSONArray();
		for (ManagedResourceQuery query : manager.getAllRunningQueries()) {
			JSONObject obj = new JSONObject();
			obj.put("user", query.getRequest().getRequestingUser().getName());
			obj.put("jobName", query.getRequest().getJobName());
			if (query.getReceivedResource() != null) {
				obj.put("receivedResource", query.getReceivedResource());
			}
			arr.put(obj);
		}

		JSONObject obj = new JSONObject();
		obj.put("requests", arr);

		return wrapResultObject(obj);
	}

}
