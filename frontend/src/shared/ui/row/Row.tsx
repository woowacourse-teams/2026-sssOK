import type { ComponentPropsWithoutRef } from "react";
import type { LayoutProps } from "../layout.types";
import { StyledRow } from "./Row.styles";

export type RowProps = LayoutProps & ComponentPropsWithoutRef<"div">;

export const Row = ({
  gap,
  align = "stretch",
  justify = "flex-start",
  children,
  ...props
}: RowProps) => {
  return (
    <StyledRow gap={gap} align={align} justify={justify} {...props}>
      {children}
    </StyledRow>
  );
};
