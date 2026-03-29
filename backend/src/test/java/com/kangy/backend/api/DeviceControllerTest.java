package com.kangy.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangy.backend.api.dto.DeviceActionAckRequest;
import com.kangy.backend.api.dto.DeviceActionEnqueueRequest;
import com.kangy.backend.api.dto.DeviceRegistrationRequest;
import com.kangy.backend.api.dto.DeviceStatusPublishRequest;
import com.kangy.backend.domain.DeviceType;
import com.kangy.backend.service.DeviceActionQueue;
import com.kangy.backend.service.DeviceRegistry;
import com.kangy.backend.service.DeviceStatusStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
class DeviceControllerTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @MockitoBean DeviceRegistry deviceRegistry;
  @MockitoBean DeviceStatusStore deviceStatusStore;
  @MockitoBean DeviceActionQueue deviceActionQueue;

  private static final String DEVICE_ID = "esp-test-1";
  private static final String TOKEN = "valid-token-abc";

  private DeviceRegistry.RegisteredDevice registeredDevice() {
    return new DeviceRegistry.RegisteredDevice(
        DEVICE_ID, DeviceType.ESP32, TOKEN, "2024-01-01T00:00:00Z",
        List.of("sensor"), List.of("led"));
  }

  // --- POST /api/devices/register ---

  @Test
  void register_validRequest_returns200WithDeviceIdAndToken() throws Exception {
    when(deviceRegistry.register(any())).thenReturn(registeredDevice());

    DeviceRegistrationRequest req = new DeviceRegistrationRequest(
        DEVICE_ID, "ESP32", List.of("sensor"), List.of("led"));

    mvc.perform(post("/api/devices/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deviceId").value(DEVICE_ID))
        .andExpect(jsonPath("$.token").value(TOKEN))
        .andExpect(jsonPath("$.message").value("SUCCESS"));
  }

  @Test
  void register_blankDeviceId_returns400() throws Exception {
    DeviceRegistrationRequest req = new DeviceRegistrationRequest(
        "", "ESP32", List.of("sensor"), List.of("led"));

    mvc.perform(post("/api/devices/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void register_missingCapabilities_returns400() throws Exception {
    String body = """
        {"deviceId":"esp-1","deviceType":"ESP32","inputCapabilities":[],"outputCapabilities":[]}
        """;

    mvc.perform(post("/api/devices/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }

  // --- GET /api/devices ---

  @Test
  void listDevices_returnsDeviceList() throws Exception {
    when(deviceRegistry.list()).thenReturn(List.of(registeredDevice()));

    mvc.perform(get("/api/devices"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].deviceId").value(DEVICE_ID))
        .andExpect(jsonPath("$[0].deviceType").value("ESP32"));
  }

  @Test
  void listDevices_returnsEmptyArray() throws Exception {
    when(deviceRegistry.list()).thenReturn(List.of());

    mvc.perform(get("/api/devices"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  // --- POST /api/devices/{deviceId}/status ---

  @Test
  void publishStatus_validToken_returns200() throws Exception {
    when(deviceRegistry.tokenMatches(DEVICE_ID, TOKEN)).thenReturn(true);
    when(deviceActionQueue.poll(eq(DEVICE_ID), anyInt())).thenReturn(List.of());

    DeviceStatusPublishRequest req = new DeviceStatusPublishRequest(
        null, List.of(), null, null);

    mvc.perform(post("/api/devices/{id}/status", DEVICE_ID)
            .header("Authorization", "Bearer " + TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deviceId").value(DEVICE_ID))
        .andExpect(jsonPath("$.message").value("ACCEPTED"));
  }

  @Test
  void publishStatus_invalidToken_returns401() throws Exception {
    when(deviceRegistry.tokenMatches(DEVICE_ID, "bad-token")).thenReturn(false);

    DeviceStatusPublishRequest req = new DeviceStatusPublishRequest(
        null, List.of(), null, null);

    mvc.perform(post("/api/devices/{id}/status", DEVICE_ID)
            .header("Authorization", "Bearer bad-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void publishStatus_missingAuthHeader_returns401() throws Exception {
    when(deviceRegistry.tokenMatches(any(), any())).thenReturn(false);

    DeviceStatusPublishRequest req = new DeviceStatusPublishRequest(
        null, List.of(), null, null);

    mvc.perform(post("/api/devices/{id}/status", DEVICE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isUnauthorized());
  }

  // --- GET /api/devices/{deviceId}/status ---

  @Test
  void getStatus_unknownDevice_returns400() throws Exception {
    when(deviceRegistry.findByDeviceId(DEVICE_ID)).thenReturn(Optional.empty());

    mvc.perform(get("/api/devices/{id}/status", DEVICE_ID))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getStatus_knownDevice_returns200() throws Exception {
    when(deviceRegistry.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(registeredDevice()));
    when(deviceStatusStore.getLatest(DEVICE_ID)).thenReturn(null);
    when(deviceStatusStore.getLastSeenAt(DEVICE_ID)).thenReturn("2024-01-01T00:00:00Z");
    when(deviceStatusStore.getMostRecentFirst(eq(DEVICE_ID), anyInt())).thenReturn(List.of());

    mvc.perform(get("/api/devices/{id}/status", DEVICE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deviceId").value(DEVICE_ID))
        .andExpect(jsonPath("$.deviceType").value("ESP32"));
  }

  // --- GET /api/devices/{deviceId}/actions ---

  @Test
  void pollActions_validToken_returns200() throws Exception {
    when(deviceRegistry.tokenMatches(DEVICE_ID, TOKEN)).thenReturn(true);
    when(deviceActionQueue.poll(eq(DEVICE_ID), anyInt())).thenReturn(List.of());

    mvc.perform(get("/api/devices/{id}/actions", DEVICE_ID)
            .header("Authorization", "Bearer " + TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deviceId").value(DEVICE_ID))
        .andExpect(jsonPath("$.actions").isArray());
  }

  @Test
  void pollActions_invalidToken_returns401() throws Exception {
    when(deviceRegistry.tokenMatches(DEVICE_ID, "bad")).thenReturn(false);

    mvc.perform(get("/api/devices/{id}/actions", DEVICE_ID)
            .header("Authorization", "Bearer bad"))
        .andExpect(status().isUnauthorized());
  }

  // --- POST /api/devices/{deviceId}/actions:ack ---

  @Test
  void ackActions_validToken_returns200() throws Exception {
    when(deviceRegistry.tokenMatches(DEVICE_ID, TOKEN)).thenReturn(true);
    when(deviceActionQueue.ack(eq(DEVICE_ID), any())).thenReturn(2);

    DeviceActionAckRequest req = new DeviceActionAckRequest(List.of("id-1", "id-2"));

    mvc.perform(post("/api/devices/{id}/actions:ack", DEVICE_ID)
            .header("Authorization", "Bearer " + TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acknowledgedCount").value(2))
        .andExpect(jsonPath("$.message").value("ACKED"));
  }

  @Test
  void ackActions_invalidToken_returns401() throws Exception {
    when(deviceRegistry.tokenMatches(DEVICE_ID, "bad")).thenReturn(false);

    DeviceActionAckRequest req = new DeviceActionAckRequest(List.of("id-1"));

    mvc.perform(post("/api/devices/{id}/actions:ack", DEVICE_ID)
            .header("Authorization", "Bearer bad")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isUnauthorized());
  }

  // --- POST /api/devices/{deviceId}/actions:enqueue ---

  @Test
  void enqueueActions_validServoAction_returns200() throws Exception {
    when(deviceRegistry.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(registeredDevice()));
    when(deviceActionQueue.enqueue(eq(DEVICE_ID), any())).thenReturn(
        List.of(new DeviceActionQueue.PendingAction("uuid-1", "servo.setPosition",
            Map.of("id", "s1", "degrees", 90), "2024-01-01T00:00:00Z")));

    DeviceActionEnqueueRequest req = new DeviceActionEnqueueRequest(
        List.of(new DeviceActionEnqueueRequest.Action("servo.setPosition",
            Map.of("id", "s1", "degrees", 90))));

    mvc.perform(post("/api/devices/{id}/actions:enqueue", DEVICE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enqueuedCount").value(1))
        .andExpect(jsonPath("$.message").value("ENQUEUED"));
  }

  @Test
  void enqueueActions_servoDegreesOutOfRange_returns400() throws Exception {
    when(deviceRegistry.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(registeredDevice()));

    DeviceActionEnqueueRequest req = new DeviceActionEnqueueRequest(
        List.of(new DeviceActionEnqueueRequest.Action("servo.setPosition",
            Map.of("id", "s1", "degrees", 200))));

    mvc.perform(post("/api/devices/{id}/actions:enqueue", DEVICE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void enqueueActions_validLedCommand_returns200() throws Exception {
    when(deviceRegistry.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(registeredDevice()));
    when(deviceActionQueue.enqueue(eq(DEVICE_ID), any())).thenReturn(
        List.of(new DeviceActionQueue.PendingAction("uuid-1", "led.command",
            Map.of("id", "led1", "mode", "blink"), "2024-01-01T00:00:00Z")));

    DeviceActionEnqueueRequest req = new DeviceActionEnqueueRequest(
        List.of(new DeviceActionEnqueueRequest.Action("led.command",
            Map.of("id", "led1", "mode", "blink"))));

    mvc.perform(post("/api/devices/{id}/actions:enqueue", DEVICE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enqueuedCount").value(1));
  }

  @Test
  void enqueueActions_invalidLedMode_returns400() throws Exception {
    when(deviceRegistry.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(registeredDevice()));

    DeviceActionEnqueueRequest req = new DeviceActionEnqueueRequest(
        List.of(new DeviceActionEnqueueRequest.Action("led.command",
            Map.of("id", "led1", "mode", "strobe"))));

    mvc.perform(post("/api/devices/{id}/actions:enqueue", DEVICE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void enqueueActions_unknownDevice_returns400() throws Exception {
    when(deviceRegistry.findByDeviceId(DEVICE_ID)).thenReturn(Optional.empty());

    DeviceActionEnqueueRequest req = new DeviceActionEnqueueRequest(
        List.of(new DeviceActionEnqueueRequest.Action("servo.setPosition",
            Map.of("id", "s1", "degrees", 90))));

    mvc.perform(post("/api/devices/{id}/actions:enqueue", DEVICE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }
}
