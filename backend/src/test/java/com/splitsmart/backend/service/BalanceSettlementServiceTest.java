package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.BalanceSummaryResponse;
import com.splitsmart.backend.dto.PairwiseBalanceResponse;
import com.splitsmart.backend.dto.SettlementRequest;
import com.splitsmart.backend.dto.SettlementResponse;
import com.splitsmart.backend.dto.SuggestedPaymentResponse;
import com.splitsmart.backend.entity.Expense;
import com.splitsmart.backend.entity.Group;
import com.splitsmart.backend.entity.Membership;
import com.splitsmart.backend.entity.Settlement;
import com.splitsmart.backend.entity.User;
import com.splitsmart.backend.repository.ExpenseRepository;
import com.splitsmart.backend.repository.GroupRepository;
import com.splitsmart.backend.repository.MembershipRepository;
import com.splitsmart.backend.repository.SettlementRepository;
import com.splitsmart.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** White-box tests for Task 4 balances and recorded settlements. */
class BalanceSettlementServiceTest {

    private static final long GROUP_ID = 10L;
    private static final long ALICE_ID = 1L;
    private static final long BOB_ID = 2L;
    private static final long CAROL_ID = 3L;

    private ExpenseRepository expenseRepository;
    private SettlementRepository settlementRepository;
    private GroupRepository groupRepository;
    private MembershipRepository membershipRepository;
    private UserRepository userRepository;
    private BalanceService balanceService;
    private SettlementService settlementService;

