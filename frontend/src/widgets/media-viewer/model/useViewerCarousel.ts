import { useLayoutEffect, useRef, type HTMLAttributes, type RefObject } from "react";

const TRANSITION_MS = 220;

interface Gesture {
  x: number;
  y: number;
  offset: number;
  axis: "x" | "y" | null;
}

export const useViewerCarousel = (
  mediaIds: number[],
  activeMediaId: number,
  onChange: (mediaId: number) => void,
): {
  ref: RefObject<HTMLDivElement | null>;
  events: HTMLAttributes<HTMLDivElement>;
} => {
  const ref = useRef<HTMLDivElement>(null);
  const gesture = useRef<Gesture | null>(null);
  const target = useRef<number | null>(null);
  const blockClick = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const index = mediaIds.indexOf(activeMediaId);
  const previousId = mediaIds[index - 1];
  const nextId = mediaIds[index + 1];

  useLayoutEffect(() => {
    clearTimeout(timer.current);
    gesture.current = null;
    target.current = null;
    if (ref.current) {
      ref.current.style.transition = "none";
      ref.current.style.transform = `translate3d(${-index * 100}%, 0, 0)`;
    }
    return () => clearTimeout(timer.current);
  }, [activeMediaId, index, previousId, nextId]);

  const finish = (): void => {
    const position = target.current;
    if (position === null) return;
    clearTimeout(timer.current);
    target.current = null;
    if (ref.current) {
      ref.current.style.transition = "none";
      ref.current.style.transform = `translate3d(${-position * 100}%, 0, 0)`;
    }
    const mediaId = mediaIds[position];
    if (mediaId !== undefined && mediaId !== activeMediaId) onChange(mediaId);
  };

  const animateTo = (position: number, offset: number): void => {
    const strip = ref.current;
    if (!strip) return;
    target.current = position;
    const remaining = Math.abs((index - position) * strip.clientWidth - offset);
    if (remaining < 1 || window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) {
      finish();
      return;
    }
    strip.style.transition = `transform ${TRANSITION_MS}ms cubic-bezier(0.22, 0.61, 0.36, 1)`;
    strip.style.transform = `translate3d(${-position * 100}%, 0, 0)`;
    timer.current = setTimeout(finish, TRANSITION_MS + 50);
  };

  const cancel = (): void => {
    if (target.current !== null) return;
    const offset = gesture.current?.offset ?? 0;
    gesture.current = null;
    animateTo(index, offset);
  };

  return {
    ref,
    events: {
      onTouchStart: (event) => {
        if (target.current !== null) return;
        blockClick.current = false;
        if (
          event.touches.length !== 1 ||
          event.target instanceof HTMLVideoElement ||
          (window.visualViewport?.scale ?? 1) > 1
        ) {
          cancel();
          return;
        }
        const touch = event.touches[0];
        gesture.current = { x: touch.clientX, y: touch.clientY, offset: 0, axis: null };
        if (ref.current) ref.current.style.transition = "none";
      },
      onTouchMove: (event) => {
        if (event.touches.length !== 1) {
          cancel();
          return;
        }
        const start = gesture.current;
        const strip = ref.current;
        if (!start || !strip) return;
        const dx = event.touches[0].clientX - start.x;
        const dy = event.touches[0].clientY - start.y;
        if (start.axis === null && Math.max(Math.abs(dx), Math.abs(dy)) >= 8) {
          start.axis = Math.abs(dx) > Math.abs(dy) ? "x" : "y";
          blockClick.current = true;
        }
        if (start.axis !== "x") return;
        const atEdge = (dx > 0 && index === 0) || (dx < 0 && index === mediaIds.length - 1);
        start.offset = atEdge
          ? Math.max(-40, Math.min(40, dx / 4))
          : Math.max(-strip.clientWidth, Math.min(strip.clientWidth, dx));
        strip.style.transform = `translate3d(calc(${-index * 100}% + ${start.offset}px), 0, 0)`;
      },
      onTouchEnd: (event) => {
        const start = gesture.current;
        const touch = event.changedTouches[0];
        if (!start || !touch) return;
        gesture.current = null;
        const dx = touch.clientX - start.x;
        const direction = start.axis === "x" && Math.abs(dx) >= 60 ? (dx < 0 ? 1 : -1) : 0;
        animateTo(Math.max(0, Math.min(mediaIds.length - 1, index + direction)), start.offset);
      },
      onTouchCancel: cancel,
      onTransitionEnd: (event) => {
        if (event.target === event.currentTarget && event.propertyName === "transform") finish();
      },
      onClickCapture: (event) => {
        if (!blockClick.current && target.current === null) return;
        blockClick.current = false;
        event.preventDefault();
        event.stopPropagation();
      },
    },
  };
};
