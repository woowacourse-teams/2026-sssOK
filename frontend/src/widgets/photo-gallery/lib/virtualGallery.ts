import type { Range } from "@tanstack/react-virtual";

export const GALLERY_GAP = 12;
export const GALLERY_SIDE_PADDING = 16;
export const GALLERY_TOP_PADDING = 20;
export const GALLERY_BOTTOM_PADDING = 120;
export const GALLERY_OVERSCAN_ROWS = 12;

export const galleryColumns = (width: number): number =>
  width >= 1080 ? 5 : width >= 860 ? 4 : width >= 600 ? 3 : 2;

/** 화면 주변 여유 행과 포커스된 행을 유지한다. */
export const galleryRange = (range: Range, focusedRow: number | null): number[] => {
  const start = Math.max(0, range.startIndex - range.overscan);
  const end = Math.min(range.count - 1, range.endIndex + range.overscan);
  const rows = Array.from({ length: Math.max(0, end - start + 1) }, (_, index) => start + index);
  if (
    focusedRow !== null &&
    focusedRow >= 0 &&
    focusedRow < range.count &&
    !rows.includes(focusedRow)
  ) {
    rows.push(focusedRow);
    rows.sort((a, b) => a - b);
  }
  return rows;
};

/** 고정 비율 카드의 좌표로 계산하므로 마운트되지 않은 사진도 드래그 범위에 포함된다. */
export const galleryHitIds = (
  ids: number[],
  columns: number,
  width: number,
  area: { x: number; y: number; width: number; height: number },
): number[] => {
  const cardWidth = Math.max(1, (width - GALLERY_GAP * (columns - 1)) / columns);
  const cardHeight = (cardWidth * 9) / 8;
  const rowSize = cardHeight + GALLERY_GAP;
  const firstRow = Math.max(0, Math.floor(area.y / rowSize));
  const lastRow = Math.min(
    Math.ceil(ids.length / columns) - 1,
    Math.floor((area.y + area.height) / rowSize),
  );
  const hits: number[] = [];
  for (let row = firstRow; row <= lastRow; row++) {
    for (let column = 0; column < columns; column++) {
      const index = row * columns + column;
      const left = column * (cardWidth + GALLERY_GAP);
      const top = row * rowSize;
      if (
        index < ids.length &&
        left < area.x + area.width &&
        left + cardWidth > area.x &&
        top < area.y + area.height &&
        top + cardHeight > area.y
      )
        hits.push(ids[index]);
    }
  }
  return hits;
};
