package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.LoginRequest;
import com.splitsmart.backend.dto.RegisterRequest;
import com.splitsmart.backend.dto.UpdateGroupRequest;
import com.splitsmart.backend.entity.Group;
import com.splitsmart.backend.entity.User;
import com.splitsmart.backend.repository.GroupRepository;
import com.splitsmart.backend.repository.MembershipRepository;
import com.splitsmart.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthenticationGroupServiceTest {
    private UserRepository users;
    private PasswordEncoder encoder;
    private JwtService jwt;
    private AuthService auth;

    @BeforeEach
    void setup() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        jwt = mock(JwtService.class);
        auth = new AuthService(users, encoder, jwt);
    }

    // WB-22: Successful registration normalizes email and hashes password.
    @Test
    void registrationNormalizesEmailAndHashesPassword() {
        RegisterRequest req = new RegisterRequest("  Alice  ", "  ALICE@USC.EDU  ", "1234567890", "password123");
        when(encoder.encode("password123")).thenReturn("hashedPassword");
        when(users.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        assertEquals(1L, auth.register(req));
        ArgumentCaptor<User> captured = ArgumentCaptor.forClass(User.class);
        verify(users).save(captured.capture());
        assertEquals("Alice", captured.getValue().getName());
        assertEquals("alice@usc.edu", captured.getValue().getEmail());
        assertEquals("hashedPassword", captured.getValue().getPasswordHash());
        verify(encoder).encode("password123");
    }

    // WB-23: Duplicate email returns 409 and does not save.
    @Test
    void duplicateEmailIsRejected() {
        when(users.existsByEmail("alice@usc.edu")).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auth.register(new RegisterRequest("Alice", "alice@usc.edu", "1234567890", "password123")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(users, never()).save(any(User.class));
    }

    // WB-24: Duplicate USC ID returns 409 and does not save.
    @Test
    void duplicateUscIdIsRejected() {
        when(users.existsByUscId("1234567890")).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auth.register(new RegisterRequest("Bob", "bob@usc.edu", "1234567890", "password123")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(users, never()).save(any(User.class));
    }

    // WB-25: Wrong password returns 401 and does not generate a JWT.
    @Test
    void incorrectPasswordIsRejected() {
        User user = new User("Alice", "alice@usc.edu", "1234567890", "storedHash");
        when(users.findByEmail("alice@usc.edu")).thenReturn(Optional.of(user));
        when(encoder.matches("wrongPassword", "storedHash")).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auth.login(new LoginRequest("alice@usc.edu", "wrongPassword")));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(jwt, never()).generateToken(any(User.class));
    }

    // WB-26: A nonowner cannot change group details.
    @Test
    void nonOwnerCannotModifyGroup() {
        GroupRepository groups = mock(GroupRepository.class);
        MembershipRepository memberships = mock(MembershipRepository.class);
        BalanceService balances = mock(BalanceService.class);
        GroupService service = new GroupService(groups, memberships, users, balances);
        User owner = new User("Alice", "alice@usc.edu", "1111111111", "hash");
        ReflectionTestUtils.setField(owner, "id", 1L);
        Group group = new Group("Original Group", "Description", owner);
        ReflectionTestUtils.setField(group, "id", 10L);
        when(groups.findById(10L)).thenReturn(Optional.of(group));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateGroup(2L, 10L, new UpdateGroupRequest("Unauthorized Change", "Changed")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Original Group", group.getName());
        verify(groups, never()).save(any(Group.class));
    }
}
