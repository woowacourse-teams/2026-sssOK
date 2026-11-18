import { useEffect, useLayoutEffect, useRef, useState, type RefObject } from "react";

import {
  intersects,
  rectangleBetween,
  selectRange,
  type Point,
  type SelectionRectangle,
} from "./dragSelection";

import { edgeScrollDelta, findScrollContainer, visibleScrollEdges } from "./galleryAutoScroll";

interface DragSelectionOptions {
  ids: number[];
  selectedIds: number[];
  onSelect?: (ids: number[]) => void;
}

interface Gesture {
  input: "mouse" | "touch";
  start: Point;
  current: Point;
  startId: number | null;
  initialIds: number[];
  mode: "select" | "deselect";
  active: boolean;
}

const pointOf = (event: MouseEvent | Touch): Point => ({ x: event.clientX, y: event.clientY });
const cardId = (target: EventTarget | null): number | null => {
  const card = target instanceof Element ? target.closest<HTMLElement>("[data-media-id]") : null;
  return card ? Number(card.dataset.mediaId) : null;
};

/** 입력 수명과 스크롤만 담당한다. 선택 상태는 부모의 일괄 선택 API로 갱신한다. */
interface DragSelectionResult {
  containerRef: RefObject<HTMLElement | null>;
  rectangle: SelectionRectangle | null;
  isSelecting: boolean;
}

