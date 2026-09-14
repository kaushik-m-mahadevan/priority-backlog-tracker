import { ReactNode } from "react";
import { Link } from "react-router-dom";
import Bell from "./Bell";
import NavMenu, { type NavMenuLink } from "./NavMenu";
import { HomeIcon } from "./icons";

/** Shared top bar for every applet (and the launcher itself): home button, the current
 *  applet's own name, optional feature-specific nav content, and the always-present
 *  notification bell + account menu. Each applet supplies only what's specific to it. */
export default function AppHeader({
  appletIcon,
  appletName,
  appletHref,
  navLinks,
  rightSlot,
  extraMenuLinks,
}: {
  /** Decorative glyph shown before the applet name, e.g. "◆", "✂", "⚙", "◇". */
  appletIcon?: string;
  /** Omit entirely on the launcher — there's no "current applet" to name there. */
  appletName?: string;
  /** Where clicking the applet name goes; omit for a non-link label. */
  appletHref?: string;
  /** Rendered right after the applet name, e.g. a `.nav-links` block of NavLinks. */
  navLinks?: ReactNode;
  /** Rendered on the right, before the bell — e.g. a group/business switcher. */
  rightSlot?: ReactNode;
  /** Applet-specific quick links inside the account-menu dropdown, e.g. "Completed". */
  extraMenuLinks?: NavMenuLink[];
}) {
  return (
    <nav className="nav">
      <Link to="/" className="iconbtn" title="Back to console" aria-label="Back to console">
        <HomeIcon />
      </Link>
      {appletName &&
        (appletHref ? (
          <Link to={appletHref} className="brand">
            {appletIcon ? `${appletIcon} ` : ""}
            {appletName}
          </Link>
        ) : (
          <span className="brand">
            {appletIcon ? `${appletIcon} ` : ""}
            {appletName}
          </span>
        ))}
      {navLinks}
      <span className="spacer" />
      {rightSlot}
      <Bell />
      <NavMenu extraLinks={extraMenuLinks} />
    </nav>
  );
}
