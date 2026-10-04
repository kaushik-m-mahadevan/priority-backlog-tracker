import { useEffect, useRef } from "react";

const ACTIVITY_EVENTS = ["pointerdown", "keydown", "wheel", "scroll", "touchstart"] as const;
const HEARTBEAT_EVERY_MS = 60_000;
const CHECK_EVERY_MS = 15_000;

/**
 * Watches for real user interaction. While the user is active it pings the server at most
 * once a minute (the server counts that as activity, so reading or typing without hitting
 * the API doesn't get a session expired under them); once there's been no interaction for
 * {@code timeoutMinutes} it calls {@code onIdle}. The server enforces the same timeout on
 * its own, so this is the polite, immediate half: the user sees the sign-out when it
 * happens instead of on their next click.
 */
export function useIdleSession(opts: {
  enabled: boolean;
  timeoutMinutes: number | null;
  onHeartbeat: () => void;
  onIdle: () => void;
}) {
  const { enabled, timeoutMinutes } = opts;
  const handlers = useRef(opts);
  handlers.current = opts;

  useEffect(() => {
    if (!enabled || !timeoutMinutes) return;

    let lastActive = Date.now();
    let lastHeartbeat = Date.now();
    let activeSinceHeartbeat = false;

    const onActivity = () => {
      lastActive = Date.now();
      activeSinceHeartbeat = true;
    };
    ACTIVITY_EVENTS.forEach((e) => window.addEventListener(e, onActivity, { passive: true, capture: true }));

    const tick = () => {
      const now = Date.now();
      if (now - lastActive >= timeoutMinutes * 60_000) {
        handlers.current.onIdle();
        return;
      }
      if (activeSinceHeartbeat && now - lastHeartbeat >= HEARTBEAT_EVERY_MS) {
        lastHeartbeat = now;
        activeSinceHeartbeat = false;
        handlers.current.onHeartbeat();
      }
    };
    const id = window.setInterval(tick, CHECK_EVERY_MS);
    // Background tabs throttle timers hard; check the moment the user comes back.
    document.addEventListener("visibilitychange", tick);

    return () => {
      ACTIVITY_EVENTS.forEach((e) => window.removeEventListener(e, onActivity, { capture: true }));
      document.removeEventListener("visibilitychange", tick);
      window.clearInterval(id);
    };
  }, [enabled, timeoutMinutes]);
}
