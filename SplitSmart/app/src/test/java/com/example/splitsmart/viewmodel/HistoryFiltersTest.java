package com.example.splitsmart.viewmodel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HistoryFiltersTest {

    @Test
    public void startsWithNoFiltersAndNewestFirst() {
        HistoryFilters filters = HistoryFilters.none();

        assertNull(filters.getKeyword());
        assertNull(filters.getType());
        assertNull(filters.getCategory());
        assertNull(filters.getMemberId());
        assertEquals("NEWEST", filters.getSort());
        assertFalse(filters.hasActiveFilters());
    }

    @Test
    public void blankValuesMeanNoFilterAndTextIsTrimmed() {
        HistoryFilters filters = HistoryFilters.none()
                .withKeyword("  pizza ")
                .withCategory("   ")
                .withSort("");

        assertEquals("pizza", filters.getKeyword());
        assertNull(filters.getCategory());
        assertEquals("NEWEST", filters.getSort());
    }

    @Test
    public void equalFiltersCompareEqualSoNoRequestIsRepeated() {
        assertEquals(HistoryFilters.none().withKeyword("pizza"),
                HistoryFilters.none().withKeyword(" pizza  "));
        assertEquals(HistoryFilters.none().withKeyword("pizza").hashCode(),
                HistoryFilters.none().withKeyword("pizza").hashCode());
        assertNotEquals(HistoryFilters.none(), HistoryFilters.none().withMemberId(3L));
    }

    @Test
    public void eachChangeReturnsACopy() {
        HistoryFilters original = HistoryFilters.none();
        HistoryFilters changed = original.withType(HistoryFilters.TYPE_SETTLEMENT);

        assertNull(original.getType());
        assertEquals("SETTLEMENT", changed.getType());
        assertTrue(changed.hasActiveFilters());
    }

    @Test
    public void clearingKeepsTheSort() {
        HistoryFilters cleared = HistoryFilters.none()
                .withSort("AMOUNT_HIGH")
                .withKeyword("rent")
                .withCategory("HOUSING")
                .withMemberId(2L)
                .withType(HistoryFilters.TYPE_EXPENSE)
                .cleared();

        assertFalse(cleared.hasActiveFilters());
        assertEquals("AMOUNT_HIGH", cleared.getSort());
    }

    @Test
    public void sortIndexMatchesTheMenu() {
        assertEquals(HistoryFilters.SORT_VALUES.length, HistoryFilters.SORT_LABELS.length);
        assertEquals(2, HistoryFilters.sortIndex("AMOUNT_HIGH"));
        assertEquals(0, HistoryFilters.sortIndex("unknown"));
    }
}
