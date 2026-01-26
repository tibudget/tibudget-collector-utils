package com.tibudget.utils;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tibudget.utils.CollectorUtils.htmlToText;

/**
 * Utility class used to parse monetary amounts from HTML or plain text.
 * <p>
 * Features:
 * - HTML entity decoding (&nbsp;, &euro;, etc.)
 * - Worldwide number formats (FR, EN, DE, CH, IN, etc.)
 * - Automatic ISO-4217 currency detection
 * - Robust decimal separator detection
 */
public final class AmountParser {

    private static final Logger LOG = Logger.getLogger(AmountParser.class.getName());

    private static final Map<String, List<String>> SYMBOL_TO_CURRENCIES = Map.of(
            "$", List.of("USD", "EUR", "CAD", "AUD", "NZD", "SGD", "HKD", "MXN"),
            "€", List.of("EUR"),
            "£", List.of("GBP"),
            "¥", List.of("JPY", "CNY"),
            "₹", List.of("INR"),
            "₩", List.of("KRW"),
            "₽", List.of("RUB"),
            "₺", List.of("TRY"),
            "₫", List.of("VND")
    );

    private static final List<String> GLOBAL_CURRENCY_PRIORITY = List.of(
            "USD",
            "EUR",
            "GBP",
            "JPY",
            "CNY",
            "CAD",
            "AUD",
            "CHF",
            "INR"
    );

    private static final Pattern ISO_CURRENCY_PATTERN =
            Pattern.compile("\\b[A-Z]{3}\\b");

    private static final Set<Currency> ALL_CURRENCIES =
            Currency.getAvailableCurrencies();

    private AmountParser() {
        // Utility class
    }

    /**
     * Parses a monetary amount from HTML or text.
     *
     * @param html   HTML or plain text containing a price
     * @param locale locale hint used as fallback for currency detection
     * @return parsed AmountDto or null if parsing fails
     */
    public static AmountDto parse(String html, Locale locale) {
        if (html == null || html.isBlank()) {
            return null;
        }

        // 1) HTML to text (entities decoded)
        String text = htmlToText(html, true);

        // 2) Normalize spaces (nbsp, thin space, etc.)
        text = text
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace('’', '\'')
                .trim();

        // 3) Detect currency
        Currency currency = detectCurrency(text, locale);

        // 4) Keep only numeric characters and separators
        String numeric = text.replaceAll("[^0-9,.'+-]", "");
        if (numeric.isBlank()) {
            return null;
        }

        // 5) Detect decimal separator (last dot or comma wins)
        int lastComma = numeric.lastIndexOf(',');
        int lastDot = numeric.lastIndexOf('.');
        int decimalIndex = Math.max(lastComma, lastDot);

        String normalized;
        if (decimalIndex >= 0) {
            String intPart = numeric.substring(0, decimalIndex)
                    .replaceAll("[^0-9+-]", "");
            String fracPart = numeric.substring(decimalIndex + 1)
                    .replaceAll("[^0-9]", "");
            normalized = intPart + "." + fracPart;
        } else {
            normalized = numeric.replaceAll("[^0-9+-]", "");
        }

        try {
            return new AmountDto(Double.valueOf(normalized), currency);
        } catch (NumberFormatException e) {
            LOG.log(Level.FINE, "Cannot parse amount from '" + html + "'");
            return null;
        }
    }

    private static Currency detectCurrency(String text, Locale locale) {

        if (text == null || text.isBlank()) {
            return null;
        }

        // 1) Explicit ISO-4217 code (EUR, USD, CHF...)
        Matcher m = ISO_CURRENCY_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Currency.getInstance(m.group());
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 2) Symbol-based detection
        for (Map.Entry<String, List<String>> entry : SYMBOL_TO_CURRENCIES.entrySet()) {
            String symbol = entry.getKey();
            if (!text.contains(symbol)) {
                continue;
            }

            List<String> candidates = entry.getValue();

            // 2a) Locale match wins
            if (locale != null) {
                try {
                    Currency localeCurrency = Currency.getInstance(locale);
                    if (candidates.contains(localeCurrency.getCurrencyCode())) {
                        return localeCurrency;
                    }
                } catch (Exception ignored) {
                }
            }

            // 2b) Fallback to most used currencies
            for (String preferred : GLOBAL_CURRENCY_PRIORITY) {
                if (candidates.contains(preferred)) {
                    return Currency.getInstance(preferred);
                }
            }

            // 2c) Stable fallback
            return Currency.getInstance(candidates.get(0));
        }

        // 3) No symbol, no ISO → locale currency if available
        if (locale != null) {
            try {
                return Currency.getInstance(locale);
            } catch (Exception ignored) {
            }
        }

        return null;
    }


}
