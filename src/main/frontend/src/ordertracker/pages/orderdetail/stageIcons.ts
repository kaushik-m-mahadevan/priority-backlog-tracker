/** Shared (fdup-1) between MaterialsSection and AssignmentsSection — both render a
 *  work-stage's icon next to its label. Named constants instead of the string literal
 *  duplicated between STAGE_ICONS and a hoursBased check, so a rename can't silently
 *  desync the two (mirrors BusinessConfig.STAGE_CROCHETING/STAGE_ASSEMBLY on the backend). */
export const STAGE_CROCHETING = "crocheting";
export const STAGE_ASSEMBLY = "assembly";

/** Work stages are configurable per business, so this is a best-effort visual cue for the
 *  common ones rather than a strict mapping — an unrecognized stageKey still gets a
 *  sensible generic icon rather than nothing. */
const STAGE_ICONS: Record<string, string> = {
  [STAGE_CROCHETING]: "🧶",
  [STAGE_ASSEMBLY]: "🧵",
  packaging: "📦",
  shipment: "🚚",
};

export function stageIcon(stageKey: string): string {
  return STAGE_ICONS[stageKey.toLowerCase()] ?? "🔧";
}
