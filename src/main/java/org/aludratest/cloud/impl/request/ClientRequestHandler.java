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
package org.aludratest.cloud.impl.request;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.aludratest.cloud.app.CloudManagerApp;
import org.aludratest.cloud.impl.app.CloudManagerApplicationHolder;
import org.aludratest.cloud.impl.app.DatabaseRequestLogger;
import org.aludratest.cloud.manager.ManagedResourceQuery;
import org.aludratest.cloud.manager.ResourceManager;
import org.aludratest.cloud.manager.ResourceManagerListener;
import org.aludratest.cloud.module.ResourceModule;
import org.aludratest.cloud.resource.Resource;
import org.aludratest.cloud.resource.ResourceListener;
import org.aludratest.cloud.resource.ResourceState;
import org.aludratest.cloud.resource.UsableResource;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorization;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationConfig;
import org.aludratest.cloud.resource.writer.JSONResourceWriter;
import org.aludratest.cloud.resource.writer.ResourceWriterFactory;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core class for handling incoming resource requests. This class passes requests to the application's resource manager and
 * listens on request status changes, e.g. a resource becoming available. <br>
 * <br>
 * All request input / output is done on a JSON object basis. An object passed to {@link #handleResourceRequest(User, JSONObject)}
 * should look like this:
 * 
 * <pre>
 * { resourceType: 'selenium', jobName: 'Nightly test run', niceLevel: -2, customAttributes: { someKey: 'someValue' } }
 * </pre>
 * 
 * <code>niceLevel</code> is optional and defaults to 0. <br>
 * <code>jobName</code> is optional and defaults to <code>null</code>. <br>
 * <code>customAttributes</code> do not need to be specified. <br>
 * <br>
 * The method {@link #handleResourceRequest(User, JSONObject)} will wait up to 10 seconds for a resource to become available for
 * the request. If the request is still waiting for a resource after this time, the method will return a JSON object containing
 * the assigned request ID and a flag that the request is still waiting:
 * 
 * <pre>
 * { requestId: 'abc123', waiting: true }
 * </pre>
 * 
 * If you receive such a JSON object from this method, you have up to 60 seconds time to again query for this request, now
 * specifying the request ID:
 * 
 * <pre>
 * handler.handleResourceRequest(user, new JSONObject(&quot;{requestId: 'abc123'}&quot;));
 * </pre>
 * 
 * If a resource has become available in the meantime or within 10 seconds from the new invocation, a positive JSON object
 * containing the resource information is returned:
 * 
 * <pre>
 * { requestId: 'abc123', resourceType: 'selenium', resource: { url: 'http://acm.myintra.int:8080/acm/proxy1' }}
 * </pre>
 * 
 * Note that the <code>resource</code> object within the result object is defined by the requested resource type and its JSON
 * resource writer. <br>
 * If you do not re-query a request within 60 seconds after receiving a "waiting" response, the handler will signal the resource
 * manager to abort the request. <br>
 * 
 * @author falbrech
 * 
 */
public class ClientRequestHandler implements ResourceManagerListener {

	private static final Logger LOG = LoggerFactory.getLogger(ClientRequestHandler.class);

	private Map<String, WaitingRequest> requestQueries = new HashMap<String, WaitingRequest>();

	/* The resources which were sent by this Servlet and can be released. */
	private Map<String, Resource> activeResources = new HashMap<String, Resource>();

	/* Scheduler for aborting queries which have no longer been requested */
	private ScheduledExecutorService abortScheduler = Executors.newScheduledThreadPool(1);

	private ResourceManager manager;

	/**
	 * Constructs a new request handler which uses the given manager for request submission and listening to request events.
	 * 
	 * @param manager
	 *            Manager to use for request submission and for listening to request events.
	 */
	public ClientRequestHandler(ResourceManager manager) {
		this.manager = manager;
		manager.addResourceManagerListener(this);
	}

	/**
	 * Handles the given resource request, which can be a new request or a reference to a previously submitted one. See class
	 * Javadoc for details on the JSON object parameter.
	 * 
	 * @param user
	 *            User submitting the request.
	 * @param object
	 *            Request object.
	 * @return A JSON object describing the received resource, or indicating that the request is still waiting for a resource to
	 *         receive.
	 * 
	 * @throws JSONException
	 *             If the input JSON object is invalid.
	 */
	public JSONObject handleResourceRequest(User user, JSONObject object) throws JSONException {
		LOG.debug("Handling resource request for user " + user);
		try {
			// if there is already a request ID, get query belonging to it
			if (object.has("requestId")) {
				String requestId = object.getString("requestId");
				return waitForFuture(requestId);
			}

			ResourceModule module = CloudManagerApp.getInstance().getResourceModule(object.getString("resourceType"));
			if (module == null) {
				return createErrorObject("Unknown resource type");
			}

			// make a quick check if user is authorized to get resource type; fail if not
			try {
				ResourceTypeAuthorizationConfig authConfig = CloudManagerApp.getInstance().getResourceTypeAuthorizationStore()
						.loadResourceTypeAuthorizations(module.getResourceType());
				if (authConfig == null) {
					return createErrorObject("User is not authorized for resource type " + module.getResourceType().getName());
				}
				ResourceTypeAuthorization auth = authConfig.getResourceTypeAuthorizationForUser(user);
				if (auth == null || auth.getMaxResources() == 0) {
					return createErrorObject("User is not authorized for resource type " + module.getResourceType().getName());
				}
			}
			catch (StoreException e) {
				// ok; will fail later on anyway
			}

			// check if there are custom attributes
			Map<String, String> attributes = new HashMap<String, String>();
			if (object.has("customAttributes")) {
				JSONObject attrs = object.getJSONObject("customAttributes");
				Iterator<?> keys = attrs.keys();
				while (keys.hasNext()) {
					String key = keys.next().toString();
					attributes.put(key, attrs.getString(key));
				}
			}

			// check for a name
			String name = "unnamed job";
			if (object.has("jobName")) {
				name = object.getString("jobName");
			}

			String requestId = generateUniqueRequestKey();
			ClientRequestImpl request = new ClientRequestImpl(requestId, user, module.getResourceType(), object.optInt(
					"niceLevel", 0), name, attributes);
			
			final DatabaseRequestLogger requestLogger = CloudManagerApplicationHolder.getInstance().getRequestLogger();
			final long dbRequestId = requestLogger.createRequestLog(user, name);

			WaitingRequest wr = new WaitingRequest();
			wr.future = new WaitForResource();
			wr.dbRequestId = dbRequestId;
			wr.jobName = name;
			wr.user = user;

			LOG.debug("Request " + requestId + " started");

			synchronized (this) {
				requestQueries.put(requestId, wr);
			}

			// returns immediately; notifies via listener methods
			manager.handleResourceRequest(request);

			return waitForFuture(requestId);
		}
		catch (SQLException e) {
			return createErrorObject(e);
		}
	}

	/**
	 * Handles a request to release the resource which has been assigned to the given resource request.
	 * 
	 * @param requestId
	 *            ID of the resource request to release the assigned resource of.
	 * 
	 * @return <code>true</code> if the request was found and the resource has been released, <code>false</code> if no request
	 *         with the given ID exists.
	 */
	public boolean handleReleaseRequest(String requestId) {
		LOG.debug("Releasing resource for request " + requestId);

		Resource resource;
		synchronized (this) {
			resource = activeResources.remove(requestId);
		}

		if (resource == null) {
			return false;
		}

		if (resource instanceof UsableResource) {
			// for correct notification of manager, if not yet used, start it
			if (resource.getState() != ResourceState.IN_USE) {
				((UsableResource) resource).startUsing();
			}

			((UsableResource) resource).stopUsing();
		}

		return true;
	}

	/**
	 * Signals that the resource request with the given ID shall be aborted, i.e. no longer wants to receive a resource.
	 * 
	 * @param requestId
	 *            ID of the request to abort.
	 */
	public void abortWaitingRequest(String requestId) {
		LOG.debug("Abort waiting request " + requestId);
		WaitingRequest wr = requestQueries.remove(requestId);
		if (wr != null) {
			if (wr.future.isDone()) {
				try {
					Resource res = wr.future.get();
					synchronized (this) {
						activeResources.put(requestId, res);
					}
					handleReleaseRequest(requestId);
				}
				catch (Exception ex) {
					// ignore
				}
			}
			wr.future.cancel(false);
		}
	}

	private JSONObject waitForFuture(final String requestId) throws JSONException, SQLException {
		WaitingRequest request;
		synchronized (this) {
			request = requestQueries.get(requestId);
			if (request == null) {
				return createErrorObject("Invalid request ID");
			}
		}

		// stop already scheduled abandon task, if any
		if (request.abandonFuture != null) {
			request.abandonFuture.cancel(false);
		}

		try {
			// wait for max 10 seconds - if it takes longer -> TimeoutException
			Resource resource = request.future.get(10, TimeUnit.SECONDS);

			// OK, first of all, delete waiting query
			synchronized (this) {
				requestQueries.remove(requestId);
				activeResources.put(requestId, resource);
			}

			startWorking(resource, request.user, request.jobName, request.dbRequestId);

			ResourceWriterFactory factory = CloudManagerApp.getInstance().getResourceWriterFactory(resource.getResourceType());
			factory.getResourceWriter(JSONResourceWriter.class);
			JSONResourceWriter writer = factory.getResourceWriter(JSONResourceWriter.class);

			// wrap it with meta object
			JSONObject resultObject = new JSONObject();
			resultObject.put("resourceType", resource.getResourceType().getName());
			resultObject.put("resource", writer.writeToJSON(resource));
			resultObject.put("requestId", requestId);

			return resultObject;
		}
		catch (ExecutionException e) {
			LOG.error("Execution exception when waiting for resource", e);
			return createErrorObject(e.getMessage());
		}
		catch (InterruptedException e) {
			return createErrorObject("AludraTest Cloud Manager server is shutting down");
		}
		catch (TimeoutException e) {
			JSONObject result = new JSONObject();
			result.put("requestId", requestId);
			result.put("waiting", true);

			// if not re-requested within 60 seconds, abort
			request.abandonFuture = abortScheduler.schedule(new Runnable() {
				@Override
				public void run() {
					LOG.debug("Aborting inactive request " + requestId);
					abortWaitingRequest(requestId);
				}
			}, 60, TimeUnit.SECONDS);

			return result;
		}
	}

	@Override
	public boolean resourceAvailable(ManagedResourceQuery request, Resource availableResource) {
		if (!(request.getRequest() instanceof ClientRequestImpl)) {
			return false;
		}

		ClientRequestImpl creq = (ClientRequestImpl) request.getRequest();
		String id = creq.getRequestId();

		synchronized (this) {
			if (requestQueries.containsKey(id)) {
				WaitingRequest wr = requestQueries.get(id);
				wr.future.setResource(availableResource);
				return true;
			}
		}
		
		return false;
	}

	@Override
	public void requestError(ManagedResourceQuery request, String errorMessage, Throwable cause) {
		if (!(request.getRequest() instanceof ClientRequestImpl)) {
			return;
		}

		ClientRequestImpl creq = (ClientRequestImpl) request.getRequest();
		String id = creq.getRequestId();

		synchronized (this) {
			if (requestQueries.containsKey(id)) {
				WaitingRequest wr = requestQueries.get(id);
				wr.future.setErrorMessage(errorMessage, cause);
			}
		}
	}

	@Override
	public void requestEnqueued(ManagedResourceQuery request) {
		// not of any interest for us
	}

	@Override
	public void resourceReleased(ManagedResourceQuery request, Resource releasedResource) {
		// not of any interest for us
	}

	private void startWorking(Resource resource, User user, String jobName, final long dbRequestId)
			throws SQLException {
		final DatabaseRequestLogger requestLogger = CloudManagerApplicationHolder.getInstance().getRequestLogger();

		// start using resource, if it does not auto-detect this
		if (resource instanceof UsableResource) {
			((UsableResource) resource).startUsing();
		}

		requestLogger.updateRequestLogWorkStarted(dbRequestId, resource.getResourceType().getName(), resource.toString());

		resource.addResourceListener(new ResourceListener() {
			@Override
			public void resourceStateChanged(Resource resource, ResourceState previousState, ResourceState newState) {
				if (previousState == ResourceState.IN_USE && newState != ResourceState.IN_USE) {
					// assume end of work
					String reason;
					switch (newState) {
						case DISCONNECTED:
							reason = "RES_DISCONNECT";
							break;
						default:
							reason = "OK_RELEASED";
							break;
					}

					// count active resources
					int cnt = 0;
					for (ManagedResourceQuery query : manager.getAllRunningQueries()) {
						if (query.getRequest().getResourceType().equals(resource.getResourceType())) {
							cnt++;
						}

					}
					requestLogger.updateRequestLogWorkDone(dbRequestId, reason, cnt);
					resource.removeResourceListener(this);
				}
			}
		});

	}

	private String generateUniqueRequestKey() {
		String key = null;
		do {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 16; i++) {
				sb.append(Integer.toHexString((int) (Math.random() * 16)));
			}
			key = sb.toString();
		}
		while (requestQueries.containsKey(key) || activeResources.containsKey(key));
		return key;
	}

	private JSONObject createErrorObject(Throwable t) throws JSONException {
		return createErrorObject(t.getMessage());
	}

	private JSONObject createErrorObject(String errorMessage) throws JSONException {
		JSONObject result = new JSONObject();
		result.put("errorMessage", errorMessage);
		return result;
	}

	// for debugging purposes
	JSONArray getRequestQueries() throws JSONException {
		JSONArray result = new JSONArray();

		for (Map.Entry<String, WaitingRequest> entry : requestQueries.entrySet()) {
			JSONObject obj = new JSONObject();
			obj.put("requestId", entry.getKey());

			JSONObject req = new JSONObject();
			req.put("jobName", entry.getValue().jobName);
			req.put("user", entry.getValue().user.getName());

			obj.put("request", req);
			result.put(obj);
		}

		return result;
	}

	private static class WaitingRequest {

		private WaitForResource future;

		private long dbRequestId;

		private User user;

		private String jobName;

		private ScheduledFuture<?> abandonFuture;

	}

	private static class WaitForResource implements Future<Resource> {

		private Resource resource;

		private String errorMessage;

		private Throwable cause;

		public synchronized void setResource(Resource resource) {
			this.resource = resource;
			notify();
		}

		public synchronized void setErrorMessage(String errorMessage, Throwable cause) {
			this.errorMessage = errorMessage;
			this.cause = cause;
			notify();
		}

		public synchronized Resource getResource() {
			return resource;
		}

		public synchronized String getErrorMessage() {
			return errorMessage;
		}

		@Override
		public synchronized boolean cancel(boolean mayInterruptIfRunning) {
			return false;
		}

		@Override
		public synchronized boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return getResource() != null;
		}

		@Override
		public Resource get() throws InterruptedException, ExecutionException {
			while (getResource() != null && getErrorMessage() != null) {
				wait(2000);
			}

			if (getErrorMessage() != null) {
				throw new ExecutionException(errorMessage, cause);
			}

			return resource;
		}

		@Override
		public Resource get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			if (getResource() != null) {
				return resource;
			}
			if (getErrorMessage() != null) {
				throw new ExecutionException(errorMessage, cause);
			}

			synchronized (this) {
				wait(TimeUnit.MILLISECONDS.convert(timeout, unit));
			}
			if (getErrorMessage() != null) {
				throw new ExecutionException(errorMessage, cause);
			}
			if (getResource() == null) {
				throw new TimeoutException();
			}
			return resource;
		}

	}

}
