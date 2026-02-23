package com.tibudget.utils.gson;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gson adapter for java.time.Instant.
 * <p>
 * Expected format:
 * 2026-02-19T03:12:00.731Z (ISO-8601 UTC)
 * <p>
 * In case of parsing error, the adapter logs the issue
 * and returns null instead of throwing an exception.
 */
public class InstantAdapter implements JsonDeserializer<Instant>, JsonSerializer<Instant> {

    private static final Logger LOGGER = Logger.getLogger(InstantAdapter.class.getName());

    @Override
    public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

        if (json == null || json.isJsonNull()) {
            return null;
        }

        try {
            return Instant.parse(json.getAsString());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Failed to parse Instant value: " + json.getAsString(), e);
            return null;
        }
    }

    @Override
    public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
        if (src == null) {
            return JsonNull.INSTANCE;
        }
        return new JsonPrimitive(src.toString());
    }
}
