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

import java.io.File;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;
import javax.sql.rowset.CachedRowSet;

import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DataSourceConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.derby.drda.NetworkServerControl;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Technical accessor class to the Derby based Request Logging Database.
 * 
 * @author falbrech
 * 
 */
public class LogDatabase {

	private static final Logger LOG = LoggerFactory.getLogger(LogDatabase.class);

	private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";

	private NetworkServerControl server;

	private DataSource dataSource;

	LogDatabase(File configDir, Integer port) throws Exception {
		File derbyDir = new File(configDir, "derby");
		derbyDir.mkdirs();
		System.setProperty("derby.system.home", derbyDir.getAbsolutePath());

		Class.forName(DRIVER).newInstance();

		// also start a network server on this DB, if requested
		if (port != null) {
			server = new NetworkServerControl(InetAddress.getByName("0.0.0.0"), port);
			server.start(null);
		}
	}

	public boolean isDatabaseExisting() {
		try {
			DriverManager.getConnection("jdbc:derby:acm").close();
			return true;
		}
		catch (SQLException e) {
			return false;
		}
	}

	public void createDatabase() throws SQLException {
		DriverManager.getConnection("jdbc:derby:acm;create=true").close();
	}

	/**
	 * Shuts the internal Derby database down. Any exceptions during shutdown are ignored.
	 */
	public void shutdown() {
		try {
			DriverManager.getConnection("jdbc:derby:;shutdown=true");
		}
		catch (Exception e) {
		}
		try {
			if (server != null) {
				server.shutdown();
			}
		}
		catch (Exception e) {
		}
	}

	private Connection getConnection() throws SQLException {
		if (dataSource == null) {
			EmbeddedDataSource ds = new EmbeddedDataSource();
			ds.setDatabaseName("acm");
			ConnectionFactory connectionFactory = new DataSourceConnectionFactory(ds);
			PoolableConnectionFactory objFactory = new PoolableConnectionFactory(connectionFactory, null);
			objFactory.setValidationQuery("VALUES 1");
			objFactory.setDefaultAutoCommit(true);
			// max 10 minutes lifetime
			objFactory.setMaxConnLifetimeMillis(1000l * 60 * 10);
			// must be fast, because is local
			objFactory.setValidationQueryTimeout(5);

			GenericObjectPool<PoolableConnection> pool = new GenericObjectPool<PoolableConnection>(objFactory);
			pool.setMaxIdle(2);
			dataSource = new PoolingDataSource<PoolableConnection>(pool);
		}

		return dataSource.getConnection();
	}

	/**
	 * Runs and populates the given query against the internal Derby database.
	 * 
	 * @param query
	 *            SQL query to execute, usually starts with <code>SELECT</code>.
	 * 
	 * @return A cached row set containing the full results of the query.
	 * 
	 * @throws SQLException
	 *             If a database exception occurs, e.g. invalid query.
	 */
	public CachedRowSet populateQuery(String query) throws SQLException {
		Connection connection = getConnection();
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			LOG.debug("Executing QUERY: " + query);
			ResultSet rs = stmt.executeQuery(query);
			LOG.debug("Query execution complete.");

			try {
				CachedRowSet rowSet = (CachedRowSet) Class.forName("com.sun.rowset.CachedRowSetImpl").newInstance();
				rowSet.populate(rs);
				rowSet.beforeFirst();
				return rowSet;
			}
			catch (SQLException se) {
				throw se;
			}
			catch (Exception e) {
				throw new SQLException(e);
			}
		}
		finally {
			closeQuietly(stmt);
			closeQuietly(connection);
		}
	}

	public void executeStatement(String sql) throws SQLException {
		executeStatement(sql, null);
	}

	public Long executeStatement(String sql, int[] autoGenerationIndices) throws SQLException {
		Connection connection = getConnection();
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			LOG.debug("Executing database statement: " + sql);
			if (autoGenerationIndices != null) {
				stmt.execute(sql, autoGenerationIndices);
			}
			else {
				stmt.execute(sql);
			}
			LOG.debug("Statement execution complete.");
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs == null || !rs.next()) {
				return null;
			}
			try {
				return rs.getLong(1);
			}
			catch (SQLException e) {
				return null;
			}
		}
		finally {
			closeQuietly(stmt);
			closeQuietly(connection);
		}
	}

	private void closeQuietly(Statement stmt) {
		if (stmt != null) {
			try {
				stmt.close();
			}
			catch (SQLException e) {
				// ignore
			}
		}
	}

	private void closeQuietly(Connection conn) {
		if (conn != null) {
			try {
				conn.close();
			}
			catch (SQLException e) {
				// ignore
			}
		}
	}



}
