import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

//import org.apache.logging.log4j.Logger;
//import org.apache.logging.log4j.LogManager;

/**
 * A Java servlet that handles file upload from client.
 *
 * @author Markus Heene
 */
public class ExecutorServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

//  private static Logger log = LogManager.getLogger(ExecutorServlet.class.getName());
	private static Logger log = Logger.getLogger(ExecutorServlet.class.getName());

	// location to store file uploaded
	private static final String UPLOAD_DIRECTORY = "upload";

	// upload settings
	private static final int MEMORY_THRESHOLD = 1024 * 1024 * 10; // 10 MB
	private static final int MAX_FILE_SIZE = 1024 * 1024 * 10; // 10 MB
	private static final int MAX_REQUEST_SIZE = 1024 * 1024 * 20; // 20 MB

	private static final ExecutorService executor = Executors.newFixedThreadPool(20);

	private String installPath; // NOSONAR
	private String cmd; // NOSONAR

	private ImmutableList<String> args;
	private ImmutableMap<String, String> env;

	@Override
	public void destroy() {
		super.destroy();
		log.info("Destroy: call");
		executor.shutdown();
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		installPath = config.getServletContext().getRealPath("");
		// Difference between Jetty and Tomcat
		if (!installPath.endsWith(File.separator)) {
			installPath = installPath + File.separator;
		}

		System.setProperty("java.util.logging.config.file", installPath + "/WEB-INF/resource/logging.properties");
		// System.setProperty("log4j.configurationFile", installPath +
		// "/WEB-INF/resource/logging.properties");

		try {
			LogManager.getLogManager().readConfiguration();
		} catch (Exception e) {
			e.printStackTrace(); // NOSONAR
		}

		log.info("Init: call");

		List<String> cmdArgs = new ArrayList<>();
		HashMap<String, String> p_env = new HashMap<>();

		cmd = installPath + getServletConfig().getInitParameter("executableRelativePath");

		String webXmlArgs = getServletConfig().getInitParameter("cmdArgsCommaSeparated");

		String[] parts = webXmlArgs.split(",");

		for (int i = 0; i < parts.length; i++) {
			cmdArgs.add(parts[i]);
		}
		args = new ImmutableList.Builder<String>().addAll(cmdArgs).build();

		String key = getServletConfig().getInitParameter("envKey");

		String value = installPath + getServletConfig().getInitParameter("envValue");
		if (log.isLoggable(Level.INFO)) {
			log.info("webXmlArgs:" + webXmlArgs);
			log.info("cmd: " + cmd);
			log.info("envKey: " + key);
			log.info("envValue: " + value);
		}

		p_env.put(key, value);
		p_env.put("LD_LIBRARY_PATH", installPath + "WEB-INF/resource/bin/.libs");
		env = new ImmutableMap.Builder<String, String>().putAll(p_env).build();

		try {
			File file = new File(cmd);
			Set<PosixFilePermission> perms = new HashSet<>();
			perms.add(PosixFilePermission.OWNER_READ);
			perms.add(PosixFilePermission.OWNER_EXECUTE);

			Files.setPosixFilePermissions(file.toPath(), perms);

			file = new File(installPath + "WEB-INF/resource/bin/.libs/bufr_decoder");

			Files.setPosixFilePermissions(file.toPath(), perms);

		} catch (IOException ioe) {
			log.severe(ioe.getMessage());
		}
	}

	/**
	 * Upon receiving file upload submission, parses the request to read upload data
	 * and saves the file on disk.
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		// checks if the request actually contains upload file
		// integrated error.jsp
		if (!ServletFileUpload.isMultipartContent(request)) {
			// if not, we stop here
			try {
				PrintWriter writer = response.getWriter();
				writer.println("Error: Form must has enctype=multipart/form-data.");
				writer.flush();
				return;
			} catch (IOException ioe) {
				log.severe(ioe.getMessage());
				return;
			}
		}

		ServletFileUpload upload = null;
		String uploadPath = null;
		File uploadDir = null;

		String outputFormat = request.getParameter("output");
		// configures upload settings
		DiskFileItemFactory factory = new DiskFileItemFactory();
		// sets memory threshold - beyond which files are stored in disk
		factory.setSizeThreshold(MEMORY_THRESHOLD);
		// sets temporary location to store files
		// System.out.println("tmpdir: " + System.getProperty("java.io.tmpdir"));
		factory.setRepository(new File(System.getProperty("java.io.tmpdir")));

		upload = new ServletFileUpload(factory);

		// sets maximum size of upload file
		upload.setFileSizeMax(MAX_FILE_SIZE);

		// sets maximum size of request (include file + form data)
		upload.setSizeMax(MAX_REQUEST_SIZE);

		// constructs the directory path to store upload file
		// this path is relative to application's directory
		uploadPath = getServletContext().getRealPath("") + File.separator + UPLOAD_DIRECTORY;

		// creates the directory if it does not exist
		if (Boolean.valueOf(getServletConfig().getInitParameter("storeFiles"))) {
			uploadDir = new File(uploadPath);
			if (!uploadDir.exists()) {
				boolean status = uploadDir.mkdir();
				if (!status) {
					try {
						PrintWriter writer = response.getWriter();
						writer.println("Error: Create Dir failed");
						writer.flush();
						return;
					} catch (IOException ioe) {
						log.severe(ioe.toString());
						return;
					}
				}
			}
		}

		StringBuilder sb = new StringBuilder();
		File tempFile = null;
		long startOverallResponseTime = System.currentTimeMillis();
		long endOverallResponseTime = -1;
		int returnCode = -1;
		String errorMessage = null;
		try {
			// parses the request's content to extract file data
			// @SuppressWarnings("unchecked")
			List<FileItem> formItems = upload.parseRequest(request);
			String fileName = null;

			if (formItems != null && formItems.size() > 0) {
				// iterates over form's fields
				for (FileItem item : formItems) {
					// processes only fields that are not form fields
					if (!item.isFormField()) {
						fileName = item.getName();
						if (Boolean.valueOf(getServletConfig().getInitParameter("storeFiles"))) {
							String filePath = uploadPath + File.separator + fileName;
							File storeFile = new File(filePath);
							// saves the file on disk; old files with same filename are overwritten
							item.write(storeFile);
						}
						// process bufr
						InputStream is = item.getInputStream();

						tempFile = File.createTempFile("prefix-", "-suffix");
						tempFile.deleteOnExit();
						FileOutputStream out = new FileOutputStream(tempFile);
						IOUtils.copy(is, out);
						out.flush();
						out.close();

						log.info("getCanonicalPath: " + tempFile.getCanonicalPath());

						List<CmdTask> tasks = new ArrayList<>();

						List<String> cmdArgs = new ArrayList<>(args);
						cmdArgs.add(tempFile.getCanonicalPath());
						tasks.add(new CmdTask("libecbufr", this.cmd, cmdArgs, this.env, executor));
						while (!tasks.isEmpty()) {
							for (Iterator<CmdTask> it = tasks.iterator(); it.hasNext();) {

								CmdTask task = it.next();
								if (task.isDone()) {
									sb.append(task.getOutput());
									returnCode = (task.getReturnCode().intValue());
									errorMessage = task.getError();
									it.remove();
								}
							}
							// avoid tight loop in "main" thread
							if (!tasks.isEmpty())
								Thread.sleep(100);
						}
						// now you have all responses for all async requests
						endOverallResponseTime = System.currentTimeMillis();
						boolean tempFileDeleted = tempFile.delete();
						log.info("Executor Deleted tempFile: " + tempFileDeleted);
						log.info("Executor: " + sb.toString().length());
					}
				}
			}

		} catch (RuntimeException e) {
			// e.printStackTrace();
			log.severe("RuntimeException getCause: " + e.getMessage());
			throw e; // NOSONAR
		} catch (Exception ex) {

			log.severe("ex: 2 Message: " + ex.getMessage());
			log.severe("ex: 2 Class: " + ex.getClass().getName());
			// ex.printStackTrace();
			if (tempFile != null) {
				boolean tempFileDeleted = tempFile.delete();
				log.info("Deleted tempFile: " + tempFileDeleted);
			}
			request.setAttribute("exception", StringEscapeUtils.escapeHtml4(ex.getMessage()));
			try {
				getServletContext().getRequestDispatcher("/error").forward(request, response);
				return;
			} catch (ServletException se) {
				log.severe(se.getMessage());
			} catch (IOException ioe) {
				log.severe(ioe.getMessage());
			}
		}

		log.info("Execution time: " + (endOverallResponseTime - startOverallResponseTime) + " ms");

		if (outputFormat == null) {
			request.setAttribute("bufr", StringEscapeUtils.escapeHtml4(sb.toString().trim()));
			// System.out.println("'" + StringEscapeUtils.escapeHtml4(sb.toString().trim())
			// + "'");
			try {
				getServletContext().getRequestDispatcher("/upload").forward(request, response);
			} catch (ServletException se) {
				log.severe(se.getMessage());
			} catch (IOException ioe) {
				log.severe(ioe.getMessage());
			}

		} else if (outputFormat.equals("text")) {
			response.setContentType("text/plain");
			request.setAttribute("text", sb.toString());
			try {
				getServletContext().getRequestDispatcher("/text").forward(request, response);
			} catch (ServletException se) {
				log.severe(se.getMessage());
			} catch (IOException ioe) {
				log.severe(ioe.getMessage());
			}
		} else if (outputFormat.equals("json")) {
			GenericResponse rrb = null;
			if (returnCode == 1) {
				rrb = new GenericResponse(null, true);
			} else {
				rrb = new GenericResponse(errorMessage, false);
			}

			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			String jsonResponseString = gson.toJson(rrb);
			response.setContentType("application/json; charset=UTF-8");
			request.setAttribute("json", jsonResponseString);

			try {
				getServletContext().getRequestDispatcher("/json").forward(request, response);
			} catch (ServletException se) {
				log.severe(se.getMessage());
			} catch (IOException ioe) {
				log.severe(ioe.getMessage());
			}
		} else {
			request.setAttribute("exception", StringEscapeUtils.escapeHtml4("Wrong parameter"));
			try {
				getServletContext().getRequestDispatcher("/error").forward(request, response);
			} catch (ServletException se) {
				log.severe(se.getMessage());
			} catch (IOException ioe) {
				log.severe(ioe.getMessage());
			}
		}
	}
}
