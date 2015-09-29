package org.aludratest.cloud.impl.app;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.aludratest.cloud.config.ConfigException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Register this Context Listener in your web.xml to startup the Cloud Manager application when Servlet Context is initialized,
 * and to shut it down when Context is destroyed.
 * 
 * @author falbrech
 * 
 */
public class CloudManagerServletContextListener implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(CloudManagerServletContextListener.class);

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		CloudManagerApplicationHolder.shutdown();
	}

	@Override
	public void contextInitialized(ServletContextEvent event) {
		try {
			CloudManagerApplicationHolder.startup();
		}
		catch (PlexusContainerException e) {
			LOG.error("Could not start Plexus container", e);
		}
		catch (ComponentLookupException e) {
			LOG.error("Could not lookup AludraTest Cloud Manager application", e);
		}
		catch (ConfigException e) {
			LOG.error("Illegal AludraTest Cloud Manager configuration", e);
		}
	}

}
