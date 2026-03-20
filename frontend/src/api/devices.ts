import { apiGetJson } from "./http";
import type { RegisteredDeviceDto } from "./types";

export function listRegisteredDevices() {
  return apiGetJson<RegisteredDeviceDto[]>("/api/devices");
}

