package com.splitsmart.backend.controller;

import com.splitsmart.backend.dto.HistoryResponse;
import com.splitsmart.backend.dto.MonthlySummaryResponse;
import com.splitsmart.backend.service.HistoryService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Transaction history and monthly summaries. Group members only.
@RestController
@RequestMapping("/api/groups/{groupId}")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    private Long currentUserId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    // GET /api/groups/{groupId}/history?q=&type=&category=&memberId=&sort=
    @GetMapping("/history")
    public HistoryResponse getHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("groupId") Long groupId,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "memberId", required = false) Long memberId,
            @RequestParam(name = "sort", required = false) String sort) {

        return historyService.getHistory(
                currentUserId(jwt), groupId, keyword, type, category, memberId, sort);
    }

    // GET /api/groups/{groupId}/summary/monthly?month=2026-11&timeZone=America/Los_Angeles
    @GetMapping("/summary/monthly")
    public MonthlySummaryResponse getMonthlySummary(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("groupId") Long groupId,
            @RequestParam(name = "month", required = false) String month,
            @RequestParam(name = "timeZone", required = false) String timeZone) {

        return historyService.getMonthlySummary(
                currentUserId(jwt), groupId, month, timeZone);
    }
}
