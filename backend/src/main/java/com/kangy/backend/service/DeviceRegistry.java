package com.kangy.backend.service;

import com.kangy.backend.api.dto.DeviceRegistrationRequest;
import com.kangy.backend.domain.DeviceType;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class DeviceRegistry {
  private final ConcurrentHashMap<String, RegisteredDevice> byDeviceId = new ConcurrentHashMap<>();
  private final SecureRandom secureRandom = new SecureRandom();

  public RegisteredDevice register(DeviceRegistrationRequest request) {
    Objects.requireNonNull(request, "request");

    // Validate/normalize type early (gives a good error message)
    DeviceType type = DeviceType.parse(request.deviceType());

    return byDeviceId.compute(request.deviceId(), (deviceId, existing) -> {
      if (existing != null) {
        return existing;
      }
      String token = generateToken();
      return new RegisteredDevice(
          deviceId,
          type,
          token,
          Instant.now().toString(),
          request.inputCapabilities(),
          request.outputCapabilities()
      );
    });
  }

  private String generateToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public List<RegisteredDevice> list() {
    return byDeviceId.values().stream()
        .sorted(Comparator.comparing(RegisteredDevice::registeredAt))
        .toList();
  }

  public Optional<RegisteredDevice> findByDeviceId(String deviceId) {
    if (deviceId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(byDeviceId.get(deviceId));
  }

  public boolean tokenMatches(String deviceId, String token) {
    if (deviceId == null || token == null) {
      return false;
    }
    RegisteredDevice d = byDeviceId.get(deviceId);
    return d != null && token.equals(d.token());
  }

  public record RegisteredDevice(
      String deviceId,
      DeviceType deviceType,
      String token,
      String registeredAt,
      java.util.List<String> inputCapabilities,
      java.util.List<String> outputCapabilities
  ) {}
}

