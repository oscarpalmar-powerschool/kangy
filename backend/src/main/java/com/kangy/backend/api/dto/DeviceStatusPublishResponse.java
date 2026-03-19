package com.kangy.backend.api.dto;

public record DeviceStatusPublishResponse(
    String deviceId,
    String acceptedAt,
    String message
) {}

