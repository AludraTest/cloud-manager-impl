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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.aludratest.cloud.app.CloudManagerAppFileStore;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Implementation of a file store using the local file system for reading and writing files. <br>
 * The root directory for all files is determined using this way:
 * <ol>
 * <li>If a Spring configuration property <code>acm.filestore.path</code> is set, its contents are used as root directory.</li>
 * <li>If a System property <code>acm.filestore.path</code> is set, its contents are used as root directory.</li>
 * <li>Otherwise, a directory <code>.atcloudmanager</code> is created within the user's home directory and used.</li>
 * </ol>
 * 
 * @author falbrech
 *
 */
@Component
public class FileSystemFileStore implements CloudManagerAppFileStore {

	@Value("${acm.filestore.path:}")
	private String configuredRoot;

	private File root;

	FileSystemFileStore() {

	}

	private File checkRoot() {
		if (root == null) {
			// case 1
			if (!StringUtils.isEmpty(configuredRoot)) {
				File f = new File(configuredRoot);
				if (!f.isDirectory() && !f.mkdirs()) {
					throw new IllegalStateException(
							"Cannot create or access root directory for ACM file store " + f.getAbsolutePath());
				}
				return root = f;
			}
			String sysprop = System.getProperty("acm.filestore.path");
			// case 2
			if (!StringUtils.isEmpty(sysprop)) {
				File f = new File(sysprop);
				if (!f.isDirectory() && !f.mkdirs()) {
					throw new IllegalStateException(
							"Cannot create or access root directory for ACM file store " + f.getAbsolutePath());
				}
				return root = f;
			}
			// default case
			File f = new File(new File(System.getProperty("user.home")), ".atcloudmanager");
			if (!f.isDirectory() && !f.mkdirs()) {
				throw new IllegalStateException(
						"Cannot create or access root directory for ACM file store " + f.getAbsolutePath());
			}
			return root = f;
		}

		return root;
	}

	@Override
	public InputStream openFile(String fileName) throws IOException {
		File f = new File(checkRoot(), fileName);

		// safer than IOException, due to method's contract
		if (!f.isFile()) {
			return null;
		}

		// some comfort here...
		return new BufferedInputStream(new FileInputStream(f));
	}

	@Override
	public boolean existsFile(String fileName) {
		return new File(checkRoot(), fileName).isFile();
	}

	@Override
	public void saveFile(String fileName, InputStream contents) throws IOException {
		File f = new File(checkRoot(), fileName);

		try (FileOutputStream fos = new FileOutputStream(f)) {
			IOUtils.copy(contents, fos);
		}
	}

}
