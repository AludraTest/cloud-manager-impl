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
package org.aludratest.cloud.impl.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aludratest.cloud.config.AbstractPreferences;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.ConfigUtil;
import org.aludratest.cloud.config.MainPreferences;
import org.aludratest.cloud.config.Preferences;
import org.aludratest.cloud.config.PreferencesListener;
import org.aludratest.cloud.config.SimplePreferences;

/**
 * Default implementation of the <code>MainPreferences</code> interface.
 * 
 * @author falbrech
 * 
 */
public class MainPreferencesImpl extends AbstractPreferences implements MainPreferences {

	private List<PreferencesListener> listeners = new ArrayList<PreferencesListener>();

	private Map<String, String> values = new HashMap<String, String>();

	private Map<String, MainPreferencesImpl> children = new HashMap<String, MainPreferencesImpl>();

	private boolean virtual;

	/**
	 * Constructs a new preferences implementation node.
	 * 
	 * @param parent
	 *            Parent to use, or <code>null</code> if root element.
	 */
	public MainPreferencesImpl(MainPreferencesImpl parent) {
		super(parent);
	}

	@Override
	public synchronized void addPreferencesListener(PreferencesListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	@Override
	public synchronized void removePreferencesListener(PreferencesListener listener) {
		listeners.remove(listener);
	}

	private synchronized PreferencesListener[] getPreferencesListeners() {
		return listeners.toArray(new PreferencesListener[0]);
	}

	@Override
	public String[] getKeyNames() {
		return values.keySet().toArray(new String[0]);
	}

	@Override
	public MainPreferences getParent() {
		return (MainPreferences) super.getParent();
	}

	@Override
	public MainPreferencesImpl getChildNode(String name) {
		return children.get(name);
	}

	@Override
	public MainPreferences getOrCreateChildNode(String name) {
		if (!children.containsKey(name)) {
			MainPreferencesImpl newNode = new MainPreferencesImpl(this);
			newNode.virtual = true;
			children.put(name, newNode);
		}
		return children.get(name);
	}

	@Override
	public String[] getChildNodeNames() {
		return children.keySet().toArray(new String[0]);
	}

	@Override
	protected String internalGetStringValue(String key) {
		return values.get(key);
	}

	/**
	 * Hook for the <code>ConfigManagerImpl</code> class to apply new configuration to this node.
	 * 
	 * @param prefs
	 *            New preferences to apply to this configuration node.
	 * 
	 * @throws ConfigException
	 *             Any listener could throw this exception during <code>preferencesAboutToChange()</code> or
	 *             <code>preferencesChanged()</code>, indicating that the new configuration is invalid or could not be applied. In
	 *             the latter case, the configuration is nevertheless stored to this node.
	 */
	public void applyPreferences(Preferences prefs) throws ConfigException {
		// if we are virtual yet, notify via parent that we have been added
		if (virtual) {
			virtual = false;
			SimplePreferences oldPrefs = new SimplePreferences(null);
			ConfigUtil.copyPreferences(getParent(), oldPrefs);
			((MainPreferencesImpl) getParent()).applyPreferences(oldPrefs);
		}

		// propagate and verify change
		fireAboutToChange(prefs);

		// copy current state for "changed" event
		SimplePreferences oldPrefs = new SimplePreferences(null);
		ConfigUtil.copyPreferences(this, oldPrefs);

		// apply
		values.clear();

		for (String key : prefs.getKeyNames()) {
			values.put(key, prefs.getStringValue(key));
		}

		List<String> newChildNodes = Arrays.asList(prefs.getChildNodeNames());

		for (String node : newChildNodes) {
			MainPreferencesImpl child = getChildNode(node);
			if (child == null) {
				child = new MainPreferencesImpl(this);
				children.put(node, child);
			}
			child.applyPreferences(prefs.getChildNode(node));
		}

		for (String node : getChildNodeNames()) {
			if (!newChildNodes.contains(node)) {
				children.remove(node);
			}
		}

		fireChanged(oldPrefs);
	}

	private void fireAboutToChange(Preferences newPrefs) throws ConfigException {
		// go down to every existing child first
		for (String node : newPrefs.getChildNodeNames()) {
			MainPreferencesImpl child = children.get(node);
			if (child != null) {
				child.fireAboutToChange(newPrefs.getChildNode(node));
			}
		}

		// now for this node
		for (PreferencesListener listener : getPreferencesListeners()) {
			listener.preferencesAboutToChange(this, newPrefs);
		}
	}

	private void fireChanged(Preferences oldPrefs) throws ConfigException {
		for (PreferencesListener listener : getPreferencesListeners()) {
			listener.preferencesChanged(oldPrefs, this);
		}
	}

}
