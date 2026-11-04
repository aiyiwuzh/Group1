package com.splitsmart.backend.controller;

import com.splitsmart.backend.dto.BalanceSummaryResponse;
import com.splitsmart.backend.dto.SuggestedPaymentResponse;
import com.splitsmart.backend.service.BalanceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/balances")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping
    public BalanceSummaryResponse getBalances(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId) {
        return balanceService.getBalances(currentUserId(jwt), groupId);
    }

    @GetMapping("/suggestions")
    public List<SuggestedPaymentResponse> getSuggestedPayments(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId) {
        return balanceService.getSuggestedPayments(currentUserId(jwt), groupId);
    }

    private Long currentUserId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
