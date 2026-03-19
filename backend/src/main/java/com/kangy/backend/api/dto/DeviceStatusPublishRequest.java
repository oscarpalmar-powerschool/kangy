package com.kangy.backend.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DeviceStatusPublishRequest(
    String reportedAt,
    @NotEmpty List<@NotNull Observation> observations
) {
  public record Observation(
      @NotNull @Size(max = 64) String type,
      Object payload
  ) {}
}

