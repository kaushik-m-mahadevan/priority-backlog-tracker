import { useEffect, useState } from "react";

/** A not-yet-uploaded local file, previewed via an object URL (image) or an embedded PDF
 *  viewer (PDF) — used on a create form before the owner it'll attach to even exists yet,
 *  so nothing is sent to the server until the whole form is submitted. */
export default function FilePreview({ file }: { file: File }) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    const objectUrl = URL.createObjectURL(file);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [file]);

  if (!url) return null;

  return file.type === "application/pdf" ? (
    <embed src={url} type="application/pdf" style={{ width: "100%", height: 420, borderRadius: 8, background: "#fff" }} />
  ) : (
    <img src={url} alt="" style={{ width: "100%", borderRadius: 8, objectFit: "contain" }} />
  );
}
