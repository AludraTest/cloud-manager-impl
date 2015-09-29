package org.aludratest.cloud.impl.rest;

import java.util.Map;

import org.aludratest.cloud.rest.RestConnector;

/**
 * Internal helper interface for getting the registered Plexus components for the RestConnector role.
 * 
 * @author falbrech
 * 
 */
public interface RestConnectorRegistry {

	/**
	 * Plexus role for this component.
	 */
	public static final String ROLE = RestConnectorRegistry.class.getName();

	/**
	 * Returns all RestConnector components registered in Plexus.
	 * 
	 * @return All RestConnector components registered in Plexus.
	 */
	public Map<String, RestConnector> getRestConnectors();
}