    private User alice;
    private User bob;
    private User carol;
    private Group group;
    private final List<Expense> expenses = new ArrayList<>();
    private final List<Settlement> settlements = new ArrayList<>();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static User user(long id, String name) {
        User user = new User(name, name.toLowerCase() + "@example.com",
                String.format("%010d", id), "test-hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @BeforeEach
    void setUp() {
        alice = user(ALICE_ID, "Alice User");
        bob = user(BOB_ID, "Bob User");
        carol = user(CAROL_ID, "Carol User");
        group = new Group("Test group", null, alice);
        ReflectionTestUtils.setField(group, "id", GROUP_ID);

        expenseRepository = mock(ExpenseRepository.class);
        settlementRepository = mock(SettlementRepository.class);
        groupRepository = mock(GroupRepository.class);
        membershipRepository = mock(MembershipRepository.class);
        userRepository = mock(UserRepository.class);

        when(groupRepository.existsById(GROUP_ID)).thenReturn(true);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(expenseRepository.findAllByGroupId(GROUP_ID)).thenAnswer(inv -> expenses);
        when(settlementRepository.findAllByGroupId(GROUP_ID)).thenAnswer(inv -> settlements);
        when(membershipRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(
                new Membership(group, alice), new Membership(group, bob),
                new Membership(group, carol)));
        when(membershipRepository.existsByGroupIdAndUserId(GROUP_ID, ALICE_ID))
                .thenReturn(true);
        when(membershipRepository.existsByGroupIdAndUserId(GROUP_ID, BOB_ID))
                .thenReturn(true);
        when(membershipRepository.existsByGroupIdAndUserId(GROUP_ID, CAROL_ID))
                .thenReturn(true);
        when(userRepository.findById(ALICE_ID)).thenReturn(Optional.of(alice));
        when(userRepository.findById(BOB_ID)).thenReturn(Optional.of(bob));
        when(userRepository.findById(CAROL_ID)).thenReturn(Optional.of(carol));
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> {
            Settlement saved = inv.getArgument(0);
            settlements.add(saved);
            return saved;
        });

        balanceService = new BalanceService(expenseRepository, settlementRepository,
                groupRepository, membershipRepository);
        settlementService = new SettlementService(settlementRepository, groupRepository,
                membershipRepository, userRepository, balanceService);
    }

    private void addExpense(User payer, String total, Object... userIdAndShare) {
        Expense expense = new Expense(group, payer);
        expense.setPayer(payer);
        expense.setAmount(d(total));
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        for (int i = 0; i < userIdAndShare.length; i += 2) {
            shares.put((Long) userIdAndShare[i], d((String) userIdAndShare[i + 1]));
        }
        expense.setShares(shares);
        expenses.add(expense);
    }

    /** Sample group ledger used to test both direct balances and payment simplification. */
    private void addTask4Ledger() {
        // Percentage supplies: Alice paid; Bob owes $9 and Carol owes $15.
        addExpense(alice, "30.00", ALICE_ID, "6.00", BOB_ID, "9.00", CAROL_ID, "15.00");
        // Exact tickets, rounding snacks, coffee, and Uber were paid by Bob.
        addExpense(bob, "30.00", ALICE_ID, "5.00", BOB_ID, "10.00", CAROL_ID, "15.00");
        addExpense(bob, "10.00", ALICE_ID, "3.33", BOB_ID, "3.34", CAROL_ID, "3.33");
        // Equal dinner was paid by Carol.
        addExpense(carol, "30.00", ALICE_ID, "10.00", BOB_ID, "10.00", CAROL_ID, "10.00");
        addExpense(bob, "10.00", BOB_ID, "5.00", CAROL_ID, "5.00");
        addExpense(bob, "129.00", ALICE_ID, "43.00", BOB_ID, "43.00", CAROL_ID, "43.00");
    }

    @Test
    void balanceSummaryNetsReciprocalClaimsForEachMemberPair() {
        addTask4Ledger();

        BalanceSummaryResponse summary = balanceService.getBalances(BOB_ID, GROUP_ID);

        assertEquals(d("0.00"), summary.totalOwed());
        assertEquals(d("98.66"), summary.totalReceivable());
        PairwiseBalanceResponse aliceBalance = summary.balances().stream()
                .filter(row -> row.memberId().equals(ALICE_ID)).findFirst().orElseThrow();
        PairwiseBalanceResponse carolBalance = summary.balances().stream()
                .filter(row -> row.memberId().equals(CAROL_ID)).findFirst().orElseThrow();
        assertEquals("OWES_YOU", aliceBalance.status());
        assertEquals(d("42.33"), aliceBalance.amount());
        assertEquals("OWES_YOU", carolBalance.status());
        assertEquals(d("56.33"), carolBalance.amount());
    }

    @Test
    void suggestedPaymentsNetDebtsAcrossTheWholeGroup() {
        addTask4Ledger();

        List<SuggestedPaymentResponse> plan =
                balanceService.getSuggestedPayments(BOB_ID, GROUP_ID);

        assertEquals(2, plan.size());
        assertEquals("Alice User", plan.get(0).payerName());
        assertEquals("Bob User", plan.get(0).payeeName());
        assertEquals(d("37.33"), plan.get(0).amount());
        assertEquals("Carol User", plan.get(1).payerName());
        assertEquals("Bob User", plan.get(1).payeeName());
        assertEquals(d("61.33"), plan.get(1).amount());
        assertEquals(d("98.66"), plan.get(0).amount().add(plan.get(1).amount()));
    }

    @Test
    void partialSettlementIsSavedAndReducesTheRemainingBalance() {
        addExpense(bob, "20.00", ALICE_ID, "20.00", BOB_ID, "0.00");

        SettlementResponse response = settlementService.createSettlement(ALICE_ID,
                GROUP_ID, new SettlementRequest(ALICE_ID, BOB_ID, d("5.00"), "  paid part  "));

        assertEquals(ALICE_ID, response.payer().id());
        assertEquals(BOB_ID, response.payee().id());
        assertEquals(d("5.00"), response.amount());
        assertEquals("paid part", response.note());
        assertEquals(d("15.00"), balanceService.getOutstandingDebt(
                GROUP_ID, ALICE_ID, BOB_ID, null));
        verify(settlementRepository).save(any(Settlement.class));
    }

    @Test
    void settlementCannotExceedTheCurrentPairwiseDebt() {
        addExpense(bob, "20.00", ALICE_ID, "20.00", BOB_ID, "0.00");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> settlementService.createSettlement(ALICE_ID, GROUP_ID,
                        new SettlementRequest(ALICE_ID, BOB_ID, d("20.01"), null)));

        assertEquals(400, error.getStatusCode().value());
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void settlementCannotPayYourself() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> settlementService.createSettlement(ALICE_ID, GROUP_ID,
                        new SettlementRequest(ALICE_ID, ALICE_ID, d("1.00"), null)));

        assertEquals(400, error.getStatusCode().value());
        verify(settlementRepository, never()).save(any());
    }
}
