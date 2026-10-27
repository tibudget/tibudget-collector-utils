package com.tibudget.utils;

import com.tibudget.api.exceptions.CollectError;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

class CollectorUtilsTest {

    @Test
    void elementToAmount_shouldParseAmountsAndUseLocaleForCurrency() throws CollectError {
        Element element = Jsoup.parse("<span>-1&nbsp;234,56 &euro;</span>").selectFirst("span");
        AmountDto amount = CollectorUtils.elementToAmount(element, Locale.FRANCE);
        assertEquals(-1234.56, amount.getAmount());
        assertEquals("EUR", amount.getCurrencyIsoCode());
        assertEquals(-1234.56, CollectorUtils.elementToAmount(element, Locale.FRANCE, null).getAmount());

        Element dollars = new Element("span").text("$42.50");
        assertEquals("CAD", CollectorUtils.elementToAmount(dollars, Locale.CANADA).getCurrencyIsoCode());
        AmountDto unknownCurrency = CollectorUtils.elementToAmount(new Element("span").text("42"), null);
        assertEquals(42.0, unknownCurrency.getAmount());
        assertNull(unknownCurrency.getCurrency());
    }

    @Test
    void elementToAmount_shouldHandleMissingAndInvalidValues() {
        AmountDto fallback = new AmountDto(0.0, Currency.getInstance("EUR"));
        assertThrows(CollectError.class, () -> CollectorUtils.elementToAmount(null, Locale.FRANCE));
        assertSame(fallback, CollectorUtils.elementToAmount(null, Locale.FRANCE, fallback));
        assertNull(CollectorUtils.elementToAmount(null, Locale.FRANCE, null));
        for (String text : new String[]{"", " ", "invalid", "--12,50 €"}) {
            Element element = new Element("span").text(text);
            assertThrows(CollectError.class, () -> CollectorUtils.elementToAmount(element, Locale.FRANCE));
            assertSame(fallback, CollectorUtils.elementToAmount(element, Locale.FRANCE, fallback));
            assertNull(CollectorUtils.elementToAmount(element, Locale.FRANCE, null));
        }
    }

    @Test
    void elementSelectionToAmount_shouldUseFirstMatchAndHandleMissingOrInvalidValues() throws CollectError {
        Element parent = Jsoup.parse("<span>12,50 €</span><span>99 €</span><p>invalid</p>");
        AmountDto fallback = new AmountDto(0.0, null);
        AmountDto amount = CollectorUtils.elementSelectionToAmount(parent, "span", Locale.FRANCE);
        assertEquals(12.50, amount.getAmount());
        assertEquals("EUR", amount.getCurrencyIsoCode());
        assertEquals(12.50, CollectorUtils.elementSelectionToAmount(parent, "span", Locale.FRANCE, fallback).getAmount());
        assertThrows(CollectError.class, () -> CollectorUtils.elementSelectionToAmount(null, "span", Locale.FRANCE));
        assertThrows(CollectError.class, () -> CollectorUtils.elementSelectionToAmount(parent, "missing", Locale.FRANCE));
        assertThrows(CollectError.class, () -> CollectorUtils.elementSelectionToAmount(parent, "p", Locale.FRANCE));
        assertSame(fallback, CollectorUtils.elementSelectionToAmount(null, "span", Locale.FRANCE, fallback));
        assertSame(fallback, CollectorUtils.elementSelectionToAmount(parent, "missing", Locale.FRANCE, fallback));
        assertSame(fallback, CollectorUtils.elementSelectionToAmount(parent, "p", Locale.FRANCE, fallback));
        assertNull(CollectorUtils.elementSelectionToAmount(parent, "missing", Locale.FRANCE, null));
    }

    @Test
    void elementToInteger_shouldReadSignedIntegersAndBounds() throws CollectError {
        for (String value : new String[]{"0", "+42", "-42", "2147483647", "-2147483648"}) {
            Element element = Jsoup.parse("<span> &nbsp; " + value + " </span>").selectFirst("span");
            assertEquals(Integer.valueOf(value), CollectorUtils.elementToInteger(element));
            assertEquals(Integer.valueOf(value), CollectorUtils.elementToInteger(element, 7));
        }
    }

    @Test
    void elementToInteger_shouldReadFirstIntegerFromMixedText() throws CollectError {
        Element parent = Jsoup.parse("<span>Quantité : 2 · 18,00 € / article</span>");
        Element element = parent.selectFirst("span");
        assertEquals(2, CollectorUtils.elementToInteger(element));
        assertEquals(2, CollectorUtils.elementToInteger(element, 7));
        assertEquals(2, CollectorUtils.elementSelectionToInteger(parent, "span"));
        assertEquals(2, CollectorUtils.elementSelectionToInteger(parent, "span", 7));
        assertEquals(-12, CollectorUtils.elementToInteger(new Element("span").text("Variation : -12 articles")));
        assertEquals(3, CollectorUtils.elementToInteger(new Element("span").text("Quantité : +3 articles")));
        assertEquals(1, CollectorUtils.elementToInteger(new Element("span").text("1.5")));
        assertEquals(1, CollectorUtils.elementToInteger(new Element("span").text("1 000")));
    }

    @Test
    void elementToInteger_shouldHandleMissingAndInvalidValues() {
        assertThrows(CollectError.class, () -> CollectorUtils.elementToInteger(null));
        assertEquals(7, CollectorUtils.elementToInteger(null, 7));
        assertNull(CollectorUtils.elementToInteger(null, null));
        for (String value : new String[]{"", "abc", "2147483648", "-2147483649", "Quantité : 2147483648 · 2 articles"}) {
            Element element = new Element("span").text(value);
            assertThrows(CollectError.class, () -> CollectorUtils.elementToInteger(element));
            assertEquals(7, CollectorUtils.elementToInteger(element, 7));
            assertNull(CollectorUtils.elementToInteger(element, null));
        }
    }

