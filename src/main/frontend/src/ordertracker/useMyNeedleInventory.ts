import { materialInventoryApi } from "../materialinventory/api";
import { useLinkedMaterialResource } from "./useLinkedMaterialResource";

const EMPTY = new Map<string, number>();

/** Same idea as {@link useMyYarnInventory}, for needle types — the viewer's own on-hand
 *  quantity per Material Inventory NeedleType id, for this business's linked inventory
 *  group (empty map if none linked). */
export function useMyNeedleInventory(groupId: string | null): Map<string, number> {
  return useLinkedMaterialResource(groupId, EMPTY, (linkedGroupId) =>
    materialInventoryApi.myNeedleInventory(linkedGroupId).then((entries) => new Map(entries.map((e) => [e.needleTypeId, e.quantity]))));
}
