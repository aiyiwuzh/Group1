
package com.splitsmart.backend.controller;

import com.splitsmart.backend.dto.ExpenseRequest;
import com.splitsmart.backend.dto.ExpenseResponse;
import com.splitsmart.backend.dto.SharePreviewResponse;
import com.splitsmart.backend.dto.SplitPreviewRequest;
import com.splitsmart.backend.service.ExpenseService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    private Long currentUserId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    @PostMapping("/groups/{groupId}/expenses")
    public ResponseEntity<ExpenseResponse> createExpense(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId,
            @Valid @RequestBody ExpenseRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(expenseService.createExpense(
                        currentUserId(jwt), groupId, request));
    }

    // Shows each person's share before the expense is saved.
    @PostMapping("/groups/{groupId}/expenses/preview")
    public SharePreviewResponse previewShares(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId,
            @Valid @RequestBody SplitPreviewRequest request) {

        return expenseService.previewShares(currentUserId(jwt), groupId, request);
    }

    @GetMapping("/groups/{groupId}/expenses")
    public List<ExpenseResponse> getGroupExpenses(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId) {

        return expenseService.getGroupExpenses(currentUserId(jwt), groupId);
    }

    @GetMapping("/expenses/{id}")
    public ExpenseResponse getExpense(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        return expenseService.getExpense(currentUserId(jwt), id);
    }

    @PutMapping("/expenses/{id}")
    public ExpenseResponse updateExpense(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody ExpenseRequest request) {

        return expenseService.updateExpense(currentUserId(jwt), id, request);
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> deleteExpense(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        expenseService.deleteExpense(currentUserId(jwt), id);

        return ResponseEntity.noContent().build();
    }
}
