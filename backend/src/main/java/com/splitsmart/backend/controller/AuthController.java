
package com.splitsmart.backend.controller;

import com.splitsmart.backend.dto.RegisterRequest;
import com.splitsmart.backend.dto.LoginRequest;
import com.splitsmart.backend.dto.LoginResponse;
import com.splitsmart.backend.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.splitsmart.backend.dto.UpdateProfileRequest;
import com.splitsmart.backend.dto.ProfileResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Register a new USC user
    @PostMapping("/register")
    public ResponseEntity<Map<String, Long>> register(
            @Valid @RequestBody RegisterRequest request) {

        Long userId = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("userId", userId));
    }

    // Log in an existing user
    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request) {

        return authService.login(request);
    }

    @GetMapping("/me")
    public Map<String, Object> me(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.oauth2.jwt.Jwt jwt) {

        return Map.of("userId", Long.parseLong(jwt.getSubject()));
    }


    @PutMapping("/me")
    public ProfileResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {

        Long userId = Long.parseLong(jwt.getSubject());

        var user = authService.updateProfile(userId, request);

        return new ProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }

}
