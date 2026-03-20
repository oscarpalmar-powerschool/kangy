package com.kangy.backend.api.dto;

public record DevicePendingAction(
    String actionId,
    String type,
    Object payload,
    String createdAt
) {}
