
package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.*;
import com.splitsmart.backend.entity.Group;
import com.splitsmart.backend.entity.Membership;
import com.splitsmart.backend.entity.User;
import com.splitsmart.backend.repository.GroupRepository;
import com.splitsmart.backend.repository.MembershipRepository;
import com.splitsmart.backend.repository.UserRepository;
import com.splitsmart.backend.service.BalanceService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final BalanceService balanceService;

    public GroupService(
            GroupRepository groupRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            BalanceService balanceService) {

        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.balanceService = balanceService;
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

    private void requireOwner(Group group, Long userId) {
        if (!group.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only the group owner can perform this action");
        }
    }

    private void requireMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
    }

    private GroupResponse toResponse(Group group) {
        List<MemberResponse> members =
                membershipRepository.findByGroupId(group.getId())
                        .stream()
                        .map(m -> new MemberResponse(
                                m.getUser().getId(),
                                m.getUser().getName(),
                                m.getUser().getEmail()))
                        .toList();

        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getOwner().getId(),
                members);
    }

    @Transactional
    public GroupResponse createGroup(Long userId, CreateGroupRequest request) {
        User owner = requireUser(userId);

        Group group = groupRepository.save(
                new Group(request.name().trim(), request.description(), owner));

        membershipRepository.save(new Membership(group, owner));

        return toResponse(group);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups(Long userId) {
        requireUser(userId);

        return membershipRepository.findByUserId(userId)
                .stream()
                .map(m -> toResponse(m.getGroup()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(Long userId, Long groupId) {
        Group group = requireGroup(groupId);
        requireMember(groupId, userId);

        return toResponse(group);
    }

    @Transactional
    public GroupResponse addMember(
            Long userId, Long groupId, AddMemberRequest request) {

        Group group = requireGroup(groupId);
        requireOwner(group, userId);

        String email = request.email().trim().toLowerCase(Locale.ROOT);

        User newMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Registered user not found"));

        if (membershipRepository.existsByGroupIdAndUserId(
                groupId, newMember.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "User is already a group member");
        }

        membershipRepository.save(new Membership(group, newMember));

        return toResponse(group);
    }

    @Transactional
    public void removeMember(Long userId, Long groupId, Long memberId) {
        Group group = requireGroup(groupId);
        requireOwner(group, userId);
        if (memberId.equals(group.getOwner().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The group owner cannot be removed as a member");
        }
        removeMembershipIfSafe(groupId, memberId);
    }

    @Transactional
    public void leaveGroup(Long userId, Long groupId) {
        Group group = requireGroup(groupId);
        requireMember(groupId, userId);
        if (group.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The group owner cannot leave. Delete the group if it is no longer needed.");
        }
        removeMembershipIfSafe(groupId, userId);
    }

    private void removeMembershipIfSafe(Long groupId, Long memberId) {
        if (!membershipRepository.existsByGroupIdAndUserId(groupId, memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Group member not found");
        }
        if (balanceService.hasOutstandingBalance(groupId, memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This member has an unpaid balance. Record the settlement before changing membership.");
        }
        membershipRepository.deleteById(new com.splitsmart.backend.entity.MembershipId(
                groupId, memberId));
    }

    @Transactional
    public GroupResponse updateGroup(
            Long userId, Long groupId, UpdateGroupRequest request) {

        Group group = requireGroup(groupId);
        requireOwner(group, userId);

        group.setName(request.name().trim());
        group.setDescription(request.description());

        return toResponse(group);
    }

    @Transactional
    public void deleteGroup(Long userId, Long groupId) {
        Group group = requireGroup(groupId);
        requireOwner(group, userId);

        // Remove memberships first to satisfy foreign keys.
        membershipRepository.deleteAll(
                membershipRepository.findByGroupId(groupId));

        groupRepository.delete(group);
    }
}
