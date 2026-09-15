import { useState } from "react";
import { MemoryRouter } from "react-router-dom";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { MandatoryItemsFields, blankMandatoryItems, type MandatoryItemDraft } from "./OrderFormFields";
import type { MandatoryItemType } from "./types";

const WOOL: MandatoryItemType = { itemKey: "wool", label: "Wool", allowedValues: null, isTool: false };
const HOOK: MandatoryItemType = { itemKey: "hook", label: "Hook", allowedValues: null, isTool: true };

/** MandatoryItemsFields is a controlled component (items/onChange props, no internal
 *  state) — this wrapper gives it somewhere to write to so a real add/remove/edit
 *  round-trip can be exercised the way a parent form actually uses it. */
function ControlledMandatoryItems({ types, initial }: { types: MandatoryItemType[]; initial: MandatoryItemDraft[] }) {
  const [items, setItems] = useState(initial);
  return <MandatoryItemsFields items={items} types={types} onChange={setItems} />;
}

function renderWithRouter(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>);
}

describe("MandatoryItemsFields", () => {
  it("shows a Business Settings link and no fields when no material types are configured", () => {
    renderWithRouter(<MandatoryItemsFields items={[]} types={[]} onChange={vi.fn()} />);
    expect(screen.getByRole("link", { name: /business settings/i })).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("only shows material types, never tool types (isTool is ToolsFields' job)", () => {
    renderWithRouter(<MandatoryItemsFields items={[]} types={[WOOL, HOOK]} onChange={vi.fn()} />);
    expect(screen.getByText("Wool")).toBeInTheDocument();
    expect(screen.queryByText("Hook")).not.toBeInTheDocument();
  });

  it("groups multiple entries of the same material type under one legend", () => {
    const items = [
      { itemKey: "wool", value: "Cream", quantity: 1, unitCost: 50, notes: "" },
      { itemKey: "wool", value: "Blue", quantity: 2, unitCost: 60, notes: "" },
    ];
    renderWithRouter(<MandatoryItemsFields items={items} types={[WOOL]} onChange={vi.fn()} />);
    expect(screen.getAllByText("Wool")).toHaveLength(1);
    expect(screen.getByDisplayValue("Cream")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Blue")).toBeInTheDocument();
  });

  it("adding another entry appends a blank one for that item type only", async () => {
    const user = userEvent.setup();
    renderWithRouter(<ControlledMandatoryItems types={[WOOL]} initial={blankMandatoryItems({ mandatoryItemTypes: [WOOL] } as never)} />);
    expect(screen.getAllByPlaceholderText("e.g. Cream, 50g")).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: /add another wool entry/i }));
    expect(screen.getAllByPlaceholderText("e.g. Cream, 50g")).toHaveLength(2);
  });

  it("removing an entry leaves the others untouched, and hides Remove when only one entry is left", async () => {
    const user = userEvent.setup();
    const initial = [
      { itemKey: "wool", value: "Cream", quantity: 1, unitCost: 50, notes: "" },
      { itemKey: "wool", value: "Blue", quantity: 2, unitCost: 60, notes: "" },
    ];
    renderWithRouter(<ControlledMandatoryItems types={[WOOL]} initial={initial} />);
    await user.click(screen.getByRole("button", { name: /remove wool entry 1/i }));
    expect(screen.queryByDisplayValue("Cream")).not.toBeInTheDocument();
    expect(screen.getByDisplayValue("Blue")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /remove wool entry 1/i })).not.toBeInTheDocument();
  });

  it("editing a field's value calls onChange with only that entry updated", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const items = [{ itemKey: "wool", value: "", quantity: 1, unitCost: 50, notes: "" }];
    renderWithRouter(<MandatoryItemsFields items={items} types={[WOOL]} onChange={onChange} />);
    await user.type(screen.getByPlaceholderText("e.g. Cream, 50g"), "X");
    expect(onChange).toHaveBeenCalledWith([{ itemKey: "wool", value: "X", quantity: 1, unitCost: 50, notes: "" }]);
  });

  it("each entry's fieldset carries the type's legend so screen-reader users can tell entries apart", () => {
    const items = [{ itemKey: "wool", value: "Cream", quantity: 1, unitCost: 50, notes: "" }];
    const { container } = renderWithRouter(<MandatoryItemsFields items={items} types={[WOOL]} onChange={vi.fn()} />);
    const fieldset = container.querySelector("fieldset")!;
    expect(within(fieldset).getByText("Wool")).toBeInTheDocument();
  });
});
