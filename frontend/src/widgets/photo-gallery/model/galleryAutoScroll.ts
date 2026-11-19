/** 갤러리가 늘어나 문서가 스크롤되는 레이아웃도 지원한다. */
export const findScrollContainer = (gallery: HTMLElement): HTMLElement => {
  let element: HTMLElement | null = gallery;
  while (element) {
    const { overflowY } = getComputedStyle(element);
    if (/(auto|scroll)/.test(overflowY) && element.scrollHeight > element.clientHeight)
      return element;
    element = element.parentElement;
  }
  return document.scrollingElement instanceof HTMLElement
    ? document.scrollingElement
    : document.documentElement;
};

export interface ScrollEdges {
  top: number;
  bottom: number;
}

export const visibleScrollEdges = (gallery: HTMLElement, scroller: HTMLElement): ScrollEdges => {
  const viewport = window.visualViewport;
  const top = viewport?.offsetTop ?? 0;
  const bottom = top + (viewport?.height ?? window.innerHeight);
  const galleryBounds = gallery.getBoundingClientRect();
  const isDocument =
    scroller === document.scrollingElement || scroller === document.documentElement;
  const scrollBounds = isDocument ? { top, bottom } : scroller.getBoundingClientRect();
  return {
    top: Math.max(top, scrollBounds.top, galleryBounds.top),
    bottom: Math.min(bottom, scrollBounds.bottom, galleryBounds.bottom),
  };
};

export const edgeScrollDelta = (y: number, edges: ScrollEdges): number => {
  if (edges.bottom <= edges.top) return 0;
  const edge = Math.min(80, (edges.bottom - edges.top) / 3);
  if (y < edges.top + edge) return -Math.min(16, (edges.top + edge - y) / 4);
  if (y > edges.bottom - edge) return Math.min(16, (y - edges.bottom + edge) / 4);
  return 0;
};
