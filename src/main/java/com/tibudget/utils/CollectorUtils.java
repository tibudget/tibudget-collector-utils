package com.tibudget.utils;

import java.text.NumberFormat;
import java.text.ParseException;
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
     * Converts an HTML-encoded string into a UTF-8 plain text string.
     * Also trims unnecessary spaces at the beginning and end of the string.
     * Optionally, replaces multiple whitespace characters (spaces, tabs) with a single space.
     *
     * @param htmlText The input string containing potential HTML entities.
     * @param normalizeWhitespace If true, replaces multiple whitespace characters with a single space.
     * @return A UTF-8 decoded and trimmed version of the input string.
     */
    public static String htmlToText(String htmlText, boolean normalizeWhitespace) {
        if (htmlText == null || htmlText.isEmpty()) {
            return "";
        }

        // Decode HTML entities
        String decoded = htmlText;
        for (Map.Entry<String, String> entry : HTML_ENTITIES.entrySet()) {
            decoded = decoded.replace(entry.getKey(), entry.getValue());
        }

        decoded = decoded.trim();

        // Normalize whitespace if the option is enabled
        if (normalizeWhitespace) {
            decoded = decoded.replaceAll("\\s+", " ");
        }

        return decoded;
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
     * Parses a price from a given string, handling HTML entities, currency symbols, and locale-based formatting.
     *
     * @param priceText The input string containing a price.
     * @param locale The locale to determine number format (e.g., Locale.FRANCE, Locale.US).
     * @return A Double representing the parsed price, or null if parsing fails.
     */
    public static Double parsePrice(String priceText, Locale locale) {
        if (priceText == null || priceText.isEmpty()) {
            LOG.log(Level.FINE, "Cannot parse price from null or empty value");
            return null;
        }

        // Decode HTML entities
        String cleanText = htmlToText(priceText, true);

        // Remove all non-numeric characters except digits, decimal separators, and grouping separators
        cleanText = cleanText.replaceAll("[^0-9.,']", "");

        // Parse using NumberFormat for the given locale
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        try {
            return numberFormat.parse(cleanText).doubleValue();
        } catch (ParseException e) {
            LOG.log(Level.SEVERE, "Cannot parse price from '"+priceText+"' : " + e.getMessage());
            return null;
        }
    }
}
