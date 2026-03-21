import { useState } from "react";
import { enqueueDeviceActions } from "../api/devices";

type Props = {
  deviceId: string;
  outputs: string[];
};

export function DeviceOutputRows({ deviceId, outputs }: Props) {
  if (outputs.length === 0) {
    return (
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        This device did not report any outputs.
      </p>
    );
  }

  return (
    <ul className="popover-output-list">
      {outputs.map((cap) => {
        const key = cap.toLowerCase();
        if (key === "led") {
          return <LedOutputRow key={cap} deviceId={deviceId} cap={cap} />;
        }
        if (key === "servo") {
          return <ServoOutputRow key={cap} deviceId={deviceId} cap={cap} />;
        }
        return <UnknownOutputRow key={cap} cap={cap} />;
      })}
    </ul>
  );
}

type RowFeed = { loading?: boolean; ok?: string; err?: string };

function LedOutputRow({ deviceId, cap }: { deviceId: string; cap: string }) {
  const [mode, setMode] = useState<"on" | "off" | "blink">("off");
  const [feed, setFeed] = useState<RowFeed>({});

  const send = async () => {
    setFeed({ loading: true });
    try {
      await enqueueDeviceActions(deviceId, [
        { type: "led.command", payload: { id: "led", mode } },
      ]);
      setFeed({ ok: "Queued" });
    } catch (e) {
      setFeed({ err: e instanceof Error ? e.message : "Failed to queue action" });
    }
  };

  return (
    <li className="popover-output-item">
      <div className="popover-output-head">
        <span>{formatOutputCapabilityLabel(cap)}</span>
        <span className="popover-output-kind">{cap}</span>
      </div>
      <div className="popover-output-controls popover-output-controls--led">
        <div className="control-label control-label--full">
          <span className="control-label-text">Mode</span>
          <div className="mode-segment" role="group" aria-label="LED mode">
            {(
              [
                { value: "on" as const, label: "On" },
                { value: "off" as const, label: "Off" },
                { value: "blink" as const, label: "Blink" },
              ] as const
            ).map(({ value, label }) => (
              <button
                key={value}
                type="button"
                className={
                  mode === value ? "mode-segment__btn mode-segment__btn--active" : "mode-segment__btn"
                }
                aria-pressed={mode === value}
                onClick={() => setMode(value)}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
        <button type="button" className="btn btn-sm" onClick={() => void send()} disabled={!!feed.loading}>
          {feed.loading ? "Sending…" : "Queue"}
        </button>
      </div>
      {feed.err ? <div className="popover-row-msg err">{feed.err}</div> : null}
      {feed.ok ? <div className="popover-row-msg ok">{feed.ok}</div> : null}
    </li>
  );
}

function ServoOutputRow({ deviceId, cap }: { deviceId: string; cap: string }) {
  const [deg, setDeg] = useState(90);
  const [feed, setFeed] = useState<RowFeed>({});

  const setDegrees = (n: number) => {
    if (!Number.isFinite(n)) return;
    const v = Math.round(Math.min(180, Math.max(0, n)));
    setDeg(v);
  };

  const send = async () => {
    setFeed({ loading: true });
    try {
      await enqueueDeviceActions(deviceId, [
        { type: "servo.setPosition", payload: { id: "servo", degrees: deg } },
      ]);
      setFeed({ ok: "Queued" });
    } catch (e) {
      setFeed({ err: e instanceof Error ? e.message : "Failed to queue action" });
    }
  };

  return (
    <li className="popover-output-item">
      <div className="popover-output-head">
        <span>{formatOutputCapabilityLabel(cap)}</span>
        <span className="popover-output-kind">{cap}</span>
      </div>
      <div className="popover-output-controls popover-output-controls--stack">
        <label className="control-label control-label--full">
          <span className="control-label-text">Degrees ({deg}°)</span>
          <input
            type="range"
            className="control-range"
            min={0}
            max={180}
            value={deg}
            onChange={(e) => setDegrees(Number(e.target.value))}
          />
        </label>
        <div className="control-inline">
          <input
            type="number"
            className="control-number"
            min={0}
            max={180}
            value={deg}
            onChange={(e) => setDegrees(Number(e.target.value))}
          />
          <button type="button" className="btn btn-sm" onClick={() => void send()} disabled={!!feed.loading}>
            {feed.loading ? "Sending…" : "Queue"}
          </button>
        </div>
      </div>
      {feed.err ? <div className="popover-row-msg err">{feed.err}</div> : null}
      {feed.ok ? <div className="popover-row-msg ok">{feed.ok}</div> : null}
    </li>
  );
}

function UnknownOutputRow({ cap }: { cap: string }) {
  return (
    <li className="popover-output-item">
      <div className="popover-output-head">
        <span>{formatOutputCapabilityLabel(cap)}</span>
        <span className="popover-output-kind">{cap}</span>
      </div>
      <p className="muted popover-unknown-note">No controls for this output yet.</p>
    </li>
  );
}

function formatOutputCapabilityLabel(cap: string): string {
  const lower = cap.toLowerCase();
  if (lower === "led") return "LED";
  return cap.length ? cap.charAt(0).toUpperCase() + cap.slice(1).toLowerCase() : cap;
}
