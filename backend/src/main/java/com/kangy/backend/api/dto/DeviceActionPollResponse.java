package com.kangy.backend.api.dto;

import java.util.List;

public record DeviceActionPollResponse(
    String deviceId,
    List<DevicePendingAction> actions,
    String polledAt
) {}

