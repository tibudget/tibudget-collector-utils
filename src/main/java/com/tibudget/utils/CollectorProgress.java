package com.tibudget.utils;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Mutable checkpoint for incremental collect by an {@link AbstractCollectorPlugin}.
 * <p>
 * Stores the last completed transaction date and the references already completed
 * on that date. A new checkpoint has no date and an empty reference set.
 * References must match {@link CollectorPendingTransaction#id}.
 * <p>
 * Use {@link AbstractCollectorPlugin#loadProgress()} to restore the checkpoint and
 * {@link AbstractCollectorPlugin#saveProgress(CollectorProgress, CollectorPendingTransaction)}
 * after each fully collected transaction. Saving a different date clears the previous
 * references, so collectors must process dates consistently and keep transactions
 * from the same date together, including across page boundaries.
 * <p>
 * This class is not thread-safe. Its field names are part of the saved JSON format.
 */
public class CollectorProgress {
    LocalDate lastTransactionDate;
    Set<String> transactionReferences = new TreeSet<>();

    /**
     * Returns the date associated with the completed references.
     *
     * @return last completed transaction date, or {@code null} before any progress
     */
    public LocalDate getLastTransactionDate() {
        return lastTransactionDate;
    }

    /**
     * Sets the checkpoint date without changing its references.
     * Use the plugin's save method when recording a completed transaction.
     *
     * @param lastTransactionDate completed date, or {@code null} for an initial checkpoint
     */
    public void setLastTransactionDate(LocalDate lastTransactionDate) {
        this.lastTransactionDate = lastTransactionDate;
    }

    /**
     * Returns the live, mutable set of references completed on the checkpoint date.
     *
     * @return non-null reference set; callers must not add null references
     */
    public Set<String> getTransactionReferences() {
        return transactionReferences;
    }

    /**
     * Replaces the completed references with a sorted defensive copy.
     * The checkpoint date is unchanged.
     *
     * @param transactionReferences references completed on the checkpoint date
     * @throws NullPointerException if the set or any reference is null
     */
    public void setTransactionReferences(Set<String> transactionReferences) {
        this.transactionReferences = new TreeSet<>(
                Objects.requireNonNull(transactionReferences, "transactionReferences must not be null"));
    }
}
