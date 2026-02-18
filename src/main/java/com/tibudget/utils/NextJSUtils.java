package com.tibudget.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to extract Next.js Flight data from HTML pages.
 *
 * This utility parses all occurrences of:
 *
 *      self.__next_f.push([id, payload]);
 *
 * It supports:
 *  - Multi-line scripts
 *  - Escaped JavaScript strings
 *  - Non-string payloads
 *  - Multiple push calls inside the same script
 *
 * The result is returned as a Map:
 *
 *      key   = push ID (Integer)
 *      value = list of decoded payload strings
 *
 * This class is reusable for any Next.js (App Router / React Flight) page.
 */
public final class NextJSUtils {

    private NextJSUtils() {
        // Utility class
    }

    /**
     * Extracts all self.__next_f.push calls from a Jsoup Document.
     *
     * @param document Jsoup parsed document
     * @return Map of push ID -> list of decoded payload chunks
     */
    public static Map<Integer, List<String>> extractNextFlightPushes(Document document) {

        Map<Integer, List<String>> result = new LinkedHashMap<>();

        if (document == null) {
            return result;
        }

        Elements scripts = document.select("script");

        if (scripts.isEmpty()) {
            return result;
        }

        // Regex to match: self.__next_f.push([id, payload]);
        // Supports:
        //   - multi-line content
        //   - string payload
        //   - object/array payload
        Pattern pushPattern = Pattern.compile(
                "self\\.__next_f\\.push\\(\\[(\\d+)\\s*,\\s*(.*?)\\]\\)",
                Pattern.DOTALL
        );

        for (Element script : scripts) {

            String scriptContent = script.data();

            if (scriptContent == null || scriptContent.isEmpty()) {
                continue;
            }

            if (!scriptContent.contains("__next_f.push")) {
                continue;
            }

            Matcher matcher = pushPattern.matcher(scriptContent);

            while (matcher.find()) {

                try {

                    Integer pushId = Integer.parseInt(matcher.group(1));
                    String rawPayload = matcher.group(2);

                    String decodedPayload = decodePayload(rawPayload);

                    result
                            .computeIfAbsent(pushId, k -> new ArrayList<>())
                            .add(decodedPayload);

                } catch (Exception ignored) {
                    // Skip malformed push entries safely
                }
            }
        }

        return result;
    }

    /**
     * Decodes a payload extracted from a push call.
     *
     * Handles:
     *  - Quoted JavaScript strings
     *  - Escaped characters
     *  - Non-string payloads
     *
     * @param rawPayload Raw payload captured by regex
     * @return Decoded payload
     */
    private static String decodePayload(String rawPayload) {

        if (rawPayload == null) {
            return "";
        }

        rawPayload = rawPayload.trim();

        // If payload is quoted string
        if (rawPayload.startsWith("\"") && rawPayload.endsWith("\"")) {

            String unquoted = rawPayload.substring(1, rawPayload.length() - 1);

            return unescapeJavaScript(unquoted);
        }

        // Otherwise return as-is (object, array, number, etc.)
        return rawPayload;
    }

    /**
     * Basic JavaScript string unescape implementation.
     *
     * Handles:
     *  - \"
     *  - \\
     *  - \n
     *  - \t
     *  - \r
     *
     * This avoids external dependencies.
     *
     * @param input Escaped JS string
     * @return Clean string
     */
    private static String unescapeJavaScript(String input) {

        StringBuilder result = new StringBuilder();
        boolean escaping = false;

        for (int i = 0; i < input.length(); i++) {

            char current = input.charAt(i);

            if (escaping) {

                switch (current) {
                    case '"':
                        result.append('"');
                        break;
                    case '\\':
                        result.append('\\');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 'u':
                        // Unicode escape handling
                        if (i + 4 < input.length()) {
                            String hex = input.substring(i + 1, i + 5);
                            try {
                                result.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                result.append("\\u").append(hex);
                                i += 4;
                            }
                        }
                        break;
                    default:
                        result.append(current);
                }

                escaping = false;

            } else if (current == '\\') {
                escaping = true;
            } else {
                result.append(current);
            }
        }

        return result.toString();
    }

    /**
     * Extracts and parses a JSON array by key from Next.js Flight chunks.
     *
     * @param pushes Map returned by extractNextFlightPushes
     * @param key    JSON key to extract (ex: "lines", "invoices")
     * @param clazz  DTO class
     * @return List of parsed DTO objects
     */
    public static <T> List<T> extractListByKey(Gson gson, Map<Integer, List<String>> pushes, String key, Class<T> clazz) {

        if (pushes == null || pushes.isEmpty()) {
            return Collections.emptyList();
        }

        for (List<String> chunks : pushes.values()) {

            for (String chunk : chunks) {

                if (!chunk.contains("\"" + key + "\"")) {
                    continue;
                }

                String jsonArray = extractJsonArrayByKey(chunk, key);

                if (jsonArray == null) {
                    continue;
                }

                try {

                    Type listType = TypeToken.getParameterized(List.class, clazz).getType();

                    return gson.fromJson(jsonArray, listType);

                } catch (Exception ignored) {
                }
            }
        }

        return Collections.emptyList();
    }

    private static String extractJsonArrayByKey(String chunk, String key) {

        String search = "\"" + key + "\":[";

        int start = chunk.indexOf(search);

        if (start == -1) {
            return null;
        }

        int arrayStart = chunk.indexOf("[", start);

        int bracketCount = 0;
        int end = -1;

        for (int i = arrayStart; i < chunk.length(); i++) {

            char c = chunk.charAt(i);

            if (c == '[') bracketCount++;
            if (c == ']') bracketCount--;

            if (bracketCount == 0) {
                end = i;
                break;
            }
        }

        if (end == -1) {
            return null;
        }

        return chunk.substring(arrayStart, end + 1);
    }

}
