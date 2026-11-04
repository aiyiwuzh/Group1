package com.example.splitsmart.viewmodel;

import java.util.Objects;

// The search, filter and sort choices on the history screen, as the server
// expects them. Immutable: each change returns a copy, so the ViewModel can
// tell when nothing changed and skip the request. Null means "no filter".
public final class HistoryFilters {

    public static final String TYPE_EXPENSE = "EXPENSE";
    public static final String TYPE_SETTLEMENT = "SETTLEMENT";

    // Server sort values, in the order the sort menu shows them.
    public static final String[] SORT_VALUES = {
            "NEWEST", "OLDEST", "AMOUNT_HIGH", "AMOUNT_LOW"
    };

    public static final String[] SORT_LABELS = {
            "Newest first", "Oldest first", "Amount: high to low", "Amount: low to high"
    };

    public static final String DEFAULT_SORT = "NEWEST";

    private final String keyword;
    private final String type;
    private final String category;
    private final Long memberId;
    private final String sort;

    public HistoryFilters(String keyword, String type, String category,
                          Long memberId, String sort) {
        this.keyword = blankToNull(keyword);
        this.type = blankToNull(type);
        this.category = blankToNull(category);
        this.memberId = memberId;
        this.sort = blankToNull(sort) == null ? DEFAULT_SORT : sort.trim();
    }

    public static HistoryFilters none() {
        return new HistoryFilters(null, null, null, null, DEFAULT_SORT);
    }

    public String getKeyword() { return keyword; }
    public String getType() { return type; }
    public String getCategory() { return category; }
    public Long getMemberId() { return memberId; }
    public String getSort() { return sort; }

    public HistoryFilters withKeyword(String value) {
        return new HistoryFilters(value, type, category, memberId, sort);
    }

    public HistoryFilters withType(String value) {
        return new HistoryFilters(keyword, value, category, memberId, sort);
    }

    public HistoryFilters withCategory(String value) {
        return new HistoryFilters(keyword, type, value, memberId, sort);
    }

    public HistoryFilters withMemberId(Long value) {
        return new HistoryFilters(keyword, type, category, value, sort);
    }

    public HistoryFilters withSort(String value) {
        return new HistoryFilters(keyword, type, category, memberId, value);
    }

    // Clears search and filters but keeps the chosen sort.
    public HistoryFilters cleared() {
        return new HistoryFilters(null, null, null, null, sort);
    }

    public boolean hasActiveFilters() {
        return keyword != null || type != null || category != null || memberId != null;
    }

    public static int sortIndex(String value) {
        for (int i = 0; i < SORT_VALUES.length; i++) {
            if (SORT_VALUES[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HistoryFilters)) return false;
        HistoryFilters f = (HistoryFilters) other;
        return Objects.equals(keyword, f.keyword)
                && Objects.equals(type, f.type)
                && Objects.equals(category, f.category)
                && Objects.equals(memberId, f.memberId)
                && Objects.equals(sort, f.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyword, type, category, memberId, sort);
    }
}
