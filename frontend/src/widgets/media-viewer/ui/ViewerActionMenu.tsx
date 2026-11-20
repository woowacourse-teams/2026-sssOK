import { useEffect, useRef, useState, type ReactNode } from "react";
import { HiEllipsisHorizontal } from "react-icons/hi2";
import {
  MoreActions,
  MoreButton,
  ActionList,
  MoreActionButton,
  ActionLabel,
} from "./ViewerActionMenu.styles";

export interface ViewerAction {
  label: string;
  icon: ReactNode;
  onSelect: () => void;
  title?: string;
  danger?: boolean;
  disabled?: boolean;
  busy?: boolean;
}

export const ViewerActionMenu = ({ actions }: { actions: ViewerAction[] }) => {
  const [open, setOpen] = useState(false);
  const actionsRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const closeOutside = (event: PointerEvent): void => {
      if (event.target instanceof Node && !actionsRef.current?.contains(event.target))
        setOpen(false);
    };
    document.addEventListener("pointerdown", closeOutside);
    return () => document.removeEventListener("pointerdown", closeOutside);
  }, [open]);

  return (
    <MoreActions
      ref={actionsRef}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
      }}
      onKeyDown={(event) => {
        if (open && event.key === "Escape") {
          event.preventDefault();
          event.stopPropagation();
          setOpen(false);
          buttonRef.current?.focus();
        }
        if (open && (event.key === "ArrowLeft" || event.key === "ArrowRight"))
          event.stopPropagation();
      }}
    >
      <MoreButton
        ref={buttonRef}
        type="button"
        aria-label="사진 더보기"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <HiEllipsisHorizontal size={24} />
      </MoreButton>
      <ActionList $open={open}>
        {actions.map((action) => (
          <MoreActionButton
            key={action.label}
            type="button"
            aria-label={action.label}
            title={action.title ?? action.label}
            $danger={action.danger}
            disabled={action.disabled}
            aria-busy={action.busy}
            onClick={() => {
              setOpen(false);
              action.onSelect();
            }}
          >
            {action.icon}
            <ActionLabel>{action.label}</ActionLabel>
          </MoreActionButton>
        ))}
      </ActionList>
    </MoreActions>
  );
};
