package com.kangy.backend.api.dto;

public record DeviceActionEnqueueResponse(
    String deviceId,
    int enqueuedCount,
    String message
) {}

