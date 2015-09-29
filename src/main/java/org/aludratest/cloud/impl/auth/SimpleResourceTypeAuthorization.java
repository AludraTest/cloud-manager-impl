package org.aludratest.cloud.impl.auth;

import org.aludratest.cloud.resource.user.ResourceTypeAuthorization;

/**
 * Simple default implementation of the <code>ResourceTypeAuthorization</code> interface.
 * 
 * @author falbrech
 * 
 */
public class SimpleResourceTypeAuthorization implements ResourceTypeAuthorization {

	private int maxResources;

	private int niceLevel;

	/**
	 * Creates a new authorization object.
	 * 
	 * @param maxResources
	 *            Maximum number of allowed resources.
	 * @param niceLevel
	 *            Default nice level for the user.
	 */
	public SimpleResourceTypeAuthorization(int maxResources, int niceLevel) {
		this.maxResources = maxResources;
		this.niceLevel = niceLevel;
	}

	@Override
	public int getMaxResources() {
		return maxResources;
	}

	@Override
	public int getNiceLevel() {
		return niceLevel;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (obj.getClass() != getClass()) {
			return false;
		}

		SimpleResourceTypeAuthorization auth = (SimpleResourceTypeAuthorization) obj;
		return (auth.maxResources == maxResources && auth.niceLevel == niceLevel);
	}

	@Override
	public int hashCode() {
		return maxResources + niceLevel;
	}

}
