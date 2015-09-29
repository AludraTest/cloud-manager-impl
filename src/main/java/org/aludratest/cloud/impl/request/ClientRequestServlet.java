package org.aludratest.cloud.impl.request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.aludratest.cloud.app.CloudManagerApp;
import org.aludratest.cloud.impl.user.BasicAuthUtil;
import org.aludratest.cloud.user.User;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A servlet which is able to handle REST-like resource requests. Resource requests must fulfill these requirements:
 * <ul>
 * <li>Content Type must be <code>application/json</code>, encoding must be <code>UTF-8</code></li>
 * <li>HTTP method must be <code>POST</code></li>
 * <li>A Basic Authentication header must be sent (<code>Authorization: Basic; ...</code>), specifying a valid user / password
 * combination</li>
 * <li>Content must be a JSON object which is accepted by {@link ClientRequestHandler#handleResourceRequest(User, JSONObject)}</li>
 * </ul>
 * 
 * The servlet will answer with the JSON object received from the {@link ClientRequestHandler}, possibly indicating that no
 * resource has become available within an internal given timeout (usually 10 seconds), and the client should send a new HTTP
 * request to retrieve the status of the pending resource request. See {@link ClientRequestHandler} for more details on the JSON
 * object formats.
 * 
 * @author falbrech
 * 
 */
public class ClientRequestServlet extends HttpServlet {
	
	private static final long serialVersionUID = 4695231001799464144L;

	private static final int MAX_CONTENT_LENGTH = 1024 * 2; // 2 KB requests only, please

	private static final Logger LOG = LoggerFactory.getLogger(ClientRequestServlet.class);

	// can be used for debugging with curl
	// do not use final to avoid compiler warnings
	private static boolean CONTENT_TYPE_CHECK_ENABLED = true;

	private static final String JSON_CONTENT_TYPE = "application/json";

	private static final Pattern PATTERN_RESOURCE_ID_URI = Pattern.compile("/([0-9a-fA-F]{16})");

	private ClientRequestHandler requestHandler;

	private GateKeeper requestSeparator = new GateKeeper(100, TimeUnit.MILLISECONDS);

	// for debugging purposes
	static ClientRequestServlet instance;

	AtomicInteger waitingRequests = new AtomicInteger();

	@Override
	public void init(ServletConfig config) throws ServletException {
		instance = this;
		super.init(config);

		requestHandler = new ClientRequestHandler(CloudManagerApp.getInstance().getResourceManager());
	}

	// for debugging purposes
	ClientRequestHandler getRequestHandler() {
		return requestHandler;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
	}
	
	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// must be /resource
		String uri = req.getServletPath();
		if (!"/resource".equals(uri)) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		// must be /<ID>
		uri = req.getPathInfo();
		Matcher m = PATTERN_RESOURCE_ID_URI.matcher(uri);
		if (!m.matches()) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		String resourceKey = m.group(1);

		if (requestHandler.handleReleaseRequest(resourceKey)) {
			resp.setStatus(HttpServletResponse.SC_OK);
		}
		else {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			requestSeparator.enter();
		}
		catch (InterruptedException e) {
			return;
		}
		LOG.debug("doPost() enter");

		try {
			// small protection against DoS attacks
			if (req.getContentLength() > MAX_CONTENT_LENGTH) {
				LOG.debug("Detected request larger than max content length. Sending BAD_REQUEST.");
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}

			// must be /resource
			// TODO should be removed; is subject to Application container to register Servlet whereever needed
			String uri = req.getServletPath();
			if (!"/resource".equals(uri)) {
				LOG.debug("Detected request to other path than /resource. Sending NOT_FOUND.");
				resp.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}

			User user = BasicAuthUtil.authenticate(req, resp);
			if (user == null) {
				LOG.debug("No or invalid user information in request. Aborting.");
				return;
			}

			// request must be JSON
			String contentType = req.getContentType();
			if (contentType != null && contentType.contains(";")) {
				contentType = contentType.substring(0, contentType.indexOf(';'));
			}
			if (CONTENT_TYPE_CHECK_ENABLED && !JSON_CONTENT_TYPE.equals(contentType)) {
				LOG.debug("Invalid content type detected. Sending BAD_REQUEST.");
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}

			// encoding must be UTF-8
			if (req.getCharacterEncoding() != null && !"UTF-8".equalsIgnoreCase(req.getCharacterEncoding())) {
				LOG.debug("Invalid character encoding detected. Sending BAD_REQUEST.");
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}

			// extract JSON payload
			InputStream data = req.getInputStream();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			IOUtils.copy(data, baos);
			data.close();

			String jsonSource = new String(baos.toByteArray(), "UTF-8");
			try {
				JSONObject requestObject = new JSONObject(jsonSource);
				waitingRequests.incrementAndGet();
				JSONObject resultObject = requestHandler.handleResourceRequest(user, requestObject);
				waitingRequests.decrementAndGet();

				// send it to response
				StringWriter writer = new StringWriter();
				resultObject.write(writer);
				resp.setStatus(HttpServletResponse.SC_OK);
				resp.setCharacterEncoding("UTF-8");
				resp.setContentType(JSON_CONTENT_TYPE);
				byte[] resultData = writer.toString().getBytes("UTF-8");
				resp.setContentLength(resultData.length);

				try {
					OutputStream os = resp.getOutputStream();
					os.write(resultData);
					os.close();
				}
				catch (IOException e) {
					// client closed connection during wait
					if (resultObject.has("requestId")) {
						requestHandler.abortWaitingRequest(resultObject.getString("requestId"));
					}
				}
			}
			catch (JSONException e) {
				LOG.debug("JSON exception occurred. Sending BAD_REQUEST.");
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
		}
		finally {
			LOG.debug("doPost() leave");
		}
	}

}
