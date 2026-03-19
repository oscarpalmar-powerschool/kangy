package com.kangy.backend.api.dto;

import java.util.List;

public record RegisteredDeviceDto(
    String deviceId,
    String deviceType,
    String registeredAt,
    List<String> inputCapabilities,
    List<String> outputCapabilities
) {}

