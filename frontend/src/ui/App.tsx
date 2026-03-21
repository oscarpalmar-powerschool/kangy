import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { listRegisteredDevices } from "../api/devices";
import type { RegisteredDeviceDto } from "../api/types";
import { DeviceOutputRows } from "./DeviceOutputRows";

type OutputsPopoverState = {
  deviceId: string;
  outputs: string[];
  top: number;
  left: number;
  width: number;
};

export function App() {
  const [devices, setDevices] = useState<RegisteredDeviceDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [outputsPopover, setOutputsPopover] = useState<OutputsPopoverState | null>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
  const popoverTriggerRef = useRef<HTMLButtonElement | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const next = await listRegisteredDevices();
      setDevices(next);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load devices");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!outputsPopover) return;

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setOutputsPopover(null);
        popoverTriggerRef.current?.focus();
      }
    };

    const onPointerDown = (e: PointerEvent) => {
      const t = e.target as Node;
      if (popoverRef.current?.contains(t)) return;
      if (popoverTriggerRef.current?.contains(t)) return;
      setOutputsPopover(null);
    };

    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("pointerdown", onPointerDown, true);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("pointerdown", onPointerDown, true);
    };
  }, [outputsPopover]);

  const toggleOutputsPopover = (device: RegisteredDeviceDto, anchor: HTMLButtonElement) => {
    if (outputsPopover?.deviceId === device.deviceId) {
      setOutputsPopover(null);
      popoverTriggerRef.current = null;
      return;
    }
    popoverTriggerRef.current = anchor;
    const width = 340;
    const r = anchor.getBoundingClientRect();
    const left = Math.min(Math.max(12, r.right - width), window.innerWidth - width - 12);
    setOutputsPopover({
      deviceId: device.deviceId,
      outputs: [...(device.outputCapabilities ?? [])].sort((a, b) =>
        a.localeCompare(b, undefined, { sensitivity: "base" }),
      ),
      top: r.bottom + 8,
      left,
      width,
    });
  };

  return (
    <div className="container">
      <div className="header">
        <div>
          <div className="title">Registered devices</div>
          <div className="subtitle">Backend: GET /api/devices</div>
        </div>
        <div className="muted">
          {devices ? `${devices.length} device${devices.length === 1 ? "" : "s"}` : ""}
        </div>
      </div>

      <div className="panel">
        <div className="toolbar">
          <div className="muted">
            {loading ? "Loading…" : devices ? "Up to date" : "Not loaded"}
          </div>
          <button className="btn" onClick={load} disabled={loading}>
            Refresh
          </button>
        </div>

        {error ? <div className="error">{error}</div> : null}

        {devices ? (
          <table>
            <thead>
              <tr>
                <th>Device ID</th>
                <th>Type</th>
                <th>Registered</th>
                <th>Inputs</th>
                <th>Outputs</th>
                <th style={{ width: 1 }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {devices.length === 0 ? (
                <tr>
                  <td colSpan={6} className="muted">
                    No devices registered yet.
                  </td>
                </tr>
              ) : (
                devices.map((d) => (
                  <tr key={d.deviceId}>
                    <td>
                      <div style={{ fontWeight: 700 }}>{d.deviceId}</div>
                    </td>
                    <td>{d.deviceType}</td>
                    <td className="muted">{formatInstantMaybe(d.registeredAt)}</td>
                    <td>
                      <div className="pill">
                        {(d.inputCapabilities ?? []).length ? (
                          d.inputCapabilities.map((c) => <span key={c} className="tag">{c}</span>)
                        ) : (
                          <span className="muted">—</span>
                        )}
                      </div>
                    </td>
                    <td>
                      <div className="pill">
                        {(d.outputCapabilities ?? []).length ? (
                          d.outputCapabilities.map((c) => <span key={c} className="tag">{c}</span>)
                        ) : (
                          <span className="muted">—</span>
                        )}
                      </div>
                    </td>
                    <td>
                      <button
                        type="button"
                        className="btn btn-sm"
                        aria-expanded={outputsPopover?.deviceId === d.deviceId}
                        aria-haspopup="dialog"
                        onClick={(e) => toggleOutputsPopover(d, e.currentTarget)}
                      >
                        Outputs…
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        ) : (
          <div className="muted" style={{ padding: 14 }}>
            {loading ? "Loading devices…" : "Click Refresh to load devices."}
          </div>
        )}
      </div>

      {outputsPopover
        ? createPortal(
            <>
              <div
                className="popover-backdrop"
                aria-hidden
                onClick={() => {
                  setOutputsPopover(null);
                  popoverTriggerRef.current?.focus();
                }}
              />
              <div
                ref={popoverRef}
                className="popover-surface"
                role="dialog"
                aria-label="Device outputs"
                style={{
                  top: outputsPopover.top,
                  left: outputsPopover.left,
                  width: outputsPopover.width,
                }}
              >
                <h3>Output components</h3>
                <div className="popover-device">{outputsPopover.deviceId}</div>
                <DeviceOutputRows deviceId={outputsPopover.deviceId} outputs={outputsPopover.outputs} />
              </div>
            </>,
            document.body,
          )
        : null}
    </div>
  );
}

function formatInstantMaybe(iso: string) {
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return iso;
  return new Date(t).toLocaleString();
}

