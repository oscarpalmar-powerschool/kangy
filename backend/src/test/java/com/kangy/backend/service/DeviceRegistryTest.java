package com.kangy.backend.service;

import com.kangy.backend.api.dto.DeviceRegistrationRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceRegistryTest {

  private DeviceRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DeviceRegistry();
  }

  private DeviceRegistrationRequest req(String deviceId, String deviceType) {
    return new DeviceRegistrationRequest(deviceId, deviceType, List.of("sensor"), List.of("led"));
  }

  // --- register ---

  @Test
  void register_createsDeviceWithGeneratedToken() {
    DeviceRegistry.RegisteredDevice device = registry.register(req("esp-1", "ESP32"));

    assertThat(device.deviceId()).isEqualTo("esp-1");
    assertThat(device.token()).isNotBlank();
    assertThat(device.registeredAt()).isNotBlank();
  }

  @Test
  void register_isIdempotent() {
    DeviceRegistry.RegisteredDevice first = registry.register(req("esp-1", "ESP32"));
    DeviceRegistry.RegisteredDevice second = registry.register(req("esp-1", "ESP32"));

    assertThat(second.token()).isEqualTo(first.token());
    assertThat(second.registeredAt()).isEqualTo(first.registeredAt());
  }

  @Test
  void register_tokenIs43CharsBase64UrlEncoded() {
    // 32 bytes → 43 chars in base64url without padding
    DeviceRegistry.RegisteredDevice device = registry.register(req("esp-1", "ESP32"));
    assertThat(device.token()).hasSize(43).matches("[A-Za-z0-9_-]+");
  }

  @Test
  void register_acceptsCaseInsensitiveDeviceType() {
    DeviceRegistry.RegisteredDevice device = registry.register(req("rpi-1", "rpi"));
    assertThat(device.deviceType().name()).isEqualTo("RPI");
  }

  @Test
  void register_throwsForUnknownDeviceType() {
    assertThatThrownBy(() -> registry.register(req("x", "UNKNOWN")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported deviceType");
  }

  @Test
  void register_throwsForNullRequest() {
    assertThatThrownBy(() -> registry.register(null))
        .isInstanceOf(NullPointerException.class);
  }

  // --- list ---

  @Test
  void list_emptyWhenNothingRegistered() {
    assertThat(registry.list()).isEmpty();
  }

  @Test
  void list_returnsSortedByRegisteredAt() throws InterruptedException {
    registry.register(req("a", "ESP32"));
    Thread.sleep(5); // ensure distinct timestamps
    registry.register(req("b", "RPI"));

    List<DeviceRegistry.RegisteredDevice> devices = registry.list();
    assertThat(devices).hasSize(2);
    assertThat(devices.get(0).deviceId()).isEqualTo("a");
    assertThat(devices.get(1).deviceId()).isEqualTo("b");
  }

  // --- findByDeviceId ---

  @Test
  void findByDeviceId_returnsDeviceWhenRegistered() {
    registry.register(req("esp-1", "ESP32"));
    Optional<DeviceRegistry.RegisteredDevice> result = registry.findByDeviceId("esp-1");

    assertThat(result).isPresent();
    assertThat(result.get().deviceId()).isEqualTo("esp-1");
  }

  @Test
  void findByDeviceId_returnsEmptyForUnknownId() {
    assertThat(registry.findByDeviceId("nope")).isEmpty();
  }

  @Test
  void findByDeviceId_returnsEmptyForNull() {
    assertThat(registry.findByDeviceId(null)).isEmpty();
  }

  // --- tokenMatches ---

  @Test
  void tokenMatches_trueForCorrectToken() {
    DeviceRegistry.RegisteredDevice device = registry.register(req("esp-1", "ESP32"));
    assertThat(registry.tokenMatches("esp-1", device.token())).isTrue();
  }

  @Test
  void tokenMatches_falseForWrongToken() {
    registry.register(req("esp-1", "ESP32"));
    assertThat(registry.tokenMatches("esp-1", "wrong-token")).isFalse();
  }

  @Test
  void tokenMatches_falseForUnknownDevice() {
    assertThat(registry.tokenMatches("ghost", "any-token")).isFalse();
  }

  @Test
  void tokenMatches_falseWhenDeviceIdIsNull() {
    assertThat(registry.tokenMatches(null, "some-token")).isFalse();
  }

  @Test
  void tokenMatches_falseWhenTokenIsNull() {
    registry.register(req("esp-1", "ESP32"));
    assertThat(registry.tokenMatches("esp-1", null)).isFalse();
  }
}
