import { useEffect, useState } from "react";
import { api } from "../api/client";
import { useGroups } from "../groups/GroupContext";
import { useAuth } from "../auth/AuthContext";
import { useItemsChanged } from "../lib/events";
import { Creature } from "./Creature";
import { Lumberjack } from "./Lumberjack";
import type { CompletionStats, GroveHealth } from "../types";

function stageFor(n: number): number {
  if (n >= 21) return 5;
  if (n >= 11) return 4;
  if (n >= 6) return 3;
  if (n >= 3) return 2;
  if (n >= 1) return 1;
  return 0;
}

/** Same absolute placement in both the compact and full Grove — like the canopy/trunk,
 *  it's the differing crop + render size between the two that changes how big it looks. */
const LUMBERJACK_HEIGHT = 20;
const LUMBERJACKS = [
  { x: 54, flip: false }, // approaches from the left, axe swings toward the trunk
  { x: 93, flip: true }, // approaches from the right
];

export default function Grove({
  compact = false,
  solo = false,
  refreshKey = 0,
}: {
  compact?: boolean;
  solo?: boolean;
  refreshKey?: number;
}) {
  const { currentGroupId } = useGroups();
  const { user } = useAuth();
  const animations = !!user?.animationsEnabled;
  const [stats, setStats] = useState<CompletionStats | null>(null);
  const [sick, setSick] = useState(0);
  const [tick, setTick] = useState(0);

  // every mounted Grove refreshes when anything completes an item, anywhere
  useItemsChanged(() => setTick((t) => t + 1));

  useEffect(() => {
    if (!currentGroupId) {
      setStats({ count: 0, days: 30, lastCompletedAt: null });
      setSick(0);
      return;
    }
    api
      .get<CompletionStats>(`/insights/completions?days=30&groupId=${currentGroupId}`)
      .then(setStats)
      .catch(() => setStats({ count: 0, days: 30, lastCompletedAt: null }));

    if (animations) {
      api
        .get<GroveHealth>(`/insights/health?groupId=${currentGroupId}`)
        .then((h) => setSick(h.stage))
        .catch(() => setSick(0));
    } else {
      setSick(0);
    }
  }, [refreshKey, tick, currentGroupId, animations]);

  const count = stats?.count ?? 0;
  const stage = stageFor(count);
  const last = stats?.lastCompletedAt ? Date.parse(stats.lastCompletedAt) : 0;
  const resting = last === 0 || Date.now() - last > 7 * 864e5;

  const say = sick
    ? [
        "",
        "the grove is wilting — a few things are overdue.",
        "someone's at the tree. overdue work is piling up.",
        "the grove is under the axe. clear the overdue items.",
        "the grove is a stump. it grows back as you catch up.",
      ][sick]
    : stage === 0 && count === 0
      ? "a fresh plot. finish something to plant the first seed."
      : resting
        ? "resting for now — whenever you're ready."
        : stage >= 5
          ? `in full bloom · ${count} finished this month`
          : `${count} done this month · coming along nicely`;

  const sufferStyle = `
    .wilt{filter:saturate(.3) brightness(.8);transition:filter .6s ease}
    /* animate the arm sub-group only — an ancestor .lumberjack <g> carries an SVG
       transform="translate(...) scale(...)" attribute, and a CSS transform (including
       one driven by an animation) on that SAME element overrides the attribute outright,
       snapping the whole figure to the origin instead of rotating it in place. */
    .lj-arm-swing{animation:chop 1.7s cubic-bezier(.5,0,.3,1) infinite;transform-box:view-box;transform-origin:27.5px 18.5px}
    @keyframes chop{0%,100%{transform:rotate(0)}55%{transform:rotate(-40deg)}}
    @media (prefers-reduced-motion:reduce){.lj-arm-swing{animation:none}.leaf{animation:none}}
  `;

  if (compact) {
    const wilt = sick >= 1;
    return (
      <div className="grove compact" title={say}>
        <svg width="52" height="46" viewBox="35 58 80 48" aria-hidden="true">
          <path d="M18 104 H132" stroke="var(--border)" strokeWidth="2" strokeLinecap="round" />
          <path
            d={`M75 104 V${sick >= 4 ? 98 : 104 - 18 - stage * 6}`}
            stroke="var(--text-dim)"
            strokeWidth={sick >= 4 ? 6 : 2 + stage * 0.6}
            strokeLinecap="round"
          />
          {sick < 4 &&
            (stage === 0 ? (
              <path
                d="M75 86 C68 84 65 78 65 78 C72 78 75 84 75 86Z"
                fill="var(--growth)"
                className={wilt ? "wilt" : undefined}
              />
            ) : (
              <g
                fill="var(--growth)"
                opacity={resting ? 0.4 : 0.9}
                className={wilt ? "wilt" : undefined}
              >
                <circle cx="75" cy={78 - stage * 5} r={(10 + stage * 5) * (wilt ? 0.8 : 1)} />
                {stage >= 2 && <circle cx={62 - stage} cy={84 - stage * 3} r={7 + stage * 3} />}
                {stage >= 2 && <circle cx={88 + stage} cy={84 - stage * 3} r={7 + stage * 3} />}
              </g>
            ))}
          {LUMBERJACKS.slice(0, sick >= 3 ? 2 : sick >= 2 ? 1 : 0).map((lj) => (
            <Lumberjack key={lj.x} x={lj.x} y={104 - LUMBERJACK_HEIGHT} height={LUMBERJACK_HEIGHT} flip={lj.flip} />
          ))}
          <style>{sufferStyle}</style>
        </svg>
        <span className="say">{say}</span>
      </div>
    );
  }

  const wilt = sick >= 1;
  const canopyScale = wilt ? 0.82 : 1;

  return (
    <div className={solo ? "grove solo" : "grove"}>
      <svg width={solo ? 224 : 150} height={solo ? 176 : 118} viewBox="0 0 150 118" aria-hidden="true">
        <path d="M18 104 H132" stroke="var(--border)" strokeWidth="2" strokeLinecap="round" />

        {sick >= 4 ? (
          <>
            {/* stump */}
            <path d="M75 104 V96" stroke="var(--text-dim)" strokeWidth="6" strokeLinecap="round" />
            <ellipse cx="75" cy="95" rx="4.5" ry="1.7" fill="var(--text-dim)" opacity="0.55" />
            <g fill="var(--growth)" opacity="0.45">
              <ellipse cx="59" cy="103" rx="3" ry="1.6" />
              <ellipse cx="91" cy="102" rx="3" ry="1.6" />
              <ellipse cx="71" cy="105.5" rx="3" ry="1.6" />
            </g>
          </>
        ) : (
          <>
            {/* trunk */}
            <path
              d={`M75 104 V${104 - 18 - stage * 6}`}
              stroke="var(--text-dim)"
              strokeWidth={2 + stage * 0.6}
              strokeLinecap="round"
              fill="none"
            />
            {/* chop notch */}
            {sick >= 2 && (
              <path
                d="M75 101 l7 -3.5 l0 7 Z"
                fill="var(--bg-elev)"
                stroke="var(--text-dim)"
                strokeWidth="0.8"
              />
            )}
            {/* canopy */}
            {stage === 0 && (
              <path
                d="M75 86 C68 84 65 78 65 78 C72 78 75 84 75 86Z"
                fill="var(--growth)"
                opacity="0.9"
                className={wilt ? "wilt" : undefined}
              />
            )}
            {stage >= 1 && (
              <g
                fill="var(--growth)"
                opacity={resting ? 0.4 : 0.9}
                className={wilt ? "wilt" : undefined}
              >
                <circle cx="75" cy={78 - stage * 5} r={(10 + stage * 5) * canopyScale} />
                {stage >= 2 && (
                  <circle cx={62 - stage} cy={84 - stage * 3} r={(7 + stage * 3) * canopyScale} />
                )}
                {stage >= 2 && (
                  <circle cx={88 + stage} cy={84 - stage * 3} r={(7 + stage * 3) * canopyScale} />
                )}
                {stage >= 4 && <circle cx="75" cy={62 - stage * 4} r={9 + stage * 2} />}
              </g>
            )}
            {stage >= 5 && !resting && !sick && (
              <g fill="var(--accent)">
                <circle cx="64" cy="52" r="2.4" />
                <circle cx="86" cy="46" r="2.4" />
                <circle cx="75" cy="38" r="2.4" />
                <circle cx="92" cy="60" r="2.4" />
              </g>
            )}
            {/* drooping / shed leaves */}
            {(resting || wilt) && stage >= 1 && (
              <g fill="var(--growth)" opacity="0.7">
                <ellipse className="leaf leaf1" cx="55" cy="92" rx="3" ry="1.7" />
                <ellipse className="leaf leaf2" cx="96" cy="88" rx="3" ry="1.7" />
                <ellipse className="leaf leaf3" cx="78" cy="96" rx="3" ry="1.7" />
              </g>
            )}
            {/* lumberjacks */}
            {LUMBERJACKS.slice(0, sick >= 3 ? 2 : sick >= 2 ? 1 : 0).map((lj) => (
              <Lumberjack
                key={lj.x}
                x={lj.x}
                y={104 - LUMBERJACK_HEIGHT}
                height={LUMBERJACK_HEIGHT}
                flip={lj.flip}
              />
            ))}
          </>
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
        ${sufferStyle}
      `}</style>
    </div>
  );
}
