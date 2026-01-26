package com.tibudget.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CollectorUtils {

    private static final Logger LOG = Logger.getLogger(CollectorUtils.class.getName());

    private static final Map<String, String> HTML_ENTITIES = new HashMap<>();

    static {
        // Common HTML entities
        HTML_ENTITIES.put("&lt;", "<");
        HTML_ENTITIES.put("&gt;", ">");
        HTML_ENTITIES.put("&amp;", "&");
        HTML_ENTITIES.put("&quot;", "\"");
        HTML_ENTITIES.put("&apos;", "'");

        // ISO-8859-1 entities
        HTML_ENTITIES.put("&nbsp;", " ");
        HTML_ENTITIES.put("&iexcl;", "¡");
        HTML_ENTITIES.put("&copy;", "©");
        HTML_ENTITIES.put("&reg;", "®");
        HTML_ENTITIES.put("&trade;", "™");
        HTML_ENTITIES.put("&times;", "×");
        HTML_ENTITIES.put("&divide;", "÷");

        // Currencies
        HTML_ENTITIES.put("&cent;", "¢");
        HTML_ENTITIES.put("&pound;", "£");
        HTML_ENTITIES.put("&yen;", "¥");
        HTML_ENTITIES.put("&euro;", "€");
        HTML_ENTITIES.put("&dollar;", "$");
        HTML_ENTITIES.put("&franc;", "₣");
        HTML_ENTITIES.put("&lira;", "₤");
        HTML_ENTITIES.put("&baht;", "฿");
        HTML_ENTITIES.put("&riel;", "៛");
        HTML_ENTITIES.put("&tugrik;", "₮");
        HTML_ENTITIES.put("&tenge;", "₸");
        HTML_ENTITIES.put("&won;", "₩");
        HTML_ENTITIES.put("&kip;", "₭");
        HTML_ENTITIES.put("&rupee;", "₹");
        HTML_ENTITIES.put("&peso;", "₱");
    }

    /**
     * Converts an HTML-encoded string into a UTF-8 plain text string.
     * Also trims unnecessary spaces at the beginning and end of the string.
     *
     * @param htmlText The input string containing potential HTML entities.
     * @return A UTF-8 decoded and trimmed version of the input string.
     */
    public static String htmlToText(String htmlText) {
        return htmlToText(htmlText, false);
    }

    /**
     * Converts an HTML string into plain UTF-8 text.
     * <p>
     * This method:
     * - removes all HTML tags
     * - decodes basic HTML entities
     * - optionally normalizes whitespace
     *
     * @param htmlText input HTML string
     * @param normalizeWhitespace if true, collapses multiple whitespace characters into one space
     * @return plain text representation
     */
    public static String htmlToText(String htmlText, boolean normalizeWhitespace) {
        if (htmlText == null || htmlText.isEmpty()) {
            return "";
        }

        // 1) Remove HTML tags
        String text = htmlText.replaceAll("(?s)<[^>]*>", " ");

        // 2) Decode HTML entities
        for (Map.Entry<String, String> entry : HTML_ENTITIES.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }

        // 3) Normalize spaces created by tag removal
        text = text
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .trim();

        if (normalizeWhitespace) {
            text = text.replaceAll("\\s+", " ");
        }

        return text;
    }


    /**
     * Truncates a given text to the specified maximum length.
     * The text is cut at the exact limit and ends with "...".
     * The resulting string will not exceed the given maxLength.
     *
     * @param text The input string to be truncated.
     * @param maxLength The maximum allowed length of the output string.
     * @return A truncated version of the input string, ending with "..." if needed.
     */
    public static String truncateString(String text, int maxLength) {
        return truncateString(text, maxLength, false);
    }

    /**
     * Truncates a given text to the specified maximum length.
     * If possible, truncation is performed at the last space within the limit if allowWordCut is true.
     * Otherwise, the text is cut at the exact limit and ends with "...".
     * The resulting string will not exceed the given maxLength.
     *
     * @param text The input string to be truncated.
     * @param maxLength The maximum allowed length of the output string.
     * @param allowWordCut If true, truncates at the last space within the limit; otherwise, cuts strictly at maxLength.
     * @return A truncated version of the input string, ending with "..." if needed.
     */
    public static String truncateString(String text, int maxLength, boolean allowWordCut) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;

        // Take the substring up to maxLength - 3 to reserve space for "..."
        String truncated = text.substring(0, Math.min(maxLength - 3, text.length()));

        if (allowWordCut) {
            int lastSpaceIndex = truncated.lastIndexOf(' ');
            if (lastSpaceIndex > 0) {
                truncated = truncated.substring(0, lastSpaceIndex);
            }
        }

        return truncated + "...";
    }

    /**
     * Copies a resource from the classpath to a temporary file.
     *
     * @param resourceName the name/path of the resource relative to the classpath
     * @return a File object pointing to the newly created temporary file
     * @throws IOException if the resource cannot be read or the file cannot be written
     */
    public static File copyResourceToTempFile(String resourceName) throws IOException {
        // Attempt to load the resource as an InputStream from the classpath
        try (InputStream inputStream = CollectorUtils.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Resource not found on classpath: " + resourceName);
            }

            // Extract the file extension (e.g., ".png", ".jpg") from the resource name
            String extension = "";
            int lastDotIndex = resourceName.lastIndexOf('.');
            if (lastDotIndex >= 0 && lastDotIndex < resourceName.length() - 1) {
                extension = resourceName.substring(lastDotIndex);
            }

            // Create a temporary file with a prefix and extracted extension
            File tempFile = File.createTempFile("tibu_", extension);
            tempFile.deleteOnExit(); // Ensure the file is removed when the JVM exits

            // Write the content of the resource to the temp file
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096]; // Larger buffer for better performance
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            return tempFile;
        }
    }

    /**
     * Converts a Date to a String using its epoch time (milliseconds since 1970-01-01 UTC).
     *
     * @param date the Date to serialize, may be null
     * @return the epoch time as String, or null if date is null
     */
    public static String dateToString(Date date) {
        return date == null ? null : String.valueOf(date.getTime());
    }

    /**
     * Converts a String containing an epoch time (milliseconds since 1970-01-01 UTC)
     * back to a Date.
     *
     * @param value the String to deserialize, may be null or empty
     * @return the corresponding Date, or null if value is null or empty
     * @throws NumberFormatException if the value is not a valid long
     */
    public static Date stringToDate(String value) {
        return (value == null || value.isEmpty())
                ? null
                : new Date(Long.parseLong(value));
    }

}
