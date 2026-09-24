import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import { Section } from "../../components/Section";
import { HoursMinutesInput } from "../../components/HoursMinutesInput";
import { useLinkedNeedleTypes } from "../useLinkedNeedleTypes";
import { useLinkedYarnTypes } from "../useLinkedYarnTypes";
import { SlideToggle } from "../../components/SlideToggle";
import {
  AddOnsFields,
  ComponentsFields,
  MandatoryItemsFields,
  VariantCardFields,
  blankComponent,
  blankMandatoryItems,
  blankVariant,
  duplicateVariant,
  validateSplits,
  type ComponentDraft,
  type LineItemDraft,
  type MandatoryItemDraft,
  type VariantDraft,
} from "../OrderFormFields";
import { NewCustomerFields, blankNewCustomerFieldsDraft, type NewCustomerFieldsDraft } from "../NewCustomerFields";
import type { BusinessConfig, ComponentTemplate, Creator, Customer, DeliveryTier, OrderType, PatternType, PresetOption } from "../types";

const DELIVERY_TIERS: { value: DeliveryTier; label: string }[] = [
  { value: "SAME_CITY", label: "Same city" },
  { value: "SAME_STATE", label: "Same state" },
  { value: "OTHER_STATE", label: "Other state" },
  { value: "INTERNATIONAL", label: "International" },
];

