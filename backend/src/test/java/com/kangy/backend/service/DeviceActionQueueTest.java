package com.kangy.backend.service;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceActionQueueTest {

  private DeviceActionQueue queue;

  @BeforeEach
  void setUp() {
    queue = new DeviceActionQueue();
  }

  private DeviceActionQueue.Action action(String type) {
    return new DeviceActionQueue.Action(type, null);
  }

  // --- enqueue ---

  @Test
  void enqueue_returnsPendingActionsWithUuids() {
    List<DeviceActionQueue.PendingAction> result = queue.enqueue("dev-1",
        List.of(action("servo.setPosition")));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).actionId()).matches(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    assertThat(result.get(0).type()).isEqualTo("servo.setPosition");
    assertThat(result.get(0).createdAt()).isNotBlank();
  }

  @Test
  void enqueue_multipleActionsGetDistinctIds() {
    List<DeviceActionQueue.PendingAction> result = queue.enqueue("dev-1",
        List.of(action("a"), action("b"), action("c")));

    assertThat(result).hasSize(3);
    assertThat(result.stream().map(DeviceActionQueue.PendingAction::actionId).distinct())
        .hasSize(3);
  }

  @Test
  void enqueue_returnsEmptyForNullActions() {
    assertThat(queue.enqueue("dev-1", null)).isEmpty();
  }

  @Test
  void enqueue_returnsEmptyForEmptyList() {
    assertThat(queue.enqueue("dev-1", List.of())).isEmpty();
  }

  // --- poll ---

  @Test
  void poll_returnsActionsUpToLimit() {
    queue.enqueue("dev-1", List.of(action("a"), action("b"), action("c")));

    List<DeviceActionQueue.PendingAction> result = queue.poll("dev-1", 2);
    assertThat(result).hasSize(2);
  }

  @Test
  void poll_returnsAllWhenLimitExceedsQueueSize() {
    queue.enqueue("dev-1", List.of(action("a"), action("b")));

    assertThat(queue.poll("dev-1", 100)).hasSize(2);
  }

  @Test
  void poll_returnsEmptyForLimitZero() {
    queue.enqueue("dev-1", List.of(action("a")));
    assertThat(queue.poll("dev-1", 0)).isEmpty();
  }

  @Test
  void poll_returnsEmptyForUnknownDevice() {
    assertThat(queue.poll("ghost", 10)).isEmpty();
  }

  @Test
  void poll_doesNotRemoveActionsFromQueue() {
    queue.enqueue("dev-1", List.of(action("a")));
    queue.poll("dev-1", 10);
    queue.poll("dev-1", 10);

    assertThat(queue.poll("dev-1", 10)).hasSize(1);
  }

  // --- ack ---

  @Test
  void ack_removesActionsByIdAndReturnsCount() {
    List<DeviceActionQueue.PendingAction> created = queue.enqueue("dev-1",
        List.of(action("a"), action("b"), action("c")));

    int removed = queue.ack("dev-1", List.of(created.get(0).actionId(), created.get(2).actionId()));

    assertThat(removed).isEqualTo(2);
    assertThat(queue.poll("dev-1", 10)).hasSize(1);
    assertThat(queue.poll("dev-1", 10).get(0).actionId()).isEqualTo(created.get(1).actionId());
  }

  @Test
  void ack_returnsZeroForUnknownDevice() {
    assertThat(queue.ack("ghost", List.of("some-id"))).isEqualTo(0);
  }

  @Test
  void ack_returnsZeroForNullActionIds() {
    queue.enqueue("dev-1", List.of(action("a")));
    assertThat(queue.ack("dev-1", null)).isEqualTo(0);
  }

  @Test
  void ack_returnsZeroForEmptyActionIds() {
    queue.enqueue("dev-1", List.of(action("a")));
    assertThat(queue.ack("dev-1", List.of())).isEqualTo(0);
  }

  @Test
  void ack_returnsZeroForNonExistentActionId() {
    queue.enqueue("dev-1", List.of(action("a")));
    assertThat(queue.ack("dev-1", List.of("does-not-exist"))).isEqualTo(0);
    assertThat(queue.poll("dev-1", 10)).hasSize(1);
  }
}
