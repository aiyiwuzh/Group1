package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.MemberResponse;
import com.splitsmart.backend.dto.SettlementRequest;
import com.splitsmart.backend.dto.SettlementResponse;
import com.splitsmart.backend.entity.Group;
import com.splitsmart.backend.entity.Settlement;
import com.splitsmart.backend.entity.User;
import com.splitsmart.backend.repository.GroupRepository;
import com.splitsmart.backend.repository.MembershipRepository;
import com.splitsmart.backend.repository.SettlementRepository;
import com.splitsmart.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final BalanceService balanceService;

    public SettlementService(
            SettlementRepository settlementRepository,
            GroupRepository groupRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            BalanceService balanceService) {
        this.settlementRepository = settlementRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.balanceService = balanceService;
    }

    private Group requireGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Group not found"));
    }

    private Settlement requireSettlement(Long settlementId) {
        return settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Settlement not found"));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private User requireGroupMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
        return requireUser(userId);
    }

    private User requirePayee(Long groupId, Long payerId, Long payeeId) {
        if (payerId.equals(payeeId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "You cannot record a payment to yourself");
        }
        return requireGroupMember(groupId, payeeId);
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Settlement amount must be greater than zero");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Settlement amount may have at most two decimal places");
        }
    }

    private void requireAmountWithinDebt(
            Long groupId, Long payerId, Long payeeId,
            BigDecimal amount, Long excludedSettlementId) {
        BigDecimal amountOwed = balanceService.getOutstandingDebt(
                groupId, payerId, payeeId, excludedSettlementId);
        if (amount.compareTo(amountOwed) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Settlement amount cannot exceed the amount currently owed");
        }
    }

    private static SettlementResponse toResponse(
            Settlement settlement, Long currentUserId) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getGroup().getId(),
                toMember(settlement.getPayer()),
                toMember(settlement.getPayee()),
                settlement.getAmount(),
                settlement.getCreatedAt(),
                settlement.getUpdatedAt(),
                settlement.getPayer().getId().equals(currentUserId));
    }

    private static MemberResponse toMember(User user) {
        return new MemberResponse(user.getId(), user.getName(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> getGroupSettlements(Long userId, Long groupId) {
        requireGroup(groupId);
        requireGroupMember(groupId, userId);
        return settlementRepository.findByGroupIdOrderByCreatedAtDescIdDesc(groupId)
                .stream()
                .map(settlement -> toResponse(settlement, userId))
                .toList();
    }

    @Transactional
    public SettlementResponse createSettlement(
            Long userId, Long groupId, SettlementRequest request) {
        Group group = requireGroup(groupId);
        User payer = requireGroupMember(groupId, userId);
        User payee = requirePayee(groupId, userId, request.payeeId());
        BigDecimal amount = normalizeAmount(request.amount());
        requireAmountWithinDebt(groupId, userId, payee.getId(), amount, null);

        Settlement settlement = settlementRepository.save(
                new Settlement(group, payer, payee, amount));
        return toResponse(settlement, userId);
    }

    @Transactional
    public SettlementResponse updateSettlement(
            Long userId, Long settlementId, SettlementRequest request) {
        Settlement settlement = requireSettlement(settlementId);
        Long groupId = settlement.getGroup().getId();
        requireGroupMember(groupId, userId);
        requirePayer(userId, settlement);

        User payee = requirePayee(groupId, userId, request.payeeId());
        BigDecimal amount = normalizeAmount(request.amount());
        requireAmountWithinDebt(
                groupId, userId, payee.getId(), amount, settlementId);

        settlement.setPayee(payee);
        settlement.setAmount(amount);
        return toResponse(settlementRepository.saveAndFlush(settlement), userId);
    }

    @Transactional
    public void deleteSettlement(Long userId, Long settlementId) {
        Settlement settlement = requireSettlement(settlementId);
        requireGroupMember(settlement.getGroup().getId(), userId);
        requirePayer(userId, settlement);
        settlementRepository.delete(settlement);
    }

    private static void requirePayer(Long userId, Settlement settlement) {
        if (!settlement.getPayer().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the member who recorded this settlement can change it");
        }
    }
}
