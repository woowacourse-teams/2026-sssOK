import {
  useCallback,
  useLayoutEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from "react";
import { useWindowVirtualizer, type Range } from "@tanstack/react-virtual";

import {
  GALLERY_BOTTOM_PADDING,
  GALLERY_GAP,
  GALLERY_OVERSCAN_ROWS,
  GALLERY_SIDE_PADDING,
  GALLERY_TOP_PADDING,
  galleryColumns,
  galleryRange,
  galleryHitIds,
} from "../lib/virtualGallery";
import { useGalleryDragSelection } from "../model/useGalleryDragSelection";
import { DragSelectionOverlay } from "./DragSelectionOverlay";
import { GalleryGrid, GallerySection } from "./PhotoGallery.styles";

interface Props {
  itemIds: number[];
  renderCard: (index: number) => ReactNode;
  selectedIds: number[];
  onSelect?: (ids: number[]) => void;
}

/** 사진 데이터와 선택 상태는 유지하고, 페이지 스크롤 주변의 카드만 마운트한다. */
export const VirtualGallery = ({ itemIds, renderCard, selectedIds, onSelect }: Props) => {
  const section = useRef<HTMLElement>(null);
  const [layout, setLayout] = useState({
    width: window.innerWidth,
    columns: galleryColumns(window.innerWidth),
    top: 0,
  });
  const [focusedId, setFocusedId] = useState<number | null>(null);
  const pendingFocus = useRef<{ index: number; last: boolean } | null>(null);

  useLayoutEffect(() => {
    const element = section.current!;
    const measure = (): void => {
      if (element.clientWidth === 0) return;
      const next = {
        width: element.clientWidth,
        columns: galleryColumns(window.innerWidth),
        top: Math.round(element.getBoundingClientRect().top + window.scrollY),
      };
      setLayout((previous) =>
        previous.width === next.width &&
        previous.columns === next.columns &&
        previous.top === next.top
          ? previous
          : next,
      );
    };
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(measure);
    observer?.observe(element);
    if (element.parentElement) observer?.observe(element.parentElement);
    window.addEventListener("resize", measure);
    measure();
    return () => {
      observer?.disconnect();
      window.removeEventListener("resize", measure);
    };
  }, []);

  const { rectangle, isSelecting } = useGalleryDragSelection({
    ids: itemIds,
    selectedIds,
    onSelect,
    containerRef: section,
    getMouseHits: (area) => {
      const bounds = section.current!.getBoundingClientRect();
      return galleryHitIds(itemIds, layout.columns, layout.width - GALLERY_SIDE_PADDING * 2, {
        ...area,
        x: area.x - bounds.left - GALLERY_SIDE_PADDING + section.current!.scrollLeft,
        y: area.y - bounds.top - GALLERY_TOP_PADDING + section.current!.scrollTop,
      });
    },
  });

  const focusedIndex = focusedId === null ? -1 : itemIds.indexOf(focusedId);
  const focusedRow = focusedIndex < 0 ? null : Math.floor(focusedIndex / layout.columns);
  const rangeExtractor = useCallback(
    (range: Range) => galleryRange(range, focusedRow),
    [focusedRow],
  );
  const cardWidth = Math.max(
    1,
    (layout.width - GALLERY_SIDE_PADDING * 2 - GALLERY_GAP * (layout.columns - 1)) / layout.columns,
  );
  const rowSize = (cardWidth * 9) / 8 + GALLERY_GAP;
  const virtualizer = useWindowVirtualizer({
    count: Math.ceil(itemIds.length / layout.columns),
    estimateSize: () => rowSize,
    scrollMargin: layout.top,
    overscan: GALLERY_OVERSCAN_ROWS,
    rangeExtractor,
    paddingStart: GALLERY_TOP_PADDING,
    paddingEnd: GALLERY_BOTTOM_PADDING - GALLERY_GAP,
    initialRect: { width: window.innerWidth, height: window.innerHeight },
  });
  useLayoutEffect(() => {
    virtualizer.measure();
  }, [virtualizer, rowSize]);

  useLayoutEffect(() => {
    const pending = pendingFocus.current;
    if (!pending) return;
    const card = section.current?.querySelector(`[data-gallery-index="${pending.index}"]`);
    const buttons = card?.querySelectorAll<HTMLButtonElement>("button:not(:disabled)");
    const button = pending.last ? buttons?.[buttons.length - 1] : buttons?.[0];
    if (button) {
      button.focus();
      pendingFocus.current = null;
    }
  });

  // Tab으로 아직 마운트되지 않은 이웃 카드를 건너뛰지 않도록 경계에서만 이동한다.
  const handleKeyDown = (event: KeyboardEvent<HTMLElement>): void => {
    if (event.key !== "Tab" || event.altKey || event.ctrlKey || event.metaKey) return;
    const target = event.target as HTMLElement;
    const card = target.closest<HTMLElement>("[data-gallery-index]");
    if (!card) return;
    const buttons = card.querySelectorAll<HTMLButtonElement>("button:not(:disabled)");
    const boundary = event.shiftKey ? buttons[0] : buttons[buttons.length - 1];
    if (target !== boundary) return;
    const index = Number(card.dataset.galleryIndex) + (event.shiftKey ? -1 : 1);
    if (index < 0 || index >= itemIds.length) return;
    if (section.current?.querySelector(`[data-gallery-index="${index}"]`)) return;
    event.preventDefault();
    pendingFocus.current = { index, last: event.shiftKey };
    virtualizer.scrollToIndex(Math.floor(index / layout.columns), { align: "auto" });
  };

  return (
    <GallerySection
      ref={section}
      $selecting={isSelecting}
      onKeyDown={handleKeyDown}
      onFocusCapture={(event) => {
        const card = (event.target as HTMLElement).closest<HTMLElement>("[data-gallery-index]");
        if (card) setFocusedId(itemIds[Number(card.dataset.galleryIndex)]);
      }}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocusedId(null);
      }}
      style={{ paddingTop: 0, paddingBottom: 0 }}
    >
      <div style={{ position: "relative", height: virtualizer.getTotalSize() }}>
        {virtualizer.getVirtualItems().map((row) => (
          <GalleryGrid
            key={row.key}
            style={{
              position: "absolute",
              top: 0,
              left: 0,
              width: "100%",
              transform: `translateY(${row.start - layout.top}px)`,
            }}
          >
            {itemIds
              .slice(row.index * layout.columns, (row.index + 1) * layout.columns)
              .map((id, column) => {
                const index = row.index * layout.columns + column;
                return (
                  <div key={id} data-gallery-index={index} style={{ minWidth: 0 }}>
                    {renderCard(index)}
                  </div>
                );
              })}
          </GalleryGrid>
        ))}
      </div>
      <DragSelectionOverlay rectangle={rectangle} />
    </GallerySection>
  );
};
