import { useEffect, useState } from "react";
import { orderTrackerApi } from "../api";
import { useAuth } from "../../auth/AuthContext";
import { useBusiness } from "../BusinessContext";
import type { BusinessConfig, CostConfigChangeRequest, Creator, MandatoryItemType, PresetOption } from "../types";

export default function BusinessSettingsPage() {
  const { currentGroupId, currentBusiness } = useBusiness();
  const { user } = useAuth();
  const groupId = currentGroupId!;
  const [config, setConfig] = useState<BusinessConfig | null>(null);
  const [profile, setProfile] = useState<Creator | null>(null);
  const [presets, setPresets] = useState<PresetOption[]>([]);
  const [changeRequests, setChangeRequests] = useState<CostConfigChangeRequest[]>([]);
  const [baseLocation, setBaseLocation] = useState("");
  const [hoursPerDay, setHoursPerDay] = useState(4);
  const [presetLabel, setPresetLabel] = useState("");
  const [presetCost, setPresetCost] = useState(0);
  const [presetHours, setPresetHours] = useState(0);
  const [newItemKey, setNewItemKey] = useState("");
  const [newItemLabel, setNewItemLabel] = useState("");
  const [newItemIsTool, setNewItemIsTool] = useState(false);
  const [proposedOverhead, setProposedOverhead] = useState(0);
  const [proposedMargin, setProposedMargin] = useState(0);
  const [costConfigError, setCostConfigError] = useState<string | null>(null);

  const load = async () => {
    const [cfg, me, p, changes] = await Promise.all([
      orderTrackerApi.businessConfig(groupId),
      orderTrackerApi.myCreatorProfile(groupId).catch(() => null),
      orderTrackerApi.packagingPresets(groupId),
      orderTrackerApi.costConfigChangeRequests(groupId),
    ]);
    setConfig(cfg);
    setProfile(me ?? null);
    setPresets(p);
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

  const saveMandatoryItemTypes = async (types: MandatoryItemType[]) => {
    if (!config) return;
    const updated = await orderTrackerApi.updateBusinessConfig(groupId, {
      currency: config.currency,
      mandatoryItemTypes: types,
      workStages: config.workStages,
    });
    setConfig(updated);
  };

  const addMandatoryItemType = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!config || !newItemKey.trim() || !newItemLabel.trim()) return;
    await saveMandatoryItemTypes([
      ...config.mandatoryItemTypes,
      { itemKey: newItemKey.trim(), label: newItemLabel.trim(), allowedValues: null, isTool: newItemIsTool },
    ]);
    setNewItemKey("");
    setNewItemLabel("");
    setNewItemIsTool(false);
  };

  const removeMandatoryItemType = async (itemKey: string) => {
    if (!config) return;
    await saveMandatoryItemTypes(config.mandatoryItemTypes.filter((t) => t.itemKey !== itemKey));
  };

  const toggleMandatoryItemTool = async (itemKey: string) => {
    if (!config) return;
    await saveMandatoryItemTypes(
      config.mandatoryItemTypes.map((t) => (t.itemKey === itemKey ? { ...t, isTool: !t.isTool } : t))
    );
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
              <label>Base location</label>
              <input value={baseLocation} onChange={(e) => setBaseLocation(e.target.value)} required />
            </div>
            <div className="form-row">
              <label>Hours available per day</label>
              <input
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
              <label>Overhead %</label>
              <input type="number" min={0} step={1} value={proposedOverhead} onChange={(e) => setProposedOverhead(Number(e.target.value))} />
            </div>
            <div className="form-row">
              <label>Profit margin %</label>
              <input type="number" min={0} step={1} value={proposedMargin} onChange={(e) => setProposedMargin(Number(e.target.value))} />
            </div>
            <button className="primary" type="submit">
              Propose change
            </button>
          </form>
        )}
      </div>

      <h2 className="settings-section">Mandatory item types</h2>
      <div className="card">
        <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
          Mark an item as a tool (e.g. a crochet hook) to skip cost/quantity tracking for it — tools are reused, not
          purchased per order.
        </p>
        {config.mandatoryItemTypes.length === 0 ? (
          <p className="empty">No mandatory item types configured.</p>
        ) : (
          <div className="kv" style={{ marginBottom: 12 }}>
            {config.mandatoryItemTypes.map((t) => (
              <span className="chip" key={t.itemKey}>
                {t.label} ({t.itemKey})
                <label style={{ marginLeft: 8, fontSize: 12 }}>
                  <input type="checkbox" checked={t.isTool} onChange={() => toggleMandatoryItemTool(t.itemKey)} /> tool
                </label>
                <button type="button" className="linkbtn" style={{ marginLeft: 8 }} onClick={() => removeMandatoryItemType(t.itemKey)}>
                  Remove
                </button>
              </span>
            ))}
          </div>
        )}
        <form className="toolbar" onSubmit={addMandatoryItemType}>
          <input placeholder="Key (e.g. wool)" value={newItemKey} onChange={(e) => setNewItemKey(e.target.value)} required />
          <input placeholder="Label (e.g. Wool)" value={newItemLabel} onChange={(e) => setNewItemLabel(e.target.value)} required />
          <label style={{ fontSize: 13 }}>
            <input type="checkbox" checked={newItemIsTool} onChange={(e) => setNewItemIsTool(e.target.checked)} /> Tool
          </label>
          <button className="primary" type="submit">
            Add
          </button>
        </form>
      </div>

      <h2 className="settings-section">Packaging presets</h2>
      <div className="card">
        <form className="toolbar" onSubmit={addPreset}>
          <input placeholder="Label" value={presetLabel} onChange={(e) => setPresetLabel(e.target.value)} required />
          <input
            type="number"
            placeholder="Cost"
            value={presetCost || ""}
            onChange={(e) => setPresetCost(Number(e.target.value))}
          />
          <input
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
    </div>
  );
}
