export type AgentStatus =
  | "COMPLETED"
  | "STEP_LIMIT_REACHED"
  | "PLANNER_ERROR"
  | "TOOL_ERROR";

export type AgentAction = "CHECK_STATUS" | "CHECK_PRIORITY" | "FINISH";

export interface AgentStep {
  action: AgentAction;
  result: string;
}

export interface AgentAnalysis {
  answer: string;
  completed: boolean;
  status: AgentStatus;
  steps: AgentStep[];
}