    @Test
    void elementSelectionToInteger_shouldUseFirstMatchAndHandleMissingOrInvalidValues() throws CollectError {
        Element parent = Jsoup.parse("<span>42</span><span>99</span><p>invalid</p>");
        assertEquals(42, CollectorUtils.elementSelectionToInteger(parent, "span"));
        assertEquals(42, CollectorUtils.elementSelectionToInteger(parent, "span", 7));
        assertThrows(CollectError.class, () -> CollectorUtils.elementSelectionToInteger(null, "span"));
        assertThrows(CollectError.class, () -> CollectorUtils.elementSelectionToInteger(parent, "missing"));
        assertThrows(CollectError.class, () -> CollectorUtils.elementSelectionToInteger(parent, "p"));
        assertEquals(7, CollectorUtils.elementSelectionToInteger(null, "span", 7));
        assertEquals(7, CollectorUtils.elementSelectionToInteger(parent, "missing", 7));
        assertEquals(7, CollectorUtils.elementSelectionToInteger(parent, "p", 7));
        assertNull(CollectorUtils.elementSelectionToInteger(parent, "missing", null));
    }

    @Test
    void htmlToText_shouldDecodeHtmlEntities() {
        String input = "&lt;div&gt;Hello &amp; welcome!&lt;/div&gt;";
        String expected = "<div>Hello & welcome!</div>";
        assertEquals(expected, CollectorUtils.htmlToText(input));
    }

    @Test
    void htmlToText_shouldNormalizeWhitespace() {
        String input = "   Hello   &nbsp;   World   ";
        String expected = "Hello World";
        assertEquals(expected, CollectorUtils.htmlToText(input, true));
    }

    @Test
    void truncateString_shouldTruncateCorrectly() {
        String input = "This is a long string that should be truncated.";
        String expected = "This is a long string...";
        assertEquals(expected, CollectorUtils.truncateString(input, 25, true));
    }

    @Test
    void truncateString_shouldNotTruncateShortString() {
        String input = "Short text";
        assertEquals(input, CollectorUtils.truncateString(input, 50));
    }

    @Test
    void htmlToText_shouldReturnEmptyStringOnNullOrEmptyInput() {
        assertEquals("", CollectorUtils.htmlToText(null));
        assertEquals("", CollectorUtils.htmlToText(""));
    }

    @Test
    void truncateString_shouldHandleNullInput() {
        assertEquals("", CollectorUtils.truncateString(null, 10));
    }

    @Test
    @DisplayName("Null input should return null")
    void maskName_null_returnsNull() {
        assertNull(CollectorUtils.maskName(null));
    }

    @Test
    @DisplayName("Empty input should return empty string")
    void maskName_empty_returnsEmpty() {
        assertEquals("", CollectorUtils.maskName(""));
    }

    @Test
    @DisplayName("Blank input should return original value")
    void maskName_blank_returnsOriginal() {
        assertEquals("   ", CollectorUtils.maskName("   "));
    }

    @Test
    @DisplayName("Single name should be masked to initial")
    void maskName_simpleName() {
        assertEquals("H.", CollectorUtils.maskName("Herry"));
    }

    @Test
    @DisplayName("Lowercase name should be uppercased")
    void maskName_lowercase() {
        assertEquals("h.".toUpperCase(), CollectorUtils.maskName("herry"));
    }

    @Test
    @DisplayName("Compound name with spaces should mask each part")
    void maskName_compoundWithSpaces() {
        assertEquals("D. L. C.", CollectorUtils.maskName("De La Cruz"));
    }

    @Test
    @DisplayName("Hyphenated name should preserve hyphen")
    void maskName_hyphenated() {
        assertEquals("J.-P.", CollectorUtils.maskName("Jean-Pierre"));
    }

    @Test
    @DisplayName("Name with apostrophe should preserve apostrophe")
    void maskName_apostrophe() {
        assertEquals("O'C.", CollectorUtils.maskName("O'Connor"));
    }

    @Test
    @DisplayName("Multiple separators should be handled correctly")
    void maskName_multipleSeparators() {
        assertEquals("J.-P. D.", CollectorUtils.maskName("Jean-Pierre Dupont"));
    }

    @Test
    @DisplayName("Extra spaces should be trimmed and normalized")
    void maskName_extraSpaces() {
        assertEquals("D. L. C.", CollectorUtils.maskName("  De   La   Cruz  "));
    }

    @Test
    @DisplayName("International characters should be handled correctly")
    void maskName_internationalCharacters() {
        assertEquals("Ł.", CollectorUtils.maskName("Łukasz"));
        assertEquals("É.", CollectorUtils.maskName("Émile"));
        assertEquals("Ç.", CollectorUtils.maskName("Çetin"));
    }

    @Test
    @DisplayName("Already masked name should remain stable")
    void maskName_alreadyMasked() {
        assertEquals("D. L. C.", CollectorUtils.maskName("D. L. C."));
    }

    @Test
    @DisplayName("Single letter name should still be masked correctly")
    void maskName_singleLetter() {
        assertEquals("A.", CollectorUtils.maskName("A"));
    }

    @Test
    @DisplayName("Mixed case name should be normalized")
    void maskName_mixedCase() {
        assertEquals("M. D.", CollectorUtils.maskName("mArIe DuPont"));
    }
}
