package com.tibudget.utils;

import com.google.gson.Gson;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent builder for application/x-www-form-urlencoded or JSON POST data.
 * Allows flexible format definition and string encoding at build time.
 */
public class PostData {

    public enum Format {
        URLENCODED,
        JSON
    }

    private final Map<String, String> data = new LinkedHashMap<>();
    private Format format = Format.URLENCODED; // default format

    public PostData() {
    }

    public PostData(Map<String, String> initialData) {
        if (initialData != null) {
            data.putAll(initialData);
        }
    }

    public PostData with(String key, String value) {
        if (key != null) {
            if (value != null) {
                data.put(key, value);
            } else {
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

    public PostData asUrlEncodedFormat() {
        this.format = Format.URLENCODED;
        return this;
    }

    public PostData asJSON() {
        this.format = Format.JSON;
        return this;
    }

    public String getContentType() {
        if (format == Format.JSON) {
            return "application/json";
        }
        else if (format == Format.URLENCODED) {
            return "application/x-www-form-urlencoded";
        }
        return "text/plain";
    }

    public String build() {
        switch (format) {
            case JSON :
                return encodeAsJSON();
            case URLENCODED :
            default:
                return encodeAsURLEncoded();
        }
    }

    private String encodeAsURLEncoded() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getValue() == null) continue;
            if (sb.length() > 0) sb.append("&");
            sb.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    private String encodeAsJSON() {
        Gson gson = new Gson();
        return gson.toJson(data);
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "PostData{" +
                "data=" + data +
                ", format=" + format +
                "'} body=" + build();
    }
}
