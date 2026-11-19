import { useEffect, useState, type DialogHTMLAttributes } from "react";

type DialogEvents = Pick<DialogHTMLAttributes<HTMLDialogElement>, "onCancel" | "onKeyDown">;

export const useViewerDialog = ({
  blocked,
  onClose,
  onPrevious,
  onNext,
}: {
  blocked: boolean;
  onClose: () => void;
  onPrevious: (() => void) | undefined;
  onNext: (() => void) | undefined;
}): {
  dialog: HTMLDialogElement | null;
  setDialog: (dialog: HTMLDialogElement | null) => void;
  events: DialogEvents;
} => {
  const [dialog, setDialog] = useState<HTMLDialogElement | null>(null);

  useEffect(() => {
    if (!dialog) return;
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    dialog.showModal();
    dialog.focus();
    document.body.style.overflow = "hidden";

    return () => {
      dialog.close();
      document.body.style.overflow = overflow;
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, [dialog]);

  return {
    dialog,
    setDialog,
    events: {
      onCancel: (event) => {
        event.preventDefault();
        if (!blocked) onClose();
      },
      onKeyDown: (event) => {
        if (
          event.target instanceof Element &&
          event.target.closest("dialog") !== event.currentTarget
        )
          return;
        if (blocked) {
          if (event.key === "Escape") event.preventDefault();
          return;
        }
        const move =
          event.key === "ArrowLeft" ? onPrevious : event.key === "ArrowRight" ? onNext : undefined;
        if (move) {
          event.preventDefault();
          move();
        }
      },
    },
  };
};
