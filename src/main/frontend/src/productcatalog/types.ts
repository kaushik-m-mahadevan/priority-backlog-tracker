export type PatternType = "TEMPLATE" | "CUSTOM";

export interface Pattern {
  patternType: PatternType | null;
  templateName: string | null;
  customPatternNotes: string | null;
  attachmentUrls: string[];
  recipeSteps: string[];
  referenceLink: string | null;
}

export interface ColorwayView {
  id: string;
  name: string;
  colour: string;
  pattern: Pattern | null;
  estimatedCost: number | null;
  notes: string | null;
  ideabox: boolean;
  createdAt: string;
}

export interface CreateColorwayRequest {
  name: string;
  colour: string;
  estimatedCost: number | null;
  notes: string | null;
  recipeSteps: string[] | null;
  referenceLink: string | null;
}
