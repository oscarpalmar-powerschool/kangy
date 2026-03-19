package com.kangy.backend.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DeviceActionAckRequest(
    @NotEmpty List<@Size(max = 128) String> actionIds
) {}

