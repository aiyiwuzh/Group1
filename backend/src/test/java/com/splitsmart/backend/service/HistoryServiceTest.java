package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.CategoryTotalResponse;
import com.splitsmart.backend.dto.HistoryItemResponse;
import com.splitsmart.backend.dto.HistoryResponse;
import com.splitsmart.backend.dto.MemberResponse;
import com.splitsmart.backend.dto.MonthlySummaryResponse;
import com.splitsmart.backend.dto.TransactionType;
import com.splitsmart.backend.entity.Expense;
import com.splitsmart.backend.entity.ExpenseCategory;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HistoryServiceTest {

    private ExpenseRepository expenseRepository;
    private SettlementRepository settlementRepository;
    private HistoryService historyService;

    private User alice;
    private User bob;
    private User carol;
    private Group group;

    // Kept in the order the repository returns them (newest first).
    private final List<Expense> expenses = new ArrayList<>();
    private final List<Settlement> settlements = new ArrayList<>();

    private static User user(long id, String name) {
        User user = new User(name, name.toLowerCase() + "@usc.edu",
                String.format("%010d", id), "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Expense expense(long id, String title, String amount, ExpenseCategory category,
                            User payer, String note, String createdAt, User... participants) {
        Expense expense = new Expense(group, payer);
        ReflectionTestUtils.setField(expense, "id", id);
        ReflectionTestUtils.setField(expense, "createdAt", Instant.parse(createdAt));
        expense.setTitle(title);
        expense.setAmount(new BigDecimal(amount));
        expense.setCategory(category);
        expense.setPayer(payer);
        expense.setNote(note);
        expense.setParticipants(new LinkedHashSet<>(List.of(participants)));

        // Equal shares; the first participant takes any leftover cent.
        BigDecimal total = new BigDecimal(amount);
        BigDecimal each = total.divide(BigDecimal.valueOf(participants.length), 2,
                java.math.RoundingMode.DOWN);
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        for (User participant : participants) {
            shares.put(participant.getId(), each);
        }
        BigDecimal leftover = total.subtract(each.multiply(BigDecimal.valueOf(participants.length)));
        shares.merge(participants[0].getId(), leftover, BigDecimal::add);
        expense.setShares(shares);

        expenses.add(expense);
        return expense;
    }

    private Settlement settlement(long id, User payer, User payee, String amount, String createdAt) {
        Settlement settlement = new Settlement(group, payer, payee, new BigDecimal(amount));
        ReflectionTestUtils.setField(settlement, "id", id);
        ReflectionTestUtils.setField(settlement, "createdAt", Instant.parse(createdAt));
        settlements.add(settlement);
        return settlement;
    }

    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        settlementRepository = mock(SettlementRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);

        alice = user(1, "Alice");
        bob = user(2, "Bob");
        carol = user(3, "Carol");

        group = new Group("Apartment", null, alice);
        ReflectionTestUtils.setField(group, "id", 10L);

        List<User> members = List.of(carol, alice, bob);

        when(groupRepository.existsById(10L)).thenReturn(true);
        when(membershipRepository.findByGroupId(10L)).thenReturn(
                members.stream().map(u -> new Membership(group, u)).toList());
        when(membershipRepository.existsByGroupIdAndUserId(anyLong(), anyLong()))
                .thenAnswer(inv -> inv.getArgument(0).equals(10L)
                        && members.stream().anyMatch(
                                u -> u.getId().equals(inv.getArgument(1))));

        // Times are UTC. Los Angeles is UTC-7 until Nov 1, 2026, then UTC-8.
        expense(14, "Groceries refill", "15.25", ExpenseCategory.FOOD, bob, null,
                "2026-11-01T06:59:59Z", alice, bob);              // Oct 31 11:59:59 PM in LA
        settlement(20, bob, alice, "20.00", "2026-10-22T16:00:00Z");
        expense(11, "Uber to LAX", "45.30", ExpenseCategory.TRANSPORTATION, bob, null,
                "2026-10-20T15:00:00Z", bob, carol);
        settlement(21, carol, bob, "10.00", "2026-10-10T16:00:00Z");
        expense(10, "Trader Joe's", "60.00", ExpenseCategory.FOOD, alice, "weekly groceries",
                "2026-10-05T17:00:00Z", alice, bob, carol);
        expense(13, "Rent utilities", "120.00", ExpenseCategory.HOUSING, alice, null,
                "2026-09-15T19:00:00Z", alice, bob);

        when(expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(10L))
                .thenAnswer(inv -> List.copyOf(expenses));
        when(settlementRepository.findByGroupIdOrderByCreatedAtDescIdDesc(10L))
                .thenAnswer(inv -> List.copyOf(settlements));
        when(expenseRepository.findByGroupIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(10L), any(Instant.class), any(Instant.class)))
                .thenAnswer(inv -> {
                    Instant start = inv.getArgument(1);
                    Instant end = inv.getArgument(2);
                    return expenses.stream()
                            .filter(e -> !e.getCreatedAt().isBefore(start)
                                    && e.getCreatedAt().isBefore(end))
                            .toList();
                });

        historyService = new HistoryService(
                expenseRepository, settlementRepository,
                groupRepository, membershipRepository);
    }

    private HistoryResponse history(String keyword, String type, String category,
                                    Long memberId, String sort) {
        return historyService.getHistory(1L, 10L, keyword, type, category, memberId, sort);
    }

    private static List<Long> ids(HistoryResponse response) {
        return response.items().stream().map(HistoryItemResponse::id).toList();
    }

    private static void assertStatus(HttpStatus status, Runnable call) {
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, call::run);
        assertEquals(status, exception.getStatusCode());
    }

    // ------------------------------------------------------------------ listing

    @Test
    void defaultHistoryIsNewestFirstWithExpensesAndSettlements() {
        HistoryResponse response = history(null, null, null, null, null);

        assertEquals(List.of(14L, 20L, 11L, 21L, 10L, 13L), ids(response));
        assertEquals(6, response.totalCount());
    }

    @Test
    void membersAreListedByName() {
        assertEquals(List.of("Alice", "Bob", "Carol"),
                history(null, null, null, null, null).members().stream()
                        .map(MemberResponse::name).toList());
    }

    @Test
    void oldestFirstIsTheReverse() {
        assertEquals(List.of(13L, 10L, 21L, 11L, 20L, 14L),
                ids(history(null, null, null, null, "oldest")));
    }

    @Test
    void sortsByAmount() {
        assertEquals(List.of(13L, 10L, 11L, 20L, 14L, 21L),
                ids(history(null, null, null, null, "AMOUNT_HIGH")));
        assertEquals(List.of(21L, 14L, 20L, 11L, 10L, 13L),
                ids(history(null, null, null, null, "amount_low")));
    }

    @Test
    void sameTimeIsBrokenByTypeThenId() {
        expense(15, "Coffee", "5.00", ExpenseCategory.FOOD, alice, null,
                "2026-11-01T06:59:59Z", alice, bob);
        assertEquals(List.of(15L, 14L), ids(history(null, null, null, null, null)).subList(0, 2));
    }

    @Test
    void deletedExpenseIsAbsent() {
        expenses.removeIf(e -> e.getId() == 11L);

        HistoryResponse response = history(null, null, null, null, null);

        assertFalse(ids(response).contains(11L));
        assertEquals(5, response.totalCount());
    }

    @Test
    void settlementRowsDescribeWhoPaidWhom() {
        HistoryItemResponse item = history(null, "SETTLEMENT", null, null, null).items().get(0);

        assertEquals(TransactionType.SETTLEMENT, item.type());
        assertEquals("Bob paid Alice", item.title());
        assertEquals(bob.getId(), item.payer().id());
        assertEquals(alice.getId(), item.payee().id());
        assertNull(item.category());
        assertEquals(2, item.participantCount());
    }

    @Test
    void expenseRowsCarryCategoryParticipantsAndNote() {
        HistoryItemResponse item = history("trader", null, null, null, null).items().get(0);

        assertEquals(TransactionType.EXPENSE, item.type());
        assertEquals(ExpenseCategory.FOOD, item.category());
        assertEquals(3, item.participantCount());
        assertEquals("weekly groceries", item.note());
        assertNull(item.payee());
    }

    // ------------------------------------------------------------------ search

    @Test
    void keywordMatchesTitlesAndNotesIgnoringCase() {
        // 14 by title, 10 by its note
        assertEquals(List.of(14L, 10L), ids(history("  GROCERIES ", null, null, null, null)));
    }

    @Test
    void keywordMatchesSettlementNames() {
        assertEquals(List.of(21L), ids(history("carol paid", null, null, null, null)));
    }

    @Test
    void blankKeywordMeansNoFilter() {
        assertEquals(6, history("   ", null, null, null, null).items().size());
    }

    @Test
    void noMatchesGivesAnEmptyList() {
        HistoryResponse response = history("sushi", null, null, null, null);
        assertTrue(response.items().isEmpty());
        assertEquals(6, response.totalCount());
    }

    @Test
    void overlyLongKeywordIsRejected() {
        String text = "x".repeat(HistoryService.MAX_KEYWORD_LENGTH + 1);
        assertStatus(HttpStatus.BAD_REQUEST, () -> history(text, null, null, null, null));
    }

    // ------------------------------------------------------------------ filters

    @Test
    void typeFilter() {
        assertEquals(List.of(14L, 11L, 10L, 13L), ids(history(null, "expense", null, null, null)));
        assertEquals(List.of(20L, 21L), ids(history(null, "SETTLEMENT", null, null, null)));
        assertEquals(6, history(null, "all", null, null, null).items().size());
    }

    @Test
    void categoryFilterHidesSettlements() {
        assertEquals(List.of(14L, 10L), ids(history(null, null, "food", null, null)));
        assertTrue(history(null, "SETTLEMENT", "FOOD", null, null).items().isEmpty());
    }

    @Test
    void memberFilterMatchesPayerParticipantAndSettlementParties() {
        // Carol: participant in 11 and 10, payer of settlement 21.
        assertEquals(List.of(11L, 21L, 10L), ids(history(null, null, null, 3L, null)));
    }

    @Test
    void filtersCombine() {
        assertEquals(List.of(13L), ids(history("rent", "EXPENSE", "HOUSING", 2L, "NEWEST")));
    }

    @Test
    void invalidFiltersAreRejected() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> history(null, "refund", null, null, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> history(null, null, "PIZZA", null, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> history(null, null, null, null, "random"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> history(null, null, null, 99L, null));
    }

    // ------------------------------------------------------------------ permissions

    @Test
    void nonMembersAndMissingGroupsAreRejected() {
        assertStatus(HttpStatus.FORBIDDEN,
                () -> historyService.getHistory(4L, 10L, null, null, null, null, null));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> historyService.getMonthlySummary(4L, 10L, "2026-10", null));
        assertStatus(HttpStatus.NOT_FOUND,
                () -> historyService.getHistory(1L, 99L, null, null, null, null, null));
        assertStatus(HttpStatus.NOT_FOUND,
                () -> historyService.getMonthlySummary(1L, 99L, "2026-10", null));
    }

    // ------------------------------------------------------------------ monthly summary

    @Test
    void octoberSummaryInLosAngelesExcludesSettlements() {
        MonthlySummaryResponse summary =
                historyService.getMonthlySummary(1L, 10L, "2026-10", "America/Los_Angeles");

        assertEquals("2026-10", summary.month());
        assertEquals("America/Los_Angeles", summary.timeZone());
        // 14 (Oct 31 late evening in LA), 11, 10 -- not 13, and no settlements
        assertEquals(3, summary.expenseCount());
        assertEquals(new BigDecimal("120.55"), summary.totalSpending());
        assertEquals(List.of(
                new CategoryTotalResponse(ExpenseCategory.FOOD, 2, new BigDecimal("75.25")),
                new CategoryTotalResponse(ExpenseCategory.TRANSPORTATION, 1, new BigDecimal("45.30"))),
                summary.categories());
    }

    @Test
    void yourShareAddsUpOnlyTheCurrentUsersShares() {
        // Alice: 7.63 of expense 14 (first participant takes the extra cent), 20.00 of expense 10
        assertEquals(new BigDecimal("27.63"),
                historyService.getMonthlySummary(1L, 10L, "2026-10", "America/Los_Angeles")
                        .yourShare());
        // Carol: 22.65 of expense 11, 20.00 of expense 10
        assertEquals(new BigDecimal("42.65"),
                historyService.getMonthlySummary(3L, 10L, "2026-10", "America/Los_Angeles")
                        .yourShare());
    }

    @Test
    void timeZoneDecidesWhichMonthAnExpenseFallsIn() {
        // In UTC, expense 14 (Nov 1 06:59 UTC) belongs to November.
        assertEquals(2, historyService.getMonthlySummary(1L, 10L, "2026-10", "UTC").expenseCount());
        assertEquals(1, historyService.getMonthlySummary(1L, 10L, "2026-11", "UTC").expenseCount());
        assertEquals(0, historyService.getMonthlySummary(
                1L, 10L, "2026-11", "America/Los_Angeles").expenseCount());
    }

    @Test
    void timeZoneDefaultsToLosAngeles() {
        assertEquals("America/Los_Angeles",
                historyService.getMonthlySummary(1L, 10L, "2026-10", null).timeZone());
    }

    @Test
    void emptyMonthHasZeroTotals() {
        MonthlySummaryResponse summary =
                historyService.getMonthlySummary(1L, 10L, "2026-01", "UTC");

        assertEquals(0, summary.expenseCount());
        assertEquals(new BigDecimal("0.00"), summary.totalSpending());
        assertEquals(new BigDecimal("0.00"), summary.yourShare());
        assertTrue(summary.categories().isEmpty());
    }

    @Test
    void summaryMathIsExact() {
        // 0.10 + 0.20 is not 0.3 with doubles; it must be exactly 0.30 here.
        expense(40, "Gum", "0.10", ExpenseCategory.FOOD, alice, null,
                "2026-03-01T20:00:00Z", alice, bob);
        expense(41, "Mint", "0.20", ExpenseCategory.FOOD, alice, null,
                "2026-03-02T20:00:00Z", alice, bob);

        assertEquals(new BigDecimal("0.30"),
                historyService.getMonthlySummary(1L, 10L, "2026-03", "UTC").totalSpending());
    }

    @Test
    void monthDefaultsToTheCurrentMonth() {
        MonthlySummaryResponse summary =
                historyService.getMonthlySummary(1L, 10L, null, "UTC");
        assertEquals(java.time.YearMonth.now(java.time.ZoneOffset.UTC).toString(),
                summary.month());
    }

    @Test
    void invalidMonthOrTimeZoneIsRejected() {
        for (String month : new String[]{"10-2026", "2026-13", "October"}) {
            assertStatus(HttpStatus.BAD_REQUEST,
                    () -> historyService.getMonthlySummary(1L, 10L, month, "UTC"));
        }
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> historyService.getMonthlySummary(1L, 10L, "2026-10", "Mars/Olympus"));
    }

    @Test
    void historyNeverChangesAnyRows() {
        history("groceries", null, null, null, null);
        historyService.getMonthlySummary(1L, 10L, "2026-10", null);

        verify(expenseRepository, never()).save(any());
        verify(expenseRepository, never()).delete(any());
        verify(settlementRepository, never()).save(any());
        verify(settlementRepository, never()).delete(any());
    }
}
