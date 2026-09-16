import { useEffect, useState } from "react";
import { api } from "../api/client";
import { materialInventoryApi } from "../materialinventory/api";
import type { LinkableNeedleType } from "./OrderFormFields";

const MATERIAL_INVENTORY_APPLET_KEY = "materialinventory";

/** Same idea as {@link useLinkedYarnTypes}, for needle entries — if this business has a
 *  linked Material Inventory group, returns that group's needle catalog for the optional
 *  "link to inventory needle" dropdown; otherwise an empty array, which hides the dropdown
 *  entirely (design decision: opt-in). Frontend-only cross-applet read. */
export function useLinkedNeedleTypes(groupId: string | null): LinkableNeedleType[] {
  const [needleTypes, setNeedleTypes] = useState<LinkableNeedleType[]>([]);

  useEffect(() => {
    if (!groupId) {
      setNeedleTypes([]);
      return;
    }
    let cancelled = false;
    api
      .get<{ linkedGroupId: string | null }>(`/groups/${groupId}/links/${MATERIAL_INVENTORY_APPLET_KEY}`)
      .then((link) => {
        if (cancelled || !link.linkedGroupId) {
          if (!cancelled) setNeedleTypes([]);
          return;
        }
        return materialInventoryApi.needleTypes(link.linkedGroupId).then((types) => {
          if (!cancelled) setNeedleTypes(types);
        });
      })
      .catch(() => {
        if (!cancelled) setNeedleTypes([]);
      });
    return () => {
      cancelled = true;
    };
  }, [groupId]);

  return needleTypes;
}
