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

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        zIndex: 99999,
        background: "#000",
        color: "#0f0",
        font: "10px/1.4 monospace",
        padding: "4px 6px",
        whiteSpace: "pre",
        pointerEvents: "none",
        opacity: 0.92,
      }}
    >
      {info}
    </div>
  );
}

function snapshot(): string {
  const nav = document.querySelector(".nav") as HTMLElement | null;
  const navRect = nav?.getBoundingClientRect();
  const tabbar = document.querySelector(".tabbar") as HTMLElement | null;
  const tabbarRect = tabbar?.getBoundingClientRect();
  const vv = window.visualViewport;
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
  ].join("\n");
}
