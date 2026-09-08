/* The completion ceremony:
   1. a pen stroke draws across the title, left → right, accelerating
   2. a ~160ms beat
   3. leaves fly into the grove and the tree pops, a fresh leaf blooming on it
   4. the row collapses away
   Pure DOM — no React re-render needed until the caller refetches. */

const STRIKE_MS = 540;
const BEAT_MS = 160;

const wait = (ms: number) => new Promise((r) => setTimeout(r, ms));

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

function leafSpan(x: number, y: number, size = 16): HTMLElement {
  const el = document.createElement("span");
  el.className = "celebrate-leaf";
  el.innerHTML =
    `<svg viewBox="0 0 12 12" width="${size}" height="${size}" fill="currentColor" aria-hidden="true">` +
    '<ellipse cx="6" cy="6" rx="5.6" ry="2.8" transform="rotate(-35 6 6)"/></svg>';
  el.style.left = `${x}px`;
  el.style.top = `${y}px`;
  document.body.appendChild(el);
  return el;
}

function flyLeaf(el: HTMLElement, dx: number, dy: number, i: number) {
  const lift = -90 - i * 18;
  const jitter = (i - 2) * 16;
  const anim = el.animate(
    [
      { transform: "translate(0,0) rotate(0) scale(1)", opacity: 1 },
      {
        transform: `translate(${dx * 0.5 + jitter}px, ${dy * 0.5 + lift}px) rotate(180deg) scale(1.25)`,
        opacity: 1,
        offset: 0.55,
      },
      { transform: `translate(${dx}px, ${dy}px) rotate(380deg) scale(0.25)`, opacity: 0 },
    ],
    { duration: 900 + i * 110, delay: i * 90, easing: "cubic-bezier(.32,0,.35,1)" },
  );
  const done = () => el.remove();
  anim.onfinish = done;
  anim.oncancel = done;
}

function popGrove(grove: SVGElement) {
  grove.style.transformBox = "fill-box";
  grove.style.transformOrigin = "center bottom";
  grove.animate(
    [
      { transform: "scale(1)" },
      { transform: "scale(1.22) rotate(-2.5deg)", offset: 0.22 },
      { transform: "scale(0.9)", offset: 0.46 },
      { transform: "scale(1.08)", offset: 0.68 },
      { transform: "scale(0.98)", offset: 0.85 },
      { transform: "scale(1)" },
    ],
    { duration: 820, easing: "cubic-bezier(.34,1.35,.5,1)" },
  );
}

/** A leaf blooms onto the canopy after the flight lands. */
function sproutLeaf(grove: SVGElement) {
  const gr = grove.getBoundingClientRect();
  const el = leafSpan(gr.left + gr.width * 0.5 - 9, gr.top + gr.height * 0.3, 18);
  const anim = el.animate(
    [
      { transform: "scale(0) rotate(-40deg)", opacity: 0 },
      { transform: "scale(1.35) rotate(8deg)", opacity: 1, offset: 0.4 },
      { transform: "scale(1) rotate(0deg)", opacity: 1, offset: 0.7 },
      { transform: "scale(1) rotate(0deg)", opacity: 0 },
    ],
    { duration: 900, easing: "cubic-bezier(.34,1.3,.5,1)" },
  );
  const done = () => el.remove();
  anim.onfinish = done;
  anim.oncancel = done;
}

/** Runs the full ceremony. Resolves once it is safe to collapse the row. */
export async function runCelebration(rowEl: HTMLElement): Promise<void> {
  rowEl.classList.add("completing");
  const grove = findGrove();

  if (reducedMotion()) {
    await wait(200);
    if (grove) popGrove(grove);
    return;
  }

  // 1 + 2: the pen stroke plays (CSS), then a beat
  await wait(STRIKE_MS + BEAT_MS);

  // 3: leaves fly, tree pops, a leaf blooms
  if (grove) {
    const rr = rowEl.getBoundingClientRect();
    const startX = rr.right - 56;
    const startY = rr.top + rr.height / 2;
    const gr = grove.getBoundingClientRect();
    const ex = gr.left + gr.width / 2;
    const ey = gr.top + gr.height / 2;

    const leaves: HTMLElement[] = [];
    for (let i = 0; i < 5; i++) {
      const leaf = leafSpan(startX, startY);
      leaves.push(leaf);
      flyLeaf(leaf, ex - startX, ey - startY, i);
    }
    window.setTimeout(() => leaves.forEach((l) => l.remove()), 2000);
    window.setTimeout(() => popGrove(grove), 640);
    window.setTimeout(() => sproutLeaf(grove), 780);
  }

  // let the flight clear the row before the caller collapses it
  await wait(620);
}

/** Collapses the row to nothing. Resolves when the transition is done. */
export async function collapseRow(rowEl: HTMLElement): Promise<void> {
  rowEl.style.maxHeight = `${rowEl.scrollHeight}px`;
  rowEl.classList.add("collapsing");
  void rowEl.offsetHeight; // force reflow so the transition takes effect
  rowEl.style.maxHeight = "0px";
  await wait(440);
}
