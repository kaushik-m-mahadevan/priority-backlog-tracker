interface P {
  size?: number;
}

const base = (size: number) => ({
  width: size,
  height: size,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.7,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
});

export const BellIcon = ({ size = 18 }: P) => (
  <svg {...base(size)}>
    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
  </svg>
);

export const GearIcon = ({ size = 18 }: P) => (
  <svg {...base(size)}>
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.2.63.77 1.09 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
  </svg>
);

export const ChevronIcon = ({ size = 16, open = false }: P & { open?: boolean }) => (
  <svg {...base(size)} style={{ transform: open ? "rotate(90deg)" : "none", transition: "transform .15s" }}>
    <path d="M9 6l6 6-6 6" />
  </svg>
);

export const SidebarIcon = ({ size = 18 }: P) => (
  <svg {...base(size)}>
    <rect x="3" y="4" width="18" height="16" rx="2" />
    <path d="M9 4v16" />
  </svg>
);

/** Back to the applet launcher — distinct from an applet's own brand link, which now
 *  stays inside that applet (design: platform integration follow-up). */
export const HomeIcon = ({ size = 18 }: P) => (
  <svg {...base(size)}>
    <path d="M3 11.5 12 4l9 7.5" />
    <path d="M5.5 10v9a1 1 0 0 0 1 1H10v-5a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v5h3.5a1 1 0 0 0 1-1v-9" />
  </svg>
);

/* rail section icons */
export const PriorityGlyph = ({ size = 17 }: P) => (
  <svg {...base(size)}>
    <circle cx="12" cy="12" r="8" />
    <circle cx="12" cy="12" r="3.4" />
  </svg>
);
export const QuickGlyph = ({ size = 17 }: P) => (
  <svg {...base(size)}>
    <path d="M13 3 L5 14h6l-1 7 8-11h-6z" />
  </svg>
);
export const AttentionGlyph = ({ size = 17 }: P) => (
  <svg {...base(size)}>
    <path d="M7 3h10 M7 21h10" />
    <path d="M8 3c0 4 8 6 8 9 0 3-8 5-8 9 M16 3c0 4-8 6-8 9" />
  </svg>
);
export const TeamGlyph = ({ size = 17 }: P) => (
  <svg {...base(size)}>
    <circle cx="9" cy="8" r="3" />
    <circle cx="17" cy="9" r="2.4" />
    <path d="M4 19c0-3 2.5-5 5-5s5 2 5 5 M15 19c0-2 1-3.5 3-3.5S21 17 21 19" />
  </svg>
);
export const ItemsGlyph = ({ size = 17 }: P) => (
  <svg {...base(size)}>
    <path d="M9 6h11 M9 12h11 M9 18h11" />
    <path d="M4 6l1 1 2-2 M4 12l1 1 2-2 M4 18l1 1 2-2" />
  </svg>
);

/* Order Tracker's own bottom-nav glyphs (ui-3: icon-only, matching Backlog Tracker's tabbar) */
export const OrdersGlyph = ({ size = 17 }: P) => (
  <svg {...base(size)}>
    <rect x="4" y="3" width="16" height="18" rx="2" />
    <path d="M8 8h8 M8 12h8 M8 16h5" />
  </svg>
);
export const NewOrderGlyph = ({ size = 17 }: P) => (
  <svg {...base(size)}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 8v8 M8 12h8" />
  </svg>
);
export const CustomersGlyph = ({ size = 17 }: P) => (
  <svg {...base(size)}>
    <circle cx="9" cy="8" r="3" />
    <circle cx="17" cy="9" r="2.4" />
    <path d="M4 19c0-3 2.5-5 5-5s5 2 5 5 M15 19c0-2 1-3.5 3-3.5S21 17 21 19" />
  </svg>
);
export const MoreGlyph = ({ size = 17 }: P) => (
  <svg {...base(size)}>
    <circle cx="5" cy="12" r="1.4" fill="currentColor" />
    <circle cx="12" cy="12" r="1.4" fill="currentColor" />
    <circle cx="19" cy="12" r="1.4" fill="currentColor" />
  </svg>
);

/* age markers for Needs Attention */
export const AgeIcon = ({ size = 14 }: P) => (
  <svg {...base(size)}>
    <path d="M7 4h10 M7 20h10" />
    <path d="M8 4c0 3.5 8 5.5 8 8s-8 4.5-8 8 M16 4c0 3.5-8 5.5-8 8" />
  </svg>
);
export const OverdueIcon = ({ size = 14 }: P) => (
  <svg {...base(size)}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v6 M12 16.5v.01" />
  </svg>
);
