import type { ComponentPropsWithoutRef } from "react";

import type { LayoutProps } from "../layout.types";
import { StyledStack } from "./Stack.styles";

export type StackProps = LayoutProps & ComponentPropsWithoutRef<"div">;

export const Stack = ({
  gap,
  align = "stretch",
  justify = "flex-start",
  children,
  ...props
}: StackProps) => {
  return (
    <StyledStack gap={gap} align={align} justify={justify} {...props}>
      {children}
    </StyledStack>
  );
};
