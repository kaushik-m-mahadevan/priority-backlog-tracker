export interface YarnTypeView {
  id: string;
  brand: string;
  thickness: string;
  colour: string;
  notes: string | null;
}

export interface CreateYarnTypeRequest {
  brand: string;
  thickness: string;
  colour: string;
  notes: string | null;
}

export interface InventoryEntryView {
  id: string;
  userId: string;
  yarnTypeId: string;
  quantity: number;
  updatedAt: string;
}

export interface SetInventoryQuantityRequest {
  quantity: number;
}
