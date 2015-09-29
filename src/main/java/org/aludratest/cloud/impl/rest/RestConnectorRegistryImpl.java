package org.aludratest.cloud.impl.rest;

import java.util.HashMap;
import java.util.Map;

import org.aludratest.cloud.rest.RestConnector;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

/**
 * Internal implementation of the RestConnectorRegistry interface. Only used for a simple access to all RestConnector components.
 * 
 * @author falbrech
 * 
 */
@Component(role = RestConnectorRegistry.class, instantiationStrategy = "singleton")
public class RestConnectorRegistryImpl implements RestConnectorRegistry {

	@Requirement(role = RestConnector.class)
	private Map<String, RestConnector> restConnectors = new HashMap<String, RestConnector>();

	@Override
	public Map<String, RestConnector> getRestConnectors() {
		return restConnectors;
	}

}
