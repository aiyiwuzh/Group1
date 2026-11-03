package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.BalanceSummaryResponse;
import com.splitsmart.backend.dto.MemberResponse;
import com.splitsmart.backend.dto.PairwiseBalanceResponse;
import com.splitsmart.backend.entity.Expense;
import com.splitsmart.backend.entity.Membership;
import com.splitsmart.backend.entity.Settlement;
import com.splitsmart.backend.entity.User;
import com.splitsmart.backend.repository.ExpenseRepository;
import com.splitsmart.backend.repository.GroupRepository;
import com.splitsmart.backend.repository.MembershipRepository;
import com.splitsmart.backend.repository.SettlementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BalanceService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;

    public BalanceService(
            ExpenseRepository expenseRepository,
            SettlementRepository settlementRepository,
            GroupRepository groupRepository,
            MembershipRepository membershipRepository) {
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
    }

    private record PairKey(Long lowerId, Long higherId) {
        static PairKey of(Long first, Long second) {
            return first < second
                    ? new PairKey(first, second)
                    : new PairKey(second, first);
        }
    }

    private void requireGroup(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        }
    }

    private void requireMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
    }

    @Transactional(readOnly = true)
    public BalanceSummaryResponse getBalances(Long userId, Long groupId) {
        requireGroup(groupId);
        requireMember(groupId, userId);

        Map<PairKey, BigDecimal> netDebts = calculateNetDebts(groupId, null);
        List<User> members = membershipRepository.findByGroupId(groupId)
                .stream()
                .map(Membership::getUser)
                .filter(member -> !member.getId().equals(userId))
                .sorted(Comparator.comparing(User::getName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        BigDecimal totalOwed = ZERO;
        BigDecimal totalReceivable = ZERO;
        List<PairwiseBalanceResponse> balances = new ArrayList<>();

        for (User member : members) {
            BigDecimal signed = netDebts.getOrDefault(
                    PairKey.of(userId, member.getId()), ZERO);
            boolean userIsLowerId = userId < member.getId();
            BigDecimal userOwes = userIsLowerId ? signed : signed.negate();

            String status;
            BigDecimal amount;
            if (userOwes.compareTo(ZERO) > 0) {
                status = "YOU_OWE";
                amount = userOwes;
                totalOwed = totalOwed.add(amount);
            } else if (userOwes.compareTo(ZERO) < 0) {
                status = "OWES_YOU";
                amount = userOwes.negate();
                totalReceivable = totalReceivable.add(amount);
            } else {
                status = "SETTLED";
                amount = ZERO;
            }

            balances.add(new PairwiseBalanceResponse(
                    member.getId(), member.getName(), status, amount.setScale(2)));
        }

        return new BalanceSummaryResponse(
                groupId, userId, totalOwed.setScale(2),
                totalReceivable.setScale(2), List.copyOf(balances));
    }

    // Returns the positive amount debtor currently owes creditor. Reciprocal
    // claims for the same pair are netted; debts across different pairs are not.
    @Transactional(readOnly = true)
    public BigDecimal getOutstandingDebt(
            Long groupId, Long debtorId, Long creditorId, Long excludedSettlementId) {
        if (debtorId.equals(creditorId)) {
            return ZERO;
        }
        BigDecimal signed = calculateNetDebts(groupId, excludedSettlementId)
                .getOrDefault(PairKey.of(debtorId, creditorId), ZERO);
        BigDecimal directed = debtorId < creditorId ? signed : signed.negate();
        return directed.compareTo(ZERO) > 0 ? directed.setScale(2) : ZERO;
    }

    private Map<PairKey, BigDecimal> calculateNetDebts(
            Long groupId, Long excludedSettlementId) {
        Map<PairKey, BigDecimal> net = new HashMap<>();

        for (Expense expense : expenseRepository.findAllByGroupId(groupId)) {
            Long payerId = expense.getPayer().getId();
            for (Map.Entry<Long, BigDecimal> share : expense.getShares().entrySet()) {
                if (!share.getKey().equals(payerId)) {
                    addDirectedAmount(net, share.getKey(), payerId, share.getValue());
                }
            }
        }

        for (Settlement settlement : settlementRepository.findAllByGroupId(groupId)) {
            if (excludedSettlementId != null
                    && excludedSettlementId.equals(settlement.getId())) {
                continue;
            }
            // A settlement reduces the debt from payer to payee.
            addDirectedAmount(net, settlement.getPayer().getId(),
                    settlement.getPayee().getId(), settlement.getAmount().negate());
        }

        return net;
    }

    // Positive values mean debtor owes creditor; a negative amount can be
    // used by a settlement to reduce that debt.
    private static void addDirectedAmount(
            Map<PairKey, BigDecimal> net, Long debtorId, Long creditorId,
            BigDecimal amount) {
        if (debtorId.equals(creditorId) || amount == null) {
            return;
        }
        PairKey pair = PairKey.of(debtorId, creditorId);
        BigDecimal signedAmount = debtorId < creditorId ? amount : amount.negate();
        net.merge(pair, signedAmount, BigDecimal::add);
    }
}
