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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A class which separates Threads from each other. When two Threads <i>A</i> and <i>B</i> call <code>enter()</code>
 * simultaneously, one of the Threads is suspended for the configured separation time, before allowing to continue. Additional
 * Threads calling <code>enter()</code> are suspended as well, with a minimum separation as configured between them.
 * 
 * @author falbrech
 * 
 */
public class GateKeeper {

	private final long separationTime;

	private AtomicInteger waitingThreads = new AtomicInteger();

	private AtomicLong lastContinuationTime = new AtomicLong();

	private AtomicLong maxFutureContinuationTime = new AtomicLong();

	/**
	 * Constructs a new GateKeeper object, using the given minimum separation time.
	 * 
	 * @param separationTime
	 *            Minimum separation time to use for the new object.
	 * @param timeUnit
	 *            Unit of the specified separation time.
	 */
	public GateKeeper(long separationTime, TimeUnit timeUnit) {
		this.separationTime = timeUnit.toMillis(separationTime);
	}

	/**
	 * Signals that the current Thread wants to "enter" the context of the Gate Keeper. This can cause the Thread to wait for a
	 * while, if many Threads have called this method just before the current Thread.
	 * 
	 * @throws InterruptedException
	 *             If the Thread is interrupted while waiting to enter.
	 */
	public void enter() throws InterruptedException {
		long waitTime = 0;
		synchronized (this) { 
			long now = System.currentTimeMillis();
			int cnt = waitingThreads.getAndIncrement();
			if (cnt == 0) {
				waitTime = Math.max(0, separationTime - (now - lastContinuationTime.get()));
				maxFutureContinuationTime.set(now + waitTime);
			}
			else {
				waitTime = maxFutureContinuationTime.get() - now + separationTime;
				maxFutureContinuationTime.addAndGet(separationTime);
			}
		}
		
		try {
			if (waitTime > 0) {
				Thread.sleep(waitTime);
			}
		}
		finally {
			synchronized (this) {
				lastContinuationTime.set(System.currentTimeMillis());
				waitingThreads.decrementAndGet();
			}
		}
	}

	/**
	 * Returns the number of Threads currently waiting at this GateKeeper.
	 * 
	 * @return The number of Threads currently wa
	 */
	public int getWaitingThreads() {
		return waitingThreads.get();
	}

}
