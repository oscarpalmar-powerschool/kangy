package com.kangy.backend.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DeviceActionEnqueueRequest(
    @NotEmpty List<@NotNull Action> actions
) {
  public record Action(
      @NotNull @Size(max = 64) String type,
      Object payload
  ) {}
}

