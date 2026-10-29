
package com.splitsmart.backend.dto;

import java.util.List;

public record GroupResponse(
        Long id,
        String name,
        String description,
        Long ownerId,
        List<MemberResponse> members
) {}
