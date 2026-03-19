package com.kangy.backend.api.dto;

public record DeviceActionAckResponse(
    String deviceId,
    int acknowledgedCount,
    String message
) {}

