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
import com.tibudget.dto.FileDto;
import com.tibudget.dto.RecurringPaymentDto;
import com.tibudget.dto.TransactionDto;
import com.tibudget.utils.exception.MustStopException;
import com.tibudget.utils.gson.InstantAdapter;
import com.tibudget.utils.gson.LocalDateAdapter;
import com.tibudget.utils.gson.OffsetDateTimeAdapter;
import com.tibudget.utils.gson.ZonedDateTimeAdapter;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base implementation for collector plugins.
 * <p>
 * This class provides common HTTP handling,
 * throttling, HTML parsing and JSON deserialization utilities.
 * <p>
 * Implement {@link #getDomain()} and {@link #collect()} in your collector.
 * Use the injected providers for requests and preserve previous DTO identity.
 * Public helpers can be tested or mocked. Instances are not thread-safe.
 */
public abstract class AbstractCollectorPlugin implements CollectorPlugin {

	private static final Logger LOG = Logger.getLogger(AbstractCollectorPlugin.class.getName());

	private static final String SETTINGS_PROGRESS_KEY = "tibu.collect.progress";

	public InternetProvider internetProvider;
	public CounterpartyProvider counterpartyProvider;
	public OTPProvider otpProvider;
	public PDFToolsProvider pdfToolsProvider;
	public volatile boolean stop = false;

	public final Map<String, String> settings = new HashMap<>();
	public final Map<String, String> headers = new HashMap<>();

	public final List<TransactionDto> transactions = new ArrayList<>();

	private String configurationIdHash = null;

	private String configurationName = null;

	/**
	 * Accounts indexed by a stable reference (IBAN, card number, etc.) provided by the collector, the value
	 * returned by {@link AccountDto#getId()}
	 */
	public final Map<String, AccountDto> accounts = new HashMap<>();

	/**
	 * Recurring payments indexed by a stable reference (IBAN, card number, etc.) provided by the collector, the value
	 * returned by {@link RecurringPaymentDto#getId()}
	 */
	public final Map<String, RecurringPaymentDto> recurringPayments = new HashMap<>();

	public final Gson gson;

	private String lastUrl;
	private String currentUrl;

	private long minIntervalBetweenRequestInMs = 2000;
	private long lastRequestTime = 0;

	private int progress = 0;

	public AbstractCollectorPlugin() {
		this.gson = new GsonBuilder()
				.registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
				.registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter())
				.registerTypeAdapter(Instant.class, new InstantAdapter())
				.registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
				.create();

		initDefaultHeaders();
	}

	/**
	 * Provides the logger instance for the class. Useful for testing.
	 *
	 * @return The Logger instance associated with this class.
	 */
	public Logger getLogger() {
		return LOG;
	}

	public void initDefaultHeaders() {
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

	@Override
	public void stop() {
		stop = true;
	}

	/**
	 * @return true if the collector must stop gracefully and save its current progress before returning from {@link #collect()}, so that the next collect can resume from where it stopped.
	 */
	protected boolean mustStop() {
		// Complete at least one transaction before honoring a stop request.
		return stop && !transactions.isEmpty();
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
	 * @return Base domain of the remote service (example: "https://www.theshop.com")
	 */
	public abstract String getDomain();

	/**
	 * Returns the user agent of the mobile device or a default one if none is found.
	 */
	public String getUserAgent() {
		return Optional.ofNullable(settings.get("user-agent"))
				.orElse("Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
	}

	public void prepareHeaders(boolean ajax, boolean json) {
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

	public String getFullURL(String url) {

		if (url == null) {
			return null;
		}

		if (url.startsWith("http")) {
			return url;
		}

		String domain = getDomain();

		if (domain == null || domain.isBlank()) {
			return url;
		}

		boolean domainEndsWithSlash = domain.endsWith("/");
		boolean urlStartsWithSlash = url.startsWith("/");

		if (domainEndsWithSlash && urlStartsWithSlash) {
			return domain + url.substring(1);
		}

		if (!domainEndsWithSlash && !urlStartsWithSlash) {
			return domain + "/" + url;
		}

		return domain + url;
	}


	public Document get(String url) throws TemporaryUnavailable {
		return get(url, false);
	}

	public Document get(String url, boolean ajax) throws TemporaryUnavailable {
		waitForNextRequest();
		prepareHeaders(ajax, false);

		try {
			InternetProvider.Response response = internetProvider.get(getFullURL(url), headers);

			if (response == null) {
				throw new TemporaryUnavailable("Empty response for " + url);
			}
			if (response.code != 200) {
				throw new TemporaryUnavailable("HTTP error " + response.code + " for " + url);
			}

			Document doc = Jsoup.parse(response.body, response.location);
			if (!ajax) setNewLocation(doc.location());
			return doc;

		} catch (IOException e) {
			logNetworkError("get", url, e);
			throw new TemporaryUnavailable("Network error (" + e.getMessage() + ")", e);
		}
	}

	public Document postForm(String url, Map<String, String> data) throws TemporaryUnavailable {
		return postForm(url, data, false);
	}

	public Document postForm(String url, Map<String, String> data, boolean ajax) throws TemporaryUnavailable {
		waitForNextRequest();
		prepareHeaders(ajax, false);

		try {
			String payload = buildFormEncodedPayload(data);
			InternetProvider.Response response =
					internetProvider.post(getFullURL(url), payload,
							"application/x-www-form-urlencoded; charset=UTF-8",
							headers);

			if (response == null) {
				throw new TemporaryUnavailable("Empty response for " + url);
			}
			if (response.code != 200) {
				throw new TemporaryUnavailable("HTTP error " + response.code + " for " + url);
			}

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

			T result = handleJsonResponse(clazz, response);
			setNewLocation(response.location);
			return result;

		} catch (IOException e) {
			logNetworkError("postJson", url, e);
			throw new TemporaryUnavailable("Network error (" + e.getMessage() + ")", e);
		}
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
			String value = input.attr("value"); // Use the input's default value when present.
			formData.put(name, value);
		}

		// Replace or add values
		formData.putAll(fieldsValues);

		return postForm(formUrl, formData, false);
	}

	public <T> T getJson(String url, Class<T> clazz)
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

	public <T> T postJson(String url, String json, Class<T> clazz)
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
	public File download(String urlStr, String contentType) {
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

	public File downloadUnkownContentType(String urlStr) {
		return download(urlStr, null);
	}

	/**
	 * Downloads a remote resource using the current session context.
	 *
	 * @param url Absolute URL
	 * @param contentType Optional content type override
	 * @return The downloaded file, or null if the download failed
	 */
	public File download(URL url, String contentType) {
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
			if (response == null) {
				throw new IOException("Empty response for download " + url);
			}

			return new File(response.body);
		} catch (IOException e) {
			logNetworkError("postForm", url.toString(), e);
			return null;
		}
	}

	public File downloadUnkownContentType(URL url) {
		return download(url, null);
	}

	/**
	 * Return the first element matching the selector within the provided parent
	 * @param parent The parent
	 * @param selector The selector
	 * @return the first element matching the selector within the provided parent
	 * @throws CollectError No matching element found
	 */
	protected Element required(Element parent, String selector) throws CollectError {
		if (parent == null) {
			throw new CollectError("Required element parent is null");
		}
		Element element = parent.selectFirst(selector);
		if (element == null) {
			throw new CollectError("Required element not found: " + selector);
		}
		return element;
	}

	/**
	 * Downloads the file and create the corresponding FileDto
	 * @throws CollectError If any error occurs and file cannot be downloaded
	 */
	protected FileDto downloadAttachment(String url, FileDto.FileDtoType type, String label, String contentType) throws CollectError {
		File file = download(url, contentType);
		if (file == null) {
			throw new CollectError("Cannot download file '" + label + "'");
		}
		return new FileDto(type, label, contentType, file);
	}

	private <T> T handleJsonResponse(Class<T> clazz, InternetProvider.Response response)
			throws AccessDeny, TemporaryUnavailable, ConnectionFailure, CollectError {

		if (response == null) {
			throw new TemporaryUnavailable("Empty response");
		}

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

	public static String buildFormEncodedPayload(Map<String, String> data)
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

	public void setNewLocation(String url) {
		lastUrl = currentUrl;
		currentUrl = url;
	}

	public void waitForNextRequest() {
		if (mustStop()) {
			throw new MustStopException();
		}
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

	public void setProgress(int value) {
		progress = Math.max(0, Math.min(100, value));
		getLogger().info("Progress: " + progress + "%");
	}

	public String getCurrentUrl() {
		return currentUrl;
	}

	public String getLastUrl() {
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

	public Map<String, String> getHeaders() {
		return Collections.unmodifiableMap(headers);
	}

	public String getHeader(String name) {
		return headers.get(name);
	}

	public void addHeader(String name, String value) {
		if (name != null && value != null) {
			headers.put(name.trim(), value);
		}
	}

	public long getMinIntervalBetweenRequestInMs() {
		return minIntervalBetweenRequestInMs;
	}

	public void setMinIntervalBetweenRequestInMs(long value) {
		this.minIntervalBetweenRequestInMs = value;
	}

	public void logNetworkError(
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

	/**
	 * Read saved progress from {@link #settings}, or start without a date on the first run.
	 *
	 * @return the last collected date and references completed on that date
	 */
	protected CollectorProgress loadProgress() {
		String savedProgress = settings.get(SETTINGS_PROGRESS_KEY);
		return savedProgress == null ? new CollectorProgress() : gson.fromJson(savedProgress, CollectorProgress.class);
	}

	/**
	 * Save the date and reference of a fully collected transaction in {@link #settings}.
	 * Keep only references from that date. Tibu stores these settings after {@link #collect()} returns.
	 */
	protected void saveProgress(CollectorProgress progress, CollectorPendingTransaction transaction) {
		if (!transaction.date.equals(progress.lastTransactionDate)) {
			progress.lastTransactionDate = transaction.date;
			progress.transactionReferences.clear();
		}
		progress.transactionReferences.add(transaction.id);
		settings.put(SETTINGS_PROGRESS_KEY, gson.toJson(progress));
	}

}
