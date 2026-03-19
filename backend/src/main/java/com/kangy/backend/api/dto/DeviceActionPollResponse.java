package com.kangy.backend.api.dto;

import java.util.List;

public record DeviceActionPollResponse(
    String deviceId,
    List<PendingAction> actions,
    String polledAt
) {
  public record PendingAction(
      String actionId,
      String type,
      Object payload,
      String createdAt
  ) {}
}

