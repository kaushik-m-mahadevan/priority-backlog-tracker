import { useEffect, useState } from "react";
import { api } from "../api/client";
import { Creature } from "./Creature";
import type { CompletionStats } from "../types";

function stageFor(n: number): number {
  if (n >= 21) return 5;
  if (n >= 11) return 4;
  if (n >= 6) return 3;
  if (n >= 3) return 2;
  if (n >= 1) return 1;
  return 0;
}

export default function Grove({
  compact = false,
  solo = false,
  refreshKey = 0,
}: {
  compact?: boolean;
  solo?: boolean;
  refreshKey?: number;
}) {
  const [stats, setStats] = useState<CompletionStats | null>(null);

  useEffect(() => {
    api
      .get<CompletionStats>("/insights/completions?days=30")
      .then(setStats)
      .catch(() => setStats({ count: 0, days: 30, lastCompletedAt: null }));
  }, [refreshKey]);

  const count = stats?.count ?? 0;
  const stage = stageFor(count);
  const last = stats?.lastCompletedAt ? Date.parse(stats.lastCompletedAt) : 0;
  const resting = last === 0 || Date.now() - last > 7 * 864e5;

  const say =
    stage === 0 && count === 0
      ? "a fresh plot. finish something to plant the first seed."
      : resting
        ? "resting for now — whenever you're ready."
        : stage >= 5
          ? `in full bloom · ${count} finished this month`
          : `${count} done this month · coming along nicely`;

  if (compact) {
    return (
      <div className="grove compact" title={say}>
        <svg width="52" height="46" viewBox="35 58 80 48" aria-hidden="true">
          <path d="M18 104 H132" stroke="var(--border)" strokeWidth="2" strokeLinecap="round" />
          <path
            d={`M75 104 V${104 - 18 - stage * 6}`}
            stroke="var(--text-dim)"
            strokeWidth={2 + stage * 0.6}
            strokeLinecap="round"
          />
          {stage === 0 ? (
            <path d="M75 86 C68 84 65 78 65 78 C72 78 75 84 75 86Z" fill="var(--growth)" />
          ) : (
            <g fill="var(--growth)" opacity={resting ? 0.4 : 0.9}>
              <circle cx="75" cy={78 - stage * 5} r={10 + stage * 5} />
              {stage >= 2 && <circle cx={62 - stage} cy={84 - stage * 3} r={7 + stage * 3} />}
              {stage >= 2 && <circle cx={88 + stage} cy={84 - stage * 3} r={7 + stage * 3} />}
            </g>
          )}
        </svg>
        <span className="say">{say}</span>
      </div>
    );
  }

  return (
    <div className={solo ? "grove solo" : "grove"}>
      <svg
        width={solo ? 224 : 150}
        height={solo ? 176 : 118}
        viewBox="0 0 150 118"
        aria-hidden="true"
      >
        <path d="M18 104 H132" stroke="var(--border)" strokeWidth="2" strokeLinecap="round" />
        {/* trunk */}
        <path
          d={`M75 104 V${104 - 18 - stage * 6}`}
          stroke="var(--text-dim)"
          strokeWidth={2 + stage * 0.6}
          strokeLinecap="round"
          fill="none"
        />
        {/* canopy grows with stage */}
        {stage === 0 && (
          <path
            d="M75 86 C68 84 65 78 65 78 C72 78 75 84 75 86Z"
            fill="var(--growth)"
            opacity="0.9"
          />
        )}
        {stage >= 1 && (
          <g fill="var(--growth)" opacity={resting ? 0.4 : 0.9}>
            <circle cx="75" cy={78 - stage * 5} r={10 + stage * 5} />
            {stage >= 2 && <circle cx={62 - stage} cy={84 - stage * 3} r={7 + stage * 3} />}
            {stage >= 2 && <circle cx={88 + stage} cy={84 - stage * 3} r={7 + stage * 3} />}
            {stage >= 4 && <circle cx="75" cy={62 - stage * 4} r={9 + stage * 2} />}
          </g>
        )}
        {stage >= 5 && !resting && (
          <g fill="var(--accent)">
            <circle cx="64" cy="52" r="2.4" />
            <circle cx="86" cy="46" r="2.4" />
            <circle cx="75" cy="38" r="2.4" />
            <circle cx="92" cy="60" r="2.4" />
          </g>
        )}
        {/* shed leaves when resting */}
        {resting && stage >= 1 && (
          <g fill="var(--growth)" opacity="0.7">
            <ellipse className="leaf leaf1" cx="55" cy="92" rx="3" ry="1.7" />
            <ellipse className="leaf leaf2" cx="96" cy="88" rx="3" ry="1.7" />
            <ellipse className="leaf leaf3" cx="78" cy="96" rx="3" ry="1.7" />
          </g>
        )}
      </svg>
      <div style={{ marginTop: -8 }}>
        <Creature seed="grove-keeper" size={26} />
      </div>
      <div className="say">{say}</div>
      <style>{`
        .leaf{animation:drift 6s ease-in-out infinite}
        .leaf2{animation-delay:1.5s}
        .leaf3{animation-delay:3s}
        @keyframes drift{
          0%{transform:translate(0,0) rotate(0);opacity:.7}
          50%{transform:translate(-4px,6px) rotate(20deg);opacity:.4}
          100%{transform:translate(0,0) rotate(0);opacity:.7}
        }
      `}</style>
    </div>
  );
}
