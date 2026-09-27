import { useEffect, useState } from "react";

/** TEMPORARY diagnostic overlay (round 6) — a real Samsung device shows the header and
 *  bottom tabbar not spanning/appearing correctly at 100% CSS width even though nothing
 *  in our own layout can be found wrong by reading the code, and the usual suspects
 *  (browser page-zoom, OS screen-zoom, stale cache) have all been ruled out on that
 *  device. This reports the actual runtime numbers the browser is using so the real
 *  mismatch (if any, between window/document/visualViewport metrics) can be seen
 *  directly instead of guessed at. Remove once the real cause is found — this must never
 *  ship long-term. */
export function ViewportDebugBadge() {
  const [info, setInfo] = useState(() => snapshot());
  // Collapsed by default (round 6 follow-up: the full readout blocked navigation/taps
  // underneath it even with pointer-events:none on the text box, since it covered enough
  // of the screen that there was nothing tappable left in that area). Starts as a small
  // corner pill; tap it to see the full numbers, tap again to get it out of the way.
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const update = () => setInfo(snapshot());
    window.addEventListener("resize", update);
    window.visualViewport?.addEventListener("resize", update);
    window.visualViewport?.addEventListener("scroll", update);
    const interval = setInterval(update, 1000);
    return () => {
      window.removeEventListener("resize", update);
      window.visualViewport?.removeEventListener("resize", update);
      window.visualViewport?.removeEventListener("scroll", update);
      clearInterval(interval);
    };
  }, []);

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => {
          setInfo(snapshot());
          setOpen(true);
        }}
        style={{
          position: "fixed",
          top: 4,
          left: 4,
          zIndex: 99999,
          background: "#000",
          color: "#0f0",
          font: "10px monospace",
          padding: "3px 7px",
          borderRadius: 999,
          border: "1px solid #0f0",
          opacity: 0.85,
        }}
      >
        dbg
      </button>
    );
  }

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        maxWidth: "92vw",
        maxHeight: "70vh",
        overflow: "auto",
        zIndex: 99999,
        background: "#000",
        color: "#0f0",
        font: "10px/1.4 monospace",
        padding: "4px 6px",
        whiteSpace: "pre-wrap",
        opacity: 0.95,
      }}
    >
      <button
        type="button"
        onClick={() => setOpen(false)}
        style={{ float: "right", background: "#111", color: "#0f0", border: "1px solid #0f0", padding: "0 6px", marginLeft: 8 }}
      >
        ✕
      </button>
      {info}
    </div>
  );
}

function findOverflowers(clientWidth: number): string[] {
  // Walk every element and report the ones whose own box actually extends past the
  // true visible width — the real cause of document.documentElement.scrollWidth being
  // wider than clientWidth. Skip elements exactly at or slightly past the edge (rounding)
  // and skip elements with huge subtrees (report only the innermost/smallest offenders by
  // checking from the most specific elements up would be ideal, but a flat list of
  // everything overflowing, sorted by how far it overflows, is enough to spot the culprit).
  const all = document.querySelectorAll<HTMLElement>("*");
  const offenders: { desc: string; right: number }[] = [];
  all.forEach((el) => {
    const r = el.getBoundingClientRect();
    if (r.right > clientWidth + 2 && r.width > 0) {
      const cls = typeof el.className === "string" && el.className ? `.${el.className.trim().split(/\s+/).join(".")}` : "";
      offenders.push({ desc: `${el.tagName.toLowerCase()}${cls} [x=${r.x.toFixed(0)} w=${r.width.toFixed(0)} right=${r.right.toFixed(0)}]`, right: r.right });
    }
  });
  offenders.sort((a, b) => b.right - a.right);
  return offenders.slice(0, 6).map((o) => o.desc);
}

function snapshot(): string {
  const nav = document.querySelector(".nav") as HTMLElement | null;
  const navRect = nav?.getBoundingClientRect();
  const tabbar = document.querySelector(".tabbar") as HTMLElement | null;
  const tabbarRect = tabbar?.getBoundingClientRect();
  const vv = window.visualViewport;
  const clientWidth = document.documentElement.clientWidth;
  const overflowers = findOverflowers(clientWidth);
  return [
    `innerW/H: ${window.innerWidth}/${window.innerHeight}`,
    `docEl clientW/H: ${document.documentElement.clientWidth}/${document.documentElement.clientHeight}`,
    `docEl scrollW/H: ${document.documentElement.scrollWidth}/${document.documentElement.scrollHeight}`,
    `body clientW: ${document.body.clientWidth} scrollW: ${document.body.scrollWidth}`,
    `visualViewport: w=${vv?.width.toFixed(1)} h=${vv?.height.toFixed(1)} scale=${vv?.scale} offL=${vv?.offsetLeft} offT=${vv?.offsetTop.toFixed(1)}`,
    `devicePixelRatio: ${window.devicePixelRatio}`,
    `screen: ${window.screen.width}x${window.screen.height}`,
    `.nav rect: ${navRect ? `x=${navRect.x} w=${navRect.width} right=${navRect.right}` : "not found"}`,
    `.tabbar rect: ${tabbarRect ? `x=${tabbarRect.x} y=${tabbarRect.y} w=${tabbarRect.width} bottom=${tabbarRect.bottom}` : "not found"}`,
    `scrollY: ${window.scrollY}`,
    `--- overflowing past ${clientWidth}px (${overflowers.length ? "top 6 by right edge" : "NONE FOUND"}) ---`,
    ...overflowers,
  ].join("\n");
}
