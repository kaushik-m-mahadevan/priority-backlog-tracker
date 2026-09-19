import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AsyncSection } from "./AsyncSection";

describe("AsyncSection", () => {
  it("shows the loading text and nothing else while loading, regardless of isEmpty", () => {
    render(
      <AsyncSection loading isEmpty empty={<p className="empty">No items</p>}>
        <p>Real content</p>
      </AsyncSection>
    );
    expect(screen.getByText("Loading…")).toBeInTheDocument();
    expect(screen.queryByText("No items")).not.toBeInTheDocument();
    expect(screen.queryByText("Real content")).not.toBeInTheDocument();
  });

  it("shows the empty state once loaded with nothing to show", () => {
    render(
      <AsyncSection loading={false} isEmpty empty={<p className="empty">No items</p>}>
        <p>Real content</p>
      </AsyncSection>
    );
    expect(screen.getByText("No items")).toBeInTheDocument();
    expect(screen.queryByText("Real content")).not.toBeInTheDocument();
  });

  it("shows the real content once loaded with something to show", () => {
    render(
      <AsyncSection loading={false} isEmpty={false} empty={<p className="empty">No items</p>}>
        <p>Real content</p>
      </AsyncSection>
    );
    expect(screen.getByText("Real content")).toBeInTheDocument();
    expect(screen.queryByText("No items")).not.toBeInTheDocument();
  });
});
