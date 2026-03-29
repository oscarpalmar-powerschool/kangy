package com.kangy.backend.service;

import com.kangy.backend.api.dto.DeviceStatusPublishRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceStatusStoreTest {

  private DeviceStatusStore store;

  @BeforeEach
  void setUp() {
    store = new DeviceStatusStore();
  }

  private DeviceStatusPublishRequest requestWith(List<DeviceStatusPublishRequest.Observation> obs) {
    return new DeviceStatusPublishRequest(null, obs, null, null);
  }

  private DeviceStatusPublishRequest requestWith(String reportedAt,
      List<DeviceStatusPublishRequest.Observation> obs) {
    return new DeviceStatusPublishRequest(reportedAt, obs, null, null);
  }

  private DeviceStatusPublishRequest.Observation obs(String type) {
    return new DeviceStatusPublishRequest.Observation(type, null);
  }

  // --- append & getLastSeenAt ---

  @Test
  void append_withObservations_updatesLastSeenAt() {
    store.append("dev-1", requestWith(List.of(obs("temperature"))));
    assertThat(store.getLastSeenAt("dev-1")).isNotBlank();
  }

  @Test
  void append_withNoObservations_isHeartbeat_updatesLastSeenAt() {
    store.append("dev-1", requestWith(null));
    assertThat(store.getLastSeenAt("dev-1")).isNotBlank();
  }

  @Test
  void append_withEmptyObservations_isHeartbeat() {
    store.append("dev-1", requestWith(List.of()));
    assertThat(store.getLastSeenAt("dev-1")).isNotBlank();
    assertThat(store.getLatest("dev-1")).isNull();
  }

  @Test
  void getLastSeenAt_returnsNullForUnknownDevice() {
    assertThat(store.getLastSeenAt("ghost")).isNull();
  }

  // --- getLatest ---

  @Test
  void getLatest_returnsNullForUnknownDevice() {
    assertThat(store.getLatest("ghost")).isNull();
  }

  @Test
  void getLatest_returnsNullWhenOnlyHeartbeats() {
    store.append("dev-1", requestWith(null));
    assertThat(store.getLatest("dev-1")).isNull();
  }

  @Test
  void getLatest_returnsMostRecentReport() {
    store.append("dev-1", requestWith(List.of(obs("temperature"))));
    store.append("dev-1", requestWith(List.of(obs("humidity"))));

    DeviceStatusStore.StatusReport latest = store.getLatest("dev-1");
    assertThat(latest).isNotNull();
    assertThat(latest.observations().get(0).type()).isEqualTo("humidity");
  }

  @Test
  void getLatest_usesProvidedReportedAt() {
    store.append("dev-1", requestWith("2024-01-01T00:00:00Z", List.of(obs("ping"))));
    assertThat(store.getLatest("dev-1").reportedAt()).isEqualTo("2024-01-01T00:00:00Z");
  }

  @Test
  void getLatest_generatesReportedAtWhenBlank() {
    store.append("dev-1", requestWith("  ", List.of(obs("ping"))));
    assertThat(store.getLatest("dev-1").reportedAt()).isNotBlank();
  }

  // --- getMostRecentFirst ---

  @Test
  void getMostRecentFirst_returnsEmptyForUnknownDevice() {
    assertThat(store.getMostRecentFirst("ghost", 10)).isEmpty();
  }

  @Test
  void getMostRecentFirst_returnsInReverseOrder() {
    store.append("dev-1", requestWith(List.of(obs("a"))));
    store.append("dev-1", requestWith(List.of(obs("b"))));
    store.append("dev-1", requestWith(List.of(obs("c"))));

    List<DeviceStatusStore.StatusReport> result = store.getMostRecentFirst("dev-1", 10);
    assertThat(result).hasSize(3);
    assertThat(result.get(0).observations().get(0).type()).isEqualTo("c");
    assertThat(result.get(1).observations().get(0).type()).isEqualTo("b");
    assertThat(result.get(2).observations().get(0).type()).isEqualTo("a");
  }

  @Test
  void getMostRecentFirst_respectsLimit() {
    store.append("dev-1", requestWith(List.of(obs("a"))));
    store.append("dev-1", requestWith(List.of(obs("b"))));
    store.append("dev-1", requestWith(List.of(obs("c"))));

    List<DeviceStatusStore.StatusReport> result = store.getMostRecentFirst("dev-1", 2);
    assertThat(result).hasSize(2);
    assertThat(result.get(0).observations().get(0).type()).isEqualTo("c");
    assertThat(result.get(1).observations().get(0).type()).isEqualTo("b");
  }

  @Test
  void getMostRecentFirst_returnsEmptyForLimitZero() {
    store.append("dev-1", requestWith(List.of(obs("a"))));
    assertThat(store.getMostRecentFirst("dev-1", 0)).isEmpty();
  }
}
