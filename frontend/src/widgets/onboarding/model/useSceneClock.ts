import { useEffect, useState } from "react";

const TICK_MS = 50;

/**
 * 장면이 시작된 뒤 흐른 시간(ms). 진행률 숫자처럼 CSS 만으로 그릴 수 없는 변화를
 * 실제 화면과 같은 순서로 보여주는 데 쓴다.
 *
 * 장면이 돌지 않을 때(지나간 장면, 움직임을 줄인 환경)는 무한대를 돌려줘
 * 마지막 모습(완료 토스트)에 멈춰 있게 한다. 장면을 다시 틀려면 부르는 쪽이 새로 그린다.
 */
export const useSceneClock = (running: boolean) => {
  const [startedAt] = useState(() => performance.now());
  const [now, setNow] = useState(startedAt);

  useEffect(() => {
    if (!running) return;

    const timer = window.setInterval(() => setNow(performance.now()), TICK_MS);
    return () => window.clearInterval(timer);
  }, [running]);

  return running ? now - startedAt : Number.POSITIVE_INFINITY;
};

/** `from` 부터 `to` 까지 0→100 으로 오르는 진행률 */
export const progressBetween = (elapsed: number, from: number, to: number) =>
  Math.round(Math.min(Math.max((elapsed - from) / (to - from), 0), 1) * 100);
