package com.example.splitsmart.model;

import java.util.List;

public class HistoryResponse {

    private List<HistoryItem> items;
    // Transactions in the group before any filter
    private int totalCount;
    private List<Member> members;

    public List<HistoryItem> getItems() { return items; }
    public int getTotalCount() { return totalCount; }
    public List<Member> getMembers() { return members; }
}
