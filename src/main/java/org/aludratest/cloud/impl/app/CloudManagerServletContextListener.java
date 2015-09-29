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
