package com.splitsmart.backend.dto;

import java.util.List;

public record HistoryResponse(
        // Matching transactions, already filtered and sorted
        List<HistoryItemResponse> items,
        // Number of transactions in the group before filtering
        int totalCount,
        // The group's members by name, for the "filter by member" choice
        List<MemberResponse> members
) {}
