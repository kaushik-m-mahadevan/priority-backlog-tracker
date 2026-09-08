import { due } from "../lib/format";

/** Always one compact unit; a rose "!" bubble only when overdue or due today. */
export function DueMark({ iso }: { iso: string | null }) {
  const d = due(iso);
  return (
    <span className="due">
      {d.urgent ? (
        <span className="bang">
          <span className="b">!</span>
          {d.text}
        </span>
      ) : (
        d.text
      )}
    </span>
  );
}
