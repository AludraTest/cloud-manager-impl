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
package org.aludratest.cloud.impl.manager;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.aludratest.cloud.app.CloudManagerApp;
import org.aludratest.cloud.manager.ManagedResourceQuery;
import org.aludratest.cloud.manager.ResourceManager;
import org.aludratest.cloud.manager.ResourceManagerListener;
import org.aludratest.cloud.module.ResourceModule;
import org.aludratest.cloud.request.ResourceRequest;
import org.aludratest.cloud.resource.Resource;
import org.aludratest.cloud.resource.ResourceCollectionListener;
import org.aludratest.cloud.resource.ResourceListener;
import org.aludratest.cloud.resource.ResourceState;
import org.aludratest.cloud.resource.ResourceStateHolder;
import org.aludratest.cloud.resource.ResourceType;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorization;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationConfig;
import org.aludratest.cloud.resourcegroup.AuthorizingResourceGroup;
import org.aludratest.cloud.resourcegroup.ResourceGroup;
import org.aludratest.cloud.resourcegroup.ResourceGroupManager;
import org.aludratest.cloud.resourcegroup.ResourceGroupManagerListener;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;
import org.codehaus.plexus.component.annotations.Component;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default Resource Manager implementation. Manages a queue of waiting requests and only becomes active when:
 * <ul>
 * <li>A new request is submitted, or</li>
 * <li>A resource becomes available.</li>
 * </ul>
 * In the first case, an available resource is searched which could be assigned to the request. In the second case, a matching
 * request is searched in the queue of requests. <br>
 * If multiple requests are waiting, they will be scored, and the request with the <b>least</b> score will receive the resource.
 * Note that the score is calculated, among other factors, based on the <i>nice Level</i> of the request and / or the user, so it
 * can be a negative value (which is why a nice level of -19 will give you highest priority for receiving resources).
 * 
 * @author falbrech
 * 
 */
