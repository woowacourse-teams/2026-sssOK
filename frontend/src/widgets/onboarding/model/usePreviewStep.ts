import { useEffect, useState } from "react";

import { usePrefersReducedMotion } from "./usePrefersReducedMotion";

/**
 * 단계마다 보여주는 시간. 장면 안의 움직임(말풍선이 차례로 뜨고, 사진이 붙고, 골라 받는 것)과
 * 결과 토스트까지 다 보여줄 만큼 둔다. 한 바퀴에 9초 남짓 걸린다.
 */
export const PREVIEW_STEP_DURATIONS_MS = [3100, 2300, 3600] as const;

/**
 * 미리보기에서 지금 보여줄 단계와, 원하는 단계로 건너뛰는 함수.
 *
 * 움직임을 줄이라는 설정이면 스스로 넘기지 않는다. 아무것도 고르지 않았을 때는
 * `step` 이 `null` 이고, 그때는 세 단계를 한꺼번에 정적으로 보여준다.
 * `round` 는 단계를 고를 때마다 바뀌어, 진행 막대와 장면을 처음부터 다시 트는 데 쓴다.
 */
export const usePreviewStep = () => {
  const stepCount = PREVIEW_STEP_DURATIONS_MS.length;
  const reducedMotion = usePrefersReducedMotion();
  const [step, setStep] = useState(0);
  const [hasPicked, setHasPicked] = useState(false);
  const [round, setRound] = useState(0);
  useEffect(() => {
    if (reducedMotion) return;

    // 단계를 고르면 round 가 바뀌어, 고른 단계부터 한 칸의 시간을 온전히 다시 센다
    const timer = window.setTimeout(() => {
      setStep((current) => (current + 1) % stepCount);
    }, PREVIEW_STEP_DURATIONS_MS[step]);
    return () => window.clearTimeout(timer);
  }, [reducedMotion, stepCount, step, round]);

  const goTo = (next: number) => {
    setStep(next);
    setHasPicked(true);
    setRound((current) => current + 1);
  };

  return { step: reducedMotion && !hasPicked ? null : step, goTo, round, reducedMotion };
};
