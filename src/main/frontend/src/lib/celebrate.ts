/* The completion flourish: strike the row, fling a few leaves into the grove,
   bump the tree, then collapse the row away. Pure DOM — no React re-render needed
   until the caller refetches. */

function reducedMotion(): boolean {
  try {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  } catch {
    return false;
  }
}

function findGrove(): SVGElement | null {
  return document.querySelector<SVGElement>(
    ".dash-aside .grove.solo svg, .head-grove .grove svg, .grove svg",
  );
}

function makeLeaf(x: number, y: number): HTMLElement {
  const el = document.createElement("span");
  el.className = "celebrate-leaf";
  el.innerHTML =
    '<svg viewBox="0 0 12 12" width="16" height="16" fill="currentColor" aria-hidden="true">' +
    '<ellipse cx="6" cy="6" rx="5.6" ry="2.8" transform="rotate(-35 6 6)"/></svg>';
  el.style.left = `${x}px`;
  el.style.top = `${y}px`;
  document.body.appendChild(el);
  return el;
}

function flyLeaf(el: HTMLElement, dx: number, dy: number, i: number) {
  const lift = -80 - i * 20;
  const jitter = (i - 2) * 14;
  const anim = el.animate(
    [
      { transform: "translate(0,0) rotate(0) scale(1)", opacity: 1 },
      {
        transform: `translate(${dx * 0.5 + jitter}px, ${dy * 0.5 + lift}px) rotate(170deg) scale(1.2)`,
        opacity: 1,
        offset: 0.55,
      },
      { transform: `translate(${dx}px, ${dy}px) rotate(360deg) scale(0.3)`, opacity: 0 },
    ],
    { duration: 760 + i * 80, delay: i * 70, easing: "cubic-bezier(.35,0,.4,1)" },
  );
  const done = () => el.remove();
  anim.onfinish = done;
  anim.oncancel = done;
}

function bumpGrove(grove: SVGElement) {
  grove.style.transformBox = "fill-box";
  grove.style.transformOrigin = "center bottom";
  grove.animate(
    [
      { transform: "scale(1)" },
      { transform: "scale(1.1) rotate(-2deg)", offset: 0.25 },
      { transform: "scale(0.96)", offset: 0.55 },
      { transform: "scale(1)" },
    ],
    { duration: 680, easing: "cubic-bezier(.3,0,.3,1)" },
  );
}

/** Adds `.completing` to the row and launches the leaf flight + grove bump.
    Resolves once the strike has played (so the API call can fire promptly). */
export async function runCelebration(rowEl: HTMLElement): Promise<void> {
  rowEl.classList.add("completing");

  const grove = findGrove();
  const soft = reducedMotion();

  if (grove) {
    if (!soft) {
      const rr = rowEl.getBoundingClientRect();
      const startX = rr.right - 56;
      const startY = rr.top + rr.height / 2;
      const gr = grove.getBoundingClientRect();
      const ex = gr.left + gr.width / 2;
      const ey = gr.top + gr.height / 2;
      const leaves: HTMLElement[] = [];
      for (let i = 0; i < 5; i++) {
        const leaf = makeLeaf(startX, startY);
        leaves.push(leaf);
        flyLeaf(leaf, ex - startX, ey - startY, i);
      }
      window.setTimeout(() => leaves.forEach((l) => l.remove()), 1700);
    }
    window.setTimeout(() => bumpGrove(grove), soft ? 0 : 480);
  }

  await new Promise((r) => setTimeout(r, soft ? 120 : 360));
}

/** Collapses the row to nothing. Resolves when the transition is done. */
export async function collapseRow(rowEl: HTMLElement): Promise<void> {
  rowEl.style.maxHeight = `${rowEl.scrollHeight}px`;
  rowEl.classList.add("collapsing");
  void rowEl.offsetHeight; // force reflow so the transition takes effect
  rowEl.style.maxHeight = "0px";
  await new Promise((r) => setTimeout(r, 320));
}
