export type Role = "ADMIN" | "USER";
export type AccountStatus = "PENDING" | "ACTIVE";

export interface UserSummary {
  id: string;
  name: string;
  handle: string;
  email: string;
  role: Role;
  status: AccountStatus;
}

export interface GroupView {
  id: string;
  name: string;
  createdAt: string | null;
  members: UserSummary[];
}

export interface User {
  id: string;
  name: string;
  handle?: string;
  email: string;
  role: Role;
  status: AccountStatus;
}

export interface Effort {
  value: number;
  unit: "MINUTES" | "HOURS" | "DAYS";
  minutes: number;
}

export interface Item {
  id: string;
  itemId: string;
  title: string;
  category: string;
  priority: string;
  effort: Effort | null;
  dueDate: string | null;
  status: "BACKLOG" | "IN_PROGRESS";
  groupId: string;
  ownerId: string | null;
  createdBy: string | null;
  lastUpdatedBy: string | null;
  notes: { content: string; format: string; updatedAt: string } | null;
  createdAt: string | null;
  updatedAt: string | null;
  version: number | null;
}

export interface RankedItem {
  item: Item;
  priorityFactor: number;
  urgencyFactor: number;
  effortFactor: number;
  sortScore: number;
}

export interface FlaggedItem {
  item: Item;
  days: number;
}

export interface NeedsAttention {
  staleAndOverdue: FlaggedItem[];
  buriedLowPriority: FlaggedItem[];
}

export interface OwnerWorkload {
  ownerId: string | null;
  ownerName: string;
  openCount: number;
  byCategory: Record<string, number>;
  criticalHighCount: number;
}

export interface WorkloadOverview {
  owners: OwnerWorkload[];
}

export interface ArchivedItem {
  id: string;
  itemId: string;
  title: string;
  category: string;
  priority: string;
  effort: Effort | null;
  dueDate: string | null;
  groupId: string;
  ownerId: string | null;
  terminalStatus: "RESOLVED" | "REJECTED" | "ARCHIVED";
  completionDate: string | null;
  movedAt: string | null;
  createdAt: string | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface CompletionStats {
  count: number;
  days: number;
  lastCompletedAt: string | null;
}

export interface AppConfig {
  id: string;
  priorityValues: Record<string, number>;
  priorities: string[];
  priorityWeight: number;
  urgencyWeight: number;
  effortWeight: number;
  urgencyWindowDays: number;
  staleThresholdDays: number;
  buriedThresholdDays: number;
  buriedPriorityLevels: string[];
  categories: string[];
  defaultDueDateOffsetDays: number;
  effortCapDays: number;
  maxGroupsPerUser: number;
}
