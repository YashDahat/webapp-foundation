// FOUNDATION-owned order-board sprint types (mirror backend SprintDto / UpsertSprintRequest /
// CompleteSprintRequest). Sprints are time-boxed batches per workflow; exactly one is ACTIVE at a time.
// Completing the active sprint closes it, opens the next, and carries forward the ticked open orders.

export type SprintStatus = 'ACTIVE' | 'CLOSED';

export interface SprintDto {
  id: number;
  workflowCode: string;
  name: string;
  startsOn?: string | null; // ISO date (yyyy-MM-dd)
  endsOn?: string | null;
  status: SprintStatus;
  createdAt: string;
}

/** Create (id omitted ⇒ new ACTIVE sprint) or edit (id present ⇒ name/dates only) a sprint. */
export interface UpsertSprintRequest {
  id?: number | null;
  workflowCode: string;
  name: string;
  startsOn?: string | null;
  endsOn?: string | null;
}

/** Complete the active sprint: close it, open the next, carry forward the ticked open orders. */
export interface CompleteSprintRequest {
  nextName?: string | null;
  nextStartsOn?: string | null;
  nextEndsOn?: string | null;
  carryForwardOrderIds: number[];
}
