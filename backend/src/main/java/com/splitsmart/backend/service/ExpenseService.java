
package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.ExpenseRequest;
import com.splitsmart.backend.dto.ExpenseResponse;
import com.splitsmart.backend.dto.MemberResponse;
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

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    static final int MIN_PARTICIPANTS = 2;

    private final ExpenseRepository expenseRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final SplitCalculator splitCalculator = new SplitCalculator();

    public ExpenseService(
            ExpenseRepository expenseRepository,
            GroupRepository groupRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository) {

        this.expenseRepository = expenseRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Group requireGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Group not found"));
    }

    private Expense requireExpense(Long expenseId) {
        return expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Expense not found"));
    }

    private void requireMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
    }

    // Only the creator can edit or delete; settled expenses are locked.
    private void requireEditable(Expense expense, Long userId) {
        if (!expense.getCreator().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the expense creator can edit or delete this expense");
        }

        if (expense.isSettled()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This expense is covered by a settlement and cannot be changed");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    // The payer, participants and shares worked out from a request.
    private record ResolvedSplit(
            User payer,
            Set<User> participants,
            SplitMethod splitMethod,
            Map<Long, BigDecimal> shares) {
    }

    // Validates the payer and participants against the group, then works out
    // each participant's share. Nothing is saved here.
    private ResolvedSplit resolve(
            Long groupId,
            BigDecimal amount,
            Long payerId,
            List<Long> participantIds,
            SplitMethod requestedMethod,
            Map<Long, BigDecimal> splitValues) {

        Map<Long, User> members =
                membershipRepository.findByGroupId(groupId)
                        .stream()
                        .map(Membership::getUser)
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        User payer = members.get(payerId);
        if (payer == null) {
            throw badRequest("Payer must be a member of this group");
        }

        Set<User> participants = new LinkedHashSet<>();
        for (Long participantId : participantIds) {
            User participant = participantId == null ? null : members.get(participantId);
            if (participant == null) {
                throw badRequest("Every participant must be a member of this group");
            }
            participants.add(participant);
        }

        if (!participants.contains(payer)) {
            throw badRequest("The payer must be included as a participant");
        }

        if (participants.size() < MIN_PARTICIPANTS) {
            throw badRequest("An expense needs at least "
                    + MIN_PARTICIPANTS + " participants, including the payer");
        }

        SplitMethod method = requestedMethod == null
                ? SplitMethod.EQUAL : requestedMethod;

        try {
            Map<Long, BigDecimal> shares = splitCalculator.calculateShares(
                    amount,
                    method,
                    payer.getId(),
                    participants.stream().map(User::getId).toList(),
                    splitValues);

            return new ResolvedSplit(payer, participants, method, shares);
        } catch (InvalidSplitException e) {
            throw badRequest(e.getMessage());
        }
    }

    // Validates the request against the group and copies it onto the expense.
    private void apply(Expense expense, ExpenseRequest request) {
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);

        ResolvedSplit split = resolve(
                expense.getGroup().getId(),
                amount,
                request.payerId(),
                request.participantIds(),
                request.splitMethod(),
                request.splitValues());

        String note = request.note() == null ? null : request.note().trim();

        expense.setTitle(request.title().trim());
        expense.setAmount(amount);
        expense.setCategory(request.category() == null
                ? ExpenseCategory.OTHER : request.category());
        expense.setPayer(split.payer());
        expense.setParticipants(split.participants());
        expense.setSplitMethod(split.splitMethod());
        expense.setShares(split.shares());
        expense.setNote(note == null || note.isEmpty() ? null : note);
    }

    private static MemberResponse toMember(User user) {
        return new MemberResponse(user.getId(), user.getName(), user.getEmail());
    }

    // One row per participant, in the same name order as the participant list.
    private static List<ShareResponse> toShares(
            Map<Long, BigDecimal> shares,
            Collection<User> participants,
            Long payerId) {

        return participants.stream()
                .filter(user -> shares.containsKey(user.getId()))
                .sorted(Comparator.comparing(User::getName,
                        String.CASE_INSENSITIVE_ORDER))
                .map(user -> new ShareResponse(
                        user.getId(),
                        user.getName(),
                        shares.get(user.getId()),
                        user.getId().equals(payerId)))
                .toList();
    }

    private ExpenseResponse toResponse(Expense expense, Long userId) {
        List<MemberResponse> participants = expense.getParticipants()
                .stream()
                .sorted(Comparator.comparing(User::getName,
                        String.CASE_INSENSITIVE_ORDER))
                .map(ExpenseService::toMember)
                .toList();

        boolean canEdit = expense.getCreator().getId().equals(userId)
                && !expense.isSettled();

        return new ExpenseResponse(
                expense.getId(),
                expense.getGroup().getId(),
                expense.getTitle(),
                expense.getAmount(),
                expense.getCategory(),
                toMember(expense.getPayer()),
                toMember(expense.getCreator()),
                participants,
                expense.getSplitMethod(),
                toShares(expense.getShares(), expense.getParticipants(),
                        expense.getPayer().getId()),
                expense.getNote(),
                expense.getCreatedAt(),
                expense.getUpdatedAt(),
                expense.isSettled(),
                canEdit);
    }

    @Transactional
    public ExpenseResponse createExpense(
            Long userId, Long groupId, ExpenseRequest request) {

        User creator = requireUser(userId);
        Group group = requireGroup(groupId);
        requireMember(groupId, userId);

        // The creator may record an expense that someone else paid for.
        Expense expense = new Expense(group, creator);
        apply(expense, request);

        return toResponse(expenseRepository.save(expense), userId);
    }

    // Shows what each person would owe, so the user can check it before
    // saving. Nothing is saved, and it uses the same rules as createExpense.
    @Transactional(readOnly = true)
    public SharePreviewResponse previewShares(
            Long userId, Long groupId, SplitPreviewRequest request) {

        requireGroup(groupId);
        requireMember(groupId, userId);

        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);

        ResolvedSplit split = resolve(
                groupId,
                amount,
                request.payerId(),
                request.participantIds(),
                request.splitMethod(),
                request.splitValues());

        return new SharePreviewResponse(
                amount,
                split.splitMethod(),
                toShares(split.shares(), split.participants(),
                        split.payer().getId()));
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getGroupExpenses(Long userId, Long groupId) {
        requireGroup(groupId);
        requireMember(groupId, userId);

        return expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(groupId)
                .stream()
                .map(expense -> toResponse(expense, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpense(Long userId, Long expenseId) {
        Expense expense = requireExpense(expenseId);
        requireMember(expense.getGroup().getId(), userId);

        return toResponse(expense, userId);
    }

    @Transactional
    public ExpenseResponse updateExpense(
            Long userId, Long expenseId, ExpenseRequest request) {

        Expense expense = requireExpense(expenseId);
        requireMember(expense.getGroup().getId(), userId);
        requireEditable(expense, userId);

        // Payer, participants and amount can change; the creation date cannot.
        apply(expense, request);

        return toResponse(expenseRepository.saveAndFlush(expense), userId);
    }

    @Transactional
    public void deleteExpense(Long userId, Long expenseId) {
        Expense expense = requireExpense(expenseId);
        requireMember(expense.getGroup().getId(), userId);
        requireEditable(expense, userId);

        expenseRepository.delete(expense);
    }
}
