import { spacing } from "@/shared/styles/tokens";

export interface LayoutProps {
  gap?: keyof typeof spacing;
  align?: "flex-start" | "center" | "flex-end" | "stretch";
  justify?: "flex-start" | "center" | "flex-end" | "space-between";
}
