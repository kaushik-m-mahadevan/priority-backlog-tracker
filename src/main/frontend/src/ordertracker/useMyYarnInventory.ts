import { materialInventoryApi } from "../materialinventory/api";
import { useLinkedMaterialResource } from "./useLinkedMaterialResource";

const EMPTY = new Map<string, number>();

/** The current user's own on-hand quantity per Material Inventory YarnType id, for this
 *  business's linked inventory group (empty map if none linked) — used to show a
 *  shortfall warning against a linked material entry's needed quantity. Deliberately
 *  checks only the viewer's own stock (design decision), matching the established
 *  per-person-inventory pattern everywhere else in Material Inventory, not a cross-member
 *  aggregate. */
export function useMyYarnInventory(groupId: string | null): Map<string, number> {
  return useLinkedMaterialResource(groupId, EMPTY, (linkedGroupId) =>
    materialInventoryApi.myInventory(linkedGroupId).then((entries) => new Map(entries.map((e) => [e.yarnTypeId, e.quantity]))));
}
