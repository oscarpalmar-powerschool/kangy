package com.kangy.backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record DeviceRegistrationRequest(
    @Size(max = 128) String deviceId,
    @NotBlank @Size(max = 64) String deviceType,
    @Size(max = 64) String firmwareVersion,
    Map<String, Object> metadata
) {}

