package org.aludratest.cloud.impl.app;

import java.io.File;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.sql.rowset.CachedRowSet;

import org.aludratest.cloud.impl.ImplConstants;
import org.aludratest.cloud.user.User;
import org.apache.derby.drda.NetworkServerControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs request information into an embedded Derby database. External clients can connect to port 1527 on the host running
 * AludraTest Cloud Manager to access and query the Derby database. Internal clients can use the {@link #populateQuery(String)}
 * method to run an SQL query against the database.
 * 
 * @author falbrech
 * 
 */
public class LogDb {
	
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

	private static final Logger LOG = LoggerFactory.getLogger(LogDb.class);

	private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";

	private static final DateFormat DF_TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	static {
		DF_TIMESTAMP.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	private NetworkServerControl server;

	LogDb() throws Exception {
		File derbyDir = new File(new File(System.getProperty("user.home")), ImplConstants.CONFIG_DIR_NAME + "/derby");
		System.setProperty("derby.system.home", derbyDir.getAbsolutePath());

		Class.forName(DRIVER).newInstance();

		Connection connection;

		try {
			connection = DriverManager.getConnection("jdbc:derby:acm");
		}
		catch (SQLException e) {
			connection = DriverManager.getConnection("jdbc:derby:acm;create=true");
			createBasicTables();
		}
		checkTablesVersion();

		// also start a network server on this DB
		server = new NetworkServerControl(InetAddress.getByName("0.0.0.0"), 1527);
		server.start(null);

		// connection will be recreated when required
		connection.close();
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
			server.shutdown();
		}
		catch (Exception e) {
		}
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
		Connection connection = DriverManager.getConnection("jdbc:derby:acm");
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

	/**
	 * Creates a new log entry for a resource request. A unique ID is automatically assigned and returned.
	 * 
	 * @param user
	 *            User who issued the resource request.
	 * @param jobName
	 *            Job name passed by the user, if any.
	 * 
	 * @return The unique ID automatically assigned to the log entry. This ID is required for calling the <code>update*</code>
	 *         methods.
	 * 
	 * @throws SQLException
	 *             If a database exception occurs.
	 */
	public long createRequestLog(User user, String jobName) throws SQLException {
		Long result = executeStatement("INSERT INTO acm_request (start_wait_time_utc, user_name, user_source, job_name) VALUES ("
						+ getCurrentUTCTimestampExpr() + ", '" + user.getName() + "', '" + user.getSource() + "', '" + jobName
						+ "')", new int[] { 1 });
		return result.longValue();
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
	 * @throws SQLException
	 *             If a database exception occurs.
	 */
	public void updateRequestLogWorkStarted(long id, String resourceType, String resource) throws SQLException {
		executeStatement("UPDATE acm_request SET start_work_time_utc = " + getCurrentUTCTimestampExpr() + ", resource_type = '"
				+ resourceType + "', received_resource = '" + resource + "' WHERE request_id = " + id);
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
	 * 
	 * @throws SQLException
	 *             If a database exception occurs.
	 */
	public void updateRequestLogWorkDone(long id, String status, int cntActiveResourcesLeft) throws SQLException {
		executeStatement("UPDATE acm_request SET end_work_time_utc = " + getCurrentUTCTimestampExpr() + ", end_work_status = '"
				+ status + "', cnt_active_res_after_work = " + cntActiveResourcesLeft + " WHERE request_id = " + id);
	}

	private void executeStatement(String sql) throws SQLException {
		executeStatement(sql, null);
	}

	private synchronized Long executeStatement(String sql, int[] autoGenerationIndices) throws SQLException {
		Connection connection = DriverManager.getConnection("jdbc:derby:acm");
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

	private void checkTablesVersion() throws SQLException {
		// check if there is a previous version; upgrade in this case
		String sql = "SELECT major, minor FROM acm_version";
		CachedRowSet rs = populateQuery(sql);
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
		executeStatement(sql);

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

		executeStatement(sql);

		writeAcmVersion();
	}

	private void writeAcmVersion() throws SQLException {
		String sql = "DELETE FROM acm_version";
		executeStatement(sql);

		sql = "INSERT INTO acm_version VALUES (" + DB_SCHEMA_VERSION[0] + ", " + DB_SCHEMA_VERSION[1] + ")";
		executeStatement(sql);
	}

	private static String getCurrentUTCTimestampExpr() {
		return getUTCTimestampExpr(new Date());
	}

	private static String getUTCTimestampExpr(Date dt) {
		return "TIMESTAMP('" + DF_TIMESTAMP.format(dt) + "')";
	}


}
