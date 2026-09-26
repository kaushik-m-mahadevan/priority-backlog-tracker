import { ReactNode, useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import Bell from "./Bell";
import Connections from "./Connections";
import NavMenu, { type NavMenuLink } from "./NavMenu";
import SearchBox from "./SearchBox";
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
  appletKey,
  groupId,
  groupName,
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
  /** This applet's own Group.APPLET_* key — together with `groupId`, shows the shared
   *  Connections widget. Omitted on the launcher/settings/admin shells, which have no
   *  single current applet+group to show connections for. */
  appletKey?: string;
  /** The currently-selected group/business/etc. in this applet, if any. */
  groupId?: string | null;
  /** The currently-selected group/business's own name — with the switcher moved out of
   *  the header into the account menu (ui-1), nothing otherwise showed which one of
   *  possibly several groups is actually active on screen right now. Shown as a small
   *  badge next to the applet name, present on every page of every applet since they all
   *  share this header. Omitted wherever `groupId` is (no group selected yet, or a shell
   *  with no single current group). */
  groupName?: string | null;
}) {
  // Published as a CSS var (ui-10) so a page-level sticky element (e.g. the order
  // detail action bar) can sit exactly below this bar regardless of applet — some
  // applets' headers wrap onto a second row (business switcher + "+ New business"),
  // so a fixed guessed offset would put the content half under the nav.
  const navRef = useRef<HTMLElement>(null);
  useEffect(() => {
    const el = navRef.current;
    if (!el) return;
    const publish = () => document.documentElement.style.setProperty("--app-header-h", `${el.getBoundingClientRect().height}px`);
    publish();
    const ro = new ResizeObserver(publish);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  return (
    <nav className="nav" ref={navRef}>
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
      {groupName && <span className="current-group-badge">{groupName}</span>}
      {navLinks}
      <span className="spacer" />
      {rightSlot}
      {/* ad-5: scoped to the current group + whatever it's linked to — only meaningful
          once a group is actually selected. */}
      {groupId && <SearchBox groupId={groupId} />}
      {appletKey && <Connections appletKey={appletKey} groupId={groupId ?? null} />}
      <Bell />
      <NavMenu extraLinks={extraMenuLinks} />
    </nav>
  );
}
