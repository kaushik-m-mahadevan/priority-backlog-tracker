import { useState } from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  AddOnsFields,
  ComponentsFields,
  MandatoryItemsFields,
  blankMandatoryItems,
  type ComponentDraft,
  type LineItemDraft,
  type MandatoryItemDraft,
} from "./OrderFormFields";
import type { ComponentTemplate } from "./types";

/** MandatoryItemsFields is a controlled component (items/onChange props, no internal
 *  state) — this wrapper gives it somewhere to write to so a real add/remove/edit
 *  round-trip can be exercised the way a parent form actually uses it. */
function ControlledMandatoryItems({ initial }: { initial: MandatoryItemDraft[] }) {
  const [items, setItems] = useState(initial);
  return <MandatoryItemsFields items={items} onChange={setItems} />;
}

describe("MandatoryItemsFields", () => {
  it("always shows exactly the two fixed kinds — Yarn and Needle", () => {
    render(<MandatoryItemsFields items={blankMandatoryItems()} onChange={vi.fn()} />);
    expect(screen.getByText("Yarn")).toBeInTheDocument();
    expect(screen.getByText("Needle")).toBeInTheDocument();
  });

  it("groups multiple entries of the same kind under one legend", () => {
    const items: MandatoryItemDraft[] = [
      { kind: "YARN", value: "Cream", quantity: 1, unitCost: 50, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null },
      { kind: "YARN", value: "Blue", quantity: 2, unitCost: 60, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null },
    ];
    render(<MandatoryItemsFields items={items} onChange={vi.fn()} />);
    expect(screen.getAllByText("Yarn")).toHaveLength(1);
    expect(screen.getByDisplayValue("Cream")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Blue")).toBeInTheDocument();
  });

  it("adding another entry appends a blank one for that kind only", async () => {
    const user = userEvent.setup();
    render(<ControlledMandatoryItems initial={blankMandatoryItems()} />);
    expect(screen.getAllByPlaceholderText("e.g. Cream, 50g")).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: /add another yarn entry/i }));
    expect(screen.getAllByPlaceholderText("e.g. Cream, 50g")).toHaveLength(2);
    expect(screen.getAllByPlaceholderText("e.g. 4mm crochet hook")).toHaveLength(1);
  });

  it("removing an entry leaves the others untouched, and hides Remove when only one entry is left", async () => {
    const user = userEvent.setup();
    const initial: MandatoryItemDraft[] = [
      { kind: "YARN", value: "Cream", quantity: 1, unitCost: 50, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null },
      { kind: "YARN", value: "Blue", quantity: 2, unitCost: 60, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null },
    ];
    render(<ControlledMandatoryItems initial={initial} />);
    await user.click(screen.getByRole("button", { name: /remove yarn entry 1/i }));
    expect(screen.queryByDisplayValue("Cream")).not.toBeInTheDocument();
    expect(screen.getByDisplayValue("Blue")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /remove yarn entry 1/i })).not.toBeInTheDocument();
  });

  it("editing a field's value calls onChange with only that entry updated", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const items: MandatoryItemDraft[] = [
      { kind: "YARN", value: "", quantity: 1, unitCost: 50, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null },
    ];
    render(<MandatoryItemsFields items={items} onChange={onChange} />);
    await user.type(screen.getByPlaceholderText("e.g. Cream, 50g"), "X");
    expect(onChange).toHaveBeenCalledWith([
      { kind: "YARN", value: "X", quantity: 1, unitCost: 50, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null },
    ]);
  });

  it("each entry's fieldset carries the kind's legend so screen-reader users can tell entries apart", () => {
    const items: MandatoryItemDraft[] = [
      { kind: "YARN", value: "Cream", quantity: 1, unitCost: 50, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null },
    ];
    const { container } = render(<MandatoryItemsFields items={items} onChange={vi.fn()} />);
    const fieldset = container.querySelectorAll("fieldset")[0]!;
    expect(within(fieldset).getByText("Yarn")).toBeInTheDocument();
  });

  it("shows the yarn link dropdown only for yarn entries when yarnTypes is provided", () => {
    const items: MandatoryItemDraft[] = [
      { kind: "YARN", value: "Cream", quantity: 1, unitCost: 50, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null },
      { kind: "NEEDLE", value: "4mm hook", quantity: 1, unitCost: 0, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null },
    ];
    render(
      <MandatoryItemsFields
        items={items}
        onChange={vi.fn()}
        yarnTypes={[{ id: "y1", brand: "Red Heart", thickness: "Worsted", colour: "Cream" }]}
      />
    );
    expect(screen.getByText("Link to inventory yarn (optional)")).toBeInTheDocument();
    expect(screen.queryByText("Link to inventory needle (optional)")).not.toBeInTheDocument();
  });
});

function ControlledAddOns({ initial }: { initial: LineItemDraft[] }) {
  const [addOns, setAddOns] = useState(initial);
  return <AddOnsFields addOns={addOns} onChange={setAddOns} />;
}

