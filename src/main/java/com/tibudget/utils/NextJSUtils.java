package com.tibudget.utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts JSON arguments from Next.js inline Flight pushes without executing JavaScript.
 * Supports direct self.__next_f.push calls and JSON payloads (not arbitrary JS expressions).
 * Preserves channel and chunk order. Malformed pushes or matching arrays fail explicitly;
 * an absent key returns an empty list. Flight references and binary frames are not resolved.
 */
public final class NextJSUtils {
    private static final Pattern PUSH = Pattern.compile("self\\s*\\.\\s*__next_f\\s*\\.\\s*push\\s*\\(");

    private NextJSUtils() { }

    /**
     * Returns decoded chunks by channel. Bootstrap pushes such as [0] have no payload.
     */
    public static Map<Integer, List<String>> extractNextFlightPushes(Document document) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        if (document == null) return result;
        for (Element script : document.select("script")) {
            String source = script.data();
            Matcher call = PUSH.matcher(source);
            if (!call.find()) continue;
            for (int cursor = 0; cursor < source.length();) {
                char c = source.charAt(cursor);
                if (c == '"' || c == '\'' || c == '`') {
                    cursor = stringEnd(source, cursor) + 1;
                } else if (source.startsWith("//", cursor)) {
                    int newline = source.indexOf('\n', cursor + 2);
                    cursor = newline < 0 ? source.length() : newline + 1;
                } else if (source.startsWith("/*", cursor)) {
                    int end = source.indexOf("*/", cursor + 2);
                    cursor = end < 0 ? source.length() : end + 2;
                } else if ((cursor == 0 || !Character.isJavaIdentifierPart(source.charAt(cursor - 1)))
                        && call.region(cursor, source.length()).lookingAt()) {
                    int start = skipWhitespace(source, call.end());
                    if (start == source.length() || source.charAt(start) != '[') {
                        throw new IllegalArgumentException("Next.js Flight push must have a JSON array argument");
                    }
                    int end = arrayEnd(source, start);
                    int close = skipWhitespace(source, end + 1);
                    if (close == source.length() || source.charAt(close) != ')') {
                        throw new IllegalArgumentException("Invalid Next.js Flight push ending");
                    }
                    JsonArray args = parseJson(source.substring(start, end + 1)).getAsJsonArray();
                    if (args.size() == 0 || !args.get(0).isJsonPrimitive()
                            || !args.get(0).getAsJsonPrimitive().isNumber()) {
                        throw new IllegalArgumentException("Next.js Flight channel must be an integer");
                    }
                    int channel;
                    try {
                        channel = args.get(0).getAsBigDecimal().toBigIntegerExact().intValueExact();
                    } catch (ArithmeticException e) {
                        throw new IllegalArgumentException("Next.js Flight channel must be an integer", e);
                    }
                    if (args.size() > 1) {
                        JsonElement payload = args.get(1);
                        String chunk = payload.isJsonPrimitive() && payload.getAsJsonPrimitive().isString()
                                ? payload.getAsString() : payload.toString();
                        result.computeIfAbsent(channel, ignored -> new ArrayList<>()).add(chunk);
                    }
                    cursor = close + 1;
                } else cursor++;
            }
        }
        return result;
    }

    /**
     * Finds the first inline JSON array for an exact property name, allowing whitespace,
     * nested arrays and delimiters in strings. Joins chunks within each channel so keys,
     * escape sequences and arrays may span pushes. Uses the caller's Gson DTO adapters.
     */
    public static <T> List<T> extractListByKey(Gson gson, Map<Integer, List<String>> pushes,
                                             String key, Class<T> clazz) {
        Objects.requireNonNull(gson, "gson");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(clazz, "clazz");
        if (pushes == null || pushes.isEmpty()) return Collections.emptyList();
        Type listType = TypeToken.getParameterized(List.class, clazz).getType();
        for (List<String> chunks : pushes.values()) {
            String source = String.join("", chunks);
            for (int cursor = 0; cursor < source.length();) {
                if (source.charAt(cursor) != '"') {
                    cursor++;
                    continue;
                }
                int end = stringEnd(source, cursor);
                int colon = skipWhitespace(source, end + 1);
                if (colon < source.length() && source.charAt(colon) == ':'
                        && key.equals(parseJson(source.substring(cursor, end + 1)).getAsString())) {
                    int start = skipWhitespace(source, colon + 1);
                    if (start < source.length() && source.charAt(start) == '[') {
                        int arrayEnd = arrayEnd(source, start);
                        String json = source.substring(start, arrayEnd + 1);
                        try {
                            JsonElement array = parseJson(json);
                            return gson.fromJson(array, listType);
                        } catch (JsonParseException e) {
                            // Do not include payloads: they can contain personal information.
                            throw new IllegalArgumentException("Invalid Next.js array for key '" + key + "'");
                        }
                    }
                }
                cursor = end + 1;
            }
        }
        return Collections.emptyList();
    }

    private static int skipWhitespace(String source, int cursor) {
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
        return cursor;
    }

    /**
     * Returns the closing quote, respecting escaped quotes and escaped backslashes.
     */
    private static int stringEnd(String source, int start) {
        char quote = source.charAt(start);
        for (int i = start + 1; i < source.length(); i++) {
            if (source.charAt(i) == '\\') i++;
            else if (source.charAt(i) == quote) return i;
        }
        throw new IllegalArgumentException("Unterminated string in Next.js data");
    }

    /**
     * Finds the matching bracket without counting brackets inside JSON strings.
     */
    private static int arrayEnd(String source, int start) {
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"') i = stringEnd(source, i);
            else if (c == '[') depth++;
            else if (c == ']' && --depth == 0) return i;
        }
        throw new IllegalArgumentException("Unterminated array in Next.js data");
    }

    private static JsonElement parseJson(String source) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Trailing content in Next.js JSON");
            }
            return value;
        } catch (IOException | JsonParseException e) {
            throw new IllegalArgumentException("Invalid JSON in Next.js data");
        }
    }
}
