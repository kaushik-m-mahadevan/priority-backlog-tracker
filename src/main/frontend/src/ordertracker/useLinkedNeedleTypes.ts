import { materialInventoryApi } from "../materialinventory/api";
import { useLinkedMaterialResource } from "./useLinkedMaterialResource";
import type { LinkableNeedleType } from "./OrderFormFields";

const EMPTY: LinkableNeedleType[] = [];

/** Same idea as {@link useLinkedYarnTypes}, for needle entries. */
export function useLinkedNeedleTypes(groupId: string | null): LinkableNeedleType[] {
  return useLinkedMaterialResource(groupId, EMPTY, (linkedGroupId) => materialInventoryApi.needleTypes(linkedGroupId));
}
