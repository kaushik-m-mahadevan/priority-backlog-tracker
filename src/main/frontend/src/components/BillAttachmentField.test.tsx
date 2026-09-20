import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BillFileInput } from "./BillAttachmentField";

describe("BillFileInput", () => {
  it("renders a file input hinting at the camera (ui-13) while creating", () => {
    render(<BillFileInput id="bill-file" editingId={null} onChange={() => {}} />);
    const input = screen.getByLabelText("Attach a bill (optional)");
    expect(input).toHaveAttribute("capture", "environment");
    expect(input).toHaveAttribute("accept", "image/jpeg,image/png,image/webp,application/pdf");
  });

  it("renders nothing while editing an existing entry", () => {
    const { container } = render(<BillFileInput id="bill-file" editingId="entry-1" onChange={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });
});
