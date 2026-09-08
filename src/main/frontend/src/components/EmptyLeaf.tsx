/**
 * A calm empty-state marker: a few leaves drifting down over the message.
 * Honours `prefers-reduced-motion` (the leaves simply sit still).
 */
export default function EmptyLeaf({ message }: { message: string }) {
  return (
    <div className="empty-leaf">
      <svg width="72" height="60" viewBox="0 0 72 60" aria-hidden="true">
        {[
          { cx: 16, delay: "0s", dur: "7s" },
          { cx: 38, delay: "1.8s", dur: "8.5s" },
          { cx: 56, delay: "3.4s", dur: "6.5s" },
        ].map((l, i) => (
          <path
            key={i}
            className="efl"
            style={{ animationDelay: l.delay, animationDuration: l.dur }}
            d={`M${l.cx} 6 C${l.cx - 7} 10 ${l.cx - 7} 20 ${l.cx} 24 C${l.cx + 7} 20 ${l.cx + 7} 10 ${l.cx} 6 Z`}
            fill="var(--growth)"
            opacity="0.75"
          />
        ))}
      </svg>
      <p className="muted">{message}</p>
      <style>{`
        .empty-leaf{display:flex;flex-direction:column;align-items:center;gap:6px;padding:26px 0;text-align:center}
        .efl{transform-box:fill-box;transform-origin:center;animation-name:efl-drift;animation-timing-function:ease-in-out;animation-iteration-count:infinite}
        @keyframes efl-drift{
          0%{transform:translateY(0) rotate(-8deg);opacity:.2}
          40%{opacity:.75}
          100%{transform:translateY(30px) rotate(14deg);opacity:.15}
        }
        @media (prefers-reduced-motion: reduce){.efl{animation:none;opacity:.5}}
      `}</style>
    </div>
  );
}
