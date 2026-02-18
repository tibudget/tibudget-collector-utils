package com.tibudget.utils.gson;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonSerializationContext;

import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gson adapter for Java 8+ ZonedDateTime.
 *
 * <p>
 * This adapter enables Gson to properly serialize and deserialize
 * {@link ZonedDateTime} using the ISO-8601 standard format.
 * </p>
 *
 * <p>
 * Supported example formats:
 * <pre>
 *     2025-12-25T00:00:00+01:00
 *     2025-12-25T00:00:00+01:00[Europe/Paris]
 * </pre>
 * </p>
 *
 * <p>
 * Behavior:
 * <ul>
 *     <li>Deserialization: Parses ISO-8601 strings into {@link ZonedDateTime}</li>
 *     <li>Serialization: Converts {@link ZonedDateTime} to ISO-8601 string</li>
 *     <li>Null-safe: Returns null when JSON value is null</li>
 *     <li>Graceful failure: Logs parsing errors and returns null</li>
 * </ul>
 * </p>
 *
 * <p>
 * Usage:
 * <pre>
 * Gson gson = new GsonBuilder()
 *     .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
 *     .create();
 * </pre>
 * </p>
 *
 * <p>
 * This implementation uses {@link DateTimeFormatter#ISO_ZONED_DATE_TIME}
 * to ensure full timezone support worldwide.
 * </p>
 */
public class ZonedDateTimeAdapter
        implements JsonDeserializer<ZonedDateTime>,
        JsonSerializer<ZonedDateTime> {

    private static final Logger LOG =
            Logger.getLogger(ZonedDateTimeAdapter.class.getName());

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_ZONED_DATE_TIME;

    /**
     * Deserializes an ISO-8601 string into a ZonedDateTime instance.
     *
     * @param json the JSON element
     * @param typeOfT the target type
     * @param context deserialization context
     * @return parsed ZonedDateTime or null if input is null or invalid
     * @throws JsonParseException if a critical parsing error occurs
     */
    @Override
    public ZonedDateTime deserialize(JsonElement json,
                                     Type typeOfT,
                                     JsonDeserializationContext context)
            throws JsonParseException {

        if (json == null || json.isJsonNull()) {
            return null;
        }

        try {
            return ZonedDateTime.parse(json.getAsString(), FORMATTER);
        } catch (Exception e) {
            LOG.log(Level.FINE,
                    "Failed to parse ZonedDateTime: " + json.getAsString(),
                    e);
            return null;
        }
    }

    /**
     * Serializes a ZonedDateTime instance into an ISO-8601 string.
     *
     * @param src the ZonedDateTime instance
     * @param typeOfSrc the source type
     * @param context serialization context
     * @return JSON string representation or JsonNull if src is null
     */
    @Override
    public JsonElement serialize(ZonedDateTime src,
                                 Type typeOfSrc,
                                 JsonSerializationContext context) {

        if (src == null) {
            return JsonNull.INSTANCE;
        }

        return new JsonPrimitive(src.format(FORMATTER));
    }
}
