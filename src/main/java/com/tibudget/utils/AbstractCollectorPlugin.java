package com.tibudget.utils;

import com.tibudget.api.CollectorPlugin;
import com.tibudget.api.InternetProvider;
import com.tibudget.api.OTPProvider;
import com.tibudget.api.exceptions.TemporaryUnavailable;
import com.tibudget.dto.AccountDto;
import com.tibudget.dto.OperationDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractCollectorPlugin implements CollectorPlugin {

	private static final Logger LOG = Logger.getLogger(AbstractCollectorPlugin.class.getName());

	public InternetProvider internetProvider;

	public OTPProvider otpProvider;

	public final Map<String, String> cookies;

	private final Map<String, String> headers;

	public List<OperationDto> operations;

	public List<AccountDto> accounts;

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
		headers = new HashMap<>();
		cookies = new HashMap<>();

		// Default Android HTTP headers
		addHeader("Connection", "keep-alive");
		addHeader("Upgrade-Insecure-Requests", "1");
		addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36");
		addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
		addHeader("Accept-Encoding", "gzip, deflate, br");
		addHeader("Accept-Language", Locale.getDefault().toLanguageTag() + "," + Locale.getDefault().getLanguage() + ";q=1");
	}

	@Override
	public void init(InternetProvider internetProvider, OTPProvider otpProvider, Map<String, String> previousCookies, List<AccountDto> previousAccounts) {
		this.internetProvider = internetProvider;
		this.otpProvider = otpProvider;
		if (previousCookies != null) {
			this.cookies.putAll(previousCookies);
		}
		if (previousAccounts != null) {
			this.accounts.addAll(previousAccounts);
		}
	}

	public Map<String, String> getHeaders() {
		return Collections.unmodifiableMap(headers);
	}

	public String getHeader(String header) {
		return headers.get(header);
	}

	public void addHeader(String name, String value) {
		if (name != null && value != null) {
			headers.put(name.trim().toLowerCase(), value);
		}
	}

	@Override
	public Map<String, String> getCookies() {
		return cookies;
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

	/**
	 * @return getDomain() name of server, like "https://www.mybank.com"
	 */
	public abstract String getDomain();

	public Document get(String url) throws TemporaryUnavailable {
		return get(url, false);
	}

	public Document get(String url, boolean ajax) throws TemporaryUnavailable {
		String fullUrl;
		if (url.startsWith("http")) {
			fullUrl = url;
		}
		else {
			fullUrl = getDomain() + url;
		}
		LOG.fine("GET " + fullUrl);
		waitForNextRequest();
		try {

			if (currentUrl != null) {
				headers.put("Referer", currentUrl);
			}
			else {
				headers.remove("Referer");
			}
			if (ajax) {
				headers.put("X-Requested-With", "XMLHttpRequest");
			}
			else {
				headers.remove("X-Requested-With");
			}
			InternetProvider.Response response = internetProvider.get(fullUrl, headers, cookies);

			if (response.code != 200) {
				StringBuilder msg = new StringBuilder();
				msg.append("HTTP/").append(response.protocol).append(" GET ").append(fullUrl).append(" => Error ").append(response.code).append(": ").append(response.message);
				for (Map.Entry<String, String> header : headers.entrySet()) {
					msg.append("\n").append(header.getKey()).append(": ").append(header.getValue());
				}
				for (Map.Entry<String, String> cookie : cookies.entrySet()) {
					msg.append("\n").append(cookie.getKey()).append(": ").append(cookie.getValue());
				}
				throw new TemporaryUnavailable(msg.toString());
			}

			cookies.clear();
			cookies.putAll(response.cookies);

			Document doc = Jsoup.parse(response.body, response.location);
			if (!ajax) setNewLocation(doc.location());
			LOG.fine("  |-> GET (" + response.protocol + ") " + response.location);
			return doc;
		} catch (IOException e) {
			throw new TemporaryUnavailable("error.tmpunavailable", e);
		}
	}

	private void updateCookies(List<String> setCookies) {
		for (String header : setCookies) {
			String[] parts = header.split(";")[0].split("=", 2);
			if (parts.length == 2) {
				cookies.put(parts[0].trim(), parts[1].trim());
			}
		}
	}

	public void setNewLocation(String url) {
		lastUrl = currentUrl;
		currentUrl = url;
	}

	public Document post(String relativeUrl, Map<String, String> postdata) throws TemporaryUnavailable {
		return post(relativeUrl, postdata, false);
	}

	public static String buildFormEncodedPayload(Map<String, String> data) throws UnsupportedEncodingException {
		StringBuilder result = new StringBuilder();
		for (Map.Entry<String, String> entry : data.entrySet()) {
			if (result.length() > 0) {
				result.append("&");
			}
			result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
			result.append("=");
			result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
		}
		return result.toString();
	}

	public Document post(String url, Map<String, String> postdatas, boolean ajax) throws TemporaryUnavailable {
		LOG.fine("POST " + url);
		Document page;
		try {
			// Avoid flooding the site
			waitForNextRequest();

			// Execute the POST request with current cookies
			InternetProvider.Response response = internetProvider.post(url, buildFormEncodedPayload(postdatas), headers, cookies);

			// Parse the returned document
			page = Jsoup.parse(response.body, response.location);

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
		return post(url, datas, false, false);
	}

	public Document post(String url, String datas, boolean ajax, boolean isJson) throws TemporaryUnavailable {
		LOG.fine("POST " + url);
		Document page;
		try {
			// Avoid flooding the site
			waitForNextRequest();

			// Prepare the POST request
			if (isJson) {
				headers.put("accept", "*/*");
				headers.put("content-type", "application/json");
				headers.put("content-length", String.valueOf(datas.length()));
			}
			if (currentUrl != null) {
				headers.put("Referer", currentUrl);
			}
			else {
				headers.remove("Referer");
			}
			if (ajax) {
				headers.put("X-Requested-With", "XMLHttpRequest");
			}
			else {
				headers.remove("X-Requested-With");
			}

			// Execute the POST request
			InternetProvider.Response response = internetProvider.post(url, datas, headers, cookies);

			// Update the cookies
			this.cookies.clear();
			this.cookies.putAll(response.cookies);

			// Parse the returned document
			page = Jsoup.parse(response.body, response.location);

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

	public File download(String urlStr) {
        try {
			if (urlStr == null) {
				LOG.log(Level.SEVERE, "Cannot download from NULL URL");
				return null;
			}
            URL url = new URL(urlStr);
			return download(url);
        } catch (MalformedURLException e) {
			LOG.log(Level.SEVERE, "Cannot download from URL: " + urlStr, e);
            return null;
        }
    }

	public File download(URL url) {
		LOG.fine("GET " + url.toString());
		File downloadedFile = null;
		try {
			InternetProvider.Response response = internetProvider.downloadFile(url.toString(), headers, cookies);
			downloadedFile = new File(response.body);
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
