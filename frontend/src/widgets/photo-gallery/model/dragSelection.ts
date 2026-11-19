export interface Point {
  x: number;
  y: number;
}

export interface SelectionRectangle extends Point {
  width: number;
  height: number;
}

export const rectangleBetween = (start: Point, end: Point): SelectionRectangle => ({
  x: Math.min(start.x, end.x),
  y: Math.min(start.y, end.y),
  width: Math.abs(end.x - start.x),
  height: Math.abs(end.y - start.y),
});

export const intersects = (selection: SelectionRectangle, card: DOMRect): boolean =>
  card.left < selection.x + selection.width &&
  card.right > selection.x &&
  card.top < selection.y + selection.height &&
  card.bottom > selection.y;

export const selectRange = (ids: number[], startId: number, endId: number): number[] => {
  const start = ids.indexOf(startId);
  const end = ids.indexOf(endId);
  if (start === -1 || end === -1) return [];
  return ids.slice(Math.min(start, end), Math.max(start, end) + 1);
};
