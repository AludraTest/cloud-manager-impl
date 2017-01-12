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

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.aludratest.cloud.impl.app.CloudManagerApplicationHolder;
import org.aludratest.cloud.rest.RestConnector;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST application for AludraTest Cloud Manager. Determines all Plexus components for the <code>RestConnector</code> role, and
 * returns their classes in {@link #getClasses()}. This establishes a kind of "extensible" REST infrastructure.
 * 
 * @author falbrech
 * 
 */
public class CloudManagerRestApplication extends Application {

	private static final Logger LOG = LoggerFactory.getLogger(CloudManagerRestApplication.class);

	@Override
	public Set<Class<?>> getClasses() {
		Set<Class<?>> result = new HashSet<Class<?>>();

		RestConnectorRegistry registry = getRestConnectorRegistry();
		if (registry != null) {
			for (RestConnector rc : registry.getRestConnectors().values()) {
				LOG.debug("Registering REST endpoint class " + rc.getClass().getName());
				result.add(rc.getClass());
			}
		}

		return result;
	}

	private RestConnectorRegistry getRestConnectorRegistry() {
		try {
			return CloudManagerApplicationHolder.getInstance().getPlexusContainer().lookup(RestConnectorRegistry.class);
		}
		catch (ComponentLookupException e) {
			LOG.error("Could not lookup REST Connector registry", e);
			return null;
		}
	}

}
