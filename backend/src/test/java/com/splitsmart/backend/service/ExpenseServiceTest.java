package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.ExpenseRequest;
import com.splitsmart.backend.dto.ExpenseResponse;
import com.splitsmart.backend.dto.ShareResponse;
import com.splitsmart.backend.dto.SharePreviewResponse;
import com.splitsmart.backend.dto.SplitPreviewRequest;
import com.splitsmart.backend.entity.Expense;
import com.splitsmart.backend.entity.ExpenseCategory;
import com.splitsmart.backend.entity.Group;
import com.splitsmart.backend.entity.Membership;
import com.splitsmart.backend.entity.SplitMethod;
import com.splitsmart.backend.entity.User;
import com.splitsmart.backend.repository.ExpenseRepository;
import com.splitsmart.backend.repository.GroupRepository;
import com.splitsmart.backend.repository.MembershipRepository;
import com.splitsmart.backend.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ExpenseServiceTest {

    private ExpenseRepository expenseRepository;
    private MembershipRepository membershipRepository;
    private ExpenseService expenseService;

    private User alice;
    private User bob;
    private User carol;
    private User outsider;
    private Group group;

    private static User user(long id, String name) {
        User user = new User(name, name.toLowerCase() + "@usc.edu",
                String.format("%010d", id), "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        membershipRepository = mock(MembershipRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        alice = user(1, "Alice");
        bob = user(2, "Bob");
        carol = user(3, "Carol");
        outsider = user(4, "Outsider");

        group = new Group("Apartment", null, alice);
        ReflectionTestUtils.setField(group, "id", 10L);

        List<User> members = List.of(alice, bob, carol);

        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        for (User u : List.of(alice, bob, carol, outsider)) {
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }
        when(membershipRepository.findByGroupId(10L)).thenReturn(
                members.stream().map(u -> new Membership(group, u)).toList());
        when(membershipRepository.existsByGroupIdAndUserId(anyLong(), anyLong()))
                .thenAnswer(inv -> inv.getArgument(0).equals(10L)
                        && members.stream().anyMatch(
                                u -> u.getId().equals(inv.getArgument(1))));
        when(expenseRepository.save(any(Expense.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(expenseRepository.saveAndFlush(any(Expense.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        expenseService = new ExpenseService(
                expenseRepository, groupRepository,
                membershipRepository, userRepository);
    }

    private static ExpenseRequest request(
            String amount, Long payerId, List<Long> participantIds) {

        return new ExpenseRequest("Groceries", new BigDecimal(amount),
                null, payerId, participantIds, "  ", null, null);
    }

    private static ExpenseRequest splitRequest(
            String amount, Long payerId, List<Long> participantIds,
            SplitMethod method, Map<Long, BigDecimal> splitValues) {

        return new ExpenseRequest("Groceries", new BigDecimal(amount),
                null, payerId, participantIds, null, method, splitValues);
    }

    private static BigDecimal total(List<ShareResponse> shares) {
        return shares.stream()
                .map(ShareResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Expense savedExpense() {
        ArgumentCaptor<Expense> saved = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(saved.capture());
        return saved.getValue();
    }

    private Expense existingExpense(User creator, boolean settled) {
        Expense expense = new Expense(group, creator);
        ReflectionTestUtils.setField(expense, "id", 100L);
        expense.setTitle("Rent");
        expense.setAmount(new BigDecimal("900.00"));
        expense.setCategory(ExpenseCategory.HOUSING);
        expense.setPayer(creator);
        expense.setParticipants(java.util.Set.of(alice, bob));
        expense.setSettled(settled);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense));
        return expense;
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, exception.getStatusCode());
    }

    @Test
    void createSavesExpenseWithDefaultsAndCreatorAsEditor() {
        ExpenseResponse response = expenseService.createExpense(
                1L, 10L, request("12.5", 1L, List.of(1L, 2L)));

        assertEquals(new BigDecimal("12.50"), response.amount());
        assertEquals(ExpenseCategory.OTHER, response.category());
        assertNull(response.note());
        assertEquals(1L, response.creator().id());
        assertEquals(2, response.participants().size());
        assertTrue(response.canEdit());
        verify(expenseRepository).save(any(Expense.class));
    }

    @Test
    void creatorCanRecordExpensePaidBySomeoneElse() {
        ExpenseResponse response = expenseService.createExpense(
                1L, 10L, request("30.00", 2L, List.of(2L, 3L)));

        assertEquals(2L, response.payer().id());
        assertEquals(1L, response.creator().id());
    }

    @Test
    void payerMustBeGroupMember() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> expenseService.createExpense(
                1L, 10L, request("10.00", 4L, List.of(4L, 1L))));
    }

    @Test
    void everyParticipantMustBeGroupMember() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> expenseService.createExpense(
                1L, 10L, request("10.00", 1L, List.of(1L, 4L))));
    }

    @Test
    void payerMustBeAParticipant() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> expenseService.createExpense(
                1L, 10L, request("10.00", 1L, List.of(2L, 3L))));
    }

    @Test
    void needsAtLeastTwoDistinctParticipants() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> expenseService.createExpense(
                1L, 10L, request("10.00", 1L, List.of(1L, 1L))));
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void nonMemberCannotCreateOrViewExpenses() {
        assertStatus(HttpStatus.FORBIDDEN, () -> expenseService.createExpense(
                4L, 10L, request("10.00", 1L, List.of(1L, 2L))));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> expenseService.getGroupExpenses(4L, 10L));
    }

    @Test
    void otherMembersCanViewButNotEdit() {
        existingExpense(alice, false);

        ExpenseResponse response = expenseService.getExpense(2L, 100L);

        assertFalse(response.canEdit());
        assertStatus(HttpStatus.FORBIDDEN, () -> expenseService.updateExpense(
                2L, 100L, request("10.00", 2L, List.of(1L, 2L))));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> expenseService.deleteExpense(2L, 100L));
    }

    @Test
    void creatorCanEditPayerParticipantsAndAmount() {
        existingExpense(alice, false);

        ExpenseResponse response = expenseService.updateExpense(
                1L, 100L, request("45.10", 3L, List.of(1L, 3L)));

        assertEquals(new BigDecimal("45.10"), response.amount());
        assertEquals(3L, response.payer().id());
        assertEquals(List.of(1L, 3L),
                response.participants().stream().map(p -> p.id()).toList());
    }

    @Test
    void settledExpenseIsLocked() {
        existingExpense(alice, true);

        assertFalse(expenseService.getExpense(1L, 100L).canEdit());
        assertStatus(HttpStatus.CONFLICT, () -> expenseService.updateExpense(
                1L, 100L, request("10.00", 1L, List.of(1L, 2L))));
        assertStatus(HttpStatus.CONFLICT,
                () -> expenseService.deleteExpense(1L, 100L));
        verify(expenseRepository, never()).delete(any());
    }

    @Test
    void creatorCanDelete() {
        Expense expense = existingExpense(alice, false);

        expenseService.deleteExpense(1L, 100L);

        verify(expenseRepository).delete(expense);
    }

    // ---- Splitting

    @Test
    void expenseWithoutASplitMethodIsSplitEqually() {
        ExpenseResponse response = expenseService.createExpense(
                1L, 10L, request("12.50", 1L, List.of(1L, 2L)));

        assertEquals(SplitMethod.EQUAL, response.splitMethod());
        assertEquals(2, response.shares().size());
        response.shares().forEach(share ->
                assertEquals(new BigDecimal("6.25"), share.amount()));
    }

    @Test
    void equalSplitSharesAddUpToTheAmount() {
        ExpenseResponse response = expenseService.createExpense(
                1L, 10L, request("10.00", 1L, List.of(1L, 2L, 3L)));

        assertEquals(new BigDecimal("10.00"), total(response.shares()));
        assertEquals(1, response.shares().stream()
                .filter(share -> share.amount().equals(new BigDecimal("3.34")))
                .count());
    }

    @Test
    void sharesAreSavedWithTheExpense() {
        expenseService.createExpense(1L, 10L, splitRequest(
                "45.50", 2L, List.of(1L, 2L, 3L), SplitMethod.EXACT,
                Map.of(1L, new BigDecimal("20.00"),
                        2L, new BigDecimal("15.25"),
                        3L, new BigDecimal("10.25"))));

        Expense expense = savedExpense();

        assertEquals(SplitMethod.EXACT, expense.getSplitMethod());
        assertEquals(new BigDecimal("20.00"), expense.getShares().get(1L));
        assertEquals(new BigDecimal("15.25"), expense.getShares().get(2L));
        assertEquals(new BigDecimal("10.25"), expense.getShares().get(3L));
    }

    @Test
    void payerHasAShareAndIsTheOnlyOneMarkedAsPayer() {
        ExpenseResponse response = expenseService.createExpense(
                1L, 10L, splitRequest("30.00", 2L, List.of(1L, 2L, 3L),
                        SplitMethod.EQUAL, null));

        assertEquals(List.of("Bob"), response.shares().stream()
                .filter(ShareResponse::payer)
                .map(ShareResponse::name)
                .toList());
        assertEquals(3, response.shares().size());
    }

    @Test
    void percentageSplitSharesAddUpToTheAmount() {
        ExpenseResponse response = expenseService.createExpense(
                1L, 10L, splitRequest("10.00", 1L, List.of(1L, 2L, 3L),
                        SplitMethod.PERCENTAGE,
                        Map.of(1L, new BigDecimal("33.33"),
                                2L, new BigDecimal("33.33"),
                                3L, new BigDecimal("33.34"))));

        assertEquals(SplitMethod.PERCENTAGE, response.splitMethod());
        assertEquals(new BigDecimal("10.00"), total(response.shares()));
    }

    @Test
    void invalidSplitsAreRejectedAndNothingIsSaved() {
        List<Long> people = List.of(1L, 2L);

        // exact amounts that do not add up to the total
        assertStatus(HttpStatus.BAD_REQUEST, () -> expenseService.createExpense(
                1L, 10L, splitRequest("20.00", 1L, people, SplitMethod.EXACT,
                        Map.of(1L, new BigDecimal("10.00"),
                                2L, new BigDecimal("9.99")))));
        // exact amount left blank
        assertStatus(HttpStatus.BAD_REQUEST, () -> expenseService.createExpense(
                1L, 10L, splitRequest("20.00", 1L, people, SplitMethod.EXACT,
                        Map.of(1L, new BigDecimal("20.00")))));
        // negative exact amount
        assertStatus(HttpStatus.BAD_REQUEST, () -> expenseService.createExpense(
                1L, 10L, splitRequest("20.00", 1L, people, SplitMethod.EXACT,
                        Map.of(1L, new BigDecimal("30.00"),
                                2L, new BigDecimal("-10.00")))));
        // exact split with no amounts at all
        assertStatus(HttpStatus.BAD_REQUEST, () -> expenseService.createExpense(
                1L, 10L, splitRequest("20.00", 1L, people, SplitMethod.EXACT, null)));
        // percentages that do not add up to 100
        assertStatus(HttpStatus.BAD_REQUEST, () -> expenseService.createExpense(
                1L, 10L, splitRequest("20.00", 1L, people, SplitMethod.PERCENTAGE,
                        Map.of(1L, new BigDecimal("50"),
                                2L, new BigDecimal("49")))));
        // negative percentage
        assertStatus(HttpStatus.BAD_REQUEST, () -> expenseService.createExpense(
                1L, 10L, splitRequest("20.00", 1L, people, SplitMethod.PERCENTAGE,
                        Map.of(1L, new BigDecimal("150"),
                                2L, new BigDecimal("-50")))));

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void editingAnExpenseRecalculatesItsShares() {
        Expense expense = existingExpense(alice, false);
        expense.setShares(Map.of(1L, new BigDecimal("450.00"),
                2L, new BigDecimal("450.00")));

        ExpenseResponse response = expenseService.updateExpense(
                1L, 100L, request("45.10", 3L, List.of(1L, 3L)));

        assertEquals(Set.of(1L, 3L), expense.getShares().keySet());
        assertEquals(new BigDecimal("45.10"), total(response.shares()));
        assertEquals(List.of("Alice", "Carol"), response.shares().stream()
                .map(ShareResponse::name)
                .toList());
    }

    @Test
    void sameInputsGiveTheSameShares() {
        ExpenseResponse first = expenseService.createExpense(
                1L, 10L, request("10.00", 1L, List.of(1L, 2L, 3L)));
        ExpenseResponse second = expenseService.createExpense(
                1L, 10L, request("10.00", 1L, List.of(1L, 2L, 3L)));

        assertEquals(first.shares(), second.shares());
    }

    @Test
    void expenseWithoutSavedSharesCanStillBeViewed() {
        existingExpense(alice, false);

        ExpenseResponse response = expenseService.getExpense(2L, 100L);

        assertTrue(response.shares().isEmpty());
        assertEquals(SplitMethod.EQUAL, response.splitMethod());
    }

    // ---- Share preview

    @Test
    void previewShowsEachPersonsShareWithoutSaving() {
        SharePreviewResponse preview = expenseService.previewShares(
                1L, 10L, new SplitPreviewRequest(new BigDecimal("30.00"), 1L,
                        List.of(1L, 2L, 3L), null, null));

        assertEquals(SplitMethod.EQUAL, preview.splitMethod());
        assertEquals(3, preview.shares().size());
        assertEquals(new BigDecimal("30.00"), total(preview.shares()));
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void previewMatchesWhatIsSavedAfterwards() {
        SharePreviewResponse preview = expenseService.previewShares(
                1L, 10L, new SplitPreviewRequest(new BigDecimal("10.00"), 1L,
                        List.of(1L, 2L, 3L), null, null));

        ExpenseResponse saved = expenseService.createExpense(
                1L, 10L, request("10.00", 1L, List.of(1L, 2L, 3L)));

        assertEquals(preview.shares(), saved.shares());
    }

    @Test
    void previewIsOnlyForGroupMembers() {
        assertStatus(HttpStatus.FORBIDDEN, () -> expenseService.previewShares(
                4L, 10L, new SplitPreviewRequest(new BigDecimal("10.00"), 1L,
                        List.of(1L, 2L), null, null)));
    }

    @Test
    void previewRejectsAnInvalidSplit() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> expenseService.previewShares(
                1L, 10L, new SplitPreviewRequest(new BigDecimal("10.00"), 1L,
                        List.of(1L, 2L), SplitMethod.EXACT,
                        Map.of(1L, new BigDecimal("1.00"),
                                2L, new BigDecimal("1.00")))));
    }
}