describe("AddOnsFields", () => {
  it("renders one row per add-on with its name/quantity/cost", () => {
    render(<AddOnsFields addOns={[{ name: "Safety eyes", quantity: 2, unitCost: 10 }]} onChange={vi.fn()} />);
    expect(screen.getByDisplayValue("Safety eyes")).toBeInTheDocument();
    expect(screen.getByLabelText("Quantity")).toHaveValue(2);
    expect(screen.getByLabelText("Unit cost")).toHaveValue(10);
  });

  it("+ Add add-on appends a blank row without touching existing ones", async () => {
    const user = userEvent.setup();
    render(<ControlledAddOns initial={[{ name: "Safety eyes", quantity: 2, unitCost: 10 }]} />);
    await user.click(screen.getByRole("button", { name: "+ Add add-on" }));
    expect(screen.getByDisplayValue("Safety eyes")).toBeInTheDocument();
    const names = screen.getAllByLabelText("Name") as HTMLInputElement[];
    expect(names).toHaveLength(2);
    expect(names[1].value).toBe("");
  });

  it("editing quantity updates only that row's quantity, leaving name/cost untouched", async () => {
    const user = userEvent.setup();
    render(<ControlledAddOns initial={[{ name: "Eyes", quantity: 1, unitCost: 10 }]} />);
    await user.clear(screen.getByLabelText("Quantity"));
    await user.type(screen.getByLabelText("Quantity"), "3");
    expect(screen.getByLabelText("Quantity")).toHaveValue(3);
    expect(screen.getByLabelText("Name")).toHaveValue("Eyes");
    expect(screen.getByLabelText("Unit cost")).toHaveValue(10);
  });

  it("Remove drops only that row", async () => {
    const user = userEvent.setup();
    render(
      <ControlledAddOns
        initial={[
          { name: "Eyes", quantity: 1, unitCost: 10 },
          { name: "Bow", quantity: 1, unitCost: 5 },
        ]}
      />
    );
    await user.click(screen.getByRole("button", { name: /remove add-on 1: eyes/i }));
    expect(screen.queryByDisplayValue("Eyes")).not.toBeInTheDocument();
    expect(screen.getByDisplayValue("Bow")).toBeInTheDocument();
  });
});

const templates: ComponentTemplate[] = [
  { id: "t-1", label: "Vase base", pattern: null, baseCraftingTimeHours: 2.5, notes: null },
  {
    id: "t-2", label: "Petal", baseCraftingTimeHours: 0.5, notes: null,
    pattern: { patternType: null, templateName: null, customPatternNotes: null, attachmentUrls: [], recipeSteps: ["Chain 4", "Slip stitch"] },
  },
];

function blankComponentDraft(templateId: string, craftingTimeHours: number): ComponentDraft {
  return { templateId, quantity: 1, mandatoryItems: [], addOns: [], craftingTimeHours };
}

function ControlledComponents({ initial }: { initial: ComponentDraft[] }) {
  const [components, setComponents] = useState(initial);
  return <ComponentsFields components={components} onChange={setComponents} templates={templates} />;
}

describe("ComponentsFields", () => {
  it("renders a template dropdown pre-selected to the component's own templateId", () => {
    render(<ComponentsFields components={[blankComponentDraft("t-2", 0.5)]} onChange={vi.fn()} templates={templates} />);
    expect(screen.getByLabelText("Component")).toHaveValue("t-2");
  });

  it("shows the selected template's pattern steps when it has any", () => {
    render(<ComponentsFields components={[blankComponentDraft("t-2", 0.5)]} onChange={vi.fn()} templates={templates} />);
    expect(screen.getByText(/Chain 4 → Slip stitch/)).toBeInTheDocument();
  });

  it("shows no pattern text for a template with no recipe steps", () => {
    render(<ComponentsFields components={[blankComponentDraft("t-1", 2.5)]} onChange={vi.fn()} templates={templates} />);
    expect(screen.queryByText(/Pattern:/)).not.toBeInTheDocument();
  });

  it("switching the template updates craftingTimeHours to the new template's base estimate", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<ComponentsFields components={[blankComponentDraft("t-1", 2.5)]} onChange={onChange} templates={templates} />);
    await user.selectOptions(screen.getByLabelText("Component"), "t-2");
    expect(onChange).toHaveBeenCalledWith([
      expect.objectContaining({ templateId: "t-2", craftingTimeHours: 0.5 }),
    ]);
  });

  it("+ Add materials appears only when a component has none yet, and adds the two fixed kinds", async () => {
    const user = userEvent.setup();
    render(<ControlledComponents initial={[blankComponentDraft("t-1", 2.5)]} />);
    const addMaterialsButton = screen.getByRole("button", { name: "+ Add materials" });
    await user.click(addMaterialsButton);
    expect(screen.getByText("Yarn")).toBeInTheDocument();
    expect(screen.getByText("Needle")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "+ Add materials" })).not.toBeInTheDocument();
  });

  it("+ Add component is disabled when there are no templates to choose from", () => {
    render(<ComponentsFields components={[]} onChange={vi.fn()} templates={[]} />);
    expect(screen.getByRole("button", { name: "+ Add component" })).toBeDisabled();
  });

  it("+ Add component appends a blankComponent defaulted to the first template", async () => {
    const user = userEvent.setup();
    render(<ControlledComponents initial={[]} />);
    await user.click(screen.getByRole("button", { name: "+ Add component" }));
    expect(screen.getByLabelText("Component")).toHaveValue("t-1");
  });

  it("Remove component drops only that component", async () => {
    const user = userEvent.setup();
    render(<ControlledComponents initial={[blankComponentDraft("t-1", 2.5), blankComponentDraft("t-2", 0.5)]} />);
    await user.click(screen.getByRole("button", { name: /remove component 1: vase base/i }));
    const selects = screen.getAllByLabelText("Component") as HTMLSelectElement[];
    expect(selects).toHaveLength(1);
    expect(selects[0].value).toBe("t-2");
  });
});
