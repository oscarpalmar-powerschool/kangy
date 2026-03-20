package com.kangy.backend.service;

import com.kangy.backend.api.dto.DeviceStatusPublishRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class DeviceStatusStore {
  private final ConcurrentHashMap<String, List<StatusReport>> historyByDeviceId = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> lastSeenAtByDeviceId = new ConcurrentHashMap<>();

  public void append(String deviceId, DeviceStatusPublishRequest request) {
    List<DeviceStatusPublishRequest.Observation> observations = request.observations();
    if (observations == null || observations.isEmpty()) {
      lastSeenAtByDeviceId.put(deviceId, Instant.now().toString());
      return;
    }

    String reportedAt = (request.reportedAt() == null || request.reportedAt().isBlank())
        ? Instant.now().toString()
        : request.reportedAt();

    StatusReport report = new StatusReport(reportedAt, observations);

    List<StatusReport> list = historyByDeviceId.computeIfAbsent(
        deviceId,
        ignored -> Collections.synchronizedList(new ArrayList<>())
    );
    list.add(report);
    lastSeenAtByDeviceId.put(deviceId, Instant.now().toString());
  }

  public String getLastSeenAt(String deviceId) {
    return lastSeenAtByDeviceId.get(deviceId);
  }

  public StatusReport getLatest(String deviceId) {
    List<StatusReport> list = historyByDeviceId.get(deviceId);
    if (list == null || list.isEmpty()) {
      return null;
    }
    synchronized (list) {
      return list.isEmpty() ? null : list.get(list.size() - 1);
    }
  }

  public List<StatusReport> getMostRecentFirst(String deviceId, int limit) {
    List<StatusReport> list = historyByDeviceId.get(deviceId);
    if (list == null || list.isEmpty()) {
      return List.of();
    }
    int safeLimit = Math.max(0, limit);
    synchronized (list) {
      int size = list.size();
      int fromIndex = Math.max(0, size - safeLimit);
      List<StatusReport> slice = new ArrayList<>(list.subList(fromIndex, size));
      Collections.reverse(slice);
      return List.copyOf(slice);
    }
  }

  public record StatusReport(
      String reportedAt,
      List<DeviceStatusPublishRequest.Observation> observations
  ) {}
}

