import { useCallback, useEffect, useState } from "react";

/**
 * 남은 초를 1초씩 줄이다 0 에서 멈춘다.
 *
 * 남은 초를 직접 빼지 않고 **마감 시각에서 매번 다시 계산한다.** 1초짜리 타이머는 정확히
 * 1초마다 오지 않고(탭이 백그라운드로 가면 더 크게 밀린다), 뺄셈으로 세면 그 오차가 그대로
 * 쌓여 실제보다 늦게 풀린다. 마감 시각을 붙들고 있으면 몇 번을 건너뛰든 남은 초는 항상 맞다.
 */
export const useRetryCountdown = () => {
  const [deadline, setDeadline] = useState<number | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);

  useEffect(() => {
    if (deadline === null) return;

    const timer = window.setInterval(() => {
      const next = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));

      setRemainingSeconds(next);

      if (next === 0) setDeadline(null);
    }, 1000);

    return () => window.clearInterval(timer);
  }, [deadline]);

  /**
   * 초를 못 읽었으면(undefined) 카운트다운을 걸지 않고 false 를 돌려준다.
   * 언제 풀리는지 모르는 채로 잠가 두면 화면이 영구 잠김이 된다.
   */
  const start = useCallback((seconds: number | undefined) => {
    if (seconds === undefined || seconds <= 0) return false;

    setRemainingSeconds(Math.ceil(seconds));
    setDeadline(Date.now() + seconds * 1000);
    return true;
  }, []);

  const clear = useCallback(() => {
    setDeadline(null);
    setRemainingSeconds(0);
  }, []);

  return { remainingSeconds, isCountingDown: remainingSeconds > 0, start, clear };
};
