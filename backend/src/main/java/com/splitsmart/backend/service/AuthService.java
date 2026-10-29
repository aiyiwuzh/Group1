
package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.RegisterRequest;
import com.splitsmart.backend.dto.LoginRequest;
import com.splitsmart.backend.dto.LoginResponse;
import com.splitsmart.backend.entity.User;
import com.splitsmart.backend.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.splitsmart.backend.dto.UpdateProfileRequest;
import static org.springframework.http.HttpStatus.NOT_FOUND;
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // Register a new USC user
    @Transactional
    public Long register(RegisterRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        // Check if email is already registered
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Email is already registered"
            );
        }

        // Check if USC ID is already registered
        if (userRepository.existsByUscId(request.uscId())) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "USC ID is already registered"
            );
        }

        // Hash password before storing it
        User user = new User(
                request.name().trim(),
                email,
                request.uscId(),
                passwordEncoder.encode(request.password())
        );

        // Save user and return generated ID
        return userRepository.save(user).getId();
    }

    // Authenticate an existing user
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        // Find user by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                UNAUTHORIZED,
                                "Invalid email or password"
                        )
                );

        // Verify password against stored BCrypt hash
        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash())) {

            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        // Generate JWT token
        String token = jwtService.generateToken(user);

        // Return token and user information
        return new LoginResponse(
                token,
                "Bearer",
                7200,
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND, "User not found"));

        user.setName(request.name().trim());

        return userRepository.save(user);
    }

}
