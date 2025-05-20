package com.tibudget.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent builder for application/x-www-form-urlencoded POST data.
 * Handles UTF-8 encoding and multiple types (String, int, boolean, etc.).
 */
public class PostData {

    private final Map<String, String> data = new LinkedHashMap<>();

    public PostData() {
    }

    public PostData(Map<String, String> initialData) {
        if (initialData != null) {
            data.putAll(initialData);
        }
    }

    /**
     * Ignore null key
     * If value is null then the key is removed.
     * @param key
     * @param value
     * @return this
     */
    public PostData with(String key, String value) {
        if (key != null) {
            if (value != null) {
                data.put(key, value);
            }
            else {
                data.remove(key);
            }
        }
        return this;
    }

    public PostData with(String key, int value) {
        return with(key, String.valueOf(value));
    }

    public PostData with(String key, long value) {
        return with(key, String.valueOf(value));
    }

    public PostData with(String key, double value) {
        return with(key, String.valueOf(value));
    }

    public PostData with(String key, boolean value) {
        return with(key, String.valueOf(value));
    }

    public PostData with(String key, Enum<?> value) {
        return with(key, value != null ? value.name() : null);
    }

    public PostData with(String key, Object value) {
        return with(key, value != null ? value.toString() : null);
    }

    public PostData remove(String key) {
        data.remove(key);
        return this;
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public int size() {
        return data.size();
    }

    public Map<String, String> asMap() {
        return Map.copyOf(data);
    }

    /**
     * Returns the POST data as a UTF-8 encoded form string: key1=value1&key2=value2
     */
    public String toFormEncoded() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getValue() == null) continue; // ignore null values
            if (sb.length() > 0) sb.append("&");
            sb.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
