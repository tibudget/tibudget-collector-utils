package com.tibudget.utils;

import com.tibudget.api.CollectorPlugin;
import com.tibudget.api.HumanSimulatorProvider;
import com.tibudget.api.OTPProvider;
import com.tibudget.api.exceptions.TemporaryUnavailable;
import com.tibudget.dto.AccountDto;
import com.tibudget.dto.OperationDto;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractCollectorPlugin implements CollectorPlugin {

	private static final Logger LOG = Logger.getLogger(AbstractCollectorPlugin.class.getName());

	public final Map<String, String> cookies;

	public final Map<String, String> headers;

	public List<OperationDto> operations;

	public List<AccountDto> accounts;

	public OTPProvider otpProvider;

	public HumanSimulatorProvider humanSimulatorProvider;

	/**
	 * To be used as a referer
	 */
	private String lastUrl = null;

	/**
	 * To be used as a current location
	 */
	private String currentUrl = null;

	/**
	 * Minimum delay in ms between 2 requests
	 */
	private long minIntervalBetweenRequestInMs = 2000;

	private long lastRequestTime = 0;

	private int progress = 0;

	public AbstractCollectorPlugin() {
		super();
		operations = new ArrayList<>();
		accounts = new ArrayList<>();
		cookies = new HashMap<>();

		// Default Android HTTP headers
		headers = new HashMap<>();
		headers.put("Connection", "keep-alive");
		headers.put("Upgrade-Insecure-Requests", "1");
		headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36");
		headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
		headers.put("Accept-Encoding", "gzip, deflate, br");
		headers.put("Accept-Language", Locale.getDefault().toLanguageTag() + "," + Locale.getDefault().getLanguage() + ";q=1");
	}

	@Override
	public List<AccountDto> getAccounts() {
		return accounts;
	}

	@Override
	public List<OperationDto> getOperations() {
		return operations;
	}

	@Override
	public int getProgress() {
		return progress;
	}

	public void setProgress(int newProgress) {
		this.progress = Math.max(0, Math.min(100, newProgress));
		LOG.info("Progress: " + progress + "%");
	}

	@Override
	public Map<String, String> getCookies() {
		return cookies;
	}

	@Override
	public void setCookies(Map<String, String> map) {
		cookies.putAll(map);
	}

	@Override
	public void setOTPProvider(OTPProvider otpProvider) {
		this.otpProvider = otpProvider;
	}

	@Override
	public void setHumanSimulatorProvider(HumanSimulatorProvider humanSimulatorProvider) {
		this.humanSimulatorProvider = humanSimulatorProvider;
	}

	@Override
	public void setAccounts(List<AccountDto> list) {
		this.accounts.addAll(list);
	}

	/**
	 * @return getDomain() name of server, like "https://www.mybank.com"
	 */
	public abstract String getDomain();

	public Document get(String url) throws TemporaryUnavailable {
		return get(url, false);
	}

	public Document get(String url, boolean ajax) throws TemporaryUnavailable {
		LOG.fine("GET " + url);
		Document page;
		try {
			// Avoid flooding the site
			waitForNextRequest();

			// Execute the GET request with current cookies
			Connection.Response response = connect(url, ajax)
					.cookies(this.cookies)
					.method(Connection.Method.GET)
					.execute();

			// Update the cookies
			this.cookies.putAll(response.cookies());

			// Parse the returned document
			page = response.parse();

		} catch (HttpStatusException e) {
			// Include HTTP error code in the exception message
			throw new TemporaryUnavailable("HTTP error " + e.getStatusCode() + ": " + e.getMessage(), e);
		} catch (IOException e) {
			throw new TemporaryUnavailable("error.tmpunavailable", e);
		}

		if (!ajax) {
			// Define the new location (from 'page' because it can be redirected)
			setNewLocation(page.location());
		}

		LOG.fine("  |-> GET " + page.location());
		return page;
	}

	public void setNewLocation(String url) {
		lastUrl = currentUrl;
		currentUrl = url;
	}

	public Connection connect(String relativeUrl, boolean ajax) {
		String fullUrl;
		if (relativeUrl.startsWith(getDomain())) {
			fullUrl = relativeUrl;
		}
		else {
			fullUrl = getDomain() + relativeUrl;
		}
		Connection connection = Jsoup.connect(fullUrl)
				.ignoreContentType(true)
				.header("Pragma", "no-cache")
				.header("Cache-Control", "no-cache");
		for (Map.Entry<String, String> header : headers.entrySet()) {
			connection.header(header.getKey(), header.getValue());
		}
		if (currentUrl != null && !currentUrl.isEmpty()) {
			// Simulate the referer
			connection.header("Referer", currentUrl);
		}
		if (ajax) {
			connection.header("X-Requested-With", "XMLHttpRequest");
		}
		return connection;
	}

	public Document post(String relativeUrl, Map<String, String> postdata) throws TemporaryUnavailable {
		return post(relativeUrl, postdata, false);
	}

	public Document post(String url, Map<String, String> postdatas, boolean ajax) throws TemporaryUnavailable {
		LOG.fine("POST " + url);
		Document page;
		try {
			// Avoid flooding the site
			waitForNextRequest();

			// Execute the POST request with current cookies
			Connection.Response response = connect(url, ajax)
					.cookies(this.cookies)
					.method(Connection.Method.POST)
					.data(postdatas)
					.execute();

			// Update the cookies
			this.cookies.putAll(response.cookies());

			// Parse the returned document
			page = response.parse();

		} catch (HttpStatusException e) {
			// Include HTTP error code in the exception message
			throw new TemporaryUnavailable("HTTP error " + e.getStatusCode() + ": " + e.getMessage(), e);
		} catch (IOException e) {
			throw new TemporaryUnavailable("error.tmpunavailable", e);
		}

		if (!ajax) {
			// Define the new location (from 'page' because it can be redirected)
			setNewLocation(page.location());
		}

		LOG.fine("  |-> POST " + page.location());
		return page;
	}

	public Document post(String url, String datas) throws TemporaryUnavailable {
		return post(url, datas, false);
	}

	public Document post(String url, String datas, boolean ajax) throws TemporaryUnavailable {
		LOG.fine("POST " + url);
		Document page;
		try {
			// Avoid flooding the site
			waitForNextRequest();

			// Execute the POST request with current cookies
			Connection.Response response = connect(url, ajax)
					.cookies(this.cookies)
					.method(Connection.Method.POST)
					.requestBody(datas)
					.execute();

			// Update the cookies
			this.cookies.putAll(response.cookies());

			// Parse the returned document
			page = response.parse();

		} catch (HttpStatusException e) {
			// Include HTTP error code in the exception message
			throw new TemporaryUnavailable("HTTP error " + e.getStatusCode() + ": " + e.getMessage(), e);
		} catch (IOException e) {
			throw new TemporaryUnavailable("error.tmpunavailable", e);
		}

		if (!ajax) {
			// Define the new location (from 'page' because it can be redirected)
			setNewLocation(page.location());
		}

		LOG.fine("  |-> POST " + page.location());
		return page;
	}

	public Document postForm(Document page, String formSelector, Map<String, String> fieldsValues) throws TemporaryUnavailable {
		Map<String, String> formData = new HashMap<>();

		// Find the form
		Element form = page.selectFirst(formSelector);
		if (form == null) {
			throw new IllegalArgumentException("Form not found with selector: " + formSelector);
		}

		String formUrl = form.attr("action");
		if (formUrl == null || formUrl.isEmpty()) {
			throw new IllegalArgumentException("The action of the form is not defined");
		}

		// Get all inputs of the form
		Elements inputs = form.select("input[name]");
		for (Element input : inputs) {
			String name = input.attr("name");
			String value = input.attr("value"); // Valeur par défaut si présente
			formData.put(name, value);
		}

		// Replace or add values
        formData.putAll(fieldsValues);

		return post(formUrl, formData, false);
	}

	public File download(URL url) {
		LOG.fine("GET " + url.toString());
		File downloadedFile = null;
		try {
			File f = File.createTempFile("tibu-", ".bin");

			URLConnection urlConn = url.openConnection();

			if (url.toString().startsWith(getDomain())) {
				// Only send cookies if it belongs to the same domain
				StringBuilder cookieStr = new StringBuilder();
				for (Map.Entry<String, String> cookie : this.cookies.entrySet()) {
					cookieStr.append(cookie.getKey());
					cookieStr.append("=");
					cookieStr.append(cookie.getValue());
					cookieStr.append("; ");
				}
				urlConn.setRequestProperty("Cookie", cookieStr.toString());
			}

			urlConn.connect();

			try (OutputStream out = Files.newOutputStream(f.toPath())) {
				byte[] buf = new byte[1024];
				int len;
				while ((len = urlConn.getInputStream().read(buf)) > 0) {
					out.write(buf, 0, len);
				}
			}

			downloadedFile = f;
		} catch (HttpStatusException e) {
			// Include HTTP error code in the log message
			LOG.log(Level.SEVERE, "Ignoring IOException (on page download): HTTP error " + e.getStatusCode() + ": " + e.getMessage(), e);
		} catch (IOException e) {
			LOG.log(Level.SEVERE, "Ignoring IOException (on page download): " + e.getMessage(), e);
		}
		return downloadedFile;
	}

	public String getCurrentUrl() {
        return currentUrl;
    }

	public String getLastUrl() {
        return lastUrl;
    }

	public long getMinIntervalBetweenRequestInMs() {
		return minIntervalBetweenRequestInMs;
	}

	public void setMinIntervalBetweenRequestInMs(long minIntervalBetweenRequestInMs) {
		this.minIntervalBetweenRequestInMs = minIntervalBetweenRequestInMs;
	}

	/**
	 * Ensures that a minimum interval between requests is respected.
	 * If the last request was made less than the defined minimum interval ago,
	 * this method will pause execution until the required time has elapsed.
	 * <p>
	 * The delay helps prevent excessive requests to the server, reducing
	 * the risk of being rate-limited or blocked.
	 * <p>
	 * This method is called before executing any HTTP request.
	 */
	public void waitForNextRequest() {
		if (lastRequestTime > 0) {
			long now = System.currentTimeMillis();
			long elapsedTime = now - lastRequestTime;

			if (elapsedTime < minIntervalBetweenRequestInMs) {
				try {
					long sleepTime = minIntervalBetweenRequestInMs - elapsedTime;
					LOG.fine("Waiting " + sleepTime + "ms before next request...");
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					// Ignore
				}
			}
		}
		lastRequestTime = System.currentTimeMillis();
	}
}
