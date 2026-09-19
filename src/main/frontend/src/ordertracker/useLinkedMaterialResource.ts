import { useEffect, useRef, useState } from "react";
import { api } from "../api/client";

const MATERIAL_INVENTORY_APPLET_KEY = "materialinventory";

/** Shared shape behind useLinkedYarnTypes/useLinkedNeedleTypes/useMyYarnInventory/
 *  useMyNeedleInventory (fdup-3): if this business has a linked Material Inventory group,
 *  fetch `resource` from it and return the result; otherwise (no link, or either fetch
 *  fails) return `defaultValue`. Frontend-only cross-applet read — Order Tracker's own
 *  backend never talks to Material Inventory's, consistent with the platform's
 *  no-cross-applet-import rule.
 *
 *  `defaultValue` and `resource` are read from refs updated every render rather than
 *  placed in the effect's dependency array, so passing a fresh `() => {}` or `[]` literal
 *  each render (as every call site below does) can't retrigger the fetch — only a real
 *  `groupId` change does, matching the original 4 hooks this replaces. */
export function useLinkedMaterialResource<T>(
  groupId: string | null,
  defaultValue: T,
  resource: (linkedGroupId: string) => Promise<T>,
): T {
  const [value, setValue] = useState<T>(defaultValue);
  const defaultRef = useRef(defaultValue);
  defaultRef.current = defaultValue;
  const resourceRef = useRef(resource);
  resourceRef.current = resource;

  useEffect(() => {
    if (!groupId) {
      setValue(defaultRef.current);
      return;
    }
    let cancelled = false;
    api
      .get<{ linkedGroupId: string | null }>(`/groups/${groupId}/links/${MATERIAL_INVENTORY_APPLET_KEY}`)
      .then((link) => {
        if (cancelled || !link.linkedGroupId) {
          if (!cancelled) setValue(defaultRef.current);
          return;
        }
        return resourceRef.current(link.linkedGroupId).then((result) => {
          if (!cancelled) setValue(result);
        });
      })
      .catch(() => {
        if (!cancelled) setValue(defaultRef.current);
      });
    return () => {
      cancelled = true;
    };
  }, [groupId]);

  return value;
}
