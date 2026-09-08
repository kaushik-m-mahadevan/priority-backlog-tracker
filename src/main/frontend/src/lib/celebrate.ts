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

function makeLeaf(x: number, y: number): HTMLElement {
  const el = document.createElement("span");
  el.className = "celebrate-leaf";
  el.innerHTML =
    '<svg viewBox="0 0 12 12" width="14" height="14" fill="currentColor" aria-hidden="true">' +
    '<ellipse cx="6" cy="6" rx="5.4" ry="2.7" transform="rotate(-35 6 6)"/></svg>';
  el.style.left = `${x}px`;
  el.style.top = `${y}px`;
  document.body.appendChild(el);
  return el;
}

function flyLeaf(el: HTMLElement, dx: number, dy: number, i: number) {
  const lift = -70 - i * 22;
  const anim = el.animate(
    [
      { transform: "translate(0,0) rotate(0) scale(1)", opacity: 1 },
      {
        transform: `translate(${dx * 0.5}px, ${dy * 0.5 + lift}px) rotate(160deg) scale(1.15)`,
        opacity: 1,
        offset: 0.55,
      },
      { transform: `translate(${dx}px, ${dy}px) rotate(340deg) scale(0.35)`, opacity: 0 },
    ],
    { duration: 720 + i * 90, delay: i * 80, easing: "cubic-bezier(.35,0,.4,1)" },
  );
  const done = () => el.remove();
  anim.onfinish = done;
  anim.oncancel = done;
}

/** Adds `.completing` to the row and launches the leaf flight + grove bump.
    Resolves once the strike has played (so the API call can fire promptly). */
export async function runCelebration(rowEl: HTMLElement): Promise<void> {
  rowEl.classList.add("completing");

  if (!reducedMotion()) {
    const rr = rowEl.getBoundingClientRect();
    const startX = rr.right - 52;
    const startY = rr.top + rr.height / 2;

    const groveSvg = document.querySelector<SVGElement>(
      ".dash-aside .grove.solo svg, .head-grove .grove svg",
    );
    if (groveSvg) {
      const gr = groveSvg.getBoundingClientRect();
      const ex = gr.left + gr.width / 2;
      const ey = gr.top + gr.height / 2;
      const leaves: HTMLElement[] = [];
      for (let i = 0; i < 3; i++) {
        const leaf = makeLeaf(startX, startY);
        leaves.push(leaf);
        flyLeaf(leaf, ex - startX, ey - startY, i);
      }
      // hard cleanup in case the environment stalls the animations
      window.setTimeout(() => leaves.forEach((l) => l.remove()), 1600);

      window.setTimeout(() => {
        groveSvg.style.transformBox = "fill-box";
        groveSvg.style.transformOrigin = "center bottom";
        groveSvg.animate(
          [
            { transform: "scale(1)" },
            { transform: "scale(1.08) rotate(-1.5deg)", offset: 0.25 },
            { transform: "scale(0.97)", offset: 0.55 },
            { transform: "scale(1)" },
          ],
          { duration: 640, easing: "cubic-bezier(.3,0,.3,1)" },
        );
      }, 500);
    }
  }

  await new Promise((r) => setTimeout(r, 340));
}

/** Collapses the row to nothing. Resolves when the transition is done. */
export async function collapseRow(rowEl: HTMLElement): Promise<void> {
  rowEl.style.maxHeight = `${rowEl.scrollHeight}px`;
  rowEl.classList.add("collapsing");
  void rowEl.offsetHeight; // force reflow so the transition takes effect
  rowEl.style.maxHeight = "0px";
  await new Promise((r) => setTimeout(r, 320));
}
