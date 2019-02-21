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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.aludratest.cloud.manager.ManagedResourceRequest;
import org.aludratest.cloud.resource.ResourceType;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorization;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationConfig;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;

public class RequestScoreComparator implements Comparator<ManagedResourceRequest> {

	private static final int NORMALIZE_DIFF = 20;

	private ResourceTypeAuthorizationConfig authConfig;

	private ZonedDateTime now;

	private int totalResourceCount;

	private Map<User, Integer> userJobCount = new HashMap<>();

	public RequestScoreComparator(ResourceType resourceType, int totalResourceCount,
			Collection<? extends ManagedResourceRequest> managedRequests, ResourceTypeAuthorizationConfig authConfig)
			throws StoreException {
		this.authConfig = authConfig;
		this.totalResourceCount = totalResourceCount;

		// build map User => Count of running requests
		for (ManagedResourceRequest request : managedRequests) {
			if (request.getState() == ManagedResourceRequest.State.WORKING
					&& request.getRequest().getResourceType().getName().equals(resourceType.getName())) {
				Integer i = userJobCount.get(request.getRequest().getRequestingUser());
				if (i == null) {
					i = Integer.valueOf(1);
				}
				else {
					i = Integer.valueOf(i.intValue() + 1);
				}
				userJobCount.put(request.getRequest().getRequestingUser(), i);
			}
		}

		now = ZonedDateTime.now();
	}

	@Override
	public int compare(ManagedResourceRequest req1, ManagedResourceRequest req2) {
		User u1 = req1.getRequest().getRequestingUser();
		User u2 = req2.getRequest().getRequestingUser();

		ResourceTypeAuthorization auth1 = authConfig.getResourceTypeAuthorizationForUser(u1);
		ResourceTypeAuthorization auth2 = authConfig.getResourceTypeAuthorizationForUser(u2);

		return calculateRequestScore(req1, auth1) - calculateRequestScore(req2, auth2);
	}

	private int calculateRequestScore(ManagedResourceRequest request, ResourceTypeAuthorization auth) {
		double normalizedNiceLevel = auth.getNiceLevel() - NORMALIZE_DIFF;
		normalizedNiceLevel += request.getRequest().getNiceLevel() * 0.1;

		double userMax = Math.min(auth.getMaxResources(), totalResourceCount);
		if (userMax == 0) {
			return 0;
		}
		double userRunCount = userJobCount.getOrDefault(request.getRequest().getRequestingUser(), 0).intValue();
		normalizedNiceLevel -= (userRunCount / userMax) * normalizedNiceLevel;

		long millisWaiting = request.getCreationTimestamp().until(now, ChronoUnit.MILLIS);
		if (millisWaiting < 100) {
			millisWaiting = 100;
		}

		// 10ths of seconds are counting into the score
		int score = (int) ((millisWaiting / 100.0) * normalizedNiceLevel);

		// store score to request, if supported - allows easy debugging and monitoring
		if (request instanceof RequestScoreHolder) {
			((RequestScoreHolder) request).setRequestScore(score);
		}

		return score;
	}


}
