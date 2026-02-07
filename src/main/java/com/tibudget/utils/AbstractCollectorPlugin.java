package com.tibudget.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.tibudget.api.*;
import com.tibudget.api.exceptions.AccessDeny;
import com.tibudget.api.exceptions.CollectError;
import com.tibudget.api.exceptions.ConnectionFailure;
import com.tibudget.api.exceptions.TemporaryUnavailable;
import com.tibudget.dto.AccountDto;
import com.tibudget.dto.RecurringPaymentDto;
import com.tibudget.dto.TransactionDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base implementation for collector plugins.
 * <p>
 * This class provides common HTTP handling,
 * throttling, HTML parsing and JSON deserialization utilities.
 */
public abstract class AbstractCollectorPlugin implements CollectorPlugin {

	private static final Logger LOG = Logger.getLogger(AbstractCollectorPlugin.class.getName());

	protected InternetProvider internetProvider;
	protected CounterpartyProvider counterpartyProvider;
	protected OTPProvider otpProvider;
	protected PDFToolsProvider pdfToolsProvider;

	protected final Map<String, String> settings = new HashMap<>();
	protected final Map<String, String> headers = new HashMap<>();

	protected final List<TransactionDto> transactions = new ArrayList<>();

	private String configurationIdHash = null;

	private String configurationName = null;

	/**
	 * Accounts indexed by a stable reference (IBAN, card number, etc.) provided by the collector, the value
	 * you defined in AccountDto.id
	 */
	protected final Map<String, AccountDto> accounts = new HashMap<>();

	/**
	 * Recurring payments indexed by a stable reference (IBAN, card number, etc.) provided by the collector, the value
	 * you defined in RecurringPaymentDto.id
	 */
	protected final Map<String, RecurringPaymentDto> recurringPayments = new HashMap<>();

	private final Gson gson;

	private String lastUrl;
	private String currentUrl;

	private long minIntervalBetweenRequestInMs = 2000;
	private long lastRequestTime = 0;

	private int progress = 0;

	protected AbstractCollectorPlugin() {
		this.gson = new GsonBuilder()
				.registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
				.create();

		initDefaultHeaders();
	}

	/**
	 * Provides the logger instance for the class. Useful for testing.
	 *
	 * @return The Logger instance associated with this class.
	 */
	protected Logger getLogger() {
		return LOG;
	}

	protected void initDefaultHeaders() {
		addHeader("User-Agent", getUserAgent());
		addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		addHeader("Accept-Language", Locale.getDefault().toLanguageTag() + "," + Locale.getDefault().getLanguage() + ";q=1");
		addHeader("Sec-GPC", "1");
		addHeader("Connection", "keep-alive");
		addHeader("Upgrade-Insecure-Requests", "1");
		addHeader("Sec-Fetch-Dest", "document");
		addHeader("Sec-Fetch-Mode", "navigate");
		addHeader("Sec-Fetch-Site", "none");
		addHeader("Sec-Fetch-User", "?1");
		addHeader("Priority", "u=0, i");
		addHeader("Pragma", "no-cache");
		addHeader("Cache-Control", "no-cache");
	}

	@Override
	public void init(
			InternetProvider internetProvider,
			CounterpartyProvider counterpartyProvider,
			OTPProvider otpProvider,
			PDFToolsProvider pdfToolsProvider,
			Map<String, String> settings,
			List<AccountDto> previousAccounts,
			List<RecurringPaymentDto> previousRecurringPayments
	) {
		this.internetProvider = internetProvider;
		this.counterpartyProvider = counterpartyProvider;
		this.otpProvider = otpProvider;
		this.pdfToolsProvider = pdfToolsProvider;

		if (settings != null) {
			this.settings.putAll(settings);
		}

		if (previousAccounts != null) {
			for (AccountDto account : previousAccounts) {
				accounts.put(account.getId(), account);
			}
		}

		if (previousRecurringPayments != null) {
			for (RecurringPaymentDto recurringPayment : previousRecurringPayments) {
				recurringPayments.put(recurringPayment.getId(), recurringPayment);
			}
		}
	}

	/**
	 * Do nothing by default.
	 * You probably need to override it if your collector uses OAuth2 to connect to the counterparty.
	 */
	@Override
	public String initConnection(URI callbackUri) {
		return null;
	}

	@Override
	public String getConfigurationName() {
		return configurationName;
	}

	public void setConfigurationIdHash(String configurationIdHash) {
		this.configurationIdHash = configurationIdHash;
	}

	public void setConfigurationName(String configurationName) {
		this.configurationName = configurationName;
	}

	@Override
	public String getConfigurationIdHash() {
		return configurationIdHash;
	}

	/**
	 * @return Base domain of the remote service (example: "https://www.mybank.com")
	 */
	public abstract String getDomain();