export default function NewOrderPage() {
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const navigate = useNavigate();
  const linkedYarnTypes = useLinkedYarnTypes(groupId);
  const linkedNeedleTypes = useLinkedNeedleTypes(groupId);

  const [config, setConfig] = useState<BusinessConfig | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [creators, setCreators] = useState<Creator[]>([]);
  const [presets, setPresets] = useState<PresetOption[]>([]);
  const [templates, setTemplates] = useState<ComponentTemplate[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [orderType, setOrderType] = useState<OrderType>("INDIVIDUAL");
  const [customerId, setCustomerId] = useState("");
  const [customerMode, setCustomerMode] = useState<"existing" | "new">("existing");
  const [newCustomerName, setNewCustomerName] = useState("");
  const [newCustomerDraft, setNewCustomerDraft] = useState<NewCustomerFieldsDraft>(blankNewCustomerFieldsDraft());
  const [createdByCreatorId, setCreatedByCreatorId] = useState("");
  const [itemName, setItemName] = useState("");
  const [orderReceivedDate, setOrderReceivedDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [quotedDeliveryDate, setQuotedDeliveryDate] = useState("");
  const [deliveryTier, setDeliveryTier] = useState<DeliveryTier>("SAME_CITY");
  const [patternType, setPatternType] = useState<PatternType | "">("");
  const [templateName, setTemplateName] = useState("");
  const [customPatternNotes, setCustomPatternNotes] = useState("");
  const [recipeStepsText, setRecipeStepsText] = useState("");
  const [assemblyPackagingInstructions, setAssemblyPackagingInstructions] = useState("");
  const [notes, setNotes] = useState("");
  const [researchTimeHours, setResearchTimeHours] = useState(0);

  // individual-only
  const [mandatoryItems, setMandatoryItems] = useState<MandatoryItemDraft[]>([]);
  const [addOns, setAddOns] = useState<LineItemDraft[]>([]);
  const [components, setComponents] = useState<ComponentDraft[]>([]);
  const [craftingTimeHours, setCraftingTimeHours] = useState(0);
  const [assemblyTimeHours, setAssemblyTimeHours] = useState(0);
  const [packagingPresetId, setPackagingPresetId] = useState("");

  // bulk-only
  const [variants, setVariants] = useState<VariantDraft[]>([]);

  useEffect(() => {
    Promise.all([
      orderTrackerApi.businessConfig(groupId),
      orderTrackerApi.customers(groupId),
      orderTrackerApi.creators(groupId),
      orderTrackerApi.packagingPresets(groupId),
      orderTrackerApi.componentTemplates(groupId),
    ]).then(([cfg, c, cr, p, t]) => {
      setConfig(cfg);
      setCustomers(c);
      setCreators(cr);
      setCustomerId(c[0]?.id ?? "");
      setCustomerMode(c.length === 0 ? "new" : "existing");
      setCreatedByCreatorId(cr[0]?.id ?? "");
      setPresets(p);
      setTemplates(t);
      setMandatoryItems(blankMandatoryItems());
      setVariants([blankVariant()]);
    });
  }, [groupId]);

  const useExistingMatch = (match: Customer) => {
    setCustomers((prev) => (prev.some((c) => c.id === match.id) ? prev : [...prev, match]));
    setCustomerId(match.id);
    setCustomerMode("existing");
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!createdByCreatorId) {
      setError("Pick who's logging this order");
      return;
    }
    if (customerMode === "existing" && !customerId) {
      setError("Pick a customer");
      return;
    }
    if (customerMode === "new" && !newCustomerName.trim()) {
      setError("Enter the new customer's name");
      return;
    }
    const recipeSteps = recipeStepsText.split("\n").map((s) => s.trim()).filter(Boolean);
    // recipeSteps lives inside pattern now (design decision: a design's recipe is part of
    // its pattern) — send a pattern object whenever there's a type chosen OR steps typed,
    // even if patternType itself was never picked, so notes-only recipes aren't lost.
    const pattern = patternType || recipeSteps.length > 0
      ? { patternType: patternType || null, templateName: patternType === "TEMPLATE" ? templateName : null,
          customPatternNotes: patternType === "CUSTOM" ? customPatternNotes : null, attachmentUrls: [], recipeSteps }
      : null;

    if (orderType === "BULK") {
      const splitError = validateSplits(variants);
      if (splitError) {
        setError(splitError);
        return;
      }
    }

    try {
      let finalCustomerId = customerId;
      if (customerMode === "new") {
        const newCustomer = await orderTrackerApi.createCustomer(groupId, {
          name: newCustomerName.trim(),
          contactNumber: newCustomerDraft.contactNumber,
          email: newCustomerDraft.email.trim() || null,
          instagramHandle: newCustomerDraft.instagramHandle.trim() || null,
          acquisitionChannel: newCustomerDraft.acquisitionChannel,
        });
        finalCustomerId = newCustomer.id;
      }
      const body: Record<string, unknown> = {
        customerId: finalCustomerId,
        orderType,
        createdByCreatorId,
        itemName,
        orderReceivedDate: new Date(orderReceivedDate).toISOString(),
        quotedDeliveryDate: quotedDeliveryDate ? new Date(quotedDeliveryDate).toISOString() : null,
        deliveryTier,
        pattern,
        researchItems: [],
        researchTimeHours,
        assemblyPackagingInstructions: assemblyPackagingInstructions.trim() || null,
        notes: notes.trim() || null,
      };
      if (orderType === "INDIVIDUAL") {
        body.mandatoryItems = mandatoryItems.filter((m) => m.value.trim());
        body.addOns = addOns.filter((a) => a.name.trim());
        body.components = components.filter((c) => c.templateId).map((c) => ({
          ...c, mandatoryItems: c.mandatoryItems.filter((m) => m.value.trim()), addOns: c.addOns.filter((a) => a.name.trim()),
        }));
        body.packagingPresetId = packagingPresetId || null;
        body.craftingTimeHours = craftingTimeHours;
        body.assemblyTimeHours = assemblyTimeHours;
      } else {
        body.variants = variants
          .filter((v) => v.label.trim())
          .map((v) => ({
            label: v.label,
            quantity: v.quantity,
            mandatoryItems: v.mandatoryItems.filter((m) => m.value.trim()),
            addOns: v.addOns.filter((a) => a.name.trim()),
            components: v.components.filter((c) => c.templateId).map((c) => ({
              ...c, mandatoryItems: c.mandatoryItems.filter((m) => m.value.trim()), addOns: c.addOns.filter((a) => a.name.trim()),
            })),
            craftingTimeHours: v.craftingTimeHours,
            assemblyTimeHours: v.assemblyTimeHours,
            splitAllocation: v.splitAllocation.filter((s) => s.creatorId),
          }));
        body.coordinatingCreatorId = createdByCreatorId;
        body.logisticsBufferDays = 0;
      }
      const created = await orderTrackerApi.createOrder(groupId, body);
      navigate(`/ordertracker/orders/${created.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create order");
    }
  };

  if (!config) return <p className="muted">Loading…</p>;

  if (creators.length === 0) {
    return (
      <div className="card">
        <h1 className="page-title">New order</h1>
        <p>
          You need a creator profile before you can log an order — it's what "Logged by" and creator splits use to
          identify you.
        </p>
        <Link to="/ordertracker/business-settings" className="primary">
          Set up your creator profile
        </Link>
      </div>
    );
  }

  return (
    <div>
      <h1 className="page-title">New order</h1>
      <form className="card" onSubmit={submit}>
        {error && <div className="error">{error}</div>}
        <p className="muted" style={{ fontSize: 12, marginBottom: 12 }}>* Required — everything else can be filled in later.</p>

        <Section title="Summary" icon="📋" defaultOpen>
        <div style={{ marginBottom: 16 }}>
          <SlideToggle
            value={orderType}
            options={[{ value: "INDIVIDUAL", label: "Individual" }, { value: "BULK", label: "Bulk" }]}
            onChange={setOrderType}
          />
        </div>

        <div style={{ marginBottom: 8 }}>
          <SlideToggle
            value={customerMode}
            options={[{ value: "existing", label: "Existing customer" }, { value: "new", label: "New customer" }]}
            onChange={setCustomerMode}
          />
        </div>

        <div className="form-grid">
          {customerMode === "existing" ? (
            <div className="form-row">
              <label htmlFor="no-customer">Customer *</label>
              <select id="no-customer" value={customerId} onChange={(e) => setCustomerId(e.target.value)} required>
                <option value="" disabled>
                  Select…
                </option>
                {customers.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <div className="form-row">
              <label htmlFor="no-customer-name">Customer name *</label>
              <input id="no-customer-name" value={newCustomerName} onChange={(e) => setNewCustomerName(e.target.value)} required />
            </div>
          )}
          <div className="form-row">
            <label htmlFor="no-logged-by">Logged by (creator) *</label>
            <select id="no-logged-by" value={createdByCreatorId} onChange={(e) => setCreatedByCreatorId(e.target.value)} required>
              {creators.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {customerMode === "new" && (
          <NewCustomerFields
            groupId={groupId}
            draft={newCustomerDraft}
            onChange={setNewCustomerDraft}
            onUseExisting={useExistingMatch}
            useExistingLabel="Use this customer instead"
            idPrefix="no"
          />
        )}

        <div className="form-row">
          <label htmlFor="no-item-name">Item name *</label>
          <input id="no-item-name" value={itemName} onChange={(e) => setItemName(e.target.value)} placeholder="e.g. Amigurumi bear" required />
        </div>

        <div className="form-grid">
          <div className="form-row">
            <label htmlFor="no-order-received">Order received *</label>
            <input id="no-order-received" type="date" value={orderReceivedDate} onChange={(e) => setOrderReceivedDate(e.target.value)} required />
          </div>
          <div className="form-row">
            <label htmlFor="no-quoted-delivery">Quoted delivery (promised to customer)</label>
            <input id="no-quoted-delivery" type="date" value={quotedDeliveryDate} onChange={(e) => setQuotedDeliveryDate(e.target.value)} />
          </div>
          <div className="form-row">
            <label htmlFor="no-delivery-tier">Shipping to</label>
            <select id="no-delivery-tier" value={deliveryTier} onChange={(e) => setDeliveryTier(e.target.value as DeliveryTier)}>
              {DELIVERY_TIERS.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>
        </div>
        </Section>

        <Section title="Pattern" icon="🧵">
        <div className="form-row">
          <label htmlFor="no-pattern-type" className="sr-only">
            Pattern type
          </label>
          <select id="no-pattern-type" value={patternType} onChange={(e) => setPatternType(e.target.value as PatternType | "")}>
            <option value="">No pattern recorded</option>
            <option value="TEMPLATE">Template</option>
            <option value="CUSTOM">Custom</option>
          </select>
        </div>
        {patternType === "TEMPLATE" && (
          <div className="form-row">
            <label htmlFor="no-template-name" className="sr-only">
              Template name
            </label>
            <input id="no-template-name" value={templateName} onChange={(e) => setTemplateName(e.target.value)} placeholder="Template name" />
          </div>
        )}
        {patternType === "CUSTOM" && (
          <div className="form-row">
            <label htmlFor="no-custom-pattern-notes" className="sr-only">
              Custom pattern notes
            </label>
            <textarea id="no-custom-pattern-notes" value={customPatternNotes} onChange={(e) => setCustomPatternNotes(e.target.value)}
              placeholder="Notes kept for recreation" />
          </div>
        )}

        </Section>

        <Section title="Recipe" icon="📃">
        <div className="form-row">
          <label htmlFor="no-recipe-steps" className="sr-only">
            Recipe steps, one per line
          </label>
          <textarea id="no-recipe-steps" value={recipeStepsText} onChange={(e) => setRecipeStepsText(e.target.value)}
            placeholder={"One step per line, e.g.\nCrochet body, attach petals\nInsert safety eyes and stuff"} />
        </div>
        <div className="form-row" style={{ maxWidth: 220 }}>
          <span className="muted" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Research time</span>
          <HoursMinutesInput idPrefix="no-research-time" hours={researchTimeHours} onChange={setResearchTimeHours} />
        </div>

        </Section>

        <Section title="Assembly &amp; packaging" icon="📦">
        <div className="form-row">
          <label htmlFor="no-assembly-packaging" className="sr-only">
            How to assemble and pack this order
          </label>
          <textarea id="no-assembly-packaging" value={assemblyPackagingInstructions}
            onChange={(e) => setAssemblyPackagingInstructions(e.target.value)}
            placeholder={"Which materials/tools go where, assembly steps, how it gets boxed up"} />
        </div>
        </Section>

        <Section title="Materials" icon="🧶">
        {orderType === "INDIVIDUAL" ? (
          <>

            <label className="muted" style={{ fontSize: 12, display: "block", marginBottom: 6 }}>
              Mandatory items
            </label>
            <MandatoryItemsFields
              items={mandatoryItems}
              onChange={setMandatoryItems}
              yarnTypes={linkedYarnTypes}
              needleTypes={linkedNeedleTypes}
            />

            <label className="muted" style={{ fontSize: 12, display: "block", margin: "12px 0 6px" }}>
              Add-ons
            </label>
            <AddOnsFields addOns={addOns} onChange={setAddOns} />

            <h2 className="settings-section">Components (optional)</h2>
            <p className="muted" style={{ fontSize: 12, marginTop: -8, marginBottom: 10 }}>
              Break this order into repeatable atomic pieces — a vase base, 3 lilies, 5 sunflowers —
              each built separately with its own materials and crochet time, then assembled together.
            </p>
            <ComponentsFields components={components} onChange={setComponents} templates={templates} />

            <h2 className="settings-section">Packaging</h2>
            <div className="form-row" style={{ maxWidth: 300 }}>
              <label htmlFor="no-packaging-preset">Packaging preset</label>
              <select id="no-packaging-preset" value={packagingPresetId} onChange={(e) => setPackagingPresetId(e.target.value)}>
                <option value="">None</option>
                {presets.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.label}
                  </option>
                ))}
              </select>
            </div>

            <h2 className="settings-section">Processes</h2>
            <div className="form-grid">
              <div className="form-row">
                <span className="muted" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Crochet time</span>
                <HoursMinutesInput idPrefix="no-crafting-time" hours={craftingTimeHours} onChange={setCraftingTimeHours} />
              </div>
              <div className="form-row">
                <span className="muted" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Assembly time</span>
                <HoursMinutesInput idPrefix="no-assembly-time" hours={assemblyTimeHours} onChange={setAssemblyTimeHours} />
              </div>
            </div>
          </>
        ) : (
          <>
            <h2 className="settings-section">Variants</h2>
            {variants.map((v, i) => (
              <VariantCardFields
                key={i}
                idPrefix="no"
                index={i}
                variant={v}
                onChange={(next) => setVariants((prev) => prev.map((x, j) => (j === i ? next : x)))}
                creators={creators}
                yarnTypes={linkedYarnTypes}
                needleTypes={linkedNeedleTypes}
                templates={templates}
              />
            ))}
            <button type="button" onClick={() =>
              setVariants((prev) => [...prev, duplicateVariant(prev[prev.length - 1] ?? blankVariant())])}>
              + Add variant (copies the last one)
            </button>
          </>
        )}
        </Section>

        <Section title="Notes" icon="📝">
        <div className="form-row">
          <label htmlFor="no-notes" className="sr-only">
            Notes — customer interactions, changes, anything else
          </label>
          <textarea id="no-notes" value={notes} onChange={(e) => setNotes(e.target.value)}
            placeholder="Customer interactions, changes mid-order, or anything else that doesn't fit above" />
        </div>
        </Section>

        <div style={{ marginTop: 20 }}>
          <button className="primary" type="submit">
            Create order
          </button>
        </div>
      </form>
    </div>
  );
}
