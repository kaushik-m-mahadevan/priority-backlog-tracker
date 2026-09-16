import { useEffect, useState } from "react";
import { api } from "../api/client";
import { materialInventoryApi } from "../materialinventory/api";
import type { LinkableYarnType } from "./OrderFormFields";

const MATERIAL_INVENTORY_APPLET_KEY = "materialinventory";

/** If this business has a linked Material Inventory group, returns that group's yarn
 *  catalog (for the optional "link to inventory yarn" dropdown on a material entry);
 *  otherwise an empty array, which hides the dropdown entirely (design decision: opt-in).
 *  Frontend-only cross-applet read — Order Tracker's own backend never talks to Material
 *  Inventory's, consistent with the platform's no-cross-applet-import rule; the browser
 *  is free to call both APIs and join the results itself. */
export function useLinkedYarnTypes(groupId: string | null): LinkableYarnType[] {
  const [yarnTypes, setYarnTypes] = useState<LinkableYarnType[]>([]);

  useEffect(() => {
    if (!groupId) {
      setYarnTypes([]);
      return;
    }
    let cancelled = false;
    api
      .get<{ linkedGroupId: string | null }>(`/groups/${groupId}/links/${MATERIAL_INVENTORY_APPLET_KEY}`)
      .then((link) => {
        if (cancelled || !link.linkedGroupId) {
          if (!cancelled) setYarnTypes([]);
          return;
        }
        return materialInventoryApi.yarnTypes(link.linkedGroupId).then((types) => {
          if (!cancelled) setYarnTypes(types);
        });
      })
      .catch(() => {
        if (!cancelled) setYarnTypes([]);
      });
    return () => {
      cancelled = true;
    };
  }, [groupId]);

  return yarnTypes;
}
