package com.kangy.backend.api.dto;

import java.util.List;

public record DeviceStatusGetResponse(
    String deviceId,
    String deviceType,
    String registeredAt,
    String lastSeenAt,
    StatusReport latest,
    List<StatusReport> historyMostRecentFirst
) {
  public record StatusReport(
      String reportedAt,
      List<DeviceStatusPublishRequest.Observation> observations
  ) {}
}

