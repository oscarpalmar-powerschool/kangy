package com.kangy.backend.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class DeviceActionQueue {
  private final Map<String, Deque<PendingAction>> queues = new ConcurrentHashMap<>();

  public List<PendingAction> enqueue(String deviceId, List<Action> actions) {
    if (actions == null || actions.isEmpty()) {
      return List.of();
    }

    Deque<PendingAction> q = queues.computeIfAbsent(deviceId, ignored -> new ArrayDeque<>());
    List<PendingAction> created = new ArrayList<>(actions.size());
    synchronized (q) {
      for (Action a : actions) {
        PendingAction pa = new PendingAction(
            UUID.randomUUID().toString(),
            a.type(),
            a.payload(),
            Instant.now().toString()
        );
        q.addLast(pa);
        created.add(pa);
      }
    }
    return List.copyOf(created);
  }

  public List<PendingAction> poll(String deviceId, int limit) {
    int safeLimit = Math.max(0, limit);
    Deque<PendingAction> q = queues.get(deviceId);
    if (q == null || safeLimit == 0) {
      return List.of();
    }
    synchronized (q) {
      if (q.isEmpty()) {
        return List.of();
      }
      List<PendingAction> out = new ArrayList<>(Math.min(safeLimit, q.size()));
      int i = 0;
      for (PendingAction a : q) {
        out.add(a);
        i++;
        if (i >= safeLimit) break;
      }
      return List.copyOf(out);
    }
  }

  public int ack(String deviceId, List<String> actionIds) {
    if (actionIds == null || actionIds.isEmpty()) {
      return 0;
    }
    Deque<PendingAction> q = queues.get(deviceId);
    if (q == null) {
      return 0;
    }
    synchronized (q) {
      int before = q.size();
      q.removeIf(a -> actionIds.contains(a.actionId()));
      return before - q.size();
    }
  }

  public record Action(String type, Object payload) {}

  public record PendingAction(
      String actionId,
      String type,
      Object payload,
      String createdAt
  ) {}
}

