type ApiErrorShape = {
  message?: string;
  error?: string;
  status?: number;
};

declare global {
  interface Window {
    __KANGY_CONFIG__?: {
      apiBase?: string;
    };
  }
}

export async function apiGetJson<T>(path: string, init?: RequestInit): Promise<T> {
  const base =
    window.__KANGY_CONFIG__?.apiBase ??
    ((import.meta.env.VITE_API_BASE as string | undefined) ?? "");
  const res = await fetch(`${base}${path}`, {
    ...init,
    method: "GET",
    headers: {
      Accept: "application/json",
      ...(init?.headers ?? {}),
    },
  });

  if (!res.ok) {
    const msg = await safeReadError(res);
    throw new Error(msg);
  }

  return (await res.json()) as T;
}

async function safeReadError(res: Response): Promise<string> {
  const fallback = `${res.status} ${res.statusText}`.trim();
  try {
    const text = await res.text();
    if (!text) return fallback;
    try {
      const json = JSON.parse(text) as ApiErrorShape;
      return json.message || json.error || fallback;
    } catch {
      return text;
    }
  } catch {
    return fallback;
  }
}

