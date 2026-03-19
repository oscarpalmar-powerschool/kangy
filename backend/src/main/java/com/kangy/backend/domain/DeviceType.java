package com.kangy.backend.domain;

public enum DeviceType {
  ESP32,
  RPI,
  XIAO;

  public static DeviceType parse(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("deviceType is required");
    }
    try {
      return DeviceType.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported deviceType: " + raw);
    }
  }
}

