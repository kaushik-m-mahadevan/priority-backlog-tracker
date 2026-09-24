import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "../../api/client";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import ProfileGatePage from "../ProfileGatePage";
import { Switch } from "../../components/Switch";
import type { BusinessConfig } from "../types";

const STEP_LABELS = ["Business settings", "Your profile", "Invite your team", "Finance & Inventory"];

/** Forced, one-time flow for a newly-created business (design decision: fully blocking —
 *  nothing else in Order Tracker is reachable until this finishes). Walks through the
 *  business's cost/material configuration, the creator's own profile (reuses
 *  ProfileGatePage as step 2 — same requirement, same form), invites, and optionally
 *  spinning up linked Finance Tracker / Material Inventory groups in one pass so a
 *  founder doesn't have to visit three different applets to get a business off the
 *  ground. OrderTrackerLayout renders this instead of the Outlet — see useSetupGate. */
export default function SetupWizardPage({ onDone }: { onDone: () => void }) {
  const { currentBusiness, currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const [step, setStep] = useState(1);
  const [config, setConfig] = useState<BusinessConfig | null>(null);
  const [currency, setCurrency] = useState("INR");
  const [overheadPct, setOverheadPct] = useState(15);
  const [marginPct, setMarginPct] = useState(20);
  const [savingStep1, setSavingStep1] = useState(false);
  const [inviteValue, setInviteValue] = useState("");
  const [invitesSent, setInvitesSent] = useState<string[]>([]);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [setUpFinance, setSetUpFinance] = useState(true);
  const [setUpInventory, setSetUpInventory] = useState(true);
  const [finishing, setFinishing] = useState(false);
  const [finishError, setFinishError] = useState<string | null>(null);
  const stepRef = useRef<HTMLDivElement>(null);

  // Move focus to the new step on every advance, so a screen-reader user isn't left
  // focused on a button that just unmounted (round 4 review) — the container's own
  // aria-label announces which step they've landed on even before its content is read.
  useEffect(() => {
    stepRef.current?.focus();
  }, [step]);

  useEffect(() => {
    orderTrackerApi.businessConfig(groupId).then((cfg) => {
      setConfig(cfg);
      setCurrency(cfg.currency);
      setOverheadPct(cfg.overheadPercentage * 100);
      setMarginPct(cfg.profitMarginPercentage * 100);
    });
  }, [groupId]);

  const saveStep1AndContinue = async () => {
    if (!config) return;
    setSavingStep1(true);
    try {
      await orderTrackerApi.updateBusinessConfig(groupId, {
        currency,
        workStages: config.workStages,
        deliveryBufferSameCityDays: config.deliveryBufferSameCityDays,
        deliveryBufferSameStateDays: config.deliveryBufferSameStateDays,
        deliveryBufferOtherStateDays: config.deliveryBufferOtherStateDays,
        deliveryBufferInternationalDays: config.deliveryBufferInternationalDays,
      });
      // A brand-new business has exactly one member (its creator) at this point, so this
      // unanimous-approval proposal auto-resolves immediately — see ApprovalService's own
      // solo-proposer rule. Skipped entirely if unchanged from the seeded defaults. Hourly
      // wage isn't a wizard field (kept for Business Settings later) — carried through
      // unchanged so a real overhead/margin tweak here doesn't accidentally "confirm" the
      // still-default wage.
      if (overheadPct / 100 !== config.overheadPercentage || marginPct / 100 !== config.profitMarginPercentage) {
        await orderTrackerApi.proposeCostConfigChange(groupId, overheadPct / 100, marginPct / 100, config.hourlyWage);
      }
      setStep(2);
    } finally {
      setSavingStep1(false);
    }
  };

  const sendInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    const to = inviteValue.trim();
    if (!to) return;
    setInviteError(null);
    try {
      await api.post(`/groups/${groupId}/invites`, { to });
      setInvitesSent([...invitesSent, to]);
      setInviteValue("");
    } catch (err) {
      setInviteError(err instanceof ApiError ? err.message : "Could not send that invite");
    }
  };

  const finish = async () => {
    if (!currentBusiness) return;
    setFinishing(true);
    setFinishError(null);
    try {
      if (setUpFinance) {
        const financeGroup = await api.post<{ id: string }>(`/groups?appletKey=financetracker`, { name: currentBusiness.name });
        await api.post(`/groups/${groupId}/links`, { groupId: financeGroup.id, inviteAllMembers: true });
      }
      if (setUpInventory) {
        const inventoryGroup = await api.post<{ id: string }>(`/groups?appletKey=materialinventory`, { name: currentBusiness.name });
        await api.post(`/groups/${groupId}/links`, { groupId: inventoryGroup.id, inviteAllMembers: true });
      }
      await orderTrackerApi.completeBusinessSetup(groupId);
      onDone();
    } catch (err) {
      setFinishError(err instanceof ApiError ? err.message : "Failed to finish setup — you can retry.");
    } finally {
      setFinishing(false);
    }
  };

  return (
    <div style={{ maxWidth: 640, margin: "0 auto" }}>
      <h1 className="page-title">Set up {currentBusiness?.name}</h1>
      <p className="page-sub">
        Step {step} of {STEP_LABELS.length}: {STEP_LABELS[step - 1]}
      </p>
      <div className="toolbar" style={{ marginBottom: 16 }}>
        {STEP_LABELS.map((label, i) => (
          <span
            key={label}
            className="badge"
            style={i + 1 === step ? { background: "var(--accent)", color: "#2a1c12" } : undefined}
          >
            {i + 1}. {label}
          </span>
        ))}
      </div>

      <div ref={stepRef} tabIndex={-1} aria-label={`Step ${step}: ${STEP_LABELS[step - 1]}`} style={{ outline: "none" }}>
      {step === 1 && config && (
        <div className="card">
          <h2>Business settings</h2>
          <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
            Starting defaults are already filled in — change anything now, or leave it and adjust later from
            Business settings.
          </p>
          <div className="form-grid">
            <div className="form-row">
              <label htmlFor="wiz-currency">Currency</label>
              <input id="wiz-currency" value={currency} onChange={(e) => setCurrency(e.target.value)} />
            </div>
            <div className="form-row">
              <label htmlFor="wiz-overhead">Overhead %</label>
              <input id="wiz-overhead" type="number" min={0} step={1} value={overheadPct} onChange={(e) => setOverheadPct(Number(e.target.value))} />
            </div>
            <div className="form-row">
              <label htmlFor="wiz-margin">Profit margin %</label>
              <input id="wiz-margin" type="number" min={0} step={1} value={marginPct} onChange={(e) => setMarginPct(Number(e.target.value))} />
            </div>
          </div>

          <div className="toolbar" style={{ marginTop: 16 }}>
            <button className="primary" disabled={savingStep1} onClick={saveStep1AndContinue}>
              {savingStep1 ? "Saving…" : "Continue"}
            </button>
          </div>
        </div>
      )}

      {step === 2 && <ProfileGatePage onDone={() => setStep(3)} />}

      {step === 3 && (
        <div className="card">
          <h2>Invite your team</h2>
          <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
            Optional — you can always invite more people later from Manage business.
          </p>
          {inviteError && <div className="error">{inviteError}</div>}
          <form className="team-add" onSubmit={sendInvite}>
            <input
              aria-label="Invite by email or handle"
              placeholder="Invite by email or @handle"
              value={inviteValue}
              onChange={(e) => setInviteValue(e.target.value)}
            />
            <button className="primary" disabled={!inviteValue.trim()}>
              Invite
            </button>
          </form>
          {invitesSent.length > 0 && (
            <div className="kv" style={{ marginTop: 12 }}>
              {invitesSent.map((to) => (
                <span className="chip" key={to}>
                  ✓ {to}
                </span>
              ))}
            </div>
          )}
          <div className="toolbar" style={{ marginTop: 16 }}>
            <button className="primary" onClick={() => setStep(4)}>
              Continue
            </button>
          </div>
        </div>
      )}

      {step === 4 && (
        <div className="card">
          <h2>Finance &amp; Inventory</h2>
          <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
            Set these up now if you plan to use them. You can always set either up later instead.
          </p>
          {finishError && <div className="error">{finishError}</div>}
          <div style={{ marginBottom: 8 }}>
            <Switch
              id="setup-finance"
              checked={setUpFinance}
              onChange={setSetUpFinance}
              label="Set up Finance Tracker"
              help={`A Finance Tracker group gets created and linked to ${currentBusiness?.name}, named to match.`}
            />
          </div>
          <div style={{ marginBottom: 16 }}>
            <Switch
              id="setup-inventory"
              checked={setUpInventory}
              onChange={setSetUpInventory}
              label="Set up Material Inventory"
              help={`A Material Inventory group gets created and linked to ${currentBusiness?.name}, named to match.`}
            />
          </div>
          <div className="toolbar">
            <button className="primary" disabled={finishing} onClick={finish}>
              {finishing ? "Finishing…" : "Finish setup"}
            </button>
          </div>
        </div>
      )}
      </div>
    </div>
  );
}
