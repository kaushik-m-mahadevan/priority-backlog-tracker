import { ApiError, getToken } from "../api/client";

export interface ImageMetaView {
  id: string;
  sequenceOrder: number;
  uploadedByUserId: string;
  uploadedAt: string;
  /** "image/jpeg" for an actual photo, or "application/pdf" for an uploaded PDF (e.g. a
   *  supplier invoice) — used to decide whether a gallery entry renders as an <img> or an
   *  embedded PDF viewer. */
  contentType: string;
}

const base = (groupId: string, ownerType: string, ownerId: string) =>
  `/api/images/${groupId}/${ownerType}/${ownerId}`;

function authHeaders(): Record<string, string> {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** Shared gallery API — same generic commons.image backend used by every applet that
 *  attaches photos to something (Order Tracker orders today; Product Catalog / Finance
 *  Tracker's invoice attachment later). Kept separate from the plain-JSON `api` client
 *  since uploads are multipart and raw image bytes need a Blob fetch, not JSON. */
export const imagesApi = {
  list: async (groupId: string, ownerType: string, ownerId: string): Promise<ImageMetaView[]> => {
    const res = await fetch(base(groupId, ownerType, ownerId), { headers: authHeaders() });
    if (!res.ok) throw new ApiError(res.status, `Failed to list images (${res.status})`);
    return res.json();
  },

  upload: async (groupId: string, ownerType: string, ownerId: string, file: File): Promise<ImageMetaView> => {
    const form = new FormData();
    form.append("file", file);
    const res = await fetch(base(groupId, ownerType, ownerId), { method: "POST", headers: authHeaders(), body: form });
    if (!res.ok) {
      const data = await res.json().catch(() => null);
      throw new ApiError(res.status, data?.message ?? `Upload failed (${res.status})`);
    }
    return res.json();
  },

  remove: async (groupId: string, ownerType: string, ownerId: string, imageId: string): Promise<void> => {
    const res = await fetch(`${base(groupId, ownerType, ownerId)}/${imageId}`, { method: "DELETE", headers: authHeaders() });
    if (!res.ok && res.status !== 204) throw new ApiError(res.status, `Delete failed (${res.status})`);
  },

  /** Returns a blob: object URL — an <img src> can't send an Authorization header, so the
   *  raw bytes are fetched once here and handed to the browser as a local URL instead.
   *  Caller is responsible for revoking it (URL.revokeObjectURL) when done, e.g. on unmount. */
  rawObjectUrl: async (groupId: string, ownerType: string, ownerId: string, imageId: string): Promise<string> => {
    const res = await fetch(`${base(groupId, ownerType, ownerId)}/${imageId}/raw`, { headers: authHeaders() });
    if (!res.ok) throw new ApiError(res.status, `Failed to load image (${res.status})`);
    const blob = await res.blob();
    return URL.createObjectURL(blob);
  },
};
