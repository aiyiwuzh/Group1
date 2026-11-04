
package com.splitsmart.backend.dto;

public record ProfileResponse(
        Long userId,
        String name,
        String email,
        String uscId
) {}
