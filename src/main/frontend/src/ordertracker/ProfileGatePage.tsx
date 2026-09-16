import { useState } from "react";
import { orderTrackerApi } from "./api";
import { useBusiness } from "./BusinessContext";

/** Blocks the rest of Order Tracker until the caller has a creator profile for this
 *  business — location + hours/day (design decision: a hard gate, not just a nag, since
 *  the assignee picker on every order silently excludes anyone without one; better to
 *  find out now than wonder later why you're never in the dropdown). Applies to anyone
 *  who reaches an already-set-up business without a profile yet, whether they just joined
 *  or have been a member for a while but never visited Business settings. */
export default function ProfileGatePage({ onDone }: { onDone: () => void }) {
  const { currentBusiness } = useBusiness();
  const [baseLocation, setBaseLocation] = useState("");
  const [hoursPerDay, setHoursPerDay] = useState(4);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentBusiness || !baseLocation.trim()) return;
    setSaving(true);
    setError(null);
    try {
      await orderTrackerApi.upsertMyCreatorProfile(currentBusiness.id, {
        baseLocation: baseLocation.trim(),
        hoursAvailablePerDay: hoursPerDay,
      });
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save your profile");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card" style={{ maxWidth: 480, margin: "40px auto" }}>
      <h1 className="page-title" style={{ fontSize: 20 }}>
        Set up your profile in {currentBusiness?.name}
      </h1>
      <p className="muted" style={{ marginTop: 0 }}>
        Every member needs this before they can be assigned any work — your base location (used for order-number
        location codes) and how many hours a day you can craft. Takes a few seconds, and you only do it once per
        business.
      </p>
      {error && <div className="error">{error}</div>}
      <form onSubmit={submit}>
        <div className="form-row">
          <label htmlFor="gate-base-location">Base location</label>
          <input id="gate-base-location" autoFocus value={baseLocation} onChange={(e) => setBaseLocation(e.target.value)} required />
        </div>
        <div className="form-row">
          <label htmlFor="gate-hours-per-day">Hours available per day</label>
          <input
            id="gate-hours-per-day"
            type="number"
            min={0.5}
            step={0.5}
            value={hoursPerDay}
            onChange={(e) => setHoursPerDay(Number(e.target.value))}
          />
        </div>
        <button className="primary" type="submit" disabled={saving || !baseLocation.trim()}>
          {saving ? "Saving…" : "Continue"}
        </button>
      </form>
    </div>
  );
}
