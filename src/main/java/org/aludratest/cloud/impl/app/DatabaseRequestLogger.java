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

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.rowset.CachedRowSet;

import org.aludratest.cloud.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runnable which queues (modifying) database commands to not slow down application by slow database performance.
 * 
 * @author falbrech
 * 
 */
public class DatabaseRequestLogger implements Runnable {

	// @formatter:off
	/* 
	 * ==== Database schema version history ====
	 * 
	 * Version     Date        Author     Description
	 *   1.0    2015-08-11    falbrech    Initial schema. Copied from previous HSDG software.
	 *    
	 */
	// @formatter:on

	/**
	 * The current schema version of the database module (software-side). This information is used when auto-updates are
	 * performed, i.e. the database contains a different version information than this.
	 */
	private static final int[] DB_SCHEMA_VERSION = { 1, 0 };

	private static final Logger LOG = LoggerFactory.getLogger(DatabaseRequestLogger.class);

	private LogDatabase database;

	private ConcurrentLinkedQueue<DatabaseCommand> commandQueue = new ConcurrentLinkedQueue<DatabaseCommand>();

	private Map<Long, Long> virtualToDbRequestIds = new ConcurrentHashMap<Long, Long>();

	private final DateFormat DF_TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	private AtomicLong nextVirtualRequestId = new AtomicLong();


	/**
	 * Creates a new database request logger object. Use a Thread object to execute this logger. Interrupt that thread to
	 * terminate the logger.
	 * 
	 * @param database
	 *            Database to write log entries to.
	 * 
	 * @throws SQLException
	 *             If the database could not be created or initialized with required tables, or has an incompatible schema
	 *             version.
	 */
	public DatabaseRequestLogger(LogDatabase database) throws SQLException {
		this.database = database;
		DF_TIMESTAMP.setTimeZone(TimeZone.getTimeZone("UTC"));

		if (!database.isDatabaseExisting()) {
			database.createDatabase();
			createBasicTables();
		}
		checkTablesVersion();
	}

	private void enqueue(DatabaseCommand command) {
		commandQueue.add(command);
		synchronized (this) {
			notify();
		}
	}

	private void executeCommand(DatabaseCommand command) throws SQLException {
		if (command.create) {
			Long result = database.executeStatement(command.sql, new int[] { 1 });
			virtualToDbRequestIds.put(command.mappingRequestId, result);
		}
		else {
			Long dbId = virtualToDbRequestIds.get(command.mappingRequestId);
			if (dbId == null) {
				throw new SQLException("No database ID known to update request " + command.mappingRequestId);
			}
			database.executeStatement(command.sql.replace(":dbId", dbId.toString()));
		}
	}

	@Override
	public void run() {
		while (!Thread.interrupted()) {
			while (commandQueue.isEmpty()) {
				synchronized (this) {
					try {
						wait();
					}
					catch (InterruptedException e) {
						return;
					}
				}
			}

			while (!commandQueue.isEmpty()) {
				DatabaseCommand command = commandQueue.poll();
				try {
					executeCommand(command);
				}
				catch (SQLException e) {
					LOG.error("Could not execute log database statement", e);
				}
				if (Thread.interrupted()) {
					return;
				}
			}
		}
	}

	/**
	 * Creates a new log entry for a resource request. A unique ID is automatically assigned and returned. <br>
	 * The database INSERT statement is enqueued into the local command queue and executed as soon as possible.
	 * 
	 * @param user
	 *            User who issued the resource request.
	 * @param jobName
	 *            Job name passed by the user, if any.
	 * 
	 * @return The unique ID automatically assigned to the log entry. This ID is required for calling the <code>update*</code>
	 *         methods.
	 */
	public long createRequestLog(User user, String jobName) {
		long virtualId = nextVirtualRequestId.incrementAndGet();

		String sql = "INSERT INTO acm_request (start_wait_time_utc, user_name, user_source, job_name) VALUES ("
				+ getCurrentUTCTimestampExpr() + ", '" + user.getName() + "', '" + user.getSource() + "', '" + jobName + "')";

		enqueue(new DatabaseCommand(sql, virtualId, true));
		return virtualId;
	}

