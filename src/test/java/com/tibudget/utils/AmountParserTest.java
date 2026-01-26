package com.tibudget.utils;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AmountParser with strict single-amount parsing rules.
 */
class AmountParserTest {

    /* =============================
     * Basic validation
     * ============================= */

    @Test
    void parse_nullInput_returnsNull() {
        assertNull(AmountParser.parse(null, Locale.FRANCE));
    }

    @Test
    void parse_emptyInput_returnsNull() {
        assertNull(AmountParser.parse("   ", Locale.FRANCE));
    }

    /* =============================
     * HTML handling
     * ============================= */

    @Test
    void parse_htmlWithTagsAndEntities() {
        String html = "\n\t            <div class=\"price\">                <span>\n16,85&nbsp;€</span>            </div>    ";

        AmountDto result = AmountParser.parse(html, Locale.FRANCE);

        assertNotNull(result);
        assertEquals(16.85, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    /* =============================
     * Locale-only fallback
     * ============================= */

    @Test
    void parse_amountWithoutSymbol_usesLocaleCurrency() {
        AmountDto result = AmountParser.parse("1 000", Locale.FRANCE);

        assertNotNull(result);
        assertEquals(1000.0, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    @Test
    void parse_amountWithoutSymbol_usLocale() {
        AmountDto result = AmountParser.parse("1000", Locale.US);

        assertNotNull(result);
        assertEquals(1000.0, result.getAmount(), 0.0001);
        assertEquals("USD", result.getCurrencyIsoCode());
    }

    /* =============================
     * ISO currency has priority
     * ============================= */

    @Test
    void parse_isoCurrency_overridesLocale() {
        AmountDto result = AmountParser.parse("100 USD", Locale.FRANCE);

        assertNotNull(result);
        assertEquals(100.0, result.getAmount(), 0.0001);
        assertEquals("USD", result.getCurrencyIsoCode());
    }

    @Test
    void parse_isoCurrency_overridesSymbolAndLocale() {
        AmountDto result = AmountParser.parse("$100 USD", Locale.CANADA);

        assertNotNull(result);
        assertEquals(100.0, result.getAmount(), 0.0001);
        assertEquals("USD", result.getCurrencyIsoCode());
    }

    /* =============================
     * Symbol + locale resolution
     * ============================= */

    @Test
    void parse_dollarWithUsLocale() {
        AmountDto result = AmountParser.parse("$1,234.56", Locale.US);

        assertNotNull(result);
        assertEquals(1234.56, result.getAmount(), 0.0001);
        assertEquals("USD", result.getCurrencyIsoCode());
    }

    @Test
    void parse_dollarWithCanadaLocale() {
        AmountDto result = AmountParser.parse("$1,234.56", Locale.CANADA);

        assertNotNull(result);
        assertEquals(1234.56, result.getAmount(), 0.0001);
        assertEquals("CAD", result.getCurrencyIsoCode());
    }

    @Test
    void parse_euroSymbol_anyLocale() {
        AmountDto result = AmountParser.parse("€99.99", Locale.US);

        assertNotNull(result);
        assertEquals(99.99, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    /* =============================
     * Other currencies
     * ============================= */

    @Test
    void parse_yenWithJapanLocale() {
        AmountDto result = AmountParser.parse("¥1200", Locale.JAPAN);

        assertNotNull(result);
        assertEquals(1200.0, result.getAmount(), 0.0001);
        assertEquals("JPY", result.getCurrencyIsoCode());
    }

    @Test
    void parse_poundWithUkLocale() {
        Locale uk = Locale.UK;

        AmountDto result = AmountParser.parse("£75.50", uk);

        assertNotNull(result);
        assertEquals(75.50, result.getAmount(), 0.0001);
        assertEquals("GBP", result.getCurrencyIsoCode());
    }

    /* =============================
     * Negative values
     * ============================= */

    @Test
    void parse_negativeAmount() {
        AmountDto result = AmountParser.parse("-12,50 €", Locale.FRANCE);

        assertNotNull(result);
        assertEquals(-12.50, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    /* =============================
     * No currency possible
     * ============================= */

    @Test
    void parse_amountWithoutLocaleAndSymbol_returnsNullCurrency() {
        AmountDto result = AmountParser.parse("1000", null);

        assertNotNull(result);
        assertEquals(1000.0, result.getAmount(), 0.0001);
        assertNull(result.getCurrency());
    }


    /* =============================
     * Totally broken input
     * ============================= */

    @Test
    void parse_onlyText_returnsNull() {
        assertNull(AmountParser.parse("hello world", Locale.FRANCE));
    }

    @Test
    void parse_onlyCurrencySymbol_returnsNull() {
        assertNull(AmountParser.parse("€", Locale.FRANCE));
    }

    @Test
    void parse_onlyIsoCode_returnsNull() {
        assertNull(AmountParser.parse("EUR", Locale.FRANCE));
    }

    @Test
    void parse_onlyHtml_returnsNull() {
        assertNull(AmountParser.parse("<div><span></span></div>", Locale.FRANCE));
    }

    @Test
    void parse_garbageCharacters_returnsNull() {
        assertNull(AmountParser.parse("@@@###!!!", Locale.FRANCE));
    }

    /* =============================
     * Weird spacing
     * ============================= */

    @Test
    void parse_multipleSpacesBetweenDigits() {
        AmountDto result = AmountParser.parse("1   000   €", Locale.FRANCE);

        assertNotNull(result);
        assertEquals(1000.0, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    @Test
    void parse_tabsAndNewlines() {
        AmountDto result = AmountParser.parse(" \n\t1\t000\n€\n", Locale.FRANCE);

        assertNotNull(result);
        assertEquals(1000.0, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    /* =============================
     * Weird separators
     * ============================= */

    @Test
    void parse_apostropheThousandsSeparator() {
        AmountDto result = AmountParser.parse("1'234.56 CHF", Locale.FRANCE);

        assertNotNull(result);
        assertEquals(1234.56, result.getAmount(), 0.0001);
        assertEquals("CHF", result.getCurrencyIsoCode());
    }

    @Test
    void parse_unicodeApostropheSeparator() {
        AmountDto result = AmountParser.parse("1’234,56 €", Locale.FRANCE);

        assertNotNull(result);
        assertEquals(1234.56, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    /* =============================
     * Decimal edge cases
     * ============================= */

    @Test
    void parse_trailingDecimalSeparator() {
        AmountDto result = AmountParser.parse("12.", Locale.US);

        assertNotNull(result);
        assertEquals(12.0, result.getAmount(), 0.0001);
        assertEquals("USD", result.getCurrencyIsoCode());
    }

    @Test
    void parse_leadingDecimalSeparator() {
        AmountDto result = AmountParser.parse(".99 €", Locale.FRANCE);

        assertNotNull(result);
        assertEquals(0.99, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    /* =============================
     * Negative and sign abuse
     * ============================= */

    @Test
    void parse_multipleMinusSigns() {
        AmountDto result = AmountParser.parse("--12,50 €", Locale.FRANCE);
        assertNull(result);
    }

    @Test
    void parse_plusSign() {
        AmountDto result = AmountParser.parse("+99.99 USD", Locale.US);

        assertNotNull(result);
        assertEquals(99.99, result.getAmount(), 0.0001);
        assertEquals("USD", result.getCurrencyIsoCode());
    }

    /* =============================
     * HTML nightmares
     * ============================= */

    @Test
    void parse_nestedHtmlWithNoise() {
        String html = " \n \t           <div>                <span class=\"price\">                    <b>  16,85&nbsp;€ </b>                </span>                <!-- comment -->            </div>  ";

        AmountDto result = AmountParser.parse(html, Locale.FRANCE);

        assertNotNull(result);
        assertEquals(16.85, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    @Test
    void parse_htmlWithInlineStyles() {
        String html = "<span style='color:red'>99,90&nbsp;€</span>";

        AmountDto result = AmountParser.parse(html, Locale.FRANCE);

        assertNotNull(result);
        assertEquals(99.90, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    /* =============================
     * Locale corner cases
     * ============================= */

    @Test
    void parse_nullLocale_noSymbol() {
        AmountDto result = AmountParser.parse("1000", null);

        assertNotNull(result);
        assertEquals(1000.0, result.getAmount(), 0.0001);
        assertNull(result.getCurrency());
    }

    @Test
    void parse_nullLocale_withIso() {
        AmountDto result = AmountParser.parse("100 EUR", null);

        assertNotNull(result);
        assertEquals(100.0, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    @Test
    void parse_weirdLocaleStillWorks() {
        Locale pirate = new Locale("xx", "YY");

        AmountDto result = AmountParser.parse("100 USD", pirate);

        assertNotNull(result);
        assertEquals(100.0, result.getAmount(), 0.0001);
        assertEquals("USD", result.getCurrencyIsoCode());
    }

    /* =============================
     * Beginner mistakes
     * ============================= */

    @Test
    void parse_commaAsThousandsAndDecimalMixed() {
        AmountDto result = AmountParser.parse("1,234,56 €", Locale.FRANCE);

        assertNotNull(result);
        assertEquals(1234.56, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }

    @Test
    void parse_spacesEverywhere() {
        AmountDto result = AmountParser.parse("  €   1   2   3   ,   4   5  ", Locale.FRANCE);

        assertNotNull(result);
        assertEquals(123.45, result.getAmount(), 0.0001);
        assertEquals("EUR", result.getCurrencyIsoCode());
    }
}
