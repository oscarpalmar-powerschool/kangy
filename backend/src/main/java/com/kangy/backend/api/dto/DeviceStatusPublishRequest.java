package com.kangy.backend.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DeviceStatusPublishRequest(
    String reportedAt,
    /** Observations to record; may be null or empty for heartbeat / ack-only round-trips. */
    @Valid List<@NotNull Observation> observations,
    /** Action IDs completed since last call; removed from queue before pending actions are returned. */
    List<@Size(max = 128) String> ackedActionIds,
    /** Max pending actions to return (default 25). */
    Integer actionLimit
) {
  public record Observation(
      @NotNull @Size(max = 64) String type,
      Object payload
  ) {}
}
