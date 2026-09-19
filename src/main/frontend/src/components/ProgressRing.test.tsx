import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProgressRing } from "./ProgressRing";

describe("ProgressRing", () => {
  it("shows the rounded percentage as real DOM text", () => {
    render(<ProgressRing percent={42.6} />);
    expect(screen.getByText("43%")).toBeInTheDocument();
  });

  it("clamps a negative percent to 0%", () => {
    render(<ProgressRing percent={-15} />);
    expect(screen.getByText("0%")).toBeInTheDocument();
  });

  it("clamps a percent over 100 to 100%", () => {
    render(<ProgressRing percent={130} />);
    expect(screen.getByText("100%")).toBeInTheDocument();
  });

  it("colors the progress stroke as the growth color only once fully complete", () => {
    const { container: under, rerender } = render(<ProgressRing percent={99} />);
    const progressCircle = () => under.querySelectorAll("circle")[1];
    expect(progressCircle().getAttribute("stroke")).toBe("var(--accent)");

    rerender(<ProgressRing percent={100} />);
    expect(progressCircle().getAttribute("stroke")).toBe("var(--growth)");
  });

  it("computes strokeDashoffset from the circle's own circumference at the given size", () => {
    const size = 44;
    const stroke = 5;
    const r = (size - stroke) / 2;
    const circumference = 2 * Math.PI * r;

    const { container } = render(<ProgressRing percent={50} size={size} />);
    const progressCircle = container.querySelectorAll("circle")[1];
    const offset = Number(progressCircle.getAttribute("stroke-dashoffset"));
    expect(offset).toBeCloseTo(circumference * 0.5, 5);
  });

  it("a 0% ring has strokeDashoffset equal to the full circumference (nothing drawn)", () => {
    const { container } = render(<ProgressRing percent={0} size={44} />);
    const progressCircle = container.querySelectorAll("circle")[1];
    const dasharray = Number(progressCircle.getAttribute("stroke-dasharray"));
    const offset = Number(progressCircle.getAttribute("stroke-dashoffset"));
    expect(offset).toBeCloseTo(dasharray, 5);
  });

  it("scales the svg to a custom size", () => {
    const { container } = render(<ProgressRing percent={50} size={80} />);
    const svg = container.querySelector("svg")!;
    expect(svg.getAttribute("width")).toBe("80");
    expect(svg.getAttribute("height")).toBe("80");
  });
});
