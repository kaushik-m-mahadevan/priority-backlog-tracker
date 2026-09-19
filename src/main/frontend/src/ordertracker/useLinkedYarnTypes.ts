import { materialInventoryApi } from "../materialinventory/api";
import { useLinkedMaterialResource } from "./useLinkedMaterialResource";
import type { LinkableYarnType } from "./OrderFormFields";

const EMPTY: LinkableYarnType[] = [];

/** If this business has a linked Material Inventory group, returns that group's yarn
 *  catalog (for the optional "link to inventory yarn" dropdown on a material entry);
 *  otherwise an empty array, which hides the dropdown entirely (design decision: opt-in). */
export function useLinkedYarnTypes(groupId: string | null): LinkableYarnType[] {
  return useLinkedMaterialResource(groupId, EMPTY, (linkedGroupId) => materialInventoryApi.yarnTypes(linkedGroupId));
}
