import { useState } from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { MandatoryItemsFields, blankMandatoryItems, type MandatoryItemDraft } from "./OrderFormFields";

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
