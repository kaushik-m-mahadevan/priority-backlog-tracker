import { useEffect } from "react";

/**
 * Design §11 — keep-alive.
 *
 * Free-tier hosts (Render and similar) idle the web service out after a few minutes
 * with no traffic; the next request then eats a cold start. While a founder has the
 * app open we pip `/actuator/health` on an interval so the dyno stays warm.
 *
 * This is a deployment convenience, not a product feature. To remove it: delete this
 * file and its one call in `Layout`. To disable it without deleting, build with
 * `VITE_KEEPALIVE=off`.
 */
const INTERVAL_MS = 4 * 60 * 1000;

const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env ?? {};

export function useKeepAlive() {
  useEffect(() => {
    if (env.VITE_KEEPALIVE === "off") return;

    const ping = () => {
      if (document.visibilityState !== "visible") return;
      fetch("/actuator/health", { method: "GET", cache: "no-store" }).catch(() => {
        /* offline or backend down — nothing useful to do here */
      });
    };

    ping();
    const id = window.setInterval(ping, INTERVAL_MS);
    return () => window.clearInterval(id);
  }, []);
}
