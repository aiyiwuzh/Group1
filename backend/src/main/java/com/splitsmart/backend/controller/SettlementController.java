package com.splitsmart.backend.controller;

import com.splitsmart.backend.dto.SettlementRequest;
import com.splitsmart.backend.dto.SettlementResponse;
import com.splitsmart.backend.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/groups/{groupId}/settlements")
    public List<SettlementResponse> getGroupSettlements(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId) {
        return settlementService.getGroupSettlements(currentUserId(jwt), groupId);
    }

    @PostMapping("/groups/{groupId}/settlements")
    public ResponseEntity<SettlementResponse> createSettlement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId,
            @Valid @RequestBody SettlementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(settlementService.createSettlement(
                        currentUserId(jwt), groupId, request));
    }

    @PutMapping("/settlements/{id}")
    public SettlementResponse updateSettlement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody SettlementRequest request) {
        return settlementService.updateSettlement(
                currentUserId(jwt), id, request);
    }

    @DeleteMapping("/settlements/{id}")
    public ResponseEntity<Void> deleteSettlement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        settlementService.deleteSettlement(currentUserId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
