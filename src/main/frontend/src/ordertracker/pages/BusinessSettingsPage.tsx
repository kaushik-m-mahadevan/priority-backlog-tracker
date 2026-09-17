import { useEffect, useState } from "react";
import { orderTrackerApi } from "../api";
import { useAuth } from "../../auth/AuthContext";
import { useBusiness } from "../BusinessContext";
import type { BusinessConfig, ComponentTemplate, CostConfigChangeRequest, Creator, PresetOption } from "../types";

const toRecipeSteps = (text: string): string[] =>
  text.split("\n").map((s) => s.trim()).filter(Boolean);

export default function BusinessSettingsPage() {
  const { currentGroupId, currentBusiness } = useBusiness();
  const { user } = useAuth();
  const groupId = currentGroupId!;
  const [config, setConfig] = useState<BusinessConfig | null>(null);
  const [profile, setProfile] = useState<Creator | null>(null);
  const [presets, setPresets] = useState<PresetOption[]>([]);
  const [templates, setTemplates] = useState<ComponentTemplate[]>([]);
  const [changeRequests, setChangeRequests] = useState<CostConfigChangeRequest[]>([]);
  const [baseLocation, setBaseLocation] = useState("");
  const [hoursPerDay, setHoursPerDay] = useState(4);
  const [presetLabel, setPresetLabel] = useState("");
  const [presetCost, setPresetCost] = useState(0);
  const [presetHours, setPresetHours] = useState(0);
  const [templateLabel, setTemplateLabel] = useState("");
  const [templateHours, setTemplateHours] = useState(0);
  const [templateRecipe, setTemplateRecipe] = useState("");
  const [templateNotes, setTemplateNotes] = useState("");
  const [proposedOverhead, setProposedOverhead] = useState(0);
  const [proposedMargin, setProposedMargin] = useState(0);
  const [costConfigError, setCostConfigError] = useState<string | null>(null);

  const load = async () => {
    const [cfg, me, p, t, changes] = await Promise.all([
      orderTrackerApi.businessConfig(groupId),
      orderTrackerApi.myCreatorProfile(groupId).catch(() => null),
      orderTrackerApi.packagingPresets(groupId),
      orderTrackerApi.componentTemplates(groupId),
      orderTrackerApi.costConfigChangeRequests(groupId),
    ]);
    setConfig(cfg);
    setProfile(me ?? null);
    setPresets(p);
    setTemplates(t);
    setChangeRequests(changes);
    setProposedOverhead(cfg.overheadPercentage * 100);
    setProposedMargin(cfg.profitMarginPercentage * 100);
    if (me) {
      setBaseLocation(me.baseLocation);
      setHoursPerDay(me.hoursAvailablePerDay);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId]);

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!baseLocation.trim()) return;
    await orderTrackerApi.upsertMyCreatorProfile(groupId, { baseLocation: baseLocation.trim(), hoursAvailablePerDay: hoursPerDay });
    await load();
  };

  const addPreset = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!presetLabel.trim()) return;
    await orderTrackerApi.addPackagingPreset(groupId, { label: presetLabel.trim(), estimatedCost: presetCost, estimatedTimeHours: presetHours });
    setPresetLabel("");
    setPresetCost(0);
    setPresetHours(0);
    await load();
  };

  const addTemplate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!templateLabel.trim()) return;
    await orderTrackerApi.addComponentTemplate(groupId, {
      label: templateLabel.trim(),
      pattern: { patternType: null, templateName: null, customPatternNotes: null, attachmentUrls: [], recipeSteps: toRecipeSteps(templateRecipe) },
      baseCraftingTimeHours: templateHours,
      notes: templateNotes.trim() || null,
    });
    setTemplateLabel("");
    setTemplateHours(0);
    setTemplateRecipe("");
    setTemplateNotes("");
    await load();
  };

  const removeTemplate = async (templateId: string) => {
    await orderTrackerApi.removeComponentTemplate(groupId, templateId);
    await load();
  };

  const pendingChangeRequest = changeRequests.find((c) => c.status === "PENDING") ?? null;

  const proposeCostConfigChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setCostConfigError(null);
    try {
      const request = await orderTrackerApi.proposeCostConfigChange(groupId, proposedOverhead / 100, proposedMargin / 100);
      setChangeRequests((prev) => [...prev, request]);
      if (request.status === "APPROVED") {
        await load();
      }
    } catch (err) {
      setCostConfigError(err instanceof Error ? err.message : "Failed to propose change");
    }
  };

  const respondToChangeRequest = async (requestId: string, approve: boolean) => {
    setCostConfigError(null);
    try {
      const updated = approve
        ? await orderTrackerApi.approveCostConfigChange(groupId, requestId)
        : await orderTrackerApi.rejectCostConfigChange(groupId, requestId);
      setChangeRequests((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
      if (updated.status === "APPROVED") {
        await load();
      }
    } catch (err) {
      setCostConfigError(err instanceof Error ? err.message : "Failed to respond to change request");
    }
  };

  if (!config) return <p className="muted">Loading…</p>;

  return (
    <div>
      <h1 className="page-title">{currentBusiness?.name}</h1>
      <p className="page-sub">Business configuration for this Order Tracker workspace.</p>

      <div className="grid cols-2">
        <div className="card">
          <h2>Your creator profile</h2>
          <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
            Sets your base location (used for order-number location codes) and how many hours a day you can craft.
          </p>
          <form onSubmit={saveProfile}>
            <div className="form-row">
              <label htmlFor="bs-base-location">Base location</label>
              <input id="bs-base-location" value={baseLocation} onChange={(e) => setBaseLocation(e.target.value)} required />
            </div>
            <div className="form-row">
              <label htmlFor="bs-hours-per-day">Hours available per day</label>
              <input
                id="bs-hours-per-day"
                type="number"
                min={0.5}
                step={0.5}
                value={hoursPerDay}
                onChange={(e) => setHoursPerDay(Number(e.target.value))}
              />
            </div>
            <button className="primary" type="submit">
              Save
            </button>
            {profile && (
              <p className="mono" style={{ marginTop: 10 }}>
                Creator code {profile.creatorCode} · Location code {profile.locationCode}
              </p>
            )}
          </form>
        </div>

        <div className="card">
          <h2>Business summary</h2>
          <p>
            Currency: <strong>{config.currency}</strong>
          </p>
          <p>
            Overhead: <strong>{(config.overheadPercentage * 100).toFixed(0)}%</strong> · Profit margin:{" "}
            <strong>{(config.profitMarginPercentage * 100).toFixed(0)}%</strong>
          </p>
          <p>
            Order type codes — individual <span className="mono">{config.individualOrderTypeCode}</span>, bulk{" "}
            <span className="mono">{config.bulkOrderTypeCode}</span>
          </p>
          <p className="muted" style={{ fontSize: 13 }}>
            Work stages: {config.workStages.map((s) => s.label).join(" → ")}
          </p>
        </div>
      </div>

      <h2 className="settings-section">Overhead &amp; profit margin</h2>
      <div className="card">
        <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
          These change what every existing order costs, so a change only takes effect once every member of{" "}
          {currentBusiness?.name} has approved it.
        </p>
        {costConfigError && <div className="error">{costConfigError}</div>}

        {pendingChangeRequest ? (
          <div className="card" style={{ background: "var(--bg-elev-2)" }}>
            <p>
              Proposed: overhead <strong>{(pendingChangeRequest.proposedOverheadPercentage * 100).toFixed(0)}%</strong>,
              profit margin <strong>{(pendingChangeRequest.proposedProfitMarginPercentage * 100).toFixed(0)}%</strong>
            </p>
            <p className="muted" style={{ fontSize: 13 }}>
              Approved by {pendingChangeRequest.approvedByUserIds.length} member(s) so far.
            </p>
            {user && pendingChangeRequest.approvedByUserIds.includes(user.id) ? (
              <p className="hint">You've approved this — waiting on everyone else.</p>
            ) : (
              <div className="toolbar">
                <button className="primary" onClick={() => respondToChangeRequest(pendingChangeRequest.id, true)}>
                  Approve
                </button>
                <button onClick={() => respondToChangeRequest(pendingChangeRequest.id, false)}>Reject</button>
              </div>
            )}
          </div>
        ) : (
          <form className="form-grid" onSubmit={proposeCostConfigChange}>
            <div className="form-row">
              <label htmlFor="bs-overhead">Overhead %</label>
              <input id="bs-overhead" type="number" min={0} step={1} value={proposedOverhead} onChange={(e) => setProposedOverhead(Number(e.target.value))} />
            </div>
            <div className="form-row">
              <label htmlFor="bs-margin">Profit margin %</label>
              <input id="bs-margin" type="number" min={0} step={1} value={proposedMargin} onChange={(e) => setProposedMargin(Number(e.target.value))} />
            </div>
            <button className="primary" type="submit">
              Propose change
            </button>
          </form>
        )}
      </div>

      <h2 className="settings-section">Packaging presets</h2>
      <div className="card">
        <form className="toolbar" onSubmit={addPreset}>
          <input aria-label="New preset label" placeholder="Label" value={presetLabel} onChange={(e) => setPresetLabel(e.target.value)} required />
          <input
            aria-label="New preset cost"
            type="number"
            placeholder="Cost"
            value={presetCost || ""}
            onChange={(e) => setPresetCost(Number(e.target.value))}
          />
          <input
            aria-label="New preset time in hours"
            type="number"
            placeholder="Time (hours)"
            step={0.05}
            value={presetHours || ""}
            onChange={(e) => setPresetHours(Number(e.target.value))}
          />
          <button className="primary" type="submit">
            Add
          </button>
        </form>
        {presets.length === 0 ? (
          <p className="empty">No packaging presets yet.</p>
        ) : (
          <div className="kv" style={{ marginTop: 12 }}>
            {presets.map((p) => (
              <span key={p.id} className="chip">
                {p.label} · {config.currency} {p.estimatedCost} · {p.estimatedTimeHours}h
              </span>
            ))}
          </div>
        )}
      </div>

      <h2 className="settings-section">Component templates</h2>
      <div className="card">
        <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
          Reusable atomic pieces — "Lily", "Vase base", "Sunflower" — each with a pattern and a
          typical crochet time, so an order can reference one instead of re-describing it every time.
        </p>
        <form className="form-grid" onSubmit={addTemplate}>
          <div className="form-row">
            <label htmlFor="ct-label">Label</label>
            <input id="ct-label" value={templateLabel} onChange={(e) => setTemplateLabel(e.target.value)} required />
          </div>
          <div className="form-row">
            <label htmlFor="ct-hours">Typical crochet time (hours, per unit)</label>
            <input id="ct-hours" type="number" min={0} step={0.05} value={templateHours || ""} onChange={(e) => setTemplateHours(Number(e.target.value))} />
          </div>
          <div className="form-row">
            <label htmlFor="ct-recipe">Recipe steps (one per line, optional)</label>
            <textarea id="ct-recipe" value={templateRecipe} onChange={(e) => setTemplateRecipe(e.target.value)} rows={3} />
          </div>
          <div className="form-row">
            <label htmlFor="ct-notes">Notes (optional)</label>
            <input id="ct-notes" value={templateNotes} onChange={(e) => setTemplateNotes(e.target.value)} />
          </div>
          <div style={{ alignSelf: "flex-end" }}>
            <button className="primary" type="submit">
              Add
            </button>
          </div>
        </form>
        {templates.length === 0 ? (
          <p className="empty">No component templates yet.</p>
        ) : (
          <div style={{ marginTop: 12, display: "flex", flexDirection: "column", gap: 8 }}>
            {templates.map((t) => (
              <div className="card" key={t.id} style={{ background: "var(--bg-elev-2)" }}>
                <div className="toolbar">
                  <strong>{t.label}</strong>
                  <span className="muted">{t.baseCraftingTimeHours}h/unit</span>
                  <span className="spacer" />
                  <button type="button" onClick={() => removeTemplate(t.id)}>
                    Remove
                  </button>
                </div>
                {(t.pattern?.recipeSteps?.length ?? 0) > 0 && (
                  <ol style={{ margin: "6px 0 0", paddingLeft: 18, fontSize: 13 }}>
                    {t.pattern!.recipeSteps.map((s, i) => (
                      <li key={i}>{s}</li>
                    ))}
                  </ol>
                )}
                {t.notes && <p className="muted" style={{ fontSize: 12, marginTop: 6 }}>{t.notes}</p>}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
