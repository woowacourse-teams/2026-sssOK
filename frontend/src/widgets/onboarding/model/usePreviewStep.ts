import { useEffect, useRef, useState } from "react";

import { usePrefersReducedMotion } from "./usePrefersReducedMotion";

/**
 * 단계마다 보여주는 시간. 업로드·다운로드 장면은 사진이 붙거나 골라지는 모습과 결과 토스트까지
 * 보여줘야 해서 링크 장면보다 길다. 한 바퀴에 8초 남짓 걸린다.
 */
export const PREVIEW_STEP_DURATIONS_MS = [2400, 2300, 3600] as const;

/**
 * 미리보기에서 지금 보여줄 단계와, 원하는 단계로 건너뛰는 함수.
 *
 * 움직임을 줄이라는 설정이면 스스로 넘기지 않는다. 아무것도 고르지 않았을 때는
 * `step` 이 `null` 이고, 그때는 세 단계를 한꺼번에 정적으로 보여준다.
 * `round` 는 단계를 고를 때마다 바뀌어, 진행 막대와 장면을 처음부터 다시 트는 데 쓴다.
 *
 * `paused` 동안은 시간을 세지 않는다. 풀리는 순간 round 를 바꿔 지금 단계를 처음부터 다시 튼다.
 * 홈 인트로가 화면을 덮고 있는 동안 첫 단계가 몰래 지나가 버리지 않게 하는 데 쓴다.
 */
export const usePreviewStep = (paused = false) => {
  const stepCount = PREVIEW_STEP_DURATIONS_MS.length;
  const reducedMotion = usePrefersReducedMotion();
  const [step, setStep] = useState(0);
  const [hasPicked, setHasPicked] = useState(false);
  const [round, setRound] = useState(0);
  const wasPaused = useRef(paused);

  useEffect(() => {
    if (wasPaused.current && !paused) setRound((current) => current + 1);
    wasPaused.current = paused;
  }, [paused]);

  useEffect(() => {
    if (reducedMotion || paused) return;

    // 단계를 고르면 round 가 바뀌어, 고른 단계부터 한 칸의 시간을 온전히 다시 센다
    const timer = window.setTimeout(() => {
      setStep((current) => (current + 1) % stepCount);
    }, PREVIEW_STEP_DURATIONS_MS[step]);
    return () => window.clearTimeout(timer);
  }, [reducedMotion, paused, stepCount, step, round]);

  const goTo = (next: number) => {
    setStep(next);
    setHasPicked(true);
    setRound((current) => current + 1);
  };

  return { step: reducedMotion && !hasPicked ? null : step, goTo, round, reducedMotion };
};
