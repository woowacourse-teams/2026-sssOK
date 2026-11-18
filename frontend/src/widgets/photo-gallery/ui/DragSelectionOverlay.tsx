import styled from "@emotion/styled";
import { createPortal } from "react-dom";

import { colors } from "@/shared/styles/tokens";
import type { SelectionRectangle } from "../model/dragSelection";

export const DragSelectionOverlay = ({ rectangle }: { rectangle: SelectionRectangle | null }) =>
  rectangle
    ? createPortal(
        <SelectionArea
          aria-hidden="true"
          style={{
            left: rectangle.x,
            top: rectangle.y,
            width: rectangle.width,
            height: rectangle.height,
          }}
        />,
        document.body,
      )
    : null;

const SelectionArea = styled.div`
  position: fixed;
  z-index: 1000;
  pointer-events: none;
  border: 1px solid ${colors.primary};
  background: ${colors.primarySubtle};
  opacity: 0.45;
`;
