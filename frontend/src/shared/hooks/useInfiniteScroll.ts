import { useEffect, type RefObject } from "react";

interface UseInfiniteScrollParams {
  /** 목록 끝에 두는 감시 대상. */
  target: RefObject<HTMLElement | null>;
  /** 더 불러올 것이 있고, 지금 불러오는 중이 아닐 때만 켠다. */
  enabled: boolean;
  onReach: () => void;
}

/**
 * 목록 끝이 화면에 들어오면 다음 페이지를 부른다.
 *
 * 스크롤 이벤트 대신 IntersectionObserver 를 쓴다 — 스크롤은 1초에 수십 번 들어와서
 * 매번 위치를 재야 하지만, 이쪽은 브라우저가 걸렸을 때만 한 번 알려준다.
 *
 * `enabled` 가 false 면 아예 붙이지 않는다. 불러오는 중에도 걸려 있으면 같은 페이지를
 * 여러 번 부른다 — 관찰자는 "이미 부른 적 있음" 을 알지 못한다.
 */
export const useInfiniteScroll = ({ target, enabled, onReach }: UseInfiniteScrollParams) => {
  useEffect(() => {
    const element = target.current;

    if (!enabled || element === null) return;

    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) onReach();
    });

    observer.observe(element);

    return () => observer.disconnect();
  }, [target, enabled, onReach]);
};
