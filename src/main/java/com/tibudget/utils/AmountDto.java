package com.tibudget.utils;

import java.util.Currency;
import java.util.Objects;

/**
 * Represents a monetary amount with an optional ISO-4217 currency.
 * <p>
 * This DTO is immutable and designed for worldwide usage.
 * The amount is stored as {@link Double} for lightweight processing
 * (display, heuristics, non-accounting use cases).
 */
public final class AmountDto {

    private final Double amount;
    private final Currency currency;

    /**
     * Creates a new monetary amount.
     *
     * @param amount   monetary value, must not be null
     * @param currency ISO-4217 currency, may be null if unknown
     */
    public AmountDto(Double amount, Currency currency) {
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.currency = currency;
    }

    /**
     * @return monetary value
     */
    public Double getAmount() {
        return amount;
    }

    /**
     * @return ISO-4217 currency or null if not detected
     */
    public Currency getCurrency() {
        return currency;
    }

    /**
     * @return ISO currency code (EUR, USD, CHF, etc.) or null
     */
    public String getCurrencyIsoCode() {
        return currency != null ? currency.getCurrencyCode() : null;
    }

    /**
     * @return true if a currency is present
     */
    public boolean hasCurrency() {
        return currency != null;
    }

    @Override
    public String toString() {
        return "AmountDto{" +
                "amount=" + amount +
                ", currency=" + getCurrencyIsoCode() +
                '}';
    }
}