	/**
	 * Returns the user agent of the mobile device or a default one if none is found.
	 */
	protected String getUserAgent() {
		return Optional.ofNullable(settings.get("user-agent"))
				.orElse("Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
	}

	protected void prepareHeaders(boolean ajax, boolean json) {
		if (currentUrl != null) {
			headers.put("Referer", currentUrl);
		} else {
			headers.remove("Referer");
		}

		if (ajax) {
			headers.put("X-Requested-With", "XMLHttpRequest");
		} else {
			headers.remove("X-Requested-With");
		}

		if (json) {
			headers.put("Accept", "application/json");
			headers.put("Content-Type", "application/json");
		}
	}

	protected String getFullURL(String url) {
		if (url == null) return null;
		return url.startsWith("http") ? url : getDomain() + url;
	}

	protected Document get(String url) throws TemporaryUnavailable {
		return get(url, false);
	}

	protected Document get(String url, boolean ajax) throws TemporaryUnavailable {
		waitForNextRequest();
		prepareHeaders(ajax, false);

		try {
			InternetProvider.Response response = internetProvider.get(getFullURL(url), headers);

			if (response.code != 200) {
				throw new TemporaryUnavailable("HTTP error " + response.code);
			}

			Document doc = Jsoup.parse(response.body, response.location);
			if (!ajax) setNewLocation(doc.location());
			return doc;

		} catch (IOException e) {
			logNetworkError("get", url, e);
			throw new TemporaryUnavailable("Network error (" + e.getMessage() + ")", e);
		}
	}

	protected Document postForm(String url, Map<String, String> data) throws TemporaryUnavailable {
		return postForm(url, data, false);
	}

	protected Document postForm(String url, Map<String, String> data, boolean ajax) throws TemporaryUnavailable {
		waitForNextRequest();
		prepareHeaders(ajax, false);

		try {
			String payload = buildFormEncodedPayload(data);
			InternetProvider.Response response =
					internetProvider.post(getFullURL(url), payload,
							"application/x-www-form-urlencoded; charset=UTF-8",
							headers);

			Document doc = Jsoup.parse(response.body, response.location);
			if (!ajax) setNewLocation(doc.location());
			return doc;

		} catch (IOException e) {
			logNetworkError("postForm", url, e);
			throw new TemporaryUnavailable("Network error (" + e.getMessage() + ")", e);
		}
	}

	public <T> T postJson(String url, Class<T> clazz, PostData postData) throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure {
		waitForNextRequest();
		prepareHeaders(false, true);

		try {
			InternetProvider.Response response =
					internetProvider.post(
							getFullURL(url),
							postData.build(),
							postData.getContentType(),
							headers);

			Document doc = Jsoup.parse(response.body, response.location);
			setNewLocation(doc.location());
			return handleJsonResponse(clazz, response);

		} catch (IOException e) {
			logNetworkError("postJson", url, e);
			throw new TemporaryUnavailable("Network error (" + e.getMessage() + ")", e);
		}
	}

	protected Document postForm(Document page, String formSelector, Map<String, String> fieldsValues) throws TemporaryUnavailable {
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

		return postForm(formUrl, formData, false);
	}

	protected <T> T getJson(String url, Class<T> clazz)
			throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure {

		waitForNextRequest();
		prepareHeaders(false, true);

		try {
			InternetProvider.Response response = internetProvider.get(getFullURL(url), headers);
			return handleJsonResponse(clazz, response);
		} catch (IOException e) {
			logNetworkError("getJson", url, e);
			throw new TemporaryUnavailable("Network error (" + e.getMessage() + ")", e);
		}
	}

	protected <T> T postJson(String url, String json, Class<T> clazz)
			throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure {

		waitForNextRequest();
		prepareHeaders(false, true);

		try {
			InternetProvider.Response response = internetProvider.post(getFullURL(url), json, "application/json", headers);
			return handleJsonResponse(clazz, response);
		} catch (IOException e) {
			logNetworkError("postJson", url, e);
			throw new TemporaryUnavailable("Network error (" + e.getMessage() + ")", e);
		}
	}

	/**
	 * Downloads a remote resource using the current session context
	 * (cookies, headers, referer and throttling).
	 *
	 * @param urlStr Absolute or relative URL of the resource
	 * @param contentType Optional content type override
	 * @return The downloaded file, or null if the download failed
	 */
	protected File download(String urlStr, String contentType) {
		String fullUrl = getFullURL(urlStr);
		if (fullUrl == null) {
			getLogger().severe("Cannot download from null URL");
			return null;
		}

		try {
			return download(new URL(fullUrl), contentType);
		} catch (MalformedURLException e) {
			getLogger().log(Level.SEVERE, "Invalid URL: " + fullUrl, e);
			return null;
		}
	}

	protected File downloadUnkownContentType(String urlStr) {
		return download(urlStr, null);
	}

	/**
	 * Downloads a remote resource using the current session context.
	 *
	 * @param url Absolute URL
	 * @param contentType Optional content type override
	 * @return The downloaded file, or null if the download failed
	 */
	protected File download(URL url, String contentType) {
		if (internetProvider == null) {
			throw new IllegalStateException("InternetProvider not provided");
		}
		if (url == null) {
			throw new IllegalArgumentException("URL to download cannot be null");
		}

		waitForNextRequest();
		prepareHeaders(false, false);

		getLogger().fine("GET " + url);

		try {
			InternetProvider.Response response = internetProvider.downloadFile(url.toString(), headers, contentType);

			return new File(response.body);
		} catch (IOException e) {
			logNetworkError("postForm", url.toString(), e);
			return null;
		}
	}

	protected File downloadUnkownContentType(URL url) {
		return download(url, null);
	}

	private <T> T handleJsonResponse(Class<T> clazz, InternetProvider.Response response)
			throws AccessDeny, TemporaryUnavailable, ConnectionFailure, CollectError {

		if (response.code == 401 || response.code == 403) {
			throw new AccessDeny("Access denied");
		}

		if (response.code >= 500) {
			throw new TemporaryUnavailable("Server error " + response.code);
		}

		if (response.code >= 400) {
			throw new CollectError("Client error " + response.code);
		}

		try {
			T object = gson.fromJson(response.body, clazz);
			if (object == null) {
				throw new CollectError("Empty JSON response");
			}
			return object;
		} catch (JsonSyntaxException e) {
			throw new CollectError("Invalid JSON format: " + e.getMessage(), e);
		}
	}

	protected static String buildFormEncodedPayload(Map<String, String> data)
			throws UnsupportedEncodingException {

		StringBuilder result = new StringBuilder();
		for (Map.Entry<String, String> entry : data.entrySet()) {
			if (result.length() > 0) result.append("&");
			result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
			result.append("=");
			result.append(URLEncoder.encode(
					Objects.toString(entry.getValue(), ""),
					StandardCharsets.UTF_8
			));
		}
		return result.toString();
	}

	protected void setNewLocation(String url) {
		lastUrl = currentUrl;
		currentUrl = url;
	}

	protected void waitForNextRequest() {
		if (lastRequestTime > 0) {
			long elapsed = System.currentTimeMillis() - lastRequestTime;
			if (elapsed < minIntervalBetweenRequestInMs) {
				try {
					Thread.sleep(minIntervalBetweenRequestInMs - elapsed);
				} catch (InterruptedException ignored) {
				}
			}
		}
		lastRequestTime = System.currentTimeMillis();
	}

	@Override
	public int getProgress() {
		return progress;
	}

	protected void setProgress(int value) {
		progress = Math.max(0, Math.min(100, value));
		getLogger().info("Progress: " + progress + "%");
	}

	protected String getCurrentUrl() {
		return currentUrl;
	}

	protected String getLastUrl() {
		return lastUrl;
	}

	@Override
	public Map<String, String> getSettings() {
		return settings;
	}

	@Override
	public List<AccountDto> getAccounts() {
		return new ArrayList<>(accounts.values());
	}

	@Override
	public List<RecurringPaymentDto> getRecurringPayments() {
		return new ArrayList<>(recurringPayments.values());
	}

	@Override
	public List<TransactionDto> getTransactions() {
		return transactions;
	}

	protected Map<String, String> getHeaders() {
		return Collections.unmodifiableMap(headers);
	}

	protected String getHeader(String name) {
		return headers.get(name);
	}

	protected void addHeader(String name, String value) {
		if (name != null && value != null) {
			headers.put(name.trim(), value);
		}
	}

	protected long getMinIntervalBetweenRequestInMs() {
		return minIntervalBetweenRequestInMs;
	}

	protected void setMinIntervalBetweenRequestInMs(long value) {
		this.minIntervalBetweenRequestInMs = value;
	}

	protected void logNetworkError(
			String operation,
			String url,
			Exception exception
	) {
		Map<String, Object> log = new LinkedHashMap<>();

		log.put("type", "network_error");
		log.put("operation", operation);
		log.put("url", sanitizeUrl(url));
		log.put("currentUrl", sanitizeUrl(currentUrl));
		log.put("lastUrl", sanitizeUrl(lastUrl));
		log.put("headersCount", headers.size());
		log.put("timestamp", System.currentTimeMillis());
		log.put("exception", exception.getClass().getName());
		log.put("message", exception.getMessage());
		log.put("stackTrace", stackTraceToString(exception));

		getLogger().severe(toJsonLine(log));
	}

	private String sanitizeUrl(String url) {
		if (url == null) return null;

		// Remove query params to avoid leaking tokens
		int idx = url.indexOf('?');
		return idx > 0 ? url.substring(0, idx) + "?[query removed for confidentiality reasons]" : url;
	}

	private String toJsonLine(Map<String, Object> map) {
		try {
			return gson.toJson(map);
		} catch (Exception e) {
			return "{\"type\":\"network_error\",\"error\":\"json_serialization_failed\"}";
		}
	}

	private String stackTraceToString(Throwable t) {
		StringBuilder sb = new StringBuilder();

		sb.append(t.toString());

		for (StackTraceElement el : t.getStackTrace()) {
			sb.append(" | at ")
					.append(el.getClassName())
					.append(".")
					.append(el.getMethodName())
					.append("(")
					.append(el.getFileName())
					.append(":")
					.append(el.getLineNumber())
					.append(")");
		}

		return sb.toString();
	}

}
