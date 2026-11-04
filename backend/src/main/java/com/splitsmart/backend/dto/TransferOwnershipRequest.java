package com.splitsmart.backend.dto;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(@NotNull Long newOwnerId) {}
