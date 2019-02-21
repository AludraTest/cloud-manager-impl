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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.aludratest.cloud.impl.MockResource;
import org.aludratest.cloud.impl.MockResourceGroup;
import org.aludratest.cloud.impl.MockResourceGroupManager;
import org.aludratest.cloud.impl.MockResourceRequest;
import org.aludratest.cloud.impl.MockUser;
import org.aludratest.cloud.impl.test.MockResourceTypeAuthorizationStore;
import org.aludratest.cloud.manager.InsufficientResourcePrivilegesException;
import org.aludratest.cloud.manager.ManagedResourceRequest;
import org.aludratest.cloud.manager.ResourceManagerException;
import org.aludratest.cloud.resource.Resource;
import org.aludratest.cloud.resource.ResourceState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/test-applicationContext.xml" })
public class ResourceManagerImplTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Test(expected = InsufficientResourcePrivilegesException.class)
	public void testNoSuchUser() throws ResourceManagerException {

		ResourceManagerImpl resourceManager = (ResourceManagerImpl) applicationContext.getAutowireCapableBeanFactory()
				.autowire(ResourceManagerImpl.class, AutowireCapableBeanFactory.AUTOWIRE_NO, true);

		List<MockResource> resources = prepareResourceManager(resourceManager, 2);

		MockUser user = new MockUser("TestUser1");
		MockResourceRequest request = new MockResourceRequest(user, resources.get(0).getResourceType(), 0, "Test Job");

		resourceManager.handleResourceRequest(request);
	}

	@Test(expected = InsufficientResourcePrivilegesException.class)
	public void testNoUserRights() throws ResourceManagerException {
		ResourceManagerImpl resourceManager = (ResourceManagerImpl) applicationContext.getAutowireCapableBeanFactory()
				.autowire(ResourceManagerImpl.class, AutowireCapableBeanFactory.AUTOWIRE_NO, true);

		List<MockResource> resources = prepareResourceManager(resourceManager, 2);

		MockUser user = new MockUser("TestUser1");

		MockResourceTypeAuthorizationStore store = applicationContext.getBean(MockResourceTypeAuthorizationStore.class);
		store.addAuthorization(user, "anotherType", 10, 0);

		MockUser notTheUser = new MockUser("TestUser2");
		store.addAuthorization(notTheUser, "mock", 10, 0);

		MockResourceRequest request = new MockResourceRequest(user, resources.get(0).getResourceType(), 0, "Test Job");
		resourceManager.handleResourceRequest(request);
	}

	@Test
	public void testStandardGet()
			throws ResourceManagerException, InterruptedException, ExecutionException, TimeoutException {
		ResourceManagerImpl resourceManager = (ResourceManagerImpl) applicationContext.getAutowireCapableBeanFactory()
				.autowire(ResourceManagerImpl.class, AutowireCapableBeanFactory.AUTOWIRE_NO, true);

		List<MockResource> resources = prepareResourceManager(resourceManager, 2);

		MockUser user = new MockUser("TestUser1");

		MockResourceTypeAuthorizationStore store = applicationContext.getBean(MockResourceTypeAuthorizationStore.class);
		store.addAuthorization(user, "mock", 10, 0);

		MockResourceRequest request = new MockResourceRequest(user, resources.get(0).getResourceType(), 0, "Test Job");
		ManagedResourceRequest waitingRequest = resourceManager.handleResourceRequest(request);
		assertEquals(ManagedResourceRequest.State.WAITING, waitingRequest.getState());

		// use SECOND resource to be sure the disconnected first one is not used
		resources.get(1).setState(ResourceState.READY);

		Resource res = waitingRequest.getResourceFuture().get(200, TimeUnit.MILLISECONDS);
		assertEquals(resources.get(1), res);
	}

	@Test
	public void testUserNiceLevel() throws ResourceManagerException, InterruptedException, ExecutionException, TimeoutException {
		ResourceManagerImpl resourceManager = (ResourceManagerImpl) applicationContext.getAutowireCapableBeanFactory()
				.autowire(ResourceManagerImpl.class, AutowireCapableBeanFactory.AUTOWIRE_NO, true);

		List<MockResource> resources = prepareResourceManager(resourceManager, 2);

		MockUser user1 = new MockUser("TestUser1");
		MockUser user2 = new MockUser("TestUser2");

		MockResourceTypeAuthorizationStore store = applicationContext.getBean(MockResourceTypeAuthorizationStore.class);
		store.addAuthorization(user1, "mock", 10, 0);
		// user2 has more important nice level
		store.addAuthorization(user2, "mock", 10, -19);

		MockResourceRequest request1 = new MockResourceRequest(user1, resources.get(0).getResourceType(), 0, "Test Job");
		ManagedResourceRequest wr1 = resourceManager.handleResourceRequest(request1);
		assertEquals(ManagedResourceRequest.State.WAITING, wr1.getState());
		// wait 100ms to give first request even some score due to longer waiting time
		Thread.sleep(100);
		MockResourceRequest request2 = new MockResourceRequest(user2, resources.get(0).getResourceType(), 0, "Test Job");
		ManagedResourceRequest wr2 = resourceManager.handleResourceRequest(request2);

		// EXPECTED: User2 wins, as higher (well, lower...) nice level
		resources.get(0).setState(ResourceState.READY);
		try {
			wr1.getResourceFuture().get(2, TimeUnit.SECONDS);
			fail("User1 received resource, expected: User2 receives resource");
		}
		catch (TimeoutException e) {
			// OK!
		}

		Resource res = wr2.getResourceFuture().get(5, TimeUnit.SECONDS);
		assertEquals(resources.get(0), res);
	}

	@Test
	public void testRequestNiceLevel()
			throws ResourceManagerException, InterruptedException, ExecutionException, TimeoutException {
		ResourceManagerImpl resourceManager = (ResourceManagerImpl) applicationContext.getAutowireCapableBeanFactory()
				.autowire(ResourceManagerImpl.class, AutowireCapableBeanFactory.AUTOWIRE_NO, true);

		List<MockResource> resources = prepareResourceManager(resourceManager, 2);

		MockUser user = new MockUser("TestUser1");

		MockResourceTypeAuthorizationStore store = applicationContext.getBean(MockResourceTypeAuthorizationStore.class);
		store.addAuthorization(user, "mock", 10, 0);

		MockResourceRequest request1 = new MockResourceRequest(user, resources.get(0).getResourceType(), 0, "Test Job");
		ManagedResourceRequest wr1 = resourceManager.handleResourceRequest(request1);
		assertEquals(ManagedResourceRequest.State.WAITING, wr1.getState());

		MockResourceRequest request2 = new MockResourceRequest(user, resources.get(0).getResourceType(), -19, "Important Job");
		ManagedResourceRequest wr2 = resourceManager.handleResourceRequest(request2);

		// give both almost same waiting time
		Thread.sleep(100);

		// EXPECTED: Request2 wins, as higher (well, lower...) nice level
		resources.get(0).setState(ResourceState.READY);
		try {
			wr1.getResourceFuture().get(2, TimeUnit.SECONDS);
			fail("Request1 received resource, expected: Request2 receives resource");
		}
		catch (TimeoutException e) {
			// OK!
		}

		Resource res = wr2.getResourceFuture().get(5, TimeUnit.SECONDS);
		assertEquals(resources.get(0), res);
	}

	@Test
	public void testOrphanedResource() throws Exception {
		ResourceManagerImpl resourceManager = (ResourceManagerImpl) applicationContext.getAutowireCapableBeanFactory()
				.autowire(ResourceManagerImpl.class, AutowireCapableBeanFactory.AUTOWIRE_NO, true);

		List<MockResource> resources = prepareResourceManager(resourceManager, 2);

		MockUser user = new MockUser("TestUser");
		MockResourceTypeAuthorizationStore store = applicationContext.getBean(MockResourceTypeAuthorizationStore.class);
		store.addAuthorization(user, "mock", 10, 0);

		MockResourceRequest request1 = new MockResourceRequest(user, resources.get(0).getResourceType(), 0,
				"Test Job 1");
		MockResourceRequest request2 = new MockResourceRequest(user, resources.get(0).getResourceType(), 0,
				"Test Job 2");
		resources.get(0).setState(ResourceState.READY);

		ManagedResourceRequest mrr1 = resourceManager.handleResourceRequest(request1);
		// ensure that first request gets resource before second request arrives
		Thread.sleep(100);
		ManagedResourceRequest mrr2 = resourceManager.handleResourceRequest(request2);

		Resource res = mrr1.getResourceFuture().get(1, TimeUnit.SECONDS);
		assertNotNull(res);
		assertEquals(res, resources.get(0));

		// start working on that resource
		((MockResource) res).startUsing();

		// assert that second request has to wait
		try {
			Resource res2 = mrr2.getResourceFuture().get(1, TimeUnit.SECONDS);
			fail("Expected timeout, but not to get busy resource " + res2);
		} catch (TimeoutException e) {
			// OK
		}

		// now, do NOT stop using it, but simulate an "orphaned" resource
		((MockResource) res).fireOrphaned();

		// mrr1 should now be marked as orphaned
		assertEquals(ManagedResourceRequest.State.ORPHANED, mrr1.getState());

		// second request should now get resource
		res = mrr2.getResourceFuture().get(1, TimeUnit.SECONDS);
		assertEquals(res, resources.get(0));
	}

	private List<MockResource> prepareResourceManager(ResourceManagerImpl resourceManager, int numResources) {
		List<MockResource> result = new ArrayList<>();

		// construct mock resource group manager
		MockResourceGroupManager groups = new MockResourceGroupManager();

		MockResource res1 = new MockResource("mock", 0);

		MockResourceGroup<MockResource> group = new MockResourceGroup<>(res1.getResourceType());

		group.add(res1);
		result.add(res1);

		for (int i = 0; i < numResources - 1; i++) {
			MockResource res = new MockResource("mock", i + 1);
			group.add(res);
			result.add(res);
		}

		groups.addResourceGroup(group);

		resourceManager.start(groups);
		return result;
	}
}
