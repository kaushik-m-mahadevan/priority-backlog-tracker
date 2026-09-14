import { useEffect, useState } from "react";
import { api } from "../api/client";
import { useGroups } from "../groups/GroupContext";
import { useGroveSettings } from "../grove/GroveSettingsContext";
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
 *  it's the differing crop + render size between the two that changes how big it looks.
 *  The x values are set so each figure's blade lands on the trunk at x=75 on the swing's
 *  impact frame; moving the trunk means re-deriving them. The second one's swing is
 *  offset half a cycle so the pair don't chop in lockstep. */
const LUMBERJACK_HEIGHT = 20;
const LUMBERJACKS = [
  { x: 61.7, flip: false, delay: undefined }, // stands left of the trunk, swings right
  { x: 74, flip: true, delay: "-0.37s" }, // mirrored, stands right of the trunk
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
  const { enabled: animations } = useGroveSettings();
  const [stats, setStats] = useState<CompletionStats | null>(null);
  const [sick, setSick] = useState(0);
  const [tick, setTick] = useState(0);
  // count===0 is both "genuinely nothing completed yet" and "haven't heard back from the
  // API yet" -- without this flag the tree renders the stage-0 seedling as a default
  // during every load, which reads as real state rather than "still loading."
  const [loading, setLoading] = useState(true);

  // every mounted Grove refreshes when anything completes an item, anywhere
  useItemsChanged(() => setTick((t) => t + 1));

  useEffect(() => {
    if (!currentGroupId) {
      setStats({ count: 0, days: 30, lastCompletedAt: null });
      setSick(0);
      setLoading(false);
      return;
    }
    setLoading(true);
    api
      .get<CompletionStats>(`/insights/completions?days=30&groupId=${currentGroupId}`)
      .then(setStats)
      .catch(() => setStats({ count: 0, days: 30, lastCompletedAt: null }))
      .finally(() => setLoading(false));

    if (animations) {
      api
        .get<GroveHealth>(`/insights/health?groupId=${currentGroupId}`)
        .then((h) => setSick(h.stage))
        .catch(() => setSick(0));
    } else {
      setSick(0);
    }
  }, [refreshKey, tick, currentGroupId, animations]);

  if (loading) {
    // nothing, not the stage-0 seedling -- an empty box the same size as the real thing,
    // so nothing jumps once data arrives.
    return compact ? (
      <div className="grove compact" style={{ width: 52, height: 46 }} />
    ) : (
      <div className={solo ? "grove solo" : "grove"} style={{ width: solo ? 224 : 150, height: solo ? 176 : 118 }} />
    );
  }

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
    /* The swing is three drawn poses cross-cut on the beat (see Lumberjack.tsx), so all
       that animates here is which one is visible: wound back and held, one fast pass
       frame, then impact held. Animating opacity also keeps this clear of the trap that
       bit the wilt animation — a CSS transform on an element that carries an SVG
       transform attribute drops the attribute outright. */
    .pose-a{animation:ljPoseA .75s linear var(--lj-delay,0s) infinite}
    .pose-b{animation:ljPoseB .75s linear var(--lj-delay,0s) infinite;opacity:0}
    .pose-c{animation:ljPoseC .75s linear var(--lj-delay,0s) infinite;opacity:0}
    @keyframes ljPoseA{0%,39.9%{opacity:1}40%,89.9%{opacity:0}90%,100%{opacity:1}}
    @keyframes ljPoseB{0%,39.9%{opacity:0}40%,47.9%{opacity:1}48%,77.9%{opacity:0}78%,89.9%{opacity:1}90%,100%{opacity:0}}
    @keyframes ljPoseC{0%,47.9%{opacity:0}48%,77.9%{opacity:1}78%,100%{opacity:0}}
    /* flash at the bite, on the impact frame only */
    .lj-spark{
      animation: ljSpark .75s ease-out var(--lj-delay,0s) infinite;
      transform-box: fill-box; transform-origin: center; opacity: 0;
    }
    @keyframes ljSpark{
      0%,47%{opacity:0;transform:scale(.4)}
      50%   {opacity:1;transform:scale(1)}
      58%   {opacity:1;transform:scale(1.15)}
      68%   {opacity:0;transform:scale(1.3)}
      100%  {opacity:0;transform:scale(.4)}
    }
    /* trunk + canopy jolt as the blade lands, then settle before the next swing */
    .tree-shake{
      animation: treeHit .75s ease-out infinite;
      transform-box: view-box; transform-origin: 75px 104px;
    }
    @keyframes treeHit {
      0%, 47%   { transform: translate(0,0) rotate(0); }
      50%   { transform: translate(0.9px,-0.2px) rotate(0.8deg); }
      56%   { transform: translate(-0.6px,0.1px) rotate(-0.5deg); }
      62%, 100% { transform: translate(0,0) rotate(0); }
    }
    @media (prefers-reduced-motion:reduce){
      .pose-a,.pose-b,.pose-c,.lj-spark,.tree-shake{animation:none}
      .pose-b,.pose-c,.lj-spark{opacity:0}
      .leaf{animation:none}
    }
  `;

  if (compact) {
    const wilt = sick >= 1;
    return (
      <div className="grove compact" title={say}>
        <svg width="52" height="46" viewBox="35 58 80 48" aria-hidden="true">
          <path d="M18 104 H132" stroke="var(--border)" strokeWidth="2" strokeLinecap="round" />
          <g className={sick >= 2 ? "tree-shake" : undefined}>
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
          </g>
          {LUMBERJACKS.slice(0, sick >= 3 ? 2 : sick >= 2 ? 1 : 0).map((lj) => (
            <Lumberjack
              key={lj.x}
              x={lj.x}
              y={104 - LUMBERJACK_HEIGHT}
              height={LUMBERJACK_HEIGHT}
              flip={lj.flip}
              delay={lj.delay}
            />
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
            <g className={sick >= 2 ? "tree-shake" : undefined}>
              {/* trunk */}
              <path
                d={`M75 104 V${104 - 18 - stage * 6}`}
                stroke="var(--text-dim)"
                strokeWidth={2 + stage * 0.6}
                strokeLinecap="round"
                fill="none"
              />
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
            </g>
            {/* lumberjacks */}
            {LUMBERJACKS.slice(0, sick >= 3 ? 2 : sick >= 2 ? 1 : 0).map((lj) => (
              <Lumberjack
                key={lj.x}
                x={lj.x}
                y={104 - LUMBERJACK_HEIGHT}
                height={LUMBERJACK_HEIGHT}
                flip={lj.flip}
                delay={lj.delay}
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