@Component(role = ResourceManager.class)
public class DefaultResourceManagerImpl implements ResourceManager, ResourceCollectionListener, ResourceGroupManagerListener,
		ResourceListener, DefaultResourceManagerImplMBean {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultResourceManagerImpl.class);

	private ResourceGroupManager groupManager;

	private List<WaitingResourceRequest> queue = new LinkedList<WaitingResourceRequest>();

	private Set<WaitingResourceRequest> runningJobs = new HashSet<WaitingResourceRequest>();

	private Map<ResourceType, Set<Resource>> idleResources = new HashMap<ResourceType, Set<Resource>>();

	private List<ResourceManagerListener> listeners = new ArrayList<ResourceManagerListener>();

	private ExecutorService queueWorkerService;

	private RequestQueueWorker queueWorker;

	// MBean infrastructure
	private AtomicInteger nextResourceId = new AtomicInteger();

	private Map<Resource, Integer> resourceIds = new ConcurrentHashMap<Resource, Integer>();

	@Override
	public void start(ResourceGroupManager resourceGroupManager) {
		if (queueWorkerService != null) {
			throw new IllegalStateException("This resource manager has already been started");
		}

		// start queue worker
		queueWorker = new RequestQueueWorker();

		queueWorkerService = Executors.newFixedThreadPool(1);
		queueWorkerService.execute(queueWorker);

		this.groupManager = resourceGroupManager;
		groupManager.addResourceGroupManagerListener(this);

		for (int id : groupManager.getAllResourceGroupIds()) {
			ResourceGroup group = groupManager.getResourceGroup(id);
			resourceGroupAdded(group);
		}
	}

	@Override
	public synchronized void addResourceManagerListener(ResourceManagerListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	@Override
	public synchronized void removeResourceManagerListener(ResourceManagerListener listener) {
		listeners.remove(listener);
	}

	@Override
	public void handleResourceRequest(ResourceRequest request) {
		WaitingResourceRequest waitingRequest = new WaitingResourceRequest(request);
		fireRequestEnqueued(waitingRequest);
		synchronized (queueWorker) {
			queue.add(waitingRequest);
			queueWorker.handleNewRequest(waitingRequest);
		}
	}

	@Override
	public void shutdown() {
		if (queueWorkerService != null) {
			queueWorkerService.shutdownNow();
			queueWorkerService = null;
		}
		queue.clear();
		runningJobs.clear();
		queueWorker = null;
	}

	@Override
	public int getTotalQueueSize() {
		synchronized (queueWorker) {
			return queue.size();
		}
	}

	@Override
	public List<? extends ManagedResourceQuery> getAllRunningQueries() {
		List<WaitingResourceRequest> result;
		synchronized (queueWorker) {
			result = new ArrayList<WaitingResourceRequest>(runningJobs);
		}
		return result;
	}

	@Override
	public void resourceAdded(Resource resource) {
		resource.addResourceListener(this);

		if (resource.getState() == ResourceState.READY) {
			putIntoIdle(resource);
			queueWorker.handleResourceAvailable(resource);
		}
	}

	private void registerResourceMBean(Resource resource) {
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		try {
			int id = nextResourceId.incrementAndGet();

			ObjectName name = new ObjectName("org.aludratest.cloud:00=resources,01=" + resource.getResourceType().getName()
					+ ",name=" + id);
			ResourceInfo info = ResourceInfo.create(resource);
			mbs.registerMBean(info, name);
			resourceIds.put(resource, Integer.valueOf(id));
		}
		catch (JMException e) {
			LOGGER.warn("Could not register resource in MBean server", e);
		}
	}

	private synchronized void putIntoIdle(Resource resource) {
		Set<Resource> idles = idleResources.get(resource.getResourceType());
		if (idles == null) {
			idleResources.put(resource.getResourceType(), idles = new HashSet<Resource>());
		}
		idles.add(resource);
	}

	private synchronized boolean isInIdle(Resource resource) {
		Set<Resource> idles = idleResources.get(resource.getResourceType());
		if (idles != null) {
			return idles.contains(resource);
		}

		return false;
	}

	private synchronized void removeFromIdle(Resource resource) {
		Set<Resource> idles = idleResources.get(resource.getResourceType());
		if (idles != null) {
			idles.remove(resource);
		}
	}

	@Override
	public void resourceRemoved(Resource resource) {
		resource.removeResourceListener(this);

		Integer id = resourceIds.remove(resource);
		if (id != null) {
			// find and remove registration in MBean server
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			try {
				Set<ObjectInstance> instances = mbs.queryMBeans(new ObjectName("org.aludratest.cloud:00=resources,01="
						+ resource.getResourceType().getName() + ",name=" + id), null);
				if (!instances.isEmpty()) {
					mbs.unregisterMBean(instances.iterator().next().getObjectName());
				}
			}
			catch (JMException e) {
				LOGGER.warn("Could not unregister resource from MBean server", e);
			}
		}
	}
	
	@Override
	public void resourceGroupAdded(ResourceGroup group) {
		group.getResourceCollection().addResourceCollectionListener(this);
		for (ResourceStateHolder rsh : group.getResourceCollection()) {
			Resource res = (Resource) rsh;
			res.addResourceListener(this);
			if (rsh.getState() == ResourceState.READY) {
				resourceAdded(res);
			}
			registerResourceMBean(res);
		}
	}

	@Override
	public void resourceGroupRemoved(ResourceGroup group) {
		group.getResourceCollection().removeResourceCollectionListener(this);
		for (ResourceStateHolder rsh : group.getResourceCollection()) {
			resourceRemoved((Resource) rsh);
		}
	}

	@Override
	public void resourceStateChanged(Resource resource, ResourceState previousState, ResourceState newState) {
		if (previousState != newState) {
			switch (previousState) {
				case IN_USE:
					checkReleasedResource(resource);
					break;
				case READY:
					removeFromIdle(resource);
					break;
				default:
					// nothing to do
			}

			switch (newState) {
				case READY:
					putIntoIdle(resource);
					queueWorker.handleResourceAvailable(resource);
					break;
				default:
					// nothing to do
			}
		}
	}

	private void fireRequestEnqueued(ManagedResourceQuery request) {
		List<ResourceManagerListener> ls;
		synchronized (this) {
			ls = new ArrayList<ResourceManagerListener>(listeners);
		}

		for (ResourceManagerListener l : ls) {
			l.requestEnqueued(request);
		}
	}

	private void fireResourceReleased(ManagedResourceQuery request, Resource releasedResource) {
		List<ResourceManagerListener> ls;
		synchronized (this) {
			ls = new ArrayList<ResourceManagerListener>(listeners);
		}

		for (ResourceManagerListener l : ls) {
			l.resourceReleased(request, releasedResource);
		}
	}

	private void fireError(ManagedResourceQuery request, String errorMessage) {
		fireError(request, errorMessage, null);

	}

	private void fireError(ManagedResourceQuery request, String errorMessage, Throwable cause) {
		List<ResourceManagerListener> ls;
		synchronized (this) {
			ls = new ArrayList<ResourceManagerListener>(listeners);
		}

		for (ResourceManagerListener l : ls) {
			l.requestError(request, errorMessage, cause);
		}
	}

	private boolean fireResourceReceived(ManagedResourceQuery request, Resource resource) {
		List<ResourceManagerListener> ls;
		synchronized (this) {
			ls = new ArrayList<ResourceManagerListener>(listeners);
		}

		for (ResourceManagerListener l : ls) {
			if (l.resourceAvailable(request, resource)) {
				return true;
			}
		}

		return false;
	}

	private void checkReleasedResource(Resource resource) {
		WaitingResourceRequest request = null;
		synchronized (queueWorker) {
			for (WaitingResourceRequest r : runningJobs) {
				if (resource.equals(r.getReceivedResource())) {
					request = r;
					break;
				}
			}
		}

		if (request != null) {
			LOGGER.debug("Request " + request + " has released resource " + resource);
			synchronized (queueWorker) {
				runningJobs.remove(request);
			}
			request.resourceReleasedTime = DateTime.now();
			fireResourceReleased(request, resource);
		}
	}

	/* MBean methods */
	@Override
	public int getIdleResourceCount() {
		int result = 0;
		synchronized (this) {
			for (Set<Resource> rs : idleResources.values()) {
				result += rs.size();
			}
		}
		return result;
	}

	@Override
	public int getRunningQueriesCount() {
		return getAllRunningQueries().size();
	}

	private class RequestQueueWorker implements Runnable {

		private List<Object> events = new ArrayList<Object>();

		public synchronized void handleNewRequest(WaitingResourceRequest request) {
			events.add(request);
			notify();
		}

		public synchronized void handleResourceAvailable(Resource resource) {
			events.add(resource);
			notify();
		}

		@Override
		public void run() {
			while (!Thread.interrupted()) {
				Object nextEvent;
				synchronized (this) {
					// wait for an event
					while (events.isEmpty()) {
						try {
							wait(5000);
						}
						catch (InterruptedException e) {
							return;
						}
					}

					nextEvent = events.remove(0);
				}

				if (nextEvent instanceof WaitingResourceRequest) {
					checkResourceForRequest((WaitingResourceRequest) nextEvent);
				}
				else if (nextEvent instanceof Resource) {
					Resource res = (Resource) nextEvent;
					if (res.getState() == ResourceState.READY && isInIdle(res) && !checkRequestForResource(res)) {
						// add to idles
						putIntoIdle(res);
					}
				}
			}
		}

		private void checkResourceForRequest(WaitingResourceRequest request) {
			LOGGER.debug("Checking resource for request " + request);
			// determine available resources for this request
			ResourceType resourceType = request.getRequest().getResourceType();

			ResourceModule module = CloudManagerApp.getInstance().getResourceModule(resourceType);
			if (module == null) {
				fireError(request, "No resources of type " + resourceType + " available in this manager.");
				return;
			}

			// check if there are ANY resource groups for this type, where the user has access
			User user = request.getRequest().getRequestingUser();
			boolean groupFound = false;
			for (int groupId : groupManager.getAllResourceGroupIds()) {
				ResourceGroup group = groupManager.getResourceGroup(groupId);
				if (resourceType.equals(group.getResourceType()) && group.getResourceCollection().getResourceCount() > 0) {
					if (group instanceof AuthorizingResourceGroup) {
						AuthorizingResourceGroup authGroup = (AuthorizingResourceGroup) group;
						if (!authGroup.isLimitingUsers() || authGroup.isUserAuthorized(user)) {
							groupFound = true;
							break;
						}
					}
					else {
						groupFound = true;
						break;
					}
				}
			}

			if (!groupFound) {
				fireError(request, "No resources of type " + resourceType + " for requesting user available in this manager.");
				return;
			}

			// check that user has access, and not yet exceeded max resource count
			ResourceTypeAuthorizationConfig authStore;
			try {
				authStore = CloudManagerApp.getInstance().getResourceTypeAuthorizationStore()
						.loadResourceTypeAuthorizations(resourceType);
			}
			catch (StoreException e) {
				LOGGER.error("Could not load resource type authorization for resource type " + resourceType, e);
				return;
			}

			ResourceTypeAuthorization auth = authStore.getResourceTypeAuthorizationForUser(user);
			if (auth == null || auth.getMaxResources() < 1) {
				fireError(request, "User does not have access to resource type " + resourceType);
				return;
			}

			Map<User, Integer> applicableUsers = getApplicableUsersMap(resourceType, authStore);
			if (!applicableUsers.containsKey(user)) {
				LOGGER.debug("Request " + request + " left in queue as user already uses max no of resources");
				return;
			}
			else {
				LOGGER.debug("User " + user.getName() + " uses " + applicableUsers.get(user) + " of max "
						+ authStore.getResourceTypeAuthorizationForUser(user).getMaxResources() + " resources");
			}

			// check idle resources
			Set<Resource> idles;
			synchronized (this) {
				idles = idleResources.get(resourceType);
				if (idles == null || idles.isEmpty()) {
					LOGGER.debug("No resources available for request " + request + ", leaving request in queue (queue size: "
							+ queue.size() + ")");
					return; // no resources available
				}
				// copy map to avoid concurrent modification
				idles = new HashSet<Resource>(idles);
			}
			
			List<? extends Resource> availables = module.getAvailableResources(request.getRequest(), idles);
			if (availables.isEmpty()) {
				LOGGER.debug("No resources available yet for incoming request, enqueueing request.");
				return; // module says no resources available
			}

			synchronized (this) {
				// further reduce availables in case something changed
				availables = new ArrayList<Resource>(availables);
				availables.retainAll(idles);

				if (availables.isEmpty()) {
					// bad luck.
					return;
				}
				
				// now use resource logic to give other requests in queue a chance
				checkRequestForResource(availables.get(0), module);
			}
		}

		private boolean checkRequestForResource(Resource resource) {
			ResourceModule module = CloudManagerApp.getInstance().getResourceModule(resource.getResourceType());
			if (module == null) {
				return false;
			}

			for (int groupId : groupManager.getAllResourceGroupIds()) {
				ResourceGroup group = groupManager.getResourceGroup(groupId);
				// could be gone in the meantime
				if (group != null && group.getResourceType().equals(module.getResourceType())) {
					LOGGER.debug("Checking if there is a waiting request for resource " + resource + "...");
					return checkRequestForResource(resource, module);
				}
			}

			return false;
		}

		private Map<User, Integer> getApplicableUsersMap(ResourceType resourceType,
				ResourceTypeAuthorizationConfig authStore) {
			// determine users not yet using their maximum resource count for this resource type
			Map<User, Integer> applicableUsers = new HashMap<User, Integer>();

			for (User user : authStore.getConfiguredUsers()) {
				applicableUsers.put(user, Integer.valueOf(0));
			}

			synchronized (this) {
				for (WaitingResourceRequest request : runningJobs) {
					if (request.getRequest().getResourceType().equals(resourceType)) {
						User u = request.getRequest().getRequestingUser();
						Integer i = applicableUsers.get(u);
						if (i == null) {
							i = Integer.valueOf(0);
						}
						applicableUsers.put(u, Integer.valueOf(i.intValue() + 1));
					}
				}
			}

			Iterator<Map.Entry<User, Integer>> mapIter = applicableUsers.entrySet().iterator();
			while (mapIter.hasNext()) {
				Map.Entry<User, Integer> entry = mapIter.next();
				ResourceTypeAuthorization auth = authStore.getResourceTypeAuthorizationForUser(entry.getKey());
				if (auth == null || auth.getMaxResources() <= entry.getValue().intValue()) {
					mapIter.remove();
				}
			}

			return applicableUsers;
		}

		private boolean checkRequestForResource(Resource resource, ResourceModule module) {
			// find all requests which are applicable for this resource
			List<WaitingResourceRequest> matchingRequests = new ArrayList<WaitingResourceRequest>();
			synchronized (this) {
				for (WaitingResourceRequest request : queue) {
					if (request.getRequest().getResourceType().equals(resource.getResourceType())) {
						matchingRequests.add(request);
					}
				}
			}

			if (matchingRequests.isEmpty()) {
				LOGGER.debug("No matching requests found for resource " + resource + " (queue size: " + queue.size() + ")");
				return false;
			}

			ResourceTypeAuthorizationConfig authStore;
			try {
				authStore = CloudManagerApp.getInstance().getResourceTypeAuthorizationStore()
						.loadResourceTypeAuthorizations(resource.getResourceType());
			}
			catch (StoreException e) {
				LOGGER.error("Could not load resource type authorization for resource type " + resource.getResourceType(), e);
				return true;
			}

			Map<User, Integer> applicableUsers = getApplicableUsersMap(resource.getResourceType(), authStore);

			Iterator<WaitingResourceRequest> iter = matchingRequests.iterator();
			while (iter.hasNext()) {
				WaitingResourceRequest request = iter.next();
				// remove all requests not having an "applicable" user
				if (!applicableUsers.containsKey(request.getRequest().getRequestingUser())) {
					iter.remove();
				}
				else {
					List<? extends Resource> ls = module.getAvailableResources(request.getRequest(),
							Collections.singleton(resource));
					if (ls.isEmpty()) {
						iter.remove();
					}
				}
			}

			if (!matchingRequests.isEmpty()) {
				// count all resources for scoring
				int resCnt = 0;
				for (int groupId : groupManager.getAllResourceGroupIds()) {
					ResourceGroup group = groupManager.getResourceGroup(groupId);
					if (group != null) {
						resCnt += group.getResourceCollection().getResourceCount();
					}
				}

				// order them by scoring
				WaitingRequestComparator comp = new WaitingRequestComparator(authStore, applicableUsers, resCnt);
				Collections.sort(matchingRequests, comp);

				// they may have changed in the meantime...
				synchronized (this) {
					matchingRequests.retainAll(queue);
				}

				for (WaitingResourceRequest request : matchingRequests) {
					synchronized (this) {
						queue.remove(request);
					}
					request.receivedResource = resource;
					request.resourceReceivedTime = DateTime.now();

					if (fireResourceReceived(request, resource)) {
						LOGGER.debug("Resource " + resource + " assigned successfully to request " + request
								+ ", removing from idle cache");
						removeFromIdle(resource);
						synchronized (DefaultResourceManagerImpl.this) {
							runningJobs.add(request);
						}
						return true;
					}
					else {
						LOGGER.debug("Request " + request + " did not want to consume resource " + resource
								+ ", releasing resource.");
						request.resourceReleasedTime = DateTime.now();
						fireResourceReleased(request, resource);
					}
				}

				return true;
			}
			LOGGER.debug("No matching waiting requests found for resource " + resource);
			return false;
		}
	}

	private static class WaitingResourceRequest implements ManagedResourceQuery {
		
		private ResourceRequest request;
		
		private DateTime enqueueStartTime;
		
		private DateTime resourceReceivedTime;
		
		private DateTime resourceReleasedTime;
		
		private Resource receivedResource;
		
		public WaitingResourceRequest(ResourceRequest request) {
			if (request == null) {
				throw new IllegalArgumentException("request is null");
			}
			this.request = request;
			enqueueStartTime = new DateTime();
		}

		@Override
		public ResourceRequest getRequest() {
			return request;
		}

		@Override
		public DateTime getEnqueueStartTime() {
			return enqueueStartTime;
		}

		@Override
		public DateTime getResourceReceivedTime() {
			return resourceReceivedTime;
		}

		@Override
		public DateTime getResourceReleasedTime() {
			return resourceReleasedTime;
		}

		@Override
		public Resource getReceivedResource() {
			return receivedResource;
		}

		@Override
		public int hashCode() {
			if (receivedResource != null) {
				return receivedResource.hashCode();
			}
			return request.hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (obj == this) {
				return true;
			}
			if (obj.getClass() != getClass()) {
				return false;
			}

			WaitingResourceRequest req = (WaitingResourceRequest) obj;
			return req.request.equals(request);
		}

		@Override
		public String toString() {
			return request.toString();
		}

	}

	private class WaitingRequestComparator implements Comparator<WaitingResourceRequest> {

		private ResourceTypeAuthorizationConfig authConfig;

		private int totalResourceCount;

		private Map<User, Integer> userJobCount;

		public WaitingRequestComparator(ResourceTypeAuthorizationConfig authConfig, Map<User, Integer> userJobCount,
				int totalResourceCount) {
			this.authConfig = authConfig;
			this.userJobCount = userJobCount;
			this.totalResourceCount = totalResourceCount;
		}

		private static final int NORMALIZE_DIFF = 20;

		@Override
		public int compare(WaitingResourceRequest req1, WaitingResourceRequest req2) {
			User u1 = req1.getRequest().getRequestingUser();
			User u2 = req2.getRequest().getRequestingUser();
			
			ResourceTypeAuthorization auth1 = authConfig.getResourceTypeAuthorizationForUser(u1);
			ResourceTypeAuthorization auth2 = authConfig.getResourceTypeAuthorizationForUser(u2);
			
			return calculateRequestScore(req1, auth1) - calculateRequestScore(req2, auth2);
		}

		private int calculateRequestScore(WaitingResourceRequest request, ResourceTypeAuthorization auth) {
			DateTime now = DateTime.now();

			int normalizedNiceLevel = auth.getNiceLevel() - NORMALIZE_DIFF;
			double userMax = Math.min(auth.getMaxResources(), totalResourceCount);
			if (userMax == 0) {
				return 0;
			}
			double userRunCount = userJobCount.get(request.getRequest().getRequestingUser()).intValue();
			normalizedNiceLevel -= (int) ((userRunCount / userMax) * normalizedNiceLevel);

			long millisWaiting = new Duration(request.getEnqueueStartTime(), now).getMillis();
			if (millisWaiting == 0) {
				millisWaiting = 1;
			}
			return (int) (millisWaiting * normalizedNiceLevel);
		}

	}

}
