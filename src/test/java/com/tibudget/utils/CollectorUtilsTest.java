package com.tibudget.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
