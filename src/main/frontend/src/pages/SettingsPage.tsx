import { useConfig } from "../config/ConfigContext";
import { useTheme } from "../theme/ThemeContext";

export default function SettingsPage() {
  const config = useConfig();
  const { theme, setTheme } = useTheme();

  const themeCard = (
    <div className="card">
      <h2>Theme</h2>
      <div className="seg" style={{ display: "flex", gap: 8 }}>
        <button className={theme === "dusk" ? "primary" : ""} onClick={() => setTheme("dusk")}>
          Dusk
        </button>
        <button className={theme === "tide" ? "primary" : ""} onClick={() => setTheme("tide")}>
          Tide
        </button>
      </div>
      <p className="muted" style={{ fontSize: 12, marginTop: 10 }}>
        Both are calm, nature-toned dark themes. Remembered on this device.
      </p>
    </div>
  );

  if (!config) {
    return (
      <div>
        <h1 className="page-title">Settings</h1>
        <p className="page-sub">Theme now; ranking config below once it loads.</p>
        {themeCard}
      </div>
    );
  }

  const weights: [string, number][] = [
    ["Priority weight", config.priorityWeight],
    ["Urgency weight", config.urgencyWeight],
    ["Effort weight", config.effortWeight],
  ];
  const thresholds: [string, number | string][] = [
    ["Urgency window (days)", config.urgencyWindowDays],
    ["Stale threshold (days)", config.staleThresholdDays],
    ["Buried threshold (days)", config.buriedThresholdDays],
    ["Default due-date offset (days)", config.defaultDueDateOffsetDays],
    ["Effort cap (days)", config.effortCapDays],
    ["Buried priority levels", config.buriedPriorityLevels.join(", ")],
  ];

  return (
    <div>
      <h1 className="page-title">Settings</h1>
      <p className="page-sub">
        Theme, and the current ranking configuration (§7). Config editing lands later —
        read-only for now.
      </p>

      <div className="grid cols-2">
        {themeCard}
        <div className="card">
          <h2>Ranking weights</h2>
          <table>
            <tbody>
              {weights.map(([k, v]) => (
                <tr key={k}>
                  <td>{k}</td>
                  <td className="mono">{v}</td>
                </tr>
              ))}
              <tr>
                <td className="muted">Sum</td>
                <td className="mono">
                  {(config.priorityWeight + config.urgencyWeight + config.effortWeight).toFixed(3)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="card">
          <h2>Thresholds</h2>
          <table>
            <tbody>
              {thresholds.map(([k, v]) => (
                <tr key={k}>
                  <td>{k}</td>
                  <td className="mono">{v}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="card">
          <h2>Priorities</h2>
          <div className="kv">
            {config.priorities.map((p) => (
              <span className="chip" key={p}>
                {p} = {config.priorityValues[p]}
              </span>
            ))}
          </div>
        </div>

        <div className="card">
          <h2>Categories</h2>
          <div className="kv">
            {config.categories.map((c) => (
              <span className="chip" key={c}>
                {c}
              </span>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
