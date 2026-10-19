import styled from "@emotion/styled";
import { HiCheck } from "react-icons/hi2";
import {
  useLayoutEffect,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type ReactNode,
} from "react";
import { colors, radius, shadow, spacing, typography } from "@/shared/styles/tokens";

type Align = "start" | "end";

const VIEWPORT_GUTTER = 8;

export interface DropdownMenuProps {
  onClose: () => void;
  children: ReactNode;
  align?: Align;
}

export const DropdownMenu = ({ onClose, children, align = "end" }: DropdownMenuProps) => {
  const card = useRef<HTMLDivElement>(null);
  const [placement, setPlacement] = useState<Align>(align);

  // 정해 둔 쪽으로 열어 화면 밖으로 나가면 그리기 전에 반대쪽으로 연다.
  useLayoutEffect(() => {
    const rect = card.current?.getBoundingClientRect();

    if (!rect) return;

    if (align === "end" && rect.left < VIEWPORT_GUTTER) setPlacement("start");
    if (align === "start" && rect.right > window.innerWidth - VIEWPORT_GUTTER) setPlacement("end");
  }, [align]);

  return (
    <>
      <Overlay data-testid="dropdown-menu-overlay" onClick={onClose} />
      <Card ref={card} data-testid="dropdown-menu" $align={placement}>
        {children}
      </Card>
    </>
  );
};

interface DropdownMenuItemProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon?: ReactNode;
  tone?: "default" | "danger";
}

export const DropdownMenuItem = ({
  icon,
  tone = "default",
  type = "button",
  children,
  ...props
}: DropdownMenuItemProps) => {
  return (
    <Item type={type} $tone={tone} {...props}>
      {icon}
      <span>{children}</span>
    </Item>
  );
};

export const DropdownMenuAnchor = styled.div`
  position: relative;
  display: inline-flex;
`;

/** 고르지 않은 항목도 같은 폭을 비워 글자 시작 자리를 맞춘다. */
export const DropdownMenuCheck = ({ checked }: { checked: boolean }) => (
  <CheckSlot aria-hidden="true">{checked && <HiCheck />}</CheckSlot>
);

const CheckSlot = styled.span`
  display: inline-flex;
  flex: none;
  width: 20px;
  height: 20px;
  color: ${colors.primary};
`;

export const DropdownMenuDivider = styled.hr`
  width: 100%;
  margin: ${spacing[8]} 0;
  border: 0;
  border-top: 1px solid ${colors.borderDefault};
`;

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  z-index: 1000;
`;

const Card = styled.div<{ $align: "start" | "end" }>`
  position: absolute;
  top: calc(100% + ${spacing[8]});
  right: ${({ $align }) => ($align === "end" ? 0 : "auto")};
  left: ${({ $align }) => ($align === "start" ? 0 : "auto")};
  z-index: 1001;
  display: inline-flex;
  flex-direction: column;
  min-width: 176px;
  padding: ${spacing[8]};
  background: ${colors.backgroundDefault};
  border: 1px solid ${colors.borderDefault};
  border-radius: ${radius[16]};
  box-shadow: ${shadow.modal};
`;

const Item = styled.button<{ $tone: "default" | "danger" }>`
  display: flex;
  align-items: center;
  gap: ${spacing[12]};
  width: 100%;
  padding: 11px ${spacing[12]};
  border-radius: 10px;
  color: ${({ $tone }) => ($tone === "danger" ? colors.danger : colors.textStrong)};
  text-align: left;

  ${typography.label5}

  svg {
    flex: none;
    width: 20px;
    height: 20px;
  }

  &:hover {
    background-color: ${({ $tone }) =>
      $tone === "danger"
        ? `color-mix(in srgb, ${colors.danger} 10%, white)`
        : colors.primarySubtle};
    color: ${({ $tone }) => ($tone === "danger" ? colors.danger : colors.primary)};
  }

  &:disabled {
    background: transparent;
    color: ${colors.disabled};
    cursor: not-allowed;
  }
`;
