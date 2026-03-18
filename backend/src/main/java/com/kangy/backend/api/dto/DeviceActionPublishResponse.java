package com.kangy.backend.api.dto;

import java.util.List;

public record DeviceActionPublishResponse(
    String deviceId,
    List<DeviceActionPublishRequest.Action> accepted,
    List<RejectedAction> rejected,
    String publishedAt
) {
  public record RejectedAction(
      int index,
      String reason
  ) {}
}

