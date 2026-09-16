import { useEffect, useRef, useState } from "react";
import { imagesApi, type ImageMetaView } from "./imagesApi";

interface LoadedImage extends ImageMetaView {
  url: string;
}

const DEFAULT_ACCEPT = "image/jpeg,image/png,image/webp";

/** A finished-product photo gallery, generic enough for any owner in any applet — pass
 *  the groupId/ownerType/ownerId this gallery belongs to (design decision: one shared
 *  gallery per order, not per bulk variant) and it handles everything else: listing,
 *  uploading (up to 6 files, 5MB each — design decision), a simple lightbox, and
 *  deleting. Object URLs are created per file (an <img src> can't carry the
 *  Authorization header a raw fetch needs) and revoked on unmount to avoid leaking
 *  memory. A PDF entry (e.g. a bill attachment) renders as an embedded viewer instead of
 *  an <img> wherever one appears — pass `accept="...,application/pdf"` to allow uploading
 *  one; existing callers that don't pass `accept` stay images-only, unaffected. */
export default function ImageGallery({
  groupId,
  ownerType,
  ownerId,
  accept = DEFAULT_ACCEPT,
}: {
  groupId: string;
  ownerType: string;
  ownerId: string;
  accept?: string;
}) {
  const [images, setImages] = useState<LoadedImage[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const urlsRef = useRef<string[]>([]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const metas = await imagesApi.list(groupId, ownerType, ownerId);
      const loaded = await Promise.all(
        metas.map(async (m) => ({ ...m, url: await imagesApi.rawObjectUrl(groupId, ownerType, ownerId, m.id) }))
      );
      urlsRef.current.forEach((u) => URL.revokeObjectURL(u));
      urlsRef.current = loaded.map((l) => l.url);
      setImages(loaded);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load images");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    return () => {
      urlsRef.current.forEach((u) => URL.revokeObjectURL(u));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId, ownerType, ownerId]);

  const onFileChosen = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      await imagesApi.upload(groupId, ownerType, ownerId, file);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to upload image");
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const removeImage = async (imageId: string) => {
    setError(null);
    try {
      await imagesApi.remove(groupId, ownerType, ownerId, imageId);
      setLightboxIndex(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to remove image");
    }
  };

  return (
    <div>
      {error && <div className="error">{error}</div>}
      {loading ? (
        <p className="muted">Loading photos…</p>
      ) : images.length === 0 ? (
        <p className="empty">No photos yet.</p>
      ) : (
        <div className="image-gallery-strip">
          {images.map((img, i) => (
            <button
              type="button"
              key={img.id}
              className="image-gallery-thumb"
              onClick={() => setLightboxIndex(i)}
              aria-label={`View ${img.contentType === "application/pdf" ? "PDF" : "photo"} ${i + 1}`}
            >
              {img.contentType === "application/pdf" ? (
                <span className="image-gallery-pdf-tile" aria-hidden="true">
                  📄 PDF
                </span>
              ) : (
                <img src={img.url} alt="" />
              )}
            </button>
          ))}
        </div>
      )}

      <div className="toolbar" style={{ marginTop: 10 }}>
        <input
          ref={fileInputRef}
          type="file"
          accept={accept}
          onChange={onFileChosen}
          disabled={uploading || images.length >= 6}
          aria-label="Upload a file"
        />
        {images.length >= 6 && <span className="muted" style={{ fontSize: 12 }}>Maximum of 6 files reached</span>}
        {uploading && <span className="muted" style={{ fontSize: 12 }}>Uploading…</span>}
      </div>

      {lightboxIndex !== null && images[lightboxIndex] && (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            position: "fixed", inset: 0, background: "rgba(0,0,0,0.85)", zIndex: 1000,
            display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 12,
          }}
          onClick={() => setLightboxIndex(null)}
        >
          {images[lightboxIndex].contentType === "application/pdf" ? (
            <embed
              src={images[lightboxIndex].url}
              type="application/pdf"
              style={{ width: "90vw", height: "75vh", borderRadius: 8, background: "#fff" }}
              onClick={(e) => e.stopPropagation()}
            />
          ) : (
            <img
              src={images[lightboxIndex].url}
              alt=""
              style={{ maxWidth: "90vw", maxHeight: "75vh", objectFit: "contain", borderRadius: 8 }}
              onClick={(e) => e.stopPropagation()}
            />
          )}
          <div className="toolbar" onClick={(e) => e.stopPropagation()}>
            {images.length > 1 && (
              <>
                <button type="button" onClick={() => setLightboxIndex((lightboxIndex - 1 + images.length) % images.length)}>
                  ← Prev
                </button>
                <button type="button" onClick={() => setLightboxIndex((lightboxIndex + 1) % images.length)}>
                  Next →
                </button>
              </>
            )}
            <button type="button" onClick={() => removeImage(images[lightboxIndex].id)}>
              Remove
            </button>
            <button type="button" onClick={() => setLightboxIndex(null)}>
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
