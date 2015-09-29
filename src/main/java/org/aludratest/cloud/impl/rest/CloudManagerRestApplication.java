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
