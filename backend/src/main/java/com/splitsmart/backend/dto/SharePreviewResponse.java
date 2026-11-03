
package com.splitsmart.backend.dto;

import com.splitsmart.backend.entity.SplitMethod;

import java.math.BigDecimal;
import java.util.List;

// The shares shown to the user before they confirm and save an expense.
public record SharePreviewResponse(
        BigDecimal amount,
        SplitMethod splitMethod,
        List<ShareResponse> shares
) {}
