import { useRef, useState } from "react";
import ReactMarkdown from "react-markdown";

interface Props {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}

/** Basic markdown editor: a textarea, a tiny formatting toolbar, and a preview toggle. */
export default function MarkdownField({ value, onChange, placeholder }: Props) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const [preview, setPreview] = useState(false);

  function wrap(before: string, after = before) {
    const el = ref.current;
    if (!el) return;
    const s = el.selectionStart;
    const e = el.selectionEnd;
    const sel = value.slice(s, e) || "text";
    const next = value.slice(0, s) + before + sel + after + value.slice(e);
    onChange(next);
    requestAnimationFrame(() => {
      el.focus();
      el.selectionStart = s + before.length;
      el.selectionEnd = s + before.length + sel.length;
    });
  }

  function linePrefix(prefix: string) {
    const el = ref.current;
    if (!el) return;
    const s = el.selectionStart;
    const lineStart = value.lastIndexOf("\n", s - 1) + 1;
    const next = value.slice(0, lineStart) + prefix + value.slice(lineStart);
    onChange(next);
    requestAnimationFrame(() => {
      el.focus();
      el.selectionStart = el.selectionEnd = s + prefix.length;
    });
  }

  return (
    <div className="md-field">
      <div className="md-toolbar">
        <button type="button" title="Bold" onClick={() => wrap("**")}>
          <b>B</b>
        </button>
        <button type="button" title="Italic" onClick={() => wrap("_")}>
          <i>I</i>
        </button>
        <button type="button" title="Heading" onClick={() => linePrefix("## ")}>
          H
        </button>
        <button type="button" title="Bullet list" onClick={() => linePrefix("- ")}>
          •
        </button>
        <span style={{ flex: 1 }} />
        <button
          type="button"
          className={preview ? "on" : ""}
          onClick={() => setPreview((p) => !p)}
        >
          {preview ? "Write" : "Preview"}
        </button>
      </div>
      {preview ? (
        <div className="md-preview">
          {value.trim() ? (
            <ReactMarkdown>{value}</ReactMarkdown>
          ) : (
            <span className="muted">Nothing to preview.</span>
          )}
        </div>
      ) : (
        <textarea
          ref={ref}
          rows={5}
          value={value}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
        />
      )}
    </div>
  );
}
