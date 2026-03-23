import { useRef, useState } from "react";
import { enqueueDeviceActions } from "../api/devices";

type Props = {
  deviceId: string;
  outputs: string[];
};

type QueueAllFeed = { loading?: boolean; ok?: string; err?: string };

type SendFn = () => Promise<void>;

export function DeviceOutputRows({ deviceId, outputs }: Props) {
  if (outputs.length === 0) {
    return (
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        This device did not report any outputs.
      </p>
    );
  }

  const hasLed = outputs.some((c) => c.toLowerCase() === "led");
  const hasServo = outputs.some((c) => c.toLowerCase() === "servo");

  const ledSendRef = useRef<SendFn | null>(null);
  const servoSendRef = useRef<SendFn | null>(null);

  const [queueAllFeed, setQueueAllFeed] = useState<QueueAllFeed>({});

  const queueAllOutputs = async () => {
    if (!hasLed && !hasServo) return;
    setQueueAllFeed({ loading: true });
    try {
      const ledIdx = outputs.findIndex((c) => c.toLowerCase() === "led");
      const servoIdx = outputs.findIndex((c) => c.toLowerCase() === "servo");
      const toRun: Array<{ idx: number; fn: SendFn }> = [];
      if (ledIdx !== -1 && ledSendRef.current) toRun.push({ idx: ledIdx, fn: ledSendRef.current });
      if (servoIdx !== -1 && servoSendRef.current)
        toRun.push({ idx: servoIdx, fn: servoSendRef.current });
      toRun.sort((a, b) => a.idx - b.idx);

      for (const { fn } of toRun) {
        await fn();
      }

      setQueueAllFeed({ ok: "Queued outputs." });
    } catch (e) {
      setQueueAllFeed({ err: e instanceof Error ? e.message : "Failed to queue outputs" });
    } finally {
      setQueueAllFeed((prev) => ({ ...prev, loading: false }));
    }
  };

  return (
    <>
      <ul className="popover-output-list">
        {outputs.map((cap) => {
          const key = cap.toLowerCase();
          if (key === "led") {
            return (
              <LedOutputRow
                key={cap}
                deviceId={deviceId}
                cap={cap}
                registerSend={(fn) => {
                  ledSendRef.current = fn;
                }}
              />
            );
          }
          if (key === "servo") {
            return (
              <ServoOutputRow
                key={cap}
                deviceId={deviceId}
                cap={cap}
                registerSend={(fn) => {
                  servoSendRef.current = fn;
                }}
              />
            );
          }
          return <UnknownOutputRow key={cap} cap={cap} />;
        })}
      </ul>

      <div style={{ marginTop: 10, display: "flex", flexDirection: "column", gap: 6 }}>
        <button
          type="button"
          className="btn btn-sm"
          onClick={() => void queueAllOutputs()}
          disabled={queueAllFeed.loading || (!hasLed && !hasServo)}
          aria-disabled={queueAllFeed.loading || (!hasLed && !hasServo)}
        >
          {queueAllFeed.loading ? "Queueing all…" : "Queue all outputs"}
        </button>
        {queueAllFeed.err ? <div className="popover-row-msg err">{queueAllFeed.err}</div> : null}
        {queueAllFeed.ok ? <div className="popover-row-msg ok">{queueAllFeed.ok}</div> : null}
      </div>
    </>
  );
}

type RowFeed = { loading?: boolean; ok?: string; err?: string };

function LedOutputRow({
  deviceId,
  cap,
  registerSend,
}: {
  deviceId: string;
  cap: string;
  registerSend: (send: SendFn) => void;
}) {
  const [mode, setMode] = useState<"on" | "off" | "blink">("off");
  const modeRef = useRef(mode);
  const [feed, setFeed] = useState<RowFeed>({});

  const send = async () => {
    setFeed({ loading: true });
    try {
      const modeToSend = modeRef.current;
      await enqueueDeviceActions(deviceId, [
        { type: "led.command", payload: { id: "led", mode: modeToSend } },
      ]);
      setFeed({ ok: "Queued" });
    } catch (e) {
      setFeed({ err: e instanceof Error ? e.message : "Failed to queue action" });
    }
  };

  registerSend(send);

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
                onClick={() => {
                  modeRef.current = value;
                  setMode(value);
                }}
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

function ServoOutputRow({
  deviceId,
  cap,
  registerSend,
}: {
  deviceId: string;
  cap: string;
  registerSend: (send: SendFn) => void;
}) {
  const [deg, setDeg] = useState(90);
  const degRef = useRef(deg);
  const [feed, setFeed] = useState<RowFeed>({});

  const setDegrees = (n: number) => {
    if (!Number.isFinite(n)) return;
    const v = Math.round(Math.min(180, Math.max(0, n)));
    setDeg(v);
    degRef.current = v;
  };

  const send = async () => {
    setFeed({ loading: true });
    try {
      const degToSend = degRef.current;
      await enqueueDeviceActions(deviceId, [
        { type: "servo.setPosition", payload: { id: "servo", degrees: degToSend } },
      ]);
      setFeed({ ok: "Queued" });
    } catch (e) {
      setFeed({ err: e instanceof Error ? e.message : "Failed to queue action" });
    }
  };

  registerSend(send);

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
