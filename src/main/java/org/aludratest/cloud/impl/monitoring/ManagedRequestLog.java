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
package org.aludratest.cloud.impl.monitoring;

import java.time.ZonedDateTime;

import org.aludratest.cloud.manager.ManagedResourceRequest;
import org.aludratest.cloud.manager.ManagedResourceRequest.State;

public final class ManagedRequestLog implements Comparable<ManagedRequestLog> {

	private ZonedDateTime timestamp;

	private ManagedResourceRequest.State newStatus;

	public ManagedRequestLog(ZonedDateTime timestamp, State newStatus) {
		if (timestamp == null) {
			throw new IllegalArgumentException("timestamp is null");
		}
		if (newStatus == null) {
			throw new IllegalArgumentException("newStatus is null");
		}

		this.timestamp = timestamp;
		this.newStatus = newStatus;
	}

	public ZonedDateTime getTimestamp() {
		return timestamp;
	}

	public ManagedResourceRequest.State getNewStatus() {
		return newStatus;
	}

	@Override
	public int compareTo(ManagedRequestLog o) {
		return timestamp.compareTo(o.timestamp);
	}

}
