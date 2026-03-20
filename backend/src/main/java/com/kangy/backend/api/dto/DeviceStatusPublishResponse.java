package com.kangy.backend.api.dto;

import java.util.List;

public record DeviceStatusPublishResponse(
    String deviceId,
    String acceptedAt,
    String message,
    /** Pending output actions for the device (same shape as GET .../actions). */
    List<DevicePendingAction> actions,
    /** How many action IDs were removed from the queue via ackedActionIds on this request. */
    int actionsAcknowledgedCount
) {}
