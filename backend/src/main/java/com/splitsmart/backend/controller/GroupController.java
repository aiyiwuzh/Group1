
package com.splitsmart.backend.controller;

import com.splitsmart.backend.dto.*;
import com.splitsmart.backend.service.GroupService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    private Long currentUserId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateGroupRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(groupService.createGroup(
                        currentUserId(jwt), request));
    }

    @GetMapping
    public List<GroupResponse> getMyGroups(
            @AuthenticationPrincipal Jwt jwt) {

        return groupService.getMyGroups(currentUserId(jwt));
    }

    @GetMapping("/{id}")
    public GroupResponse getGroup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        return groupService.getGroup(currentUserId(jwt), id);
    }

    @PostMapping("/{id}/members")
    public GroupResponse addMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody AddMemberRequest request) {

        return groupService.addMember(
                currentUserId(jwt), id, request);
    }

    @PutMapping("/{id}")
    public GroupResponse updateGroup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody UpdateGroupRequest request) {

        return groupService.updateGroup(
                currentUserId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        groupService.deleteGroup(currentUserId(jwt), id);

        return ResponseEntity.noContent().build();
    }
}
