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

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.aludratest.cloud.event.ManagedResourceRequestCanceledEvent;
import org.aludratest.cloud.event.ManagedResourceRequestStateChangedEvent;
import org.aludratest.cloud.event.ResourceRequestReceivedEvent;
import org.aludratest.cloud.manager.InsufficientResourcePrivilegesException;
import org.aludratest.cloud.manager.ManagedResourceRequest;
import org.aludratest.cloud.manager.NoMatchingResourcesAvailableException;
import org.aludratest.cloud.manager.ResourceManager;
import org.aludratest.cloud.manager.ResourceManagerException;
import org.aludratest.cloud.request.ResourceRequest;
import org.aludratest.cloud.resource.OrphanedListener;
import org.aludratest.cloud.resource.Resource;
import org.aludratest.cloud.resource.ResourceCollectionListener;
import org.aludratest.cloud.resource.ResourceListener;
import org.aludratest.cloud.resource.ResourceState;
import org.aludratest.cloud.resource.ResourceStateHolder;
import org.aludratest.cloud.resource.ResourceType;
import org.aludratest.cloud.resource.UsableResource;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorization;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationConfig;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationStore;
import org.aludratest.cloud.resourcegroup.ResourceGroup;
import org.aludratest.cloud.resourcegroup.ResourceGroupManager;
import org.aludratest.cloud.resourcegroup.ResourceGroupManagerListener;
import org.aludratest.cloud.user.StoreException;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class ResourceManagerImpl implements ResourceManager {

	private static final long SECONDS_BEFORE_REQUEST_REMOVAL = 60;

	private Queue<ManagedResourceRequestImpl> managedRequests = new ConcurrentLinkedQueue<ManagedResourceRequestImpl>();

	private ResourceGroupManager resourceGroupManager;

	private ResourceAssigner resourceAssigner;

	private Thread resourceAssignerThread;

	private ScheduledExecutorService requestRemoveService;

	@Autowired
	private ResourceTypeAuthorizationStore authorizationStore;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Override
	public void start(ResourceGroupManager resourceGroupManager) {
		this.resourceGroupManager = resourceGroupManager;
		requestRemoveService = Executors.newScheduledThreadPool(1);
		resourceAssigner = new ResourceAssigner(resourceGroupManager);
		resourceAssignerThread = new Thread(resourceAssigner);
		resourceAssignerThread.start();
	}

	@Override
	public ManagedResourceRequest handleResourceRequest(ResourceRequest request) throws ResourceManagerException {
		// check if there are any matching resources available for this request
		if (countResources(resourceGroupManager, request.getResourceType()) == 0) {
			throw new NoMatchingResourcesAvailableException("No resources of requested type available in this resource manager");
		}

		// does user have right to access that resource type?
		try {
			ResourceTypeAuthorizationConfig authConfig = authorizationStore
					.loadResourceTypeAuthorizations(request.getResourceType());
			if (authConfig == null) {
				throw new InsufficientResourcePrivilegesException("Unknown resource type " + request.getResourceType().getName());
			}
			ResourceTypeAuthorization auth = authConfig.getResourceTypeAuthorizationForUser(request.getRequestingUser());
			if (auth == null || auth.getMaxResources() == 0) {
				throw new InsufficientResourcePrivilegesException(
						"User does not have the right to receive resources of type " + request.getResourceType().getName());
			}
		}
		catch (StoreException se) {
			throw new InsufficientResourcePrivilegesException("Could not load resource authorizations", se);
		}

		ManagedResourceRequestImpl managedRequest = new ManagedResourceRequestImpl(request);
		managedRequests.add(managedRequest);
		resourceAssigner.requestAdded(managedRequest);

		eventPublisher.publishEvent(new ResourceRequestReceivedEvent(managedRequest));

		return managedRequest;
	}

	@Override
	public void shutdown() {
		if (resourceAssignerThread != null) {
			resourceAssignerThread.interrupt();
			resourceAssignerThread = null;
		}
		if (requestRemoveService != null) {
			requestRemoveService.shutdown();
			requestRemoveService = null;
		}
	}

	@Override
	public Iterator<? extends ManagedResourceRequest> getManagedRequests() {
		return managedRequests.iterator();
	}

	private void handleReadyResourceLost(Resource resource) {
		// if there is a request having this resource assigned and being in state READY, put it back to WAITING
		managedRequests.stream()
				.filter(request -> resource.equals(request.assignedResource)
						&& request.getState() == ManagedResourceRequest.State.READY)
				.forEach(request -> {
					request.setAssignedResource(null);
					resourceAssigner.requestAdded(request);
				});
	}

	private ManagedResourceRequestImpl assignResourceToRequest(Resource resource) {
		// find request having best score
		try {
			Optional<ManagedResourceRequestImpl> optRequest = managedRequests.stream()
					.filter(request -> request.getState() == ManagedResourceRequest.State.WAITING && request
							.getRequest().getResourceType().getName().equals(resource.getResourceType().getName()))
					.min(new RequestScoreComparator(resource.getResourceType(),
							countResources(resourceGroupManager, resource.getResourceType()), managedRequests,
							authorizationStore.loadResourceTypeAuthorizations(resource.getResourceType())));
			if (optRequest.isPresent()) {
				ManagedResourceRequestImpl request = optRequest.get();
				request.setAssignedResource(resource);
				return request;
			}
			else {
				return null;
			}
		}
		catch (StoreException se) {
			LogFactory.getLog(ResourceManagerImpl.class).error("Could not load resource type authorizations", se);
			return null;
		}
	}

	private void cancelRequest(ManagedResourceRequest request) {
		if (managedRequests.contains(request)) {
			resourceAssigner.removeTaskForRequest(request);
			managedRequests.remove(request);
			eventPublisher.publishEvent(new ManagedResourceRequestCanceledEvent(request));
		}
	}

	private void scheduleRequestForRemoval(ManagedResourceRequest request) {
		requestRemoveService.schedule(() -> {
			managedRequests.remove(request);
		}, SECONDS_BEFORE_REQUEST_REMOVAL, TimeUnit.SECONDS);
	}

	private class ResourceAssigner
			implements Runnable, ResourceGroupManagerListener, ResourceCollectionListener, ResourceListener {

		private ResourceGroupManager groupManager;

		private Queue<Resource> resourceQueue = new ConcurrentLinkedQueue<>();

		private Queue<ManagedResourceRequestImpl> requestQueue = new ConcurrentLinkedQueue<>();

		private ResourceAssigner(ResourceGroupManager groupManager) {
			this.groupManager = groupManager;
			groupManager.addResourceGroupManagerListener(this);
			for (int groupId : groupManager.getAllResourceGroupIds()) {
				resourceGroupAdded(groupManager.getResourceGroup(groupId));
			}
		}

		@Override
		public void run() {
			while (!Thread.interrupted()) {
				while (resourceQueue.isEmpty() && (requestQueue.isEmpty() || getAvailableResources().isEmpty())) {
					synchronized (this) {
						try {
							wait();
						}
						catch (InterruptedException e) {
							return;
						}
					}
				}

				// always work resources; only if no resources to check, work requests
				while (!resourceQueue.isEmpty()) {
					Resource res = resourceQueue.poll();
					if (res.getState() == ResourceState.READY) {
						ManagedResourceRequestImpl request = assignResourceToRequest(res);
						if (request != null) {
							removeTaskForRequest(request);
						}
					}
				}

				while (resourceQueue.isEmpty() && !requestQueue.isEmpty()) {
					// collect available and non-assigned resources from ResourceGroupManager
					Collection<Resource> allResources = getAvailableResources();

					boolean found = false;
					for (Resource res : allResources) {
						if (res.getState() == ResourceState.READY) {
							ManagedResourceRequestImpl request = assignResourceToRequest(res);
							if (request != null) {
								found = true;
								removeTaskForRequest(request);
							}
						}
					}

					if (!found) {
						// wait a while before continuing, to avoid endless loop
						try {
							Thread.sleep(1000);
						}
						catch (InterruptedException e) {
							return;
						}
						break;
					}
				}
			}

			groupManager.removeResourceGroupManagerListener(this);
		}

		private Collection<Resource> getAvailableResources() {
			// get all required Resource Types
			Set<String> resourceTypeNames = requestQueue.stream().map(request -> request.getRequest().getResourceType().getName())
					.collect(Collectors.toCollection(HashSet::new));

			// collect available and non-assigned resources from ResourceGroupManager
			Collection<Resource> allResources = collectReadyResources(groupManager);
			if (allResources.isEmpty()) {
				return allResources;
			}

			for (ManagedResourceRequestImpl request : managedRequests) {
				if (request.getState() != ManagedResourceRequest.State.ORPHANED
						&& request.getState() != ManagedResourceRequest.State.FINISHED && request.assignedResource != null) {
					allResources.remove(request.assignedResource);
				}
			}

			// remove resources not required by any request
			return allResources.stream().filter(resource -> resourceTypeNames.contains(resource.getResourceType().getName()))
					.collect(Collectors.toList());
		}

		private void requestAdded(ManagedResourceRequestImpl request) {
			requestQueue.add(request);
			synchronized (this) {
				notify();
			}
		}

		@Override
		public void resourceGroupAdded(ResourceGroup group) {
			for (ResourceStateHolder res : group.getResourceCollection()) {
				resourceAdded((Resource) res);
			}
		}

		@Override
		public void resourceGroupRemoved(ResourceGroup group) {
			for (ResourceStateHolder res : group.getResourceCollection()) {
				resourceRemoved((Resource) res);
			}
			group.getResourceCollection().removeResourceCollectionListener(this);
		}

		@Override
		public void resourceAdded(Resource resource) {
			resource.addResourceListener(this);
			resourceQueue.add(resource);
			synchronized (this) {
				notify();
			}
		}

		@Override
		public void resourceRemoved(Resource resource) {
			resource.removeResourceListener(this);
			removeTaskForResource(resource);
			handleReadyResourceLost(resource);
		}

		@Override
		public void resourceStateChanged(Resource resource, ResourceState previousState, ResourceState newState) {
			if ((newState == ResourceState.DISCONNECTED || newState == ResourceState.ERROR)
					&& previousState == ResourceState.READY) {
				removeTaskForResource(resource);
				handleReadyResourceLost(resource);
			}
			else if (newState == ResourceState.READY && previousState != ResourceState.READY) {
				resourceQueue.add(resource);
				synchronized (this) {
					notify();
				}
			}
		}

		private void removeTaskForResource(Resource resource) {
			synchronized (resourceQueue) {
				Iterator<Resource> iter = resourceQueue.iterator();
				while (iter.hasNext()) {
					Resource task = iter.next();
					if (resource.equals(task)) {
						iter.remove();
					}
				}
			}
		}

		private void removeTaskForRequest(ManagedResourceRequest request) {
			synchronized (requestQueue) {
				Iterator<ManagedResourceRequestImpl> iter = requestQueue.iterator();
				while (iter.hasNext()) {
					ManagedResourceRequestImpl task = iter.next();
					if (request == task) {
						iter.remove();
					}
				}
			}
		}

	}

	private class ManagedResourceRequestImpl implements ManagedResourceRequest, RequestScoreHolder, Future<Resource>,
			ResourceListener, OrphanedListener {

		private Resource assignedResource;

		private volatile boolean canceled;

		private ZonedDateTime creationTimestamp;

		private volatile State state;

		private Map<State, ZonedDateTime> statusLog = new ConcurrentHashMap<State, ZonedDateTime>();

		private final ResourceRequest request;

		private long idleStart;

		private Optional<Integer> requestScore = Optional.empty();

		private ManagedResourceRequestImpl(ResourceRequest request) {
			creationTimestamp = ZonedDateTime.now();
			this.request = request;
			idleStart = System.currentTimeMillis();
			setState(State.WAITING);
		}

		@Override
		public State getState() {
			return state;
		}

		@Override
		public long getIdleTimeMs() {
			return System.currentTimeMillis() - idleStart;
		}

		@Override
		public long getWaitTimeMs() {
			if (statusLog.containsKey(State.READY)) {
				return creationTimestamp.until(statusLog.get(State.READY), ChronoUnit.MILLIS);
			}
			return creationTimestamp.until(ZonedDateTime.now(), ChronoUnit.MILLIS);
		}

		private void touch() {
			idleStart = System.currentTimeMillis();
		}

		@Override
		public ResourceRequest getRequest() {
			return request;
		}

		@Override
		public void setRequestScore(int requestScore) {
			this.requestScore = Optional.of(requestScore);
		}

		@Override
		public Optional<Integer> getRequestScore() {
			return requestScore;
		}

		@Override
		public ZonedDateTime getCreationTimestamp() {
			return creationTimestamp;
		}

		@Override
		public Future<Resource> getResourceFuture() {
			return this;
		}

		@Override
		public void markOrphaned() {
			State oldState = this.state;
			setState(State.ORPHANED);
			scheduleRequestForRemoval(this);
			if (assignedResource != null) {
				assignedResource.removeResourceListener(this);
				if (oldState == State.READY) {
					// simulate that resource has just become available again
					resourceAssigner.resourceStateChanged(assignedResource, ResourceState.IN_USE, ResourceState.READY);
				}
			}
		}

		private void setState(State state) {
			State oldState = this.state;
			if (this.state != state) {
				this.state = state;
				if (!statusLog.containsKey(state)) {
					statusLog.put(state, ZonedDateTime.now());
				}
				eventPublisher.publishEvent(new ManagedResourceRequestStateChangedEvent(this, oldState, state));
			}
		}

		private synchronized void setAssignedResource(Resource assignedResource) {
			if (this.assignedResource != null) {
				this.assignedResource.removeResourceListener(this);
			}
			this.assignedResource = assignedResource;
			if (assignedResource != null) {
				assignedResource.addResourceListener(this);
				if (assignedResource instanceof UsableResource) {
					((UsableResource) assignedResource).addOrphanedListener(this);
				}

				setState(State.READY);
			}
			else {
				setState(State.WAITING);
			}
			notifyAll();
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if (assignedResource != null) {
				return false;
			}

			synchronized (this) {
				canceled = true;
				notify();
			}

			cancelRequest(this);
			return true;
		}

		@Override
		public boolean isCancelled() {
			return canceled;
		}

		@Override
		public boolean isDone() {
			return assignedResource != null;
		}

		@Override
		public synchronized Resource get() throws InterruptedException, ExecutionException {
			touch();
			while (assignedResource == null && !canceled) {
				wait();
			}
			if (canceled) {
				throw new CancellationException();
			}

			return assignedResource;
		}

		@Override
		public synchronized Resource get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException {
			touch();
			if (canceled) {
				throw new CancellationException();
			}
			if (assignedResource == null) {
				wait(unit.toMillis(timeout));
			}
			if (canceled) {
				throw new CancellationException();
			}

			if (assignedResource == null) {
				throw new TimeoutException("Timeout waiting for resource to be assigned");
			}

			return assignedResource;
		}

		@Override
		public void resourceStateChanged(Resource resource, ResourceState previousState, ResourceState newState) {
			if (previousState != ResourceState.IN_USE && newState == ResourceState.IN_USE) {
				setState(State.WORKING);
			}
			if (previousState == ResourceState.IN_USE && newState != ResourceState.IN_USE) {
				if (state != State.ORPHANED) {
					setState(State.FINISHED);
				}
				resource.removeResourceListener(this);
				if (resource instanceof UsableResource) {
					((UsableResource) resource).removeOrphanedListener(this);
				}
				scheduleRequestForRemoval(this);
			}
		}

		@Override
		public void resourceOrphaned(Resource resource) {
			if (resource == assignedResource) {
				// do not call markOrphaned() here, as it would also call
				// scheduleRequestForRemoval,
				// what the state listener after stopUsing() will also cause.
				setState(State.ORPHANED);
				// should always be true here...
				if (resource instanceof UsableResource) {
					((UsableResource) resource).stopUsing();
				}
			}
		}
	}

	private static Collection<Resource> collectReadyResources(ResourceGroupManager groupManager) {
		List<Resource> result = new LinkedList<Resource>();

		for (int groupId : groupManager.getAllResourceGroupIds()) {
			ResourceGroup group = groupManager.getResourceGroup(groupId);
			for (ResourceStateHolder rsh : group.getResourceCollection()) {
				if (rsh instanceof Resource && rsh.getState() == ResourceState.READY) {
					result.add((Resource) rsh);
				}
			}
		}

		return result;
	}

	private static int countResources(ResourceGroupManager groupManager, ResourceType resourceType) {
		int result = 0;

		for (int groupId : groupManager.getAllResourceGroupIds()) {
			ResourceGroup group = groupManager.getResourceGroup(groupId);
			for (ResourceStateHolder rsh : group.getResourceCollection()) {
				if (rsh instanceof Resource && ((Resource) rsh).getResourceType().getName().equals(resourceType.getName())) {
					result++;
				}
			}
		}

		return result;
	}

}
