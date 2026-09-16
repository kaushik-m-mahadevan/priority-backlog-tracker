import { useEffect, useState } from "react";
import { api } from "../api/client";
import { materialInventoryApi } from "../materialinventory/api";

const MATERIAL_INVENTORY_APPLET_KEY = "materialinventory";

/** Same idea as {@link useMyYarnInventory}, for needle types — the viewer's own on-hand
 *  quantity per Material Inventory NeedleType id, for this business's linked inventory
 *  group (empty map if none linked). */
export function useMyNeedleInventory(groupId: string | null): Map<string, number> {
  const [quantities, setQuantities] = useState<Map<string, number>>(new Map());

  useEffect(() => {
    if (!groupId) {
      setQuantities(new Map());
      return;
    }
    let cancelled = false;
    api
      .get<{ linkedGroupId: string | null }>(`/groups/${groupId}/links/${MATERIAL_INVENTORY_APPLET_KEY}`)
      .then((link) => {
        if (cancelled || !link.linkedGroupId) {
          if (!cancelled) setQuantities(new Map());
          return;
        }
        return materialInventoryApi.myNeedleInventory(link.linkedGroupId).then((entries) => {
          if (!cancelled) setQuantities(new Map(entries.map((e) => [e.needleTypeId, e.quantity])));
        });
      })
      .catch(() => {
        if (!cancelled) setQuantities(new Map());
      });
    return () => {
      cancelled = true;
    };
  }, [groupId]);

  return quantities;
}
