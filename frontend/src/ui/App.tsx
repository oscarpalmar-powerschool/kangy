import { useCallback, useEffect, useState } from "react";
import { listRegisteredDevices } from "../api/devices";
import type { RegisteredDeviceDto } from "../api/types";

export function App() {
  const [devices, setDevices] = useState<RegisteredDeviceDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

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
              </tr>
            </thead>
            <tbody>
              {devices.length === 0 ? (
                <tr>
                  <td colSpan={5} className="muted">
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
    </div>
  );
}

function formatInstantMaybe(iso: string) {
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return iso;
  return new Date(t).toLocaleString();
}

