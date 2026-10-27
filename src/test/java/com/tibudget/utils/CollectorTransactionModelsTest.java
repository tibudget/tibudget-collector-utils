package com.tibudget.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CollectorTransactionModelsTest {
    private final AbstractCollectorPlugin plugin = new AbstractCollectorPlugin() {
        @Override
        public String getDomain() {
            return "https://example.com";
        }

        @Override
        public boolean collect() {
            return true;
        }
    };

    @Test
    void checkpointSurvivesSerializationAndKeepsOnlyReferencesFromTheLatestSavedDate() {
        LocalDate date = LocalDate.of(2026, 10, 26);
        CollectorProgress progress = plugin.loadProgress();
        assertNull(progress.getLastTransactionDate());
        assertTrue(progress.getTransactionReferences().isEmpty());

        plugin.saveProgress(progress, new CollectorPendingTransaction("first", date));
        plugin.saveProgress(progress, new CollectorPendingTransaction("second", date));
        CollectorProgress restored = plugin.loadProgress();
        assertEquals(date, restored.getLastTransactionDate());
        assertEquals(Set.of("first", "second"), restored.getTransactionReferences());

        plugin.saveProgress(restored, new CollectorPendingTransaction("third", date.minusDays(1)));
        assertEquals(Set.of("third"), plugin.loadProgress().getTransactionReferences());
        assertEquals(date.minusDays(1), plugin.loadProgress().getLastTransactionDate());
    }

    @Test
    void pageRetainsItsContentsWhenTheSourceListChanges() {
        CollectorPendingTransaction transaction = new CollectorPendingTransaction("id", LocalDate.now());
        List<CollectorPendingTransaction> source = new ArrayList<>(List.of(transaction));
        CollectorTransactionPage page = new CollectorTransactionPage(source, "cursor");
        source.clear();
        assertEquals(List.of(transaction), page.transactions);
        assertThrows(UnsupportedOperationException.class, () -> page.transactions.clear());
        assertEquals("cursor", new CollectorTransactionPage(List.of(), "cursor").nextPage);
    }

    @Test
    void checkpointOwnsItsReferencesAndRemainsMutableAfterRestoringJson() {
        Set<String> source = new HashSet<>(Set.of("id"));
        CollectorProgress progress = new CollectorProgress();
        progress.setTransactionReferences(source);
        source.clear();
        assertEquals(Set.of("id"), progress.getTransactionReferences());
        CollectorProgress restored = plugin.gson.fromJson(plugin.gson.toJson(progress), CollectorProgress.class);
        restored.getTransactionReferences().add("another");
        assertEquals(Set.of("id", "another"), restored.getTransactionReferences());
    }

    @Test
    void rejectsInvalidPendingTransactionsAndPageContents() {
        assertThrows(NullPointerException.class, () -> new CollectorPendingTransaction(null, LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> new CollectorPendingTransaction(" \t", LocalDate.now()));
        assertThrows(NullPointerException.class, () -> new CollectorPendingTransaction("id", null));
        assertThrows(NullPointerException.class, () -> new CollectorTransactionPage(null, null));
        assertThrows(NullPointerException.class,
                () -> new CollectorTransactionPage(java.util.Arrays.asList((CollectorPendingTransaction) null), null));
    }
}
