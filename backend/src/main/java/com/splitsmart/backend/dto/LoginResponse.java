
package com.splitsmart.backend.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        Long userId,
        String name,
        String email
) {}
