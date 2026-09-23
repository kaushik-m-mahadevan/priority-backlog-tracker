import type { Page } from "@playwright/test";

/** Shared across every e2e spec (tf-6) — previously copy-pasted into each one. */

export const ADMIN_EMAIL = "test123";
export const ADMIN_PASSWORD = "test123";

export function unique(prefix: string): string {
  return `${prefix}${Date.now().toString(36)}`;
}

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Exact-match on the label text, but tolerant of a trailing " *" required-field marker —
 *  anchored so a short label (e.g. "Password") never also matches a longer one that
 *  contains it as a substring (e.g. "Confirm password"). This app's `.form-row` markup
 *  pairs a `<label>` with its `<input>` as plain siblings, not via `for`/`id` or
 *  wrapping, so labels aren't programmatically associated with their fields (a real,
 *  separate accessibility gap — not fixed here, just worked around). */
export function formField(page: Page, labelText: string) {
  return page
    .locator(".form-row", { has: page.getByText(new RegExp(`^${escapeRegExp(labelText)} ?\\*?$`)) })
    .locator("input, select, textarea");
}

export async function login(page: Page, email: string, password: string) {
  await page.goto("/login");
  await formField(page, "Email").fill(email);
  await formField(page, "Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  // wait for the login POST to resolve and the SPA to actually route away — without
  // this, an immediate page.goto() right after can race the in-flight request and
  // land back on a blank /login.
  await page.getByText("Pick where you want to work.").waitFor();
}
