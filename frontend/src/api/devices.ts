import { apiGetJson, apiPostJson } from "./http";
import type {
  DeviceActionEnqueueRequest,
  DeviceActionEnqueueResponse,
  RegisteredDeviceDto,
} from "./types";

export function listRegisteredDevices() {
  return apiGetJson<RegisteredDeviceDto[]>("/api/devices");
}

export function enqueueDeviceActions(
  deviceId: string,
  actions: DeviceActionEnqueueRequest["actions"],
) {
  const path = `/api/devices/${encodeURIComponent(deviceId)}/actions:enqueue`;
  return apiPostJson<DeviceActionEnqueueResponse, DeviceActionEnqueueRequest>(path, { actions });
}

