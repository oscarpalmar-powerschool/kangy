package com.kangy.backend.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DeviceActionPublishRequest(
    @NotEmpty List<Action> actions
) {
  public record Action(
      @Size(max = 64) String type,
      Object payload
  ) {}
}

