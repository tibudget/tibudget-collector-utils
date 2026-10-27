package com.tibudget.utils;

import com.tibudget.dto.TransactionDto;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A transaction discovered in a history page, before its full details are collected.
 * Used by collectors extending {@link AbstractCollectorPlugin} to track completed work.
 * <p>
 * The reference must be stable across runs and match {@link TransactionDto#getId()}.
 * The date is the transaction's calendar date in the account's time zone.
 * Save progress only after the corresponding transaction has been fully collected.
 */
public class CollectorPendingTransaction {
    /** Stable, non-blank website reference, preserved without trimming. */
    public final String id;

    /** Non-null transaction date in the account's time zone. */
    public final LocalDate date;

    /**
     * Optional HTML context used to collect the transaction details, or {@code null}.
     * The element is retained by reference and remains mutable; it is not a snapshot.
     */
    public final Element details;

    /**
     * Creates a pending transaction without HTML context.
     *
     * @param id stable, non-blank transaction reference
     * @param date transaction date in the account's time zone
     * @throws NullPointerException if {@code id} or {@code date} is null
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public CollectorPendingTransaction(String id, LocalDate date) {
        this(id, date, null);
    }

    /**
     * Creates a pending transaction with optional HTML context.
     *
     * @param id stable, non-blank transaction reference
     * @param date transaction date in the account's time zone
     * @param details HTML context, or {@code null} if details are fetched separately
     * @throws NullPointerException if {@code id} or {@code date} is null
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public CollectorPendingTransaction(String id, LocalDate date, Element details) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.date = Objects.requireNonNull(date, "date must not be null");
        this.details = details;
    }
}
