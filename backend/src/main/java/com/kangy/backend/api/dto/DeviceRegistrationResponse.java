package com.kangy.backend.api.dto;

public record DeviceRegistrationResponse(
    String deviceId,
    String status,
    String registeredAt
) {}