export const useGalleryDragSelection = (options: DragSelectionOptions): DragSelectionResult => {
  const containerRef = useRef<HTMLElement>(null);
  const latest = useRef(options);
  const [rectangle, setRectangle] = useState<SelectionRectangle | null>(null);
  const [isSelecting, setIsSelecting] = useState(false);
  useLayoutEffect(() => {
    latest.current = options;
  });

  const enabled = options.onSelect !== undefined;
  useEffect(() => {
    const container = containerRef.current;
    if (!container || !enabled) return;
    let gesture: Gesture | null = null;
    let holdTimer: ReturnType<typeof setTimeout> | undefined;
    let clickTimer: ReturnType<typeof setTimeout> | undefined;
    let suppressClick = false;
    let suppressMouseAfterTouch = false;
    let frame = 0;
    let previousSelection = "";
    let scroller = container;
    let lastScrollTop = 0;
    let pointerMoved = false;
    let lastRectangle: SelectionRectangle | null = null;

    const updateSelection = (): void => {
      if (!gesture?.active) return;
      const cards = [...container.querySelectorAll<HTMLElement>("[data-media-id]")];
      let hits: number[];
      if (gesture.input === "mouse") {
        // 시작 지점은 콘텐츠 좌표, 현재 지점은 뷰포트 좌표로 보관해 스크롤에도 대응한다.
        const bounds = container.getBoundingClientRect();
        const start = {
          x: gesture.start.x + bounds.left - container.scrollLeft,
          y: gesture.start.y + bounds.top - container.scrollTop,
        };
        const area = rectangleBetween(start, gesture.current);
        if (
          !lastRectangle ||
          area.x !== lastRectangle.x ||
          area.y !== lastRectangle.y ||
          area.width !== lastRectangle.width ||
          area.height !== lastRectangle.height
        ) {
          lastRectangle = area;
          setRectangle(area);
        }
        hits = cards
          .filter((card) => intersects(area, card.getBoundingClientRect()))
          .map((card) => Number(card.dataset.mediaId));
      } else {
        const target = document.elementFromPoint(gesture.current.x, gesture.current.y);
        let endId = target && container.contains(target) ? cardId(target) : null;
        // 고정 하단 바나 카드 사이 여백에 손가락이 있어도 보이는 카드로 범위를 이어 간다.
        if (endId === null) {
          const edges = visibleScrollEdges(container, scroller);
          const y = Math.max(edges.top + 1, Math.min(edges.bottom - 1, gesture.current.y));
          let nearestDistance = Infinity;
          for (const card of cards) {
            const bounds = card.getBoundingClientRect();
            if (bounds.bottom <= edges.top || bounds.top >= edges.bottom) continue;
            const dx = Math.max(
              bounds.left - gesture.current.x,
              0,
              gesture.current.x - bounds.right,
            );
            const dy = Math.max(bounds.top - y, 0, y - bounds.bottom);
            const distance = Math.hypot(dx, dy);
            if (distance < nearestDistance) {
              nearestDistance = distance;
              endId = Number(card.dataset.mediaId);
            }
          }
        }
        if (endId === null || gesture.startId === null) return;
        hits = selectRange(latest.current.ids, gesture.startId, endId);
      }
      const hitIds = new Set(hits);
      const next =
        gesture.mode === "deselect"
          ? gesture.initialIds.filter((id) => !hitIds.has(id))
          : [...new Set([...gesture.initialIds, ...hits])];
      const key = next.join(",");
      if (key !== previousSelection) {
        previousSelection = key;
        latest.current.onSelect?.(next);
      }
    };

    const tick = (): void => {
      if (!gesture?.active) return;
      const edges = visibleScrollEdges(container, scroller);
      scroller.scrollTop += edgeScrollDelta(gesture.current.y, edges);
      if (pointerMoved || scroller.scrollTop !== lastScrollTop) {
        pointerMoved = false;
        updateSelection();
      }
      lastScrollTop = scroller.scrollTop;
      frame = requestAnimationFrame(tick);
    };

    const activate = (): void => {
      if (!gesture) return;
      scroller = findScrollContainer(container);
      lastScrollTop = scroller.scrollTop;
      gesture.active = true;
      suppressClick = true;
      setIsSelecting(true);
      updateSelection();
      frame = requestAnimationFrame(tick);
    };

    const finish = (cancel = false): void => {
      clearTimeout(holdTimer);
      cancelAnimationFrame(frame);
      if (gesture?.active) {
        suppressMouseAfterTouch = gesture.input === "touch";
        if (cancel) latest.current.onSelect?.(gesture.initialIds);
        lastRectangle = null;
        setRectangle(null);
        setIsSelecting(false);
        // 브라우저가 pointer/touch 종료 뒤 합성하는 클릭까지 막는다.
        clearTimeout(clickTimer);
        clickTimer = setTimeout(() => {
          suppressClick = false;
        }, 500);
      }
      gesture = null;
    };

    const begin = (input: Gesture["input"], point: Point, target: EventTarget | null): void => {
      if (gesture) {
        return;
      }
      if (!(target instanceof Element)) {
        return;
      }
      if (target.closest("[data-drag-selection-ignore]")) {
        return;
      }
      const startId = cardId(target);
      if (input === "touch" && startId === null) {
        return;
      }
      clearTimeout(clickTimer);
      pointerMoved = false;
      suppressClick = false;
      const bounds = container.getBoundingClientRect();
      gesture = {
        input,
        current: point,
        start:
          input === "mouse"
            ? {
                x: point.x - bounds.left + container.scrollLeft,
                y: point.y - bounds.top + container.scrollTop,
              }
            : point,
        startId,
        initialIds: [...latest.current.selectedIds],
        mode:
          startId !== null && latest.current.selectedIds.includes(startId) ? "deselect" : "select",
        active: false,
      };
      previousSelection = gesture.initialIds.join(",");
      if (input === "touch") holdTimer = setTimeout(activate, 400);
    };

    const mouseDown = (event: MouseEvent): void => {
      // 새 마우스 누르기는 다음 드래그다. 터치 뒤 합성된 마우스 입력만 제외한다.
      if (suppressClick && suppressMouseAfterTouch) return;
      if (event.button === 0) begin("mouse", pointOf(event), event.target);
    };
    const mouseMove = (event: MouseEvent): void => {
      if (gesture?.input !== "mouse") return;
      if (event.buttons !== 1) {
        finish();
        return;
      }
      pointerMoved =
        pointerMoved || gesture.current.x !== event.clientX || gesture.current.y !== event.clientY;
      gesture.current = pointOf(event);
      const bounds = container.getBoundingClientRect();
      const dx = event.clientX - (gesture.start.x + bounds.left - container.scrollLeft);
      const dy = event.clientY - (gesture.start.y + bounds.top - container.scrollTop);
      if (!gesture.active && Math.hypot(dx, dy) >= 6) activate();
      if (gesture.active) event.preventDefault();
    };
    const touchStart = (event: TouchEvent): void => {
      if (event.touches.length !== 1) {
        finish(true);
        return;
      }
      begin("touch", pointOf(event.touches[0]), event.target);
    };
    const touchMove = (event: TouchEvent): void => {
      if (gesture?.input !== "touch") return;
      if (event.touches.length !== 1) {
        finish(true);
        return;
      }
      pointerMoved =
        pointerMoved ||
        gesture.current.x !== event.touches[0].clientX ||
        gesture.current.y !== event.touches[0].clientY;
      gesture.current = pointOf(event.touches[0]);
      if (!gesture.active) {
        if (
          Math.hypot(gesture.current.x - gesture.start.x, gesture.current.y - gesture.start.y) > 10
        )
          finish();
        return;
      }
      if (!event.cancelable) {
        finish(true);
        return;
      }
      event.preventDefault();
    };
    const end = (): void => {
      updateSelection();
      finish();
    };
    const cancel = (): void => finish(true);
    const keyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape") cancel();
    };
    const visibilityChange = (): void => {
      if (document.hidden) cancel();
    };
    const click = (event: MouseEvent): void => {
      if (!suppressClick || event.detail === 0) return;
      event.preventDefault();
      event.stopPropagation();
      suppressClick = false;
    };
    const contextMenu = (event: Event): void => {
      if (gesture?.input === "touch") event.preventDefault();
    };
    const preventNativeDrag = (event: Event): void => event.preventDefault();

    container.addEventListener("mousedown", mouseDown);
    container.addEventListener("touchstart", touchStart, { passive: true });
    container.addEventListener("touchmove", touchMove, { passive: false });
    container.addEventListener("click", click, true);
    container.addEventListener("contextmenu", contextMenu);
    container.addEventListener("dragstart", preventNativeDrag);
    window.addEventListener("mousemove", mouseMove);
    window.addEventListener("mouseup", end);
    window.addEventListener("touchend", end);
    window.addEventListener("touchcancel", cancel);
    window.addEventListener("blur", cancel);
    window.addEventListener("keydown", keyDown);
    document.addEventListener("visibilitychange", visibilityChange);
    return () => {
      clearTimeout(holdTimer);
      clearTimeout(clickTimer);
      cancelAnimationFrame(frame);
      container.removeEventListener("mousedown", mouseDown);
      container.removeEventListener("touchstart", touchStart);
      container.removeEventListener("touchmove", touchMove);
      container.removeEventListener("click", click, true);
      container.removeEventListener("contextmenu", contextMenu);
      container.removeEventListener("dragstart", preventNativeDrag);
      window.removeEventListener("mousemove", mouseMove);
      window.removeEventListener("mouseup", end);
      window.removeEventListener("touchend", end);
      window.removeEventListener("touchcancel", cancel);
      window.removeEventListener("blur", cancel);
      window.removeEventListener("keydown", keyDown);
      document.removeEventListener("visibilitychange", visibilityChange);
    };
  }, [enabled]);

  return { containerRef, rectangle, isSelecting };
};
