import { useEffect, useState } from "react";
import { api } from "../api/client";
import { materialInventoryApi } from "../materialinventory/api";

const MATERIAL_INVENTORY_APPLET_KEY = "materialinventory";

/** The current user's own on-hand quantity per Material Inventory YarnType id, for this
 *  business's linked inventory group (empty map if none linked) — used to show a
 *  shortfall warning against a linked material entry's needed quantity. Deliberately
 *  checks only the viewer's own stock (design decision), matching the established
 *  per-person-inventory pattern everywhere else in Material Inventory, not a cross-member
 *  aggregate. */
export function useMyYarnInventory(groupId: string | null): Map<string, number> {
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
        return materialInventoryApi.myInventory(link.linkedGroupId).then((entries) => {
          if (!cancelled) setQuantities(new Map(entries.map((e) => [e.yarnTypeId, e.quantity])));
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
