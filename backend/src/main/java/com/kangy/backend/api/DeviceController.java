package com.kangy.backend.api;

import com.kangy.backend.api.dto.DeviceActionPublishRequest;
import com.kangy.backend.api.dto.DeviceActionPublishResponse;
import com.kangy.backend.api.dto.DeviceRegistrationRequest;
import com.kangy.backend.api.dto.DeviceRegistrationResponse;
import com.kangy.backend.api.dto.DeviceStatusResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/devices", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeviceController {

  @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
  public DeviceRegistrationResponse registerDevice(@Valid @RequestBody DeviceRegistrationRequest request) {
    String assignedDeviceId = (request.deviceId() == null || request.deviceId().isBlank())
        ? UUID.randomUUID().toString()
        : request.deviceId();

    return new DeviceRegistrationResponse(
        assignedDeviceId,
        "REGISTERED",
        Instant.now().toString()
    );
  }

  @GetMapping("/{deviceId}/status")
  public DeviceStatusResponse getDeviceStatus(@PathVariable String deviceId) {
    return new DeviceStatusResponse(
        deviceId,
        "UNKNOWN",
        null,
        null
    );
  }

  @PostMapping(value = "/{deviceId}/actions:publish", consumes = MediaType.APPLICATION_JSON_VALUE)
  public DeviceActionPublishResponse publishDeviceActions(
      @PathVariable String deviceId,
      @Valid @RequestBody DeviceActionPublishRequest request
  ) {
    return new DeviceActionPublishResponse(
        deviceId,
        request.actions(),
        List.of(),
        Instant.now().toString()
    );
  }
}

