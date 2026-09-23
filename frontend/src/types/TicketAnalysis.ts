export type Category =
  | "TECHNICAL"
  | "BILLING"
  | "ACCOUNT"
  | "OTHER";

export type Priority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface TicketAnalysis {
  category: Category;
  priority: Priority;
  summary: string;
  suggestedAction: string;
}
