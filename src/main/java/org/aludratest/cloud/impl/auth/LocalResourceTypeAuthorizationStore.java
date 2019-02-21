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
package org.aludratest.cloud.impl.auth;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.aludratest.cloud.impl.ImplConstants;
import org.aludratest.cloud.resource.ResourceType;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationConfig;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationStore;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.admin.UserDatabaseRegistry;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Implementation of an Authorization Store which is based on a JSON file. By default, this file is located in the user's home
 * directory (<code>~/.atcloudmanager/resourceAuth.json</code>). You can change the location using {@link #setStoreFile(String)}.
 * 
 * @author falbrech
 * 
 */
@Component
@Qualifier("local")
public class LocalResourceTypeAuthorizationStore implements ResourceTypeAuthorizationStore {
	
	@Autowired
	private UserDatabaseRegistry userDatabaseRegistry;

	private String storeFile = "~/" + ImplConstants.CONFIG_DIR_NAME + "/resourceAuth.json";

	@Override
	public ResourceTypeAuthorizationConfig loadResourceTypeAuthorizations(ResourceType resourceType)
			throws StoreException {
		try {
			return new JSONResourceTypeAuthorizationConfig(load().getJSONArray(resourceType.getName()), userDatabaseRegistry);
		}
		catch (JSONException e) {
			// no such element in map
			return null;
		}
	}

	@Override
	public void saveResourceTypeAuthorizations(ResourceType resourceType, ResourceTypeAuthorizationConfig authorizations)
			throws StoreException {
		FileOutputStream fos = null;

		try {
			JSONObject obj = load();

			// optimization
			if (authorizations instanceof JSONResourceTypeAuthorizationConfig) {
				obj.put(resourceType.getName(), ((JSONResourceTypeAuthorizationConfig) authorizations).getJsonArray());
			}
			else {
				obj.put(resourceType.getName(), JSONResourceTypeAuthorizationConfig.toJSONArray(authorizations));
			}

			fos = new FileOutputStream(getFile());
			fos.write(obj.toString().getBytes("UTF-8"));
		}
		catch (IOException e) {
			throw new StoreException("Could not write resource authorization file", e);
		}
		catch (JSONException e) {
			throw new StoreException("Could not create JSON for resource authorization file", e);
		}
		finally {
			IOUtils.closeQuietly(fos);
		}
	}

	private JSONObject load() throws StoreException {
		if (!getFile().exists()) {
			return new JSONObject();
		}
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(getFile());
			return new JSONObject(IOUtils.toString(fis, "UTF-8"));
		}
		catch (IOException e) {
			throw new StoreException("Could not load resource type authorization file", e);
		}
		catch (JSONException e) {
			throw new StoreException("Resource type authorization file has invalid contents", e);
		}
		finally {
			IOUtils.closeQuietly(fis);
		}

	}
	
	private synchronized File getFile() {
		if (storeFile.startsWith("~")) {
			return new File(System.getProperty("user.home") + storeFile.substring(1));
		}

		return new File(storeFile);
	}

	/**
	 * Sets the file name to use to write the authorization to and read it from.
	 * 
	 * @param storeFile
	 *            Store file. Must be a valid file path.
	 */
	public synchronized void setStoreFile(String storeFile) {
		this.storeFile = storeFile;
	}

	/**
	 * Returns the location of the file which is used to store the authorization configuration.
	 * 
	 * @return The location of the file which is used to store the authorization configuration.
	 */
	public synchronized String getStoreFile() {
		return storeFile;
	}

}
