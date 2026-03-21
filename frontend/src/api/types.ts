export type RegisteredDeviceDto = {
  deviceId: string;
  deviceType: string;
  registeredAt: string;
  inputCapabilities: string[];
  outputCapabilities: string[];
};

export type DeviceActionEnqueueRequest = {
  actions: Array<{ type: string; payload: Record<string, unknown> }>;
};

export type DeviceActionEnqueueResponse = {
  deviceId: string;
  enqueuedCount: number;
  message: string;
};

