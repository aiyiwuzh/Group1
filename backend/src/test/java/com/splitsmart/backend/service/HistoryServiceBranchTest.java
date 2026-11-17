package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.CategoryTotalResponse;
import com.splitsmart.backend.dto.HistoryItemResponse;
import com.splitsmart.backend.dto.HistoryResponse;
import com.splitsmart.backend.dto.MonthlySummaryResponse;
import com.splitsmart.backend.entity.Expense;
import com.splitsmart.backend.entity.Group;
import com.splitsmart.backend.entity.Membership;
import com.splitsmart.backend.entity.Settlement;
import com.splitsmart.backend.entity.User;
import com.splitsmart.backend.repository.ExpenseRepository;
import com.splitsmart.backend.repository.GroupRepository;
import com.splitsmart.backend.repository.MembershipRepository;
import com.splitsmart.backend.repository.SettlementRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * White-box tests for Task 5 (Lukas Li): branches of HistoryService added with
 * custom categories and participant search that HistoryServiceTest does not reach.
 */
class HistoryServiceBranchTest {

    private static final long GROUP_ID = 20L;

    private HistoryService historyService;
    private Group group;
    private User alice;
    private User bob;
    private User carol;

    // Kept in the order the repository returns them (newest first).
    private final List<Expense> expenses = new ArrayList<>();
    private final List<Settlement> settlements = new ArrayList<>();

