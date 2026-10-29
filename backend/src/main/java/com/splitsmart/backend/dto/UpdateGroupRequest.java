
package com.splitsmart.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
        @NotBlank
        @Size(max = 150)
        String name,

        String description
) {}
