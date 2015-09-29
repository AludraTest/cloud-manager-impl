package org.aludratest.cloud.impl.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.aludratest.cloud.app.CloudManagerApp;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.ConfigUtil;
import org.aludratest.cloud.config.MainPreferences;
import org.aludratest.cloud.config.Preferences;
import org.aludratest.cloud.config.SimplePreferences;
import org.aludratest.cloud.impl.app.CloudManagerApplicationHolder;
import org.aludratest.cloud.rest.AbstractRestConnector;
import org.aludratest.cloud.rest.RestConnector;
import org.codehaus.plexus.component.annotations.Component;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * REST endpoint for setting and retrieving AludraTest Cloud Manager configuration. All nodes of the main Preferences tree can be
 * modified. Every call to {@link #setConfig(String, String)} results in a write to the configuration file, if the new
 * configuration is valid.
 * 
 * @author falbrech
 * 
 */
@Component(role = RestConnector.class, hint = "basic-config")
@Path("/config/{property: .*}")
public class ConfigEndpoint extends AbstractRestConnector {

	/**
	 * Returns a JSON object describing the configuration property with the given path.
	 * 
	 * @param property
	 *            Path to property to return the value for, e.g. <code>basic/hostName</code>.
	 * 
	 * @return A JSON object describing the configuration property with the given path. If no configuration property with this
	 *         path exists, or it is empty, the JSON object returns <code>empty: true</code> instead of a <code>value</code>
	 *         field.
	 * 
	 */
	@GET
	@Produces(JSON_TYPE)
	public Response getConfig(@PathParam("property") String property) {
		Preferences prefs = CloudManagerApplicationHolder.getInstance().getRootPreferences();
		
		String value = prefs.getStringValue(property);

		JSONObject result = new JSONObject();
		try {
			result.put("name", property);
			if (value != null) {
				result.put("value", value);
			}
			else {
				result.put("empty", true);
			}

			return wrapResultObject(result);
		}
		catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Sets the given configuration property to the given value. This causes immediate appliance of the changed value, and if any
	 * Preferences listener signals that the new configuration would be invalid, an error JSON object is returned. Otherwise, the
	 * new value of the configuration property is returned, in the same format as for {@link #getConfig(String)}.
	 * 
	 * @param property
	 *            Configuration property path to set value for.
	 * @param value
	 *            New configuration value, in its String representation.
	 * 
	 * @return A JSON object showing the new value of the configuration property, or an error JSON object providing the
	 *         configuration error message.
	 */
	@POST
	@Consumes(FORM_TYPE)
	@Produces(JSON_TYPE)
	public Response setConfig(@PathParam("property") String property, @FormParam("value") String value) {
		if (property == null || "".equals(property)) {
			IllegalArgumentException iae = new IllegalArgumentException("Invalid configuration path");
			return createErrorObject(iae.getMessage(), iae);
		}
		MainPreferences prefs = CloudManagerApplicationHolder.getInstance().getRootPreferences();
		SimplePreferences mutablePrefs = null;

		// find node containing property, or last MainPreferences node available when new path has to be constructed
		while (property.contains("/")) {
			String nextChild = property.substring(0, property.indexOf('/'));

			if (prefs.getChildNode(nextChild) == null) {
				// we have to start here
				break;
			}

			prefs = prefs.getChildNode(nextChild);
			property = property.substring(property.indexOf('/') + 1);
		}

		mutablePrefs = new SimplePreferences(null);
		ConfigUtil.copyPreferences(prefs, mutablePrefs);

		mutablePrefs.setValue(property, value);
		try {
			CloudManagerApp.getInstance().getConfigManager().applyConfig(mutablePrefs, prefs);
		}
		catch (ConfigException e) {
			return createErrorObject(e.getMessage(), e);
		}
		return getConfig(property);
	}

}