    private static User user(long id, String name) {
        User user = new User(name, "user" + id + "@usc.edu",
                String.format("%010d", id), "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // Equal split in whole cents; the payer is always the first participant.
    private void expense(long id, String title, String amount, String category,
                         String createdAt, User payer, User... others) {
        Expense expense = new Expense(group, payer);
        ReflectionTestUtils.setField(expense, "id", id);
        ReflectionTestUtils.setField(expense, "createdAt", Instant.parse(createdAt));
        expense.setTitle(title);
        expense.setAmount(new BigDecimal(amount));
        expense.setCategory(category);
        expense.setPayer(payer);

        LinkedHashSet<User> participants = new LinkedHashSet<>();
        participants.add(payer);
        participants.addAll(List.of(others));
        expense.setParticipants(participants);

        BigDecimal each = new BigDecimal(amount).divide(
                BigDecimal.valueOf(participants.size()), 2, java.math.RoundingMode.DOWN);
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        for (User participant : participants) {
            shares.put(participant.getId(), each);
        }
        expense.setShares(shares);
        expenses.add(expense);
    }

    @BeforeEach
    void setUp() {
        ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
        SettlementRepository settlementRepository = mock(SettlementRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);

        alice = user(1, "Alice Kim");
        bob = user(2, "Bob Lee");
        carol = user(3, "Carol Nguyen");
        group = new Group("Climbing club", null, alice);
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        List<User> members = List.of(alice, bob, carol);

        when(groupRepository.existsById(GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupId(GROUP_ID)).thenReturn(
                members.stream().map(u -> new Membership(group, u)).toList());
        when(membershipRepository.existsByGroupIdAndUserId(anyLong(), anyLong()))
                .thenAnswer(inv -> inv.getArgument(0).equals(GROUP_ID)
                        && members.stream().anyMatch(
                                u -> u.getId().equals(inv.getArgument(1))));

        // All in November 2026 (UTC), newest first.
        expense(44, "Movie night", "65.00", "ENTERTAINMENT", "2026-11-12T20:00:00Z", carol, bob);
        expense(43, "Yoga class", "25.00", "gym", "2026-11-09T18:00:00Z", bob, alice);
        Settlement settlement = new Settlement(group, bob, alice, new BigDecimal("5.00"),
                bob, "Venmo");
        ReflectionTestUtils.setField(settlement, "id", 50L);
        ReflectionTestUtils.setField(settlement, "createdAt",
                Instant.parse("2026-11-08T12:00:00Z"));
        settlements.add(settlement);
        expense(42, "Climbing pass", "40.00", "Gym", "2026-11-06T17:00:00Z", alice, bob);
        expense(41, "Coffee run", "6.00", "FOOD", "2026-11-02T16:00:00Z", alice, carol);

        when(expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(GROUP_ID))
                .thenAnswer(inv -> List.copyOf(expenses));
        when(settlementRepository.findByGroupIdOrderByCreatedAtDescIdDesc(GROUP_ID))
                .thenAnswer(inv -> List.copyOf(settlements));
        when(expenseRepository.findByGroupIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(GROUP_ID), any(Instant.class), any(Instant.class)))
                .thenAnswer(inv -> {
                    Instant start = inv.getArgument(1);
                    Instant end = inv.getArgument(2);
                    return expenses.stream()
                            .filter(e -> !e.getCreatedAt().isBefore(start)
                                    && e.getCreatedAt().isBefore(end))
                            .toList();
                });

        historyService = new HistoryService(expenseRepository, settlementRepository,
                groupRepository, membershipRepository);
    }

    private HistoryResponse history(String keyword, String category) {
        return historyService.getHistory(1L, GROUP_ID, keyword, null, category, null, null);
    }

    private static List<Long> ids(HistoryResponse response) {
        return response.items().stream().map(HistoryItemResponse::id).toList();
    }

    @Test
    void keywordMatchesExpenseParticipantNamesCaseSensitively() {
        // "Coffee run" matches only through its participant Carol Nguyen; Movie night
        // has Carol as payer and participant. Neither title nor note contains "Nguyen".
        assertEquals(List.of(44L, 41L), ids(history("Nguyen", null)));
        // The participant-name branch is case-sensitive like the title and note branches.
        assertTrue(history("nguyen", null).items().isEmpty());
    }

    @Test
    void categoryFilterNormalizesStandardNamesAndMatchesCustomCategoriesIgnoringCase() {
        // A lowercase standard name is normalized to FOOD before matching.
        assertEquals(List.of(41L), ids(history(null, "food")));
        // A custom category matches both spellings, and the settlement is hidden.
        assertEquals(List.of(43L, 42L), ids(history(null, "GYM")));
        // A custom category with no expenses is valid and simply matches nothing.
        HistoryResponse none = history(null, "Pets");
        assertTrue(none.items().isEmpty());
        assertEquals(5, none.totalCount());
    }

    @Test
    void searchAndCategoryLengthLimitsAcceptTheBoundaryAndRejectOneMore() {
        assertTrue(history("x".repeat(HistoryService.MAX_KEYWORD_LENGTH), null)
                .items().isEmpty());
        assertTrue(history(null, "c".repeat(50)).items().isEmpty());

        ResponseStatusException tooLong = assertThrows(ResponseStatusException.class,
                () -> history(null, "c".repeat(51)));
        assertEquals(HttpStatus.BAD_REQUEST, tooLong.getStatusCode());
        assertEquals("category cannot exceed 50 characters", tooLong.getReason());
    }

    @Test
    void monthlySummaryMergesCustomCategorySpellingsAndOrdersTiesByName() {
        MonthlySummaryResponse summary =
                historyService.getMonthlySummary(1L, GROUP_ID, "2026-11", "UTC");

        // Four expenses; the $5.00 settlement is not spending.
        assertEquals(4, summary.expenseCount());
        assertEquals(new BigDecimal("136.00"), summary.totalSpending());
        // "Gym" and "gym" become one row named after the first spelling seen.
        // It ties ENTERTAINMENT at $65.00, so the two are ordered by name.
        assertEquals(List.of(
                new CategoryTotalResponse("ENTERTAINMENT", 1, new BigDecimal("65.00")),
                new CategoryTotalResponse("gym", 2, new BigDecimal("65.00")),
                new CategoryTotalResponse("FOOD", 1, new BigDecimal("6.00"))),
                summary.categories());
        // Alice's shares: 12.50 (Yoga) + 20.00 (Climbing) + 3.00 (Coffee).
        assertEquals(new BigDecimal("35.50"), summary.yourShare());
    }
}
