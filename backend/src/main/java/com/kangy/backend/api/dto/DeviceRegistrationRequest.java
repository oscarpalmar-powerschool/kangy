package com.kangy.backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DeviceRegistrationRequest(
    @NotBlank @Size(max = 128) String deviceId,
    @NotBlank @Size(max = 16) String deviceType,
    @NotEmpty List<@NotBlank @Size(max = 64) String> inputCapabilities,
    @NotEmpty List<@NotBlank @Size(max = 64) String> outputCapabilities
) {}

