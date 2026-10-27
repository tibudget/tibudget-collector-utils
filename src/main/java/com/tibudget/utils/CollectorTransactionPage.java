package com.tibudget.utils;

import java.util.List;
import java.util.Objects;

/**
 * One page of transaction history discovered by a collector extending
 * {@link AbstractCollectorPlugin}.
 * <p>
 * Transactions retain the order supplied by the collector. Pagination is independent
 * of the number of transactions: an empty page may still have a next page.
 */
public class CollectorTransactionPage {
    /**
     * Unmodifiable snapshot of the pending transactions, with no null entries.
     */
    public final List<CollectorPendingTransaction> transactions;

    /**
     * Collector-specific next-page URL, cursor or token; {@code null} marks the end
     * of the history. The value is passed through without interpretation.
     */
    public final String nextPage;

    /**
     * Creates a history page, copying the list while retaining its transaction objects.
     *
     * @param transactions pending transactions in collect order; may be empty
     * @param nextPage next-page location or token, or {@code null} for the last page
     * @throws NullPointerException if the list or any transaction is null
     */
    public CollectorTransactionPage(List<CollectorPendingTransaction> transactions, String nextPage) {
        this.transactions = List.copyOf(Objects.requireNonNull(transactions, "transactions must not be null"));
        this.nextPage = nextPage;
    }
}
