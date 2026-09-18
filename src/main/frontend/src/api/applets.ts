/** Every applet that a group can belong to, and the one place their identity (key, name,
 *  icon, base route) is defined — used by the shared cross-applet Connections widget so
 *  it can list "every other applet" without each applet re-declaring its neighbors. Kept
 *  here rather than in commons/backend since it's pure display metadata for this specific
 *  piece of UI, not a domain concept the backend needs to know about (the backend only
 *  ever sees the plain string keys already defined in Group.java). */
export interface AppletMeta {
  key: string;
  name: string;
  icon: string;
  /** Where "open this applet" should navigate to. */
  href: string;
}

export const APPLETS: AppletMeta[] = [
  { key: "backlogtracker", name: "Priority Backlog Tracker", icon: "◆", href: "/backlog" },
  { key: "ordertracker", name: "Order Tracker", icon: "✂", href: "/ordertracker" },
  { key: "financetracker", name: "Finance Tracker", icon: "💰", href: "/financetracker" },
  { key: "materialinventory", name: "Material Inventory", icon: "🧶", href: "/materialinventory" },
  { key: "productcatalog", name: "Product Catalog", icon: "🎨", href: "/productcatalog" },
];

export function otherApplets(currentKey: string): AppletMeta[] {
  return APPLETS.filter((a) => a.key !== currentKey);
}
