package com.splitsmart.backend.service;

import com.splitsmart.backend.entity.Expense;
import com.splitsmart.backend.entity.ExpenseReceipt;
import com.splitsmart.backend.repository.ExpenseReceiptRepository;
import com.splitsmart.backend.repository.ExpenseRepository;
import com.splitsmart.backend.repository.MembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExpenseReceiptService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final ExpenseRepository expenseRepository;
    private final ExpenseReceiptRepository receiptRepository;
    private final MembershipRepository membershipRepository;

    public ExpenseReceiptService(ExpenseRepository expenseRepository,
            ExpenseReceiptRepository receiptRepository,
            MembershipRepository membershipRepository) {
        this.expenseRepository = expenseRepository;
        this.receiptRepository = receiptRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public void save(Long userId, Long expenseId, String contentType, byte[] bytes) {
        Expense expense = requireExpense(expenseId, userId);
        if (!expense.getCreator().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the expense creator can add a receipt photo");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Receipt photo must be between 1 byte and 5 MB");
        }
        if (contentType == null || !(contentType.equals("image/jpeg")
                || contentType.equals("image/png") || contentType.equals("image/webp"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use a JPEG, PNG, or WebP image");
        }
        receiptRepository.save(new ExpenseReceipt(expense, contentType, bytes));
    }

    @Transactional(readOnly = true)
    public ExpenseReceipt get(Long userId, Long expenseId) {
        requireExpense(expenseId, userId);
        return receiptRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No receipt photo is attached"));
    }

    private Expense requireExpense(Long expenseId, Long userId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Expense not found"));
        if (!membershipRepository.existsByGroupIdAndUserId(
                expense.getGroup().getId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not a member of this group");
        }
        return expense;
    }
}