	/**
	 * Updates the given log entry when a resource has been assigned to the request.
	 * 
	 * @param id
	 *            Log entry ID, as returned by {@link #createRequestLog(User, String)}.
	 * @param resourceType
	 *            Type of the resource assigned to the request.
	 * @param resource
	 *            The resource assigned to the request. The String representation (<code>toString()</code>) of the resource is
	 *            logged to the database.
	 */
	public void updateRequestLogWorkStarted(long id, String resourceType, String resource) {
		String sql = "UPDATE acm_request SET start_work_time_utc = " + getCurrentUTCTimestampExpr() + ", resource_type = '"
				+ resourceType + "', received_resource = '" + resource + "' WHERE request_id = :dbId";
		enqueue(new DatabaseCommand(sql, id, false));
	}

	/**
	 * Updates the given log entry when an assigned resource has been released.
	 * 
	 * @param id
	 *            Log entry ID, as returned by {@link #createRequestLog(User, String)}.
	 * @param status
	 *            Arbitrary status of the associated request, could e.g. be <code>SUCCESS</code> or <code>ABORTED</code>. This
	 *            depends on the request handler implementation.
	 * @param cntActiveResourcesLeft
	 *            Number of active (<code>IN_USE</code>) left of the associated resource type. This is logged in an extra field
	 *            and can be used for easy "workload" reports.
	 */
	public void updateRequestLogWorkDone(long id, String status, int cntActiveResourcesLeft) {
		String sql = "UPDATE acm_request SET end_work_time_utc = " + getCurrentUTCTimestampExpr() + ", end_work_status = '"
				+ status + "', cnt_active_res_after_work = " + cntActiveResourcesLeft + " WHERE request_id = :dbId";
		enqueue(new DatabaseCommand(sql, id, false));
	}

	private void checkTablesVersion() throws SQLException {
		// check if there is a previous version; upgrade in this case
		String sql = "SELECT major, minor FROM acm_version";
		CachedRowSet rs = database.populateQuery(sql);
		if (!rs.next()) {
			throw new SQLException("acm_version is empty");
		}

		int major = rs.getInt(1);
		int minor = rs.getInt(2);

		if (major != DB_SCHEMA_VERSION[0] || minor != DB_SCHEMA_VERSION[1]) {
			throw new SQLException("Unsupported version of database schema: " + major + "." + minor);
		}
	}

	private void createBasicTables() throws SQLException {
		String sql = "CREATE TABLE acm_version (major INTEGER NOT NULL, minor INTEGER NOT NULL)";
		database.executeStatement(sql);

		// @formatter:off
		sql = "CREATE TABLE acm_request (request_id BIGINT GENERATED ALWAYS AS IDENTITY, "
			+ "start_wait_time_utc TIMESTAMP, "
			+ "start_work_time_utc TIMESTAMP, "
			+ "end_work_time_utc TIMESTAMP, "
			+ "user_name VARCHAR(50), "
			+ "user_source VARCHAR(100), "
			+ "job_name VARCHAR(400), "
			+ "received_resource VARCHAR(400), "
			+ "resource_type VARCHAR(40), "
			+ "end_work_status VARCHAR(20), "
			+ "cnt_active_res_after_work INTEGER)";
		// @formatter:on

		database.executeStatement(sql);

		writeAcmVersion();
	}

	private void writeAcmVersion() throws SQLException {
		String sql = "DELETE FROM acm_version";
		database.executeStatement(sql);

		sql = "INSERT INTO acm_version VALUES (" + DB_SCHEMA_VERSION[0] + ", " + DB_SCHEMA_VERSION[1] + ")";
		database.executeStatement(sql);
	}

	private String getCurrentUTCTimestampExpr() {
		return getUTCTimestampExpr(new Date());
	}

	private String getUTCTimestampExpr(Date dt) {
		return "TIMESTAMP('" + DF_TIMESTAMP.format(dt) + "')";
	}

	private static class DatabaseCommand {

		private String sql;

		private boolean create;

		private Long mappingRequestId;

		public DatabaseCommand(String sql, long mappingRequestId, boolean create) {
			this.sql = sql;
			this.mappingRequestId = mappingRequestId;
			this.create = create;
		}
	}

}
