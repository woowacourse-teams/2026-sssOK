import { useEffect, useState } from "react";

/*
 * 화면에 나타나고 `delayMs` 만큼 기다린 뒤에 true 를 돌려준다.
 * 금방 끝나는 로딩에 스피너를 바로 띄우면, 깜빡이기만 하고 사라져서 오히려 거슬리기 때문에
 * 로딩이 짧으면 스피너를 아예 안 보여주려고 쓴다.
 */
export const useDelayedVisibility = (delayMs: number) => {
  const [isVisible, setIsVisible] = useState(delayMs <= 0);

  useEffect(() => {
    if (delayMs <= 0) return;

    const timer = window.setTimeout(() => setIsVisible(true), delayMs);

    return () => window.clearTimeout(timer);
  }, [delayMs]);

  return isVisible;
};
