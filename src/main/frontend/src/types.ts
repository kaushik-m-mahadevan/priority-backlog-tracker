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

/** One outstanding invite on a group's "Pending invites" list — view-only, no cancel/
 *  revoke action yet. */
export interface PendingInvite {
  id: string;
  invitedDisplay: string;
  invitedByName: string;
  createdAt: string;
}

export interface User {
  id: string;
  name: string;
  handle?: string;
  email: string;
  role: Role;
  status: AccountStatus;
}

export interface GroveHealth {
  overdue: number;
  stale: number;
  neglect: number;
  stage: number; // 0 healthy .. 4 stump
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
  pinned: boolean;
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

export interface TopList {
  items: RankedItem[];
  pinnedCount: number;
  overPinned: boolean;
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

/** The three ways a live item leaves the backlog (mirrors the backend's TerminalStatus
 *  enum) — the single source for this list; anywhere that needs to iterate the options
 *  (e.g. a "complete as..." picker) should map over this array, not redeclare it. */
export const TERMINAL_STATUSES = ["RESOLVED", "REJECTED", "ARCHIVED"] as const;
export type TerminalStatus = (typeof TERMINAL_STATUSES)[number];

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
  terminalStatus: TerminalStatus;
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
  defaultDueDateOffsetDays: number;
  effortCapDays: number;
  maxGroupsPerUser: number;
}
