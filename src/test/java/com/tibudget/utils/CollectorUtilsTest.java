package com.tibudget.utils;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CollectorUtilsTest {

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
    void parsePrice_shouldParseFrenchPrice() {
        String input = "1&nbsp;234,56&nbsp;€";
        Double expected = 1234.56;
        assertEquals(expected, CollectorUtils.parsePrice(input, Locale.FRANCE));
    }

    @Test
    void parsePrice_shouldParseUSPrice() {
        String input = "$1,234.56";
        Double expected = 1234.56;
        assertEquals(expected, CollectorUtils.parsePrice(input, Locale.US));
    }

    @Test
    void parsePrice_shouldReturnNullForInvalidInput() {
        String input = "not a price";
        assertNull(CollectorUtils.parsePrice(input, Locale.US));
    }

    @Test
    void parsePrice_shouldHandleNullAndEmptyInput() {
        assertNull(CollectorUtils.parsePrice(null, Locale.US));
        assertNull(CollectorUtils.parsePrice("", Locale.US));
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
}
