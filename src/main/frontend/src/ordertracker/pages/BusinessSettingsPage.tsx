import { useEffect, useState } from "react";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import type { BusinessConfig, Creator, PresetOption } from "../types";

export default function BusinessSettingsPage() {
  const { currentGroupId, currentBusiness } = useBusiness();
  const groupId = currentGroupId!;
  const [config, setConfig] = useState<BusinessConfig | null>(null);
  const [profile, setProfile] = useState<Creator | null>(null);
  const [presets, setPresets] = useState<PresetOption[]>([]);
  const [baseLocation, setBaseLocation] = useState("");
  const [hoursPerDay, setHoursPerDay] = useState(4);
  const [presetLabel, setPresetLabel] = useState("");
  const [presetCost, setPresetCost] = useState(0);
  const [presetHours, setPresetHours] = useState(0);

  const load = async () => {
    const [cfg, me, p] = await Promise.all([
      orderTrackerApi.businessConfig(groupId),
      orderTrackerApi.myCreatorProfile(groupId).catch(() => null),
      orderTrackerApi.packagingPresets(groupId),
    ]);
    setConfig(cfg);
    setProfile(me ?? null);
    setPresets(p);
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
