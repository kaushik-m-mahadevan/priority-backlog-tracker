import { useState } from "react";

/** Every boolean setting is a switch, never a checkbox (governing UI/UX principle) — a
 *  real native `<input type="checkbox" role="switch">` under a custom sliding track/thumb,
 *  so keyboard and screen-reader behavior come for free instead of being reimplemented.
 *  `description` stays to one line by convention (the caller keeps it short); anything
 *  longer goes in `help`, revealed only behind the "?" button rather than shown inline. */
export function Switch({
  checked,
  onChange,
  label,
  description,
  help,
  disabled,
  id,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
  label: string;
  description?: string;
  help?: string;
  disabled?: boolean;
  id?: string;
}) {
  const [showHelp, setShowHelp] = useState(false);

  return (
    <div className="switch-field">
      <label className="switch-row" htmlFor={id}>
        <span className="switch-label">
          <span>{label}</span>
          {help && (
            <button
              type="button"
              className="switch-help-btn"
              aria-expanded={showHelp}
              aria-label={`More about ${label}`}
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                setShowHelp((s) => !s);
              }}
            >
              ?
            </button>
          )}
        </span>
        <span className="switch-control">
          <input
            id={id}
            type="checkbox"
            role="switch"
            checked={checked}
            disabled={disabled}
            onChange={(e) => onChange(e.target.checked)}
          />
          <span className="switch-track" aria-hidden="true">
            <span className="switch-thumb" />
          </span>
        </span>
      </label>
      {description && <p className="hint switch-description">{description}</p>}
      {help && showHelp && <p className="hint switch-help-text">{help}</p>}
    </div>
  );
}
