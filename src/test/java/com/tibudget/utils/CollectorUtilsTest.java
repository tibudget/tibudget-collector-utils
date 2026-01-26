package com.tibudget.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void htmlToText_shouldReturnEmptyStringOnNullOrEmptyInput() {
        assertEquals("", CollectorUtils.htmlToText(null));
        assertEquals("", CollectorUtils.htmlToText(""));
    }

    @Test
    void truncateString_shouldHandleNullInput() {
        assertEquals("", CollectorUtils.truncateString(null, 10));
    }
}
