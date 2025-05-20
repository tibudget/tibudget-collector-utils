package com.tibudget.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class for building and encoding query parameters for HTTP URLs.
 * Supports fluent chaining and ensures proper UTF-8 encoding of parameter names and values.
 */
public class QueryParameters {

    private final Map<String, String> parameters = new LinkedHashMap<>();

    /**
     * Default constructor for creating an empty parameter set.
     */
    public QueryParameters() {
    }

    /**
     * Constructs a QueryParameters instance from an existing map.
     *
     * @param initialParams Initial parameters to include.
     */
    public QueryParameters(Map<String, String> initialParams) {
        if (initialParams != null) {
            parameters.putAll(initialParams);
        }
    }

    /**
     * Adds or replaces a parameter.
     *
     * @param name  The parameter name.
     * @param value The parameter value.
     * @return The current QueryParameters instance (for chaining).
     */
    public QueryParameters with(String name, String value) {
        parameters.put(name, value);
        return this;
    }

    public QueryParameters with(String name, int value) {
        return with(name, String.valueOf(value));
    }

    public QueryParameters with(String name, long value) {
        return with(name, String.valueOf(value));
    }

    public QueryParameters with(String name, double value) {
        return with(name, String.valueOf(value));
    }

    public QueryParameters with(String name, boolean value) {
        return with(name, String.valueOf(value));
    }

    public QueryParameters with(String name, Enum<?> value) {
        return with(name, value != null ? value.name() : null);
    }

    public QueryParameters with(String name, Object value) {
        return with(name, value != null ? value.toString() : null);
    }

    /**
     * Removes a parameter by name.
     *
     * @param name The name of the parameter to remove.
     * @return The current QueryParameters instance (for chaining).
     */
    public QueryParameters remove(String name) {
        parameters.remove(name);
        return this;
    }

    /**
     * Clears all parameters.
     *
     * @return The current QueryParameters instance (for chaining).
     */
    public QueryParameters clear() {
        parameters.clear();
        return this;
    }

    /**
     * Checks if the given parameter is present.
     *
     * @param name The name of the parameter.
     * @return True if the parameter exists, false otherwise.
     */
    public boolean has(String name) {
        return parameters.containsKey(name);
    }

    /**
     * Returns true if no parameters have been added.
     *
     * @return True if empty, false otherwise.
     */
    public boolean isEmpty() {
        return parameters.isEmpty();
    }

    /**
     * Returns the number of parameters.
     *
     * @return The parameter count.
     */
    public int size() {
        return parameters.size();
    }

    /**
     * Returns an unmodifiable view of the parameter map.
     *
     * @return The parameters as a read-only Map.
     */
    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(parameters);
    }

    /**
     * Builds the URL-encoded query string, e.g., "key1=value1&key2=value2".
     *
     * @return The encoded query string, or empty string if none.
     */
    public String toQueryString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    /**
     * Appends the encoded query string to the given base URL.
     *
     * @param baseUrl The base URL, with or without existing parameters.
     * @return The complete URL with query parameters appended.
     */
    public String appendToUrl(String baseUrl) {
        Objects.requireNonNull(baseUrl, "Base URL must not be null");
        String query = toQueryString();
        if (query.isEmpty()) return baseUrl;
        return baseUrl.contains("?") ? baseUrl + "&" + query : baseUrl + "?" + query;
    }

    /**
     * Encodes a string for use in a URL query string (UTF-8).
     *
     * @param value The string to encode.
     * @return The encoded string.
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
