package com.kangy.backend.api.dto;

public record DeviceStatusResponse(
    String deviceId,
    String status,
    String lastSeenAt,
    Object details
) {}

